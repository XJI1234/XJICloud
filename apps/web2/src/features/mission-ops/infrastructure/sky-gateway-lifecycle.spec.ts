// @vitest-environment node
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const source = readFileSync(path.resolve(import.meta.dirname, '../../../../scripts/sky-command-gateway.mjs'), 'utf8')
  .replace(/^import .*\r?\n/gm, '')

async function startGateway(options: { startFails?: boolean; discoveryFails?: boolean } = {}) {
  const events: string[] = []
  const signals: Record<string, () => void> = {}
  const application = {
    start: async () => { events.push('relay-start'); return { ok: !options.startFails, code: 'MEDIA_START_FAILED' } },
    stop: async () => { events.push('relay-stop') },
    dispose: async () => { events.push('relay-dispose') },
  }
  const discovery = {
    start: () => { events.push('discovery-start'); if (options.discoveryFails) throw new Error('broadcast unavailable') },
    stop: () => { events.push('discovery-stop') },
  }
  const createRelayDiscovery = vi.fn(() => discovery)
  const exit = vi.fn((code: number) => { if (code !== 0) throw new Error('startup failed') })
  const run = runInNewContext(`(async () => { ${source}\n })()`, {
    createServer: () => ({
      listen: (_port: number, _host: string, ready: () => void) => { events.push('control-ready'); ready() },
      close: () => { events.push('control-stop') },
    }),
    mkdirSync: vi.fn(),
    networkInterfaces: () => ({ 'Wi-Fi': [{ family: 'IPv4', address: '192.168.1.20', internal: false }] }),
    tmpdir: () => '/tmp', path, randomUUID: () => 'instance-id',
    DesktopApplication: { create: () => ({ ok: true, value: application }) },
    DesktopUiGateway: { create: vi.fn() }, createMediaPorts: () => ({}),
    createRelayDiscovery, loadOrCreateServerId: () => 'server-id',
    process: { exit, on: (signal: string, listener: () => void) => { signals[signal] = listener } },
    console: { log: vi.fn(), error: vi.fn() },
  }) as Promise<void>
  return { run, events, signals, createRelayDiscovery, exit }
}

describe('web relay discovery lifecycle', () => {
  it('advertises only after the relay and browser control are ready, then stops before the relay', async () => {
    const harness = await startGateway()
    await harness.run
    expect(harness.events).toEqual(['relay-start', 'control-ready', 'discovery-start'])
    expect(harness.createRelayDiscovery).toHaveBeenCalledWith('server-id', 'instance-id', 8080, expect.any(Object))
    harness.signals.SIGTERM!()
    await vi.waitFor(() => expect(harness.exit).toHaveBeenCalledWith(0))
    expect(harness.events).toEqual([
      'relay-start', 'control-ready', 'discovery-start',
      'discovery-stop', 'control-stop', 'relay-stop', 'relay-dispose',
    ])
  })

  it('does not advertise a relay that failed to start', async () => {
    const harness = await startGateway({ startFails: true })
    await expect(harness.run).rejects.toThrow('startup failed')
    expect(harness.createRelayDiscovery).not.toHaveBeenCalled()
  })

  it('cleans a failed broadcaster while retaining manual connections', async () => {
    const harness = await startGateway({ discoveryFails: true })
    await expect(harness.run).resolves.toBeUndefined()
    expect(harness.events).toEqual(['relay-start', 'control-ready', 'discovery-start', 'discovery-stop'])
    expect(harness.exit).not.toHaveBeenCalled()
  })
})
