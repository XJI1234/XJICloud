import { Worker } from 'node:worker_threads'

// Windows adapter enumeration can block for hundreds of milliseconds. Keep it
// outside the event loop that sends flight commands and receives their results.
export function createNetworkMonitor({ onError = () => {} } = {}) {
  const worker = new Worker(new URL('./sky-command-network.worker.mjs', import.meta.url))
  return new Promise((resolve, reject) => {
    let current = {}
    let ready = false
    let closed = false
    const monitor = {
      snapshot: () => current,
      close: () => {
        if (closed) return
        closed = true
        void worker.terminate()
      },
    }
    const failed = () => {
      if (closed) return
      current = {}
      onError()
      if (!ready) {
        monitor.close()
        reject(new Error('Network adapter enumeration failed'))
      }
    }
    worker.on('message', (message) => {
      if (closed) return
      if (!message.ok) { failed(); return }
      current = message.interfaces
      if (!ready) { ready = true; resolve(monitor) }
    })
    worker.on('error', failed)
    worker.on('exit', () => { if (!closed) failed() })
  })
}
