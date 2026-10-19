import { performance } from 'node:perf_hooks'
import { randomUUID } from 'node:crypto'
import { appendFile, mkdir, readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { createSession } from '../../../scripts/test-records/archive.mjs'
import { environmentEvidence, repositoryEvidence } from '../../../scripts/test-records/environment.mjs'
import { writeIndex, writeSessionReport } from '../../../scripts/test-records/catalog.mjs'

export function createOperationRecorder(session) {
  const record = (kind, data) => { try { return session?.record(kind, data) ?? false } catch { return false } }
  return {
    record,
    invoke: async (method, input, operation) => {
      const operationId = randomUUID()
      const started = performance.now()
      const requestedAtMs = Date.now()
      try {
        // Dispatch first. Evidence serialization must not delay sending a flight command.
        const pending = operation()
        record('operation-request', { operationId, method, requestedAtMs, input })
        const result = await pending
        record('operation-result', { operationId, method, elapsedMs: performance.now() - started, result })
        return result
      } catch (error) {
        record('operation-result', { operationId, method, elapsedMs: performance.now() - started, threw: true })
        throw error
      }
    },
  }
}
export function createDiagnosticSink(session, legacyDirectory = process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Sky Command', 'diagnostics') : path.join(process.cwd(), 'diagnostics')) {
  return { persist: async batch => {
    const stored = await session.persist('sdk-diagnostic', batch)
    if (!stored) return false
    // Keep the established diagnostic file for existing inspection tools.
    try {
      await mkdir(legacyDirectory, { recursive: true })
      await appendFile(path.join(legacyDirectory, 'relay-events.ndjson'), `${JSON.stringify(batch)}\n`, 'utf8')
    } catch { console.error('[test-records] 原诊断日志镜像写入失败，长期档案已保存') }
    return true
  } }
}
export async function createGatewayRecording({ label = '网页网关真机会话', metadata = {} } = {}) {
  let session
  try {
    const appDirectory = fileURLToPath(new URL('../', import.meta.url))
    const require = createRequire(import.meta.url)
    const sdkPackage = require.resolve('@xjicloud/sky-command-sdk/package.json')
    const sdk = JSON.parse(await readFile(sdkPackage, 'utf8'))
    const workspace = path.resolve(appDirectory, '../..')
    const sibling = path.dirname(workspace)
    session = await createSession({ kind: 'live', label, metadata: {
      ...await environmentEvidence(appDirectory),
      relatedRepositories: await Promise.all(['Sky Command', 'MSDK-relay'].map(name => repositoryEvidence(path.join(sibling, name)))),
      sdk: { version: sdk.version, desktopBundleSha256: createHash('sha256').update(await readFile(path.join(path.dirname(sdkPackage), 'dist/desktop.js'))).digest('hex') }, installedApk: 'unknown', aircraftFirmware: 'unknown',
      coverage: { source: 'gateway', sdkDiagnosticReports: true, applicationStateEvents: true, userOperations: true, browserAndMediaDiagnostics: true, videoRecording: false, aircraftPhysicalMotion: 'manual observation required' },
      ...metadata,
    } })
    await writeSessionReport(session.directory)
    await writeIndex()
    console.log(`[test-records] 真机档案：${session.directory}`)
  } catch { console.error('[test-records] 无法创建完整档案；控制功能继续运行，SDK 诊断保留原存储') }
  const recorder = createOperationRecorder(session)
  const timer = session ? setInterval(() => { void session.flush().catch(() => console.error('[test-records] 档案刷新失败')) }, 5000) : null
  timer?.unref()
  return {
    ...recorder,
    directory: session?.directory ?? null,
    diagnosticSink: session ? createDiagnosticSink(session) : undefined,
    close: async () => {
      if (timer) clearInterval(timer)
      if (!session) return
      try { await session.finish('unverified', { reason: 'gateway-stopped; live behavior needs explicit observations' }); await writeSessionReport(session.directory); await writeIndex() }
      catch { console.error('[test-records] 会话结束记录失败，档案标记为未正常结束') }
    },
  }
}
