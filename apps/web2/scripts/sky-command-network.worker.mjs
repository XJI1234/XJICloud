import { parentPort } from 'node:worker_threads'
import { networkInterfaces } from 'node:os'

function refresh() {
  try { parentPort.postMessage({ ok: true, interfaces: networkInterfaces() }) }
  catch { parentPort.postMessage({ ok: false }) }
  // One scan at a time; slow scans cannot accumulate a queue.
  setTimeout(refresh, 1000)
}

refresh()
