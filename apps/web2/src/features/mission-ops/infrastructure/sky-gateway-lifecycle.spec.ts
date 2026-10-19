// @vitest-environment node
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { EventEmitter } from 'node:events'
import { describe, expect, it, vi } from 'vitest'

const source = readFileSync(path.resolve(import.meta.dirname, '../../../../scripts/sky-command-gateway.mjs'), 'utf8')
  .replace(/^import .*\r?\n/gm, '')

async function startGateway(options: { startFails?: boolean; discoveryFails?: boolean } = {}) {
  const events: string[] = []
  const signals: Record<string, () => void> = {}
  let snapshot = { workflow: { devices: [] as Record<string, unknown>[] } }
  let nowMs = 50_000
  let listener: ((value: unknown) => void) | undefined
  const unsubscribe = vi.fn(() => { listener = undefined })
  let serve: (request: unknown, response: unknown) => void
  const application = {
    snapshot: () => snapshot,
    subscribe: (callback: (value: unknown) => void) => { listener = callback; return unsubscribe },
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
  const recording = { record: vi.fn(), close: vi.fn(async () => {}), diagnosticSink: { persist: vi.fn(async () => true) }, invoke: vi.fn(async (_method: string, _input: unknown, operation: () => Promise<unknown>) => operation()) }
  const createGateway = vi.fn((_options: unknown) => ({ invoke: async () => ({ ok: true, value: snapshot }) }))
  const routeStorage = { snapshot: () => ({ phase: 'ready', savedCount: 2, restoredCount: 2, failedCount: 0 }), close: vi.fn(async () => { events.push('routes-close') }), invoke: vi.fn(async () => ({ ok: true, value: snapshot })) }
  const exit = vi.fn((code: number) => { if (code !== 0) throw new Error('startup failed') })
  const run = runInNewContext(`(async () => { ${source}\n })()`, {
    createServer: (handler: typeof serve) => { serve = handler; return {
      listen: (_port: number, _host: string, ready: () => void) => { events.push('control-ready'); ready() },
      close: () => { events.push('control-stop') },
    } },
    Buffer,
    Date: class extends Date { static now() { return nowMs } },
    mkdirSync: vi.fn(),
    networkInterfaces,
    createNetworkMonitor: async () => networkMonitor,
    createPlaybackDiagnosticLog: () => ({ recordPlayback: () => true, recordMedia: () => {}, flush: async () => {} }),
    createGatewayRecording: async () => recording,
    createRouteStorage: async () => { events.push('routes-restore'); return routeStorage },
    playbackDiagnostic: () => ({ event: 'sample' }),
    mediaDiagnostic: () => null,
    tmpdir: () => '/tmp', path, randomUUID: () => 'instance-id',
    DesktopApplication: { create: createApplication },
    DesktopUiGateway: { create: createGateway }, createMediaPorts: () => ({}),
    createRelayDiscovery, loadOrCreateServerId: () => 'server-id',
    process: { exit, on: (signal: string, listener: () => void) => { signals[signal] = listener } },
    console: { log: vi.fn(), error: vi.fn() },
  }) as Promise<void>
  return {
    run, events, signals, createRelayDiscovery, exit, networkInterfaces, networkMonitor, createApplication, createGateway, unsubscribe, recording, routeStorage,
    advance: (ms: number) => { nowMs += ms },
    publish: (devices: Record<string, unknown>[]) => { snapshot = { workflow: { devices } }; listener?.(snapshot) },
    readSnapshot: () => new Promise<{ value: { workflow: { devices: { connection: Record<string, unknown> }[] } } }>((resolve) => {
      const request = Object.assign(new EventEmitter(), { method: 'POST', url: '/invoke', headers: {} })
      serve(request, { writeHead: vi.fn(), end: (body: string) => resolve(JSON.parse(body)) })
      request.emit('data', Buffer.from(JSON.stringify({ method: 'state-snapshot' })))
      request.emit('end')
    }),
    changeAddress: (address: string) => { interfaces = { 'Wi-Fi': [{ family: 'IPv4', address, internal: false }] } },
    invoke: (method: string, input?: unknown) => new Promise<unknown>((resolve) => {
      const request = Object.assign(new EventEmitter(), { method: 'POST', url: '/invoke', headers: {} })
      serve(request, { writeHead: vi.fn(), end: (body: string) => resolve(JSON.parse(body)) })
      request.emit('data', Buffer.from(JSON.stringify({ method, input })))
      request.emit('end')
    }),
  }
}

describe('web relay discovery lifecycle', () => {
  it('advertises only after the relay and browser control are ready, then stops before the relay', async () => {
    const harness = await startGateway()
    await harness.run
    expect(harness.events).toEqual(['relay-start', 'routes-restore', 'control-ready', 'discovery-start'])
    expect(harness.createRelayDiscovery).toHaveBeenCalledWith('server-id', 'instance-id', 8080, expect.any(Object))
    harness.signals.SIGTERM!()
    await vi.waitFor(() => expect(harness.exit).toHaveBeenCalledWith(0))
    expect(harness.networkMonitor.close).toHaveBeenCalledOnce()
    expect(harness.events).toEqual([
      'relay-start', 'routes-restore', 'control-ready', 'discovery-start',
      'discovery-stop', 'control-stop', 'routes-close', 'relay-stop', 'relay-dispose',
    ])
    expect(harness.recording.close).toHaveBeenCalledOnce()
  })

  it('does not advertise a relay that failed to start', async () => {
    const harness = await startGateway({ startFails: true })
    await expect(harness.run).rejects.toThrow('startup failed')
    expect(harness.createRelayDiscovery).not.toHaveBeenCalled()
    expect(harness.recording.close).toHaveBeenCalledOnce()
  })

  it('cleans a failed broadcaster while retaining manual connections', async () => {
    const harness = await startGateway({ discoveryFails: true })
    await expect(harness.run).resolves.toBeUndefined()
    expect(harness.events).toEqual(['relay-start', 'routes-restore', 'control-ready', 'discovery-start', 'discovery-stop'])
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

  it('records state changes and control calls while passing the diagnostic sink to the existing SDK', async () => {
    const harness = await startGateway()
    await harness.run
    expect(harness.createApplication.mock.calls[0]![0]).toMatchObject({ relay: { diagnosticSink: harness.recording.diagnosticSink } })
    harness.publish([landingDevice('NOT_SAFE_TO_LAND')])
    expect(harness.recording.record).toHaveBeenCalledWith('application-state', expect.objectContaining({ workflow: expect.any(Object) }))
    const result = await harness.invoke('flight-request', { deviceId: 'phone-1', action: 'land' })
    expect(result).toMatchObject({ ok: true })
    expect(harness.recording.invoke).toHaveBeenCalledWith('flight-request', { deviceId: 'phone-1', action: 'land' }, expect.any(Function))
    await harness.readSnapshot()
    expect(harness.recording.invoke).toHaveBeenCalledOnce()
  })

  it('uses persistent route operations and exposes storage health alongside the untouched SDK state', async () => {
    const harness = await startGateway()
    await harness.run
    await harness.invoke('route-import', { fileName: 'a.kml', bytes: { __skyBytes: 'AQI=' } })
    const call = harness.routeStorage.invoke.mock.calls[0] as unknown as [string, { fileName: string; bytes: Uint8Array }]
    expect(call[0]).toBe('route.import')
    expect(call[1].fileName).toBe('a.kml')
    expect(Array.from(call[1].bytes)).toEqual([1, 2])
    expect(await harness.readSnapshot()).toMatchObject({ value: { routeStorage: { phase: 'ready', savedCount: 2 } } })
  })
})

const landingDevice = (state: string, extra: Record<string, unknown> = {}, deviceId = 'phone-1', connectionEpoch = 1) => ({
  deviceId, connectionEpoch,
  connection: {
    flightController: 'connected', flightState: 'flying', motorsOn: true,
    landingProtectionState: state, landingConfirmationNeeded: false, telemetryReceivedAtMs: 50_000,
    ...extra,
  },
})

describe('landing protection notice retention', () => {
  it('retains a brief unsafe report between browser polls, without changing the raw state or extending its timestamp', async () => {
    const test = await startGateway()
    await test.run
    test.publish([landingDevice('NOT_SAFE_TO_LAND')])
    test.advance(60)
    test.publish([landingDevice('NONE', { telemetryReceivedAtMs: 50_060 })])
    test.advance(940)
    const result = await test.readSnapshot()
    expect(result.value.workflow.devices[0]!.connection).toMatchObject({ landingProtectionState: 'NONE', landingProtectionNoticeAtMs: 50_000 })
    test.advance(19_001)
    expect((await test.readSnapshot()).value.workflow.devices[0]!.connection).not.toHaveProperty('landingProtectionNoticeAtMs')
    test.signals.SIGTERM!()
    await vi.waitFor(() => expect(test.unsubscribe).toHaveBeenCalledOnce())
  })

  it.each([
    { landingProtectionState: 'SAFE_TO_LAND' },
    { landingConfirmationNeeded: true },
    { flightState: 'grounded', motorsOn: false },
    { flightController: 'disconnected' },
  ])('clears a retained warning when subsequent facts resolve it: %j', async (extra) => {
    const test = await startGateway()
    await test.run
    test.publish([landingDevice('NOT_SAFE_TO_LAND')])
    expect((await test.readSnapshot()).value.workflow.devices[0]!.connection.landingProtectionNoticeAtMs).toBe(50_000)
    test.publish([landingDevice('NONE', extra)])
    expect((await test.readSnapshot()).value.workflow.devices[0]!.connection).not.toHaveProperty('landingProtectionNoticeAtMs')
  })

  it('keeps warnings isolated by phone and connection, and discards removed phones', async () => {
    const test = await startGateway()
    await test.run
    test.publish([landingDevice('NOT_SAFE_TO_LAND'), landingDevice('NONE', {}, 'phone-2')])
    let result = await test.readSnapshot()
    expect(result.value.workflow.devices[0]!.connection.landingProtectionNoticeAtMs).toBe(50_000)
    expect(result.value.workflow.devices[1]!.connection).not.toHaveProperty('landingProtectionNoticeAtMs')
    test.publish([landingDevice('NONE', {}, 'phone-1', 2)])
    expect((await test.readSnapshot()).value.workflow.devices[0]!.connection).not.toHaveProperty('landingProtectionNoticeAtMs')
    test.publish([landingDevice('NOT_SAFE_TO_LAND')])
    test.publish([])
    test.publish([landingDevice('NONE')])
    result = await test.readSnapshot()
    expect(result.value.workflow.devices[0]!.connection).not.toHaveProperty('landingProtectionNoticeAtMs')
  })
})
