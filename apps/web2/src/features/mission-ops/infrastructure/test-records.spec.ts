// @vitest-environment node
import { appendFile, mkdtemp, mkdir, readFile, readdir, rename, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFile, spawn } from 'node:child_process'
import { promisify } from 'node:util'
import { describe, expect, it } from 'vitest'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { createSession, writeJson } from '../../../../../../scripts/test-records/archive.mjs'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { listSessions, compareSessions, markSession, attachFile } from '../../../../../../scripts/test-records/catalog.mjs'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { createOperationRecorder, createDiagnosticSink } from '../../../../scripts/sky-test-recording.mjs'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { createJsonTail } from '../../../../../../scripts/test-records/tail.mjs'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { repositoryEvidence } from '../../../../../../scripts/test-records/environment.mjs'

const runner = fileURLToPath(new URL('../../../../../../scripts/test-records/run.mjs', import.meta.url))
const lines = async (directory: string) => (await readFile(path.join(directory, 'events.ndjson'), 'utf8')).trim().split('\n').filter(Boolean).map(line => JSON.parse(line))
const temporary = async (test: (root: string) => Promise<void>) => {
  const root = await mkdtemp(path.join(tmpdir(), '测试档案 with spaces-'))
  try { await test(root) } finally { await rm(root, { recursive: true, force: true }) }
}
const run = (root: string, args: string[]) => new Promise<{ code: number | null; output: string }>((resolve, reject) => {
  const child = spawn(process.execPath, [runner, '--label', '中文测试', '--', ...args], { env: { ...process.env, XJI_TEST_RECORDS_ROOT: root } })
  let output = ''
  child.stdout.on('data', data => { output += data.toString() })
  child.stderr.on('data', data => { output += data.toString() })
  child.on('error', reject)
  child.on('close', code => resolve({ code, output }))
})

describe('long-lived test evidence', () => {
  it('captures the actual Git version and untracked evidence when invoked from a nested package', () => temporary(async root => {
    const git = async (...args: string[]) => promisify(execFile)('git', args, { cwd: root, windowsHide: true })
    await git('init')
    await writeFile(path.join(root, 'tracked.ts'), 'export const version = 1\n')
    await git('add', 'tracked.ts')
    await git('-c', 'user.name=Test Recorder', '-c', 'user.email=recorder@example.invalid', 'commit', '-m', 'fixture')
    await writeFile(path.join(root, 'tracked.ts'), 'export const version = 2\n')
    const nested = path.join(root, 'apps', 'web')
    await mkdir(nested, { recursive: true })
    await writeFile(path.join(nested, 'new.ts'), 'export const newBehavior = true\n')
    await writeFile(path.join(root, '.env'), 'SECRET=do-not-copy')
    const evidence = await repositoryEvidence(nested)
    expect(evidence.commit).toMatch(/^[0-9a-f]{40}$/)
    expect(evidence.patch).toContain('export const version = 2')
    expect(evidence.untrackedFiles).toEqual([expect.objectContaining({ file: 'apps/web/new.ts', sha256: expect.any(String) })])
    expect(JSON.stringify(evidence)).not.toContain('do-not-copy')
  }))

  it('publishes valid JSON while multiple independent writers update the same catalog file', () => temporary(async root => {
    const target = path.join(root, 'summary.json')
    await Promise.all(Array.from({ length: 50 }, (_, i) => writeJson(target, { revision: i, content: '证据'.repeat(100) })))
    expect(JSON.parse(await readFile(target, 'utf8'))).toMatchObject({ revision: expect.any(Number), content: '证据'.repeat(100) })
  }))

  it('keeps concurrent sessions separate, persists SDK facts unchanged and redacts secrets/binary data', () => temporary(async root => {
    const a = await createSession({ root, kind: 'live', label: '降落' })
    const b = await createSession({ root, kind: 'automated', label: '构建' })
    const sdk = { runId: 'phone-run', events: [{ sequence: 11, timestampMillis: 123, eventCode: 'SDK_ON_FAILURE', safeDetail: 'REQUEST_TIMEOUT', operationId: 'op-1' }] }
    expect(await a.persist('sdk-diagnostic', sdk)).toBe(true)
    a.record('request', { token: 'sensitive', authorization: 'Bearer secret', nested: { password: 'secret' }, bytes: new Uint8Array([1, 2, 3]) })
    await a.finish('unverified')
    await b.finish('passed')
    const events = await lines(a.directory)
    expect(events.find(event => event.kind === 'sdk-diagnostic').data).toEqual(sdk)
    const request = events.find(event => event.kind === 'request').data
    expect(JSON.stringify(request)).not.toMatch(/sensitive|Bearer secret|"password"/)
    expect(request.bytes).toMatchObject({ byteLength: 3, sha256: expect.any(String) })
    expect(events.map(event => event.sequence)).toEqual(events.map((_, i) => i + 1))
    expect(a.directory).not.toBe(b.directory)
    expect(await listSessions(root)).toHaveLength(2)
  }))

  it('bounds memory and records explicit evidence gaps; durable writes never acknowledge missing bytes', () => temporary(async root => {
    const session = await createSession({ root, kind: 'live', label: '压力' })
    for (let i = 0; i < 3000; i++) session.record('sample', { i })
    expect(session.record('oversized', { text: 'x'.repeat(2 * 1024 * 1024) })).toBe(false)
    await session.finish('unverified')
    const result = JSON.parse(await readFile(path.join(session.directory, 'result.json'), 'utf8'))
    expect(result.dropped).toBeGreaterThan(0)
    expect((await lines(session.directory)).some(event => event.kind === 'recording-gap')).toBe(true)
    expect(session.record('after-close', {})).toBe(false)
    expect(await session.persist('after-close', {})).toBe(false)
    const broken = await createSession({ root, kind: 'live', label: '写入失败', onError: () => {} })
    await rm(path.join(broken.directory, 'events.ndjson'))
    await mkdir(path.join(broken.directory, 'events.ndjson'))
    expect(await broken.persist('sdk-diagnostic', { eventCode: 'SDK_OK' })).toBe(false)
    await broken.finish('unverified')
    expect(JSON.parse(await readFile(path.join(broken.directory, 'result.json'), 'utf8')).writeErrors).toBeGreaterThan(0)
  }))

  it('indexes unfinished runs honestly and compares timing, SDK counts and human observations', () => temporary(async root => {
    const a = await createSession({ root, kind: 'live', label: '旧版本', metadata: { sdk: '0.2.6', cwd: path.join(root, 'old-checkout') } })
    const b = await createSession({ root, kind: 'live', label: '新版本', metadata: { sdk: '0.2.7', cwd: path.join(root, 'new-checkout') } })
    a.record('operation-result', { method: 'flight-request', elapsedMs: 1200, result: { ok: false } })
    b.record('operation-result', { method: 'flight-request', elapsedMs: 30, result: { ok: true } })
    a.record('sdk-diagnostic', { events: [{ eventCode: 'SDK_ON_FAILURE' }] })
    await a.finish('unverified')
    await b.flush()
    expect((await listSessions(root)).find(item => item.sessionId === b.sessionId).outcome).toBe('unfinished')
    await markSession(a.directory, { observation: '飞机确实悬停', inference: '原因尚未确认', expected: 'SDK 返回成功' })
    const image = path.join(root, '截图.png')
    await writeFile(image, new Uint8Array([1, 2, 3]))
    await attachFile(a.directory, image)
    await writeFile(path.join(a.directory, 'test-results.json'), JSON.stringify({ numTotalTests: 1, numPassedTests: 0, numFailedTests: 1, numPendingTests: 0, testResults: [{ name: path.join(root, 'old-checkout', 'landing.spec.ts'), assertionResults: [{ fullName: 'landing confirmation', status: 'failed', duration: 1200, failureMessages: ['expected continuation'] }] }] }))
    await writeFile(path.join(b.directory, 'test-results.json'), JSON.stringify({ numTotalTests: 1, numPassedTests: 1, numFailedTests: 0, numPendingTests: 0, testResults: [{ name: path.join(root, 'new-checkout', 'landing.spec.ts'), assertionResults: [{ fullName: 'landing confirmation', status: 'passed', duration: 30, failureMessages: [] }] }] }))
    const compared = await compareSessions(a.directory, b.directory)
    expect(compared.a.operations['flight-request'].maxMs).toBe(1200)
    expect(compared.b.operations['flight-request'].maxMs).toBe(30)
    expect(compared.a.sdkEvents.SDK_ON_FAILURE).toBe(1)
    expect(compared.a.markers[0].observation).toBe('飞机确实悬停')
    expect(compared.testCaseChanges.statusChanged[0]).toMatchObject({ before: { status: 'failed' }, after: { status: 'passed' } })
    expect((await readdir(path.join(a.directory, 'attachments')))).toHaveLength(1)
    await b.finish('unverified')
  }))

  it('retains JUnit case outcomes from attached Gradle reports instead of treating cached builds as executed tests', () => temporary(async root => {
    const session = await createSession({ root, kind: 'automated', label: 'Gradle XML' })
    const xml = path.join(root, 'TEST-example.xml')
    await writeFile(xml, '<testsuite><testcase name="降落" classname="Flight" time="0.015"/><testcase name="确认" classname="Flight" time="0.025"><failure message="expected">failure evidence</failure></testcase></testsuite>')
    await attachFile(session.directory, xml)
    await session.finish('failed')
    const compared = await compareSessions(session.directory, session.directory)
    expect(compared.a.automated.counts).toEqual({ total: 2, passed: 1, failed: 1, pending: 0 })
    expect(compared.a.automated.cases[0]).toMatchObject({ name: 'Flight::降落', durationMs: 15 })
  }))

  it('does not change operation return values or thrown errors when recording fails', async () => {
    const recorder = createOperationRecorder({ record: () => { throw new Error('disk failed') } })
    const result = { ok: false, code: 'SDK_ON_FAILURE', detail: 'REQUEST_TIMEOUT' }
    expect(await recorder.invoke('flight-request', { deviceId: 'phone' }, async () => result)).toBe(result)
    const failure = new Error('original')
    await expect(recorder.invoke('flight-request', {}, async () => { throw failure })).rejects.toBe(failure)
  })

  it('dispatches the original operation before serializing its evidence', async () => {
    const order: string[] = []
    const recorder = createOperationRecorder({ record: () => order.push('record') })
    await recorder.invoke('flight-request', {}, async () => { order.push('dispatch'); return { ok: true } })
    expect(order[0]).toBe('dispatch')
  })

  it('tails partial UTF-8 SDK reports without corrupting Chinese, and marks rotations explicitly', () => temporary(async root => {
    const file = path.join(root, 'phone.ndjson')
    await writeFile(file, '')
    const events: { kind: string; data: unknown }[] = []
    const tail = await createJsonTail({ file, kind: 'sdk-diagnostic', chunkBytes: 2, record: (kind: string, data: unknown) => events.push({ kind, data }) })
    const record = { safeDetail: '不适合降落', eventCode: 'SDK_ON_FAILURE' }
    const line = JSON.stringify(record) + '\n'
    await appendFile(file, line)
    for (let i = 0; i < Buffer.byteLength(line); i++) await tail()
    expect(events).toEqual([{ kind: 'sdk-diagnostic', data: record }])
    await rename(file, `${file}.previous`)
    await writeFile(file, JSON.stringify({ eventCode: 'NEW_RUN' }) + '\n')
    for (let i = 0; i < 50; i++) await tail()
    expect(events.some(event => event.kind === 'recording-gap')).toBe(true)
    expect(events.at(-1)?.data).toEqual({ eventCode: 'NEW_RUN' })
  }))

  it('fails visibly before executing a test when the archive root cannot be created', () => temporary(async root => {
    const blocked = path.join(root, 'not-a-directory')
    const sentinel = path.join(root, 'must-not-run')
    await writeFile(blocked, 'occupied')
    const result = await run(blocked, [process.execPath, '-e', `require('node:fs').writeFileSync(${JSON.stringify(sentinel)}, 'unexpected')`])
    expect(result.code).not.toBe(0)
    await expect(readFile(sentinel)).rejects.toThrow()
  }))

  it('acknowledges SDK reports only after durable persistence and keeps the established diagnostic mirror', () => temporary(async root => {
    const session = await createSession({ root, kind: 'live', label: 'SDK 落盘确认' })
    const directory = path.join(root, 'legacy')
    const sink = createDiagnosticSink(session, directory)
    const report = { deviceId: 'phone', runId: 'run', events: [{ sequence: 1, eventCode: 'SDK_ON_FAILURE', safeDetail: 'REQUEST_TIMEOUT' }] }
    expect(await sink.persist(report)).toBe(true)
    expect((await lines(session.directory)).find(item => item.kind === 'sdk-diagnostic').data).toEqual(report)
    expect(JSON.parse((await readFile(path.join(directory, 'relay-events.ndjson'), 'utf8')).trim())).toEqual(report)
    const unavailable = createDiagnosticSink({ persist: async () => false }, path.join(root, 'not-created'))
    expect(await unavailable.persist(report)).toBe(false)
    await expect(readdir(path.join(root, 'not-created'))).rejects.toThrow()
    await session.finish('unverified')
  }))

  it('archives full output and actual exit codes for success, failure, and missing executables', () => temporary(async root => {
    const passed = await run(root, [process.execPath, '-e', 'console.log("原始输出");console.error("stderr-evidence")'])
    expect(passed.code).toBe(0)
    const failed = await run(root, [process.execPath, '-e', 'console.log("failed-case");process.exit(7)'])
    expect(failed.code).toBe(7)
    const missing = await run(root, ['no-such-xji-test-executable'])
    expect(missing.code).not.toBe(0)
    const sessions = await listSessions(root)
    expect(sessions).toHaveLength(3)
    const success = sessions.find(item => item.outcome === 'passed')
    expect(await readFile(path.join(success.directory, 'stdout.log'), 'utf8')).toContain('原始输出')
    expect(await readFile(path.join(success.directory, 'stderr.log'), 'utf8')).toContain('stderr-evidence')
    expect(sessions.some(item => item.result?.exitCode === 7)).toBe(true)
  }), 30000)
})
