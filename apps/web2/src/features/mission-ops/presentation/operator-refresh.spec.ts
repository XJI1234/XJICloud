// @vitest-environment node
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

function refreshHarness(pendingMethod: string) {
  const pending = deferred<unknown>()
  let snapshot = { devices: [{ deviceId: 'A', waypoint: 1 }, { deviceId: 'B', waypoint: 2 }] }
  const invoke = vi.fn((method: string) => method === pendingMethod ? pending.promise : Promise.resolve(
    { ok: true, value: method === 'state-snapshot' ? snapshot : { hints: ['ws://test/relay'] } },
  ))
  const extract = (start: string, end: string) => bundle.slice(bundle.indexOf(start), bundle.indexOf(end, bundle.indexOf(start)))
  const source = [
    'var lastSnapshot = {}; var lastRelayHint = ""; var lastRoutePreview = null;',
    'var state = { workspace: "routes" };',
    extract('  var renderAbortError = ', '  var safeRenderInvoke = '),
    extract('  async function projectView(', '  var projectCached = '),
    extract('  var settledInvoke = ', '  function applyRouteMap('),
    '({ fetchOnce, projectView, snapshot: () => lastSnapshot, hint: () => lastRelayHint })',
  ].join('\n')
  const paint = vi.fn(async () => {})
  const api = runInNewContext(source, {
    bridge: () => ({ invoke }), console,
    projectCached: () => ({ missionDeviceId: 'A', streamDeviceId: 'B', selectedRoute: { routeId: 'r1' } }),
    read4: (value: Record<string, unknown> | null, key: string) => value?.[key],
    previewGeometry: (value: unknown) => value,
    paintScheduler: { request: paint },
  }) as {
    fetchOnce: (signal: AbortSignal) => Promise<void>
    projectView: () => Promise<unknown>
    snapshot: () => typeof snapshot
    hint: () => string
  }
  return { api, invoke, pending, paint, nextSnapshot: () => { snapshot = { devices: [{ deviceId: 'A', waypoint: 3 }, { deviceId: 'B', waypoint: 4 }] } } }
}

describe('operator status refresh isolation', () => {
  it.each(['stream-refresh', 'network-hint', 'route-preview'])('keeps updating devices while %s remains pending', async (method) => {
    const harness = refreshHarness(method)
    let completed = 0
    const first = harness.api.fetchOnce(new AbortController().signal).then(() => { completed += 1 })
    await vi.waitFor(() => expect(completed).toBe(1), { timeout: 100 })
    expect(harness.api.snapshot().devices[1]?.waypoint).toBe(2)
    harness.nextSnapshot()
    await harness.api.fetchOnce(new AbortController().signal)
    expect(harness.api.snapshot().devices[1]?.waypoint).toBe(4)
    expect(harness.invoke.mock.calls.filter(([name]) => name === method)).toHaveLength(1)
    harness.pending.resolve({ ok: true, value: { hints: ['ws://recovered/relay'] } })
    await first
  })

  it('returns the current device view without waiting for the network hint', async () => {
    const harness = refreshHarness('network-hint')
    let completed = false
    void harness.api.projectView().then(() => { completed = true })
    await vi.waitFor(() => expect(completed).toBe(true), { timeout: 100 })
    expect(harness.api.snapshot().devices[1]?.deviceId).toBe('B')
    harness.pending.resolve(undefined)
  })

  it.each(['stream-refresh', 'network-hint'])('allows the next %s read after a failed request', async (method) => {
    const harness = refreshHarness(method)
    await harness.api.fetchOnce(new AbortController().signal)
    harness.pending.reject(new Error('auxiliary service unavailable'))
    await vi.waitFor(async () => {
      await harness.api.fetchOnce(new AbortController().signal)
      expect(harness.invoke.mock.calls.filter(([name]) => name === method).length).toBeGreaterThan(1)
    }, { timeout: 100 })
    expect(harness.api.snapshot().devices[1]?.waypoint).toBe(2)
  })

  it('discards a snapshot that arrives after its refresh was cancelled', async () => {
    const harness = refreshHarness('state-snapshot')
    const controller = new AbortController()
    const refresh = harness.api.fetchOnce(controller.signal)
    controller.abort()
    await refresh
    harness.pending.resolve({ ok: true, value: { devices: [{ deviceId: 'stale' }] } })
    await Promise.resolve()
    expect(harness.api.snapshot()).toEqual({})
  })
})
