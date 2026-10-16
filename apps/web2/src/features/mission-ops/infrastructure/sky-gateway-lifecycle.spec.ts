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
  const createRelayDiscovery = vi.fn((_serverId: string, _instanceId: string, _port: number, _options: unknown) => discovery)
  let interfaces = { 'Wi-Fi': [{ family: 'IPv4', address: '192.168.1.20', internal: false }] }
  const networkInterfaces = vi.fn(() => interfaces)
  const networkMonitor = { snapshot: () => interfaces, close: vi.fn() }
  const createApplication = vi.fn((_options: unknown) => ({ ok: true, value: application }))
  const createGateway = vi.fn((_options: unknown) => ({}))
  const exit = vi.fn((code: number) => { if (code !== 0) throw new Error('startup failed') })
  const run = runInNewContext(`(async () => { ${source}\n })()`, {
    createServer: () => ({
      listen: (_port: number, _host: string, ready: () => void) => { events.push('control-ready'); ready() },
      close: () => { events.push('control-stop') },
    }),
    mkdirSync: vi.fn(),
    networkInterfaces,
    createNetworkMonitor: async () => networkMonitor,
    tmpdir: () => '/tmp', path, randomUUID: () => 'instance-id',
    DesktopApplication: { create: createApplication },
    DesktopUiGateway: { create: createGateway }, createMediaPorts: () => ({}),
    createRelayDiscovery, loadOrCreateServerId: () => 'server-id',
    process: { exit, on: (signal: string, listener: () => void) => { signals[signal] = listener } },
    console: { log: vi.fn(), error: vi.fn() },
  }) as Promise<void>
  return {
    run, events, signals, createRelayDiscovery, exit, networkInterfaces, networkMonitor, createApplication, createGateway,
    changeAddress: (address: string) => { interfaces = { 'Wi-Fi': [{ family: 'IPv4', address, internal: false }] } },
  }
}

describe('web relay discovery lifecycle', () => {
  it('advertises only after the relay and browser control are ready, then stops before the relay', async () => {
    const harness = await startGateway()
    await harness.run
    expect(harness.events).toEqual(['relay-start', 'control-ready', 'discovery-start'])
    expect(harness.createRelayDiscovery).toHaveBeenCalledWith('server-id', 'instance-id', 8080, expect.any(Object))
    harness.signals.SIGTERM!()
    await vi.waitFor(() => expect(harness.exit).toHaveBeenCalledWith(0))
    expect(harness.networkMonitor.close).toHaveBeenCalledOnce()
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

  it('reads current background network snapshots without enumerating adapters on the command thread', async () => {
    const harness = await startGateway()
    await harness.run
    const application = harness.createApplication.mock.calls[0]![0] as unknown as {
      media: { dependencies: { resolveEndpointHost: () => string } }
    }
    const gateway = harness.createGateway.mock.calls[0]![0] as { relayHint: () => string[] }
    harness.networkInterfaces.mockClear()
    for (let i = 0; i < 20; i++) {
      expect(application.media.dependencies.resolveEndpointHost()).toBe('192.168.1.20')
      expect(gateway.relayHint()).toEqual(['ws://192.168.1.20:8080/relay'])
    }
    expect(harness.networkInterfaces).not.toHaveBeenCalled()
    harness.changeAddress('192.168.2.30')
    expect(application.media.dependencies.resolveEndpointHost()).toBe('192.168.2.30')
    const discovery = harness.createRelayDiscovery.mock.calls[0]![3] as unknown as { interfaces: () => unknown }
    expect(discovery.interfaces()).toEqual(harness.networkMonitor.snapshot())
  })
})
