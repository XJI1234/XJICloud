// @vitest-environment node
import { EventEmitter } from 'node:events'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const source = readFileSync(path.resolve(import.meta.dirname, '../../../../scripts/sky-command-network.mjs'), 'utf8')
  .replace(/^import .*\r?\n/gm, '')
  .replace('export function', 'function')
  .replace('import.meta.url', '"file:///gateway/sky-command-network.mjs"')

function harness() {
  const worker = new EventEmitter() as EventEmitter & { terminate: ReturnType<typeof vi.fn> }
  worker.terminate = vi.fn(async () => 0)
  const onError = vi.fn()
  const create = runInNewContext(`${source}\ncreateNetworkMonitor`, {
    Worker: function () { return worker }, URL,
  }) as (options: { onError: () => void }) => Promise<{ snapshot: () => object; close: () => void }>
  return { worker, onError, ready: create({ onError }) }
}

describe('gateway background network monitor', () => {
  it('waits for the first scan and publishes changes without waiting for another scan', async () => {
    const test = harness()
    const settled = vi.fn()
    void test.ready.then(settled)
    await Promise.resolve()
    expect(settled).not.toHaveBeenCalled()
    test.worker.emit('message', { ok: true, interfaces: { wlan: ['192.168.1.2'] } })
    const monitor = await test.ready
    expect(monitor.snapshot()).toEqual({ wlan: ['192.168.1.2'] })
    test.worker.emit('message', { ok: true, interfaces: { wlan: ['192.168.2.3'] } })
    expect(monitor.snapshot()).toEqual({ wlan: ['192.168.2.3'] })
    monitor.close()
    monitor.close()
    test.worker.emit('message', { ok: true, interfaces: { wlan: ['late'] } })
    expect(monitor.snapshot()).toEqual({ wlan: ['192.168.2.3'] })
    expect(test.worker.terminate).toHaveBeenCalledOnce()
  })

  it('invalidates old addresses on scan failure and recovers after a successful scan', async () => {
    const test = harness()
    test.worker.emit('message', { ok: true, interfaces: { wlan: ['old'] } })
    const monitor = await test.ready
    test.worker.emit('message', { ok: false })
    expect(monitor.snapshot()).toEqual({})
    expect(test.onError).toHaveBeenCalledOnce()
    test.worker.emit('message', { ok: true, interfaces: { wlan: ['new'] } })
    expect(monitor.snapshot()).toEqual({ wlan: ['new'] })
    monitor.close()
  })

  it('rejects startup and closes the worker when the initial scan fails', async () => {
    const test = harness()
    const rejected = expect(test.ready).rejects.toThrow('Network adapter enumeration failed')
    test.worker.emit('error', new Error('worker failed'))
    await rejected
    expect(test.worker.terminate).toHaveBeenCalledOnce()
  })
})
