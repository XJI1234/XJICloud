import { spawn } from 'node:child_process'
import { open } from 'node:fs/promises'
import path from 'node:path'
import { createSession } from './archive.mjs'
import { environmentEvidence } from './environment.mjs'
import { writeIndex, writeSessionReport } from './catalog.mjs'

let session
let stdout
let stderr
try {
  const args = process.argv.slice(2)
  const separator = args.indexOf('--')
  if (separator < 0 || separator === args.length - 1) throw new Error('run.mjs --label <名称> -- <可执行文件> <参数...>')
  const options = args.slice(0, separator)
  if (options.length && (options.length !== 2 || options[0] !== '--label')) throw new Error('Unknown runner option')
  const command = args.slice(separator + 1)
  const executable = command[0] === 'node' ? process.execPath : command[0]
  session = await createSession({ kind: 'automated', label: options[1] || path.basename(command[0]), metadata: { ...await environmentEvidence(), command, cwd: process.cwd(), parentSessionId: process.env.XJI_TEST_PARENT_SESSION ?? null } })
  console.log(`[test-records] ${session.directory}`)
  stdout = await open(path.join(session.directory, 'stdout.log'), 'wx')
  stderr = await open(path.join(session.directory, 'stderr.log'), 'wx')
  let outputWrites = Promise.resolve()
  let outputError = false
  const output = (handle, terminal, bytes) => {
    terminal.write(bytes)
    // Backpressure child pipes while the disk write is pending; never drop test output.
    outputWrites = outputWrites.then(async () => { await handle.writeFile(bytes) }).catch(() => { outputError = true })
  }
  const childArgs = command.slice(1)
  // Vitest's ordinary console reporter remains visible; also retain machine-readable cases.
  const vitest = childArgs.findIndex(arg => /(^|[\\/])vitest(?:\.mjs)?$/.test(arg))
  if (vitest >= 0 && childArgs.includes('run') && !childArgs.some(arg => arg.startsWith('--reporter'))) {
    childArgs.push('--reporter=default', '--reporter=json', `--outputFile=${path.join(session.directory, 'test-results.json')}`)
  }
  session.record('execution-command', { executable, args: childArgs })
  const child = spawn(executable, childArgs, { cwd: process.cwd(), env: { ...process.env, XJI_TEST_PARENT_SESSION: session.sessionId, XJI_TEST_RECORDS_DIRECTORY: session.directory }, windowsHide: true, stdio: ['inherit', 'pipe', 'pipe'], shell: false })
  child.stdout.on('data', data => { child.stdout.pause(); output(stdout, process.stdout, data); void outputWrites.then(() => child.stdout.resume()) })
  child.stderr.on('data', data => { child.stderr.pause(); output(stderr, process.stderr, data); void outputWrites.then(() => child.stderr.resume()) })
  let spawnError = null
  child.on('error', error => { spawnError = error.code ?? 'SPAWN_FAILED' })
  const interrupt = signal => { session.record('runner-signal', { signal }); child.kill(signal) }
  const sigint = () => interrupt('SIGINT')
  const sigterm = () => interrupt('SIGTERM')
  process.on('SIGINT', sigint); process.on('SIGTERM', sigterm)
  const ended = await new Promise(resolve => child.once('close', (exitCode, signal) => resolve({ exitCode, signal })))
  process.off('SIGINT', sigint); process.off('SIGTERM', sigterm)
  await outputWrites
  await stdout.close(); stdout = null
  await stderr.close(); stderr = null
  const result = await session.finish(ended.signal ? 'interrupted' : ended.exitCode === 0 && !outputError && !spawnError ? 'passed' : 'failed', { ...ended, spawnError, outputError })
  await writeSessionReport(session.directory)
  await writeIndex()
  process.exitCode = ended.exitCode || (ended.signal || spawnError || outputError || result.writeErrors || result.dropped ? 1 : 0)
} catch (error) {
  console.error(`[test-records] 验证或归档失败：${error.code ?? error.message}`)
  await stdout?.close().catch(() => {})
  await stderr?.close().catch(() => {})
  if (session) await session.finish('failed', { runnerError: error.code ?? 'RUNNER_FAILED' }).catch(() => {})
  process.exitCode = 1
}
