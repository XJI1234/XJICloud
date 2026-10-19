import { access, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createGatewayRecording } from '../../apps/web2/scripts/sky-test-recording.mjs'
import { createJsonTail } from './tail.mjs'

// Read-only compatibility collector for a gateway already running before recording was installed.
const recording = await createGatewayRecording({ label: '旧网关只读旁路采集', metadata: {
  coverage: { source: 'polling-sidecar', sdkDiagnosticReports: 'legacy-file-tail-after-start', applicationStateEvents: '250ms snapshots; brief transitions may be missed', userOperations: false, browserAndMediaDiagnostics: 'legacy playback log tail; rotation gaps possible', videoRecording: false, aircraftPhysicalMotion: 'manual observation required' },
} })
if (!recording.directory) process.exit(1)
let stopping = false
const locations = [
  { file: path.join(process.env.LOCALAPPDATA || process.cwd(), 'Sky Command', 'diagnostics', 'relay-events.ndjson'), kind: 'sdk-diagnostic' },
  { file: path.join(tmpdir(), 'sky-command-web', 'diagnostics', 'playback.ndjson'), kind: 'playback-legacy' },
]
const tails = await Promise.all(locations.map(item => createJsonTail({ ...item, record: recording.record })))
await writeFile(path.join(recording.directory, 'observer.json'), JSON.stringify({ pid: process.pid, stopFile: path.join(recording.directory, 'stop-requested') }))
let previous = ''
let unavailable = false
async function collect() {
  if (stopping) return
  try {
    const response = await fetch('http://127.0.0.1:8767/invoke', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{"method":"state-snapshot"}', signal: AbortSignal.timeout(1500) })
    const result = await response.json()
    const serialized = JSON.stringify(result)
    if (serialized !== previous) { recording.record('application-state-polled', result); previous = serialized }
    if (unavailable) recording.record('source-restored', { source: 'gateway' })
    unavailable = false
  } catch {
    if (!unavailable) recording.record('source-unavailable', { source: 'gateway' })
    unavailable = true
  }
  // The phone log remains useful even if the control HTTP interface is unavailable.
  await Promise.all(tails.map(tail => tail()))
}
const stop = async () => { stopping = true; clearInterval(timer); await pending; await recording.close(); process.exit(0) }
let pending = null
const timer = setInterval(() => {
  if (!pending) pending = (async () => {
    try { await access(path.join(recording.directory, 'stop-requested')); setImmediate(() => { void stop() }); return } catch { /* no stop request */ }
    await collect()
  })().finally(() => { pending = null })
}, 250)
process.on('SIGINT', () => { void stop() })
process.on('SIGTERM', () => { void stop() })
console.log('[test-records] 只读旁路已运行，不发送控制命令。完整入口记录将在新网关启动后启用。')
