// @vitest-environment node
import { mkdtemp, mkdir, readFile, readdir, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { describe, expect, it, vi } from 'vitest'
import { DesktopApplication, DesktopUiGateway } from '@xjicloud/sky-command-sdk/desktop'
// @ts-expect-error Node scripts are outside the browser TypeScript project.
import { createRouteStorage } from '../../../../scripts/sky-route-storage.mjs'

const kml = (longitude = 120) => new TextEncoder().encode(`<kml xmlns="http://www.opengis.net/kml/2.2"><Document><Placemark><LineString><coordinates>${longitude},30,10 121,31,20</coordinates></LineString></Placemark></Document></kml>`)
const digest = (bytes: Uint8Array) => createHash('sha256').update(bytes).digest('hex')
const unwrap = (result: any): any => result?.ok === true && result.value !== undefined ? unwrap(result.value) : result
const withDirectory = async (test: (directory: string) => Promise<void>) => {
  const root = await mkdtemp(path.join(tmpdir(), '航线保存 with spaces-'))
  try { await test(path.join(root, 'routes')) } finally { await rm(root, { recursive: true, force: true }) }
}
function application() {
  const made = DesktopApplication.create({
    network: { listenPort: 19350, relayPort: 18081, manualHost: null },
    relay: { address: { host: '127.0.0.1', port: 18081 }, handshakeTimeoutMs: 1000, maxConnections: 2, commandTimeoutMs: 1000, missionTimeoutMs: 1000 },
    media: {
      dependencies: {
        rtmp: { listen: async () => {}, close: () => {} }, httpFlv: { listen: async () => {}, close: () => {} },
        fileFacts: { isExecutableFile: () => true }, processFactory: () => ({ launch: () => ({ terminate: () => {} }) }),
        player: { setSource: () => {}, clear: () => {} }, clock: () => 100,
      },
      options: { rtmpPort: 19350, httpFlvPort: 18080, health: { ingestTimeoutMs: 1000, playbackTimeoutMs: 1000 } },
      startInput: { interfaces: [{ name: 'test', enabled: true, internal: false, kind: 'wifi', ipv4: '192.168.1.8' }], manualHost: null, httpFlvRootDirectory: 'unused', ffmpegCandidates: [{ source: 'bundled', executablePath: 'unused' }] },
    },
    mission: { createMissionId: () => 'test-mission' },
    flight: { now: () => 100, confirmation: { ttlMs: 1000, createConfirmationId: () => 'test-confirm' } },
    hardwareReadiness: { lanAddressAvailable: true, legacyMediaAvailable: true }, now: () => 100,
  })
  if (!made.ok) throw new Error(made.code)
  const app = made.value
  const gateway = DesktopUiGateway.create({ application: app })
  return { app, gateway, routes: () => (app.snapshot().workflow as any).routes as any[], close: async () => { gateway.dispose(); await app.dispose() } }
}
const indexAt = async (directory: string) => JSON.parse(await readFile(path.join(directory, 'index.json'), 'utf8'))

describe('persistent imported routes', () => {
  it('saves exact original KML bytes and restores through a new real SDK application without assignments or commands', () => withDirectory(async directory => {
    const first = application()
    const store = await createRouteStorage({ gateway: first.gateway, directory })
    const bytes = kml()
    expect(unwrap(await store.invoke('route.import', { fileName: '中文 航线.kml', bytes }))).toMatchObject({ status: 'imported' })
    expect(await readFile(path.join(directory, `${digest(bytes)}.kml`))).toEqual(Buffer.from(bytes))
    await store.close(); await first.close()
    const second = application()
    const invoke = vi.fn((method, input) => second.gateway.invoke(method, input))
    const restored = await createRouteStorage({ gateway: { invoke }, directory })
    expect(second.routes()).toMatchObject([{ displayName: '中文 航线.kml', classification: 'preview-only', waypointCount: 2 }])
    expect(restored.snapshot()).toMatchObject({ phase: 'ready', savedCount: 1, restoredCount: 1, failedCount: 0 })
    expect(invoke.mock.calls.every(([method]) => ['route.import', 'route.select'].includes(String(method)))).toBe(true)
    expect((second.app.snapshot().workflow as any).devices).toEqual([])
    await restored.close(); await second.close()
  }))

  it('keeps KMZ payload byte-for-byte and preserves upload-candidate classification after restoration', () => withDirectory(async directory => {
    const bytes = await readFile(new URL('./fixtures/persistence-route.kmz', import.meta.url))
    const first = application()
    const store = await createRouteStorage({ gateway: first.gateway, directory })
    expect(unwrap(await store.invoke('route.import', { fileName: '任务.kmz', bytes }))).toMatchObject({ status: 'imported', route: { classification: 'upload-candidate' } })
    expect(await readFile(path.join(directory, `${digest(bytes)}.kmz`))).toEqual(bytes)
    await store.close(); await first.close()
    const second = application()
    const restored = await createRouteStorage({ gateway: second.gateway, directory })
    expect(second.routes()).toMatchObject([{ classification: 'upload-candidate', sha256: digest(bytes) }])
    await restored.close(); await second.close()
  }))

  it('deduplicates content, retains the original name and persists same-name different-content routes independently', () => withDirectory(async directory => {
    const target = application()
    const store = await createRouteStorage({ gateway: target.gateway, directory })
    await Promise.all([store.invoke('route.import', { fileName: '原名.kml', bytes: kml() }), store.invoke('route.import', { fileName: '改名.kml', bytes: kml() }), store.invoke('route.import', { fileName: '原名.kml', bytes: kml(119) })])
    expect(target.routes().map(r => r.displayName)).toEqual(['原名.kml', '原名.kml'])
    expect((await indexAt(directory)).routes).toHaveLength(2)
    await store.close(); await target.close()
  }))

  it('captures a byte copy before awaiting queued work so caller mutations cannot alter the saved route', () => withDirectory(async directory => {
    const target = application()
    const store = await createRouteStorage({ gateway: target.gateway, directory })
    const input = kml()
    const original = input.slice()
    const importing = store.invoke('route.import', { fileName: 'a.kml', bytes: input })
    input.fill(0)
    expect(unwrap(await importing).status).toBe('imported')
    expect(await readFile(path.join(directory, `${digest(original)}.kml`))).toEqual(Buffer.from(original))
    await store.close(); await target.close()
  }))

  it('deletes from the durable catalog so orphan raw files cannot reappear on restart', () => withDirectory(async directory => {
    const first = application()
    const store = await createRouteStorage({ gateway: first.gateway, directory })
    await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    expect(await store.invoke('route.remove', { routeId: first.routes()[0].routeId })).toMatchObject({ ok: true, value: { ok: true } })
    expect(first.routes()).toEqual([])
    expect((await indexAt(directory)).routes).toEqual([])
    await store.close(); await first.close()
    const second = application()
    const restored = await createRouteStorage({ gateway: second.gateway, directory })
    expect(second.routes()).toEqual([])
    await restored.close(); await second.close()
  }))

  it('does not change disk state when the real gateway refuses a removal', () => withDirectory(async directory => {
    const target = application()
    const refusal = { ok: true, value: { ok: false, code: 'ROUTE_ASSIGNED' } }
    const gateway = { invoke: (method: string, input: unknown) => method === 'route.remove' ? Promise.resolve(refusal) : target.gateway.invoke(method, input) }
    const store = await createRouteStorage({ gateway, directory })
    await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    const before = await indexAt(directory)
    expect(await store.invoke('route.remove', { routeId: target.routes()[0].routeId })).toBe(refusal)
    expect(await indexAt(directory)).toEqual(before)
    await store.close(); await target.close()
  }))

  it('rejects invalid input without saving files or bypassing the SDK importer', () => withDirectory(async directory => {
    const target = application()
    const store = await createRouteStorage({ gateway: target.gateway, directory })
    for (const input of [{ fileName: '../escape.kml', bytes: kml() }, { fileName: 'bad.kml', bytes: new TextEncoder().encode('invalid XML') }, { fileName: 'a.kml', bytes: kml(), extra: true }]) {
      const result = unwrap(await store.invoke('route.import', input))
      expect(result.status === 'rejected' || result.ok === false).toBe(true)
    }
    expect(target.routes()).toEqual([])
    expect((await readdir(directory)).filter(name => name.endsWith('.kml'))).toEqual([])
    await store.close(); await target.close()
  }))

  it('reports write failure without inserting an unsaved in-memory route', () => withDirectory(async directory => {
    const target = application()
    const store = await createRouteStorage({ gateway: target.gateway, directory, onError: () => {} })
    await mkdir(path.join(directory, 'index.json'))
    const result = unwrap(await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() }))
    expect(result).toMatchObject({ status: 'rejected', error: { code: 'ROUTE_STORAGE_FAILED' } })
    expect(target.routes()).toEqual([])
    await store.close(); await target.close()
  }))

  it.each(['corrupt', 'missing'])('restores healthy entries but protects %s files instead of overwriting their index', mode => withDirectory(async directory => {
    const first = application()
    const store = await createRouteStorage({ gateway: first.gateway, directory })
    await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    await store.invoke('route.import', { fileName: 'b.kml', bytes: kml(119) })
    await store.close(); await first.close()
    const originalIndex = await readFile(path.join(directory, 'index.json'), 'utf8')
    const brokenFile = path.join(directory, `${digest(kml())}.kml`)
    if (mode === 'missing') await rm(brokenFile)
    else await writeFile(brokenFile, 'corrupted')
    const second = application()
    const restored = await createRouteStorage({ gateway: second.gateway, directory, onError: () => {} })
    expect(second.routes()).toMatchObject([{ displayName: 'b.kml' }])
    expect(restored.snapshot()).toMatchObject({ phase: 'degraded', failedCount: 1 })
    expect(unwrap(await restored.invoke('route.import', { fileName: 'new.kml', bytes: kml(118) }))).toMatchObject({ status: 'rejected' })
    expect(await readFile(path.join(directory, 'index.json'), 'utf8')).toBe(originalIndex)
    await restored.close(); await second.close()
  }))

  it('protects unreadable index data and forwards unrelated operations immediately', () => withDirectory(async directory => {
    await mkdir(directory, { recursive: true })
    await writeFile(path.join(directory, 'index.json'), 'broken JSON')
    const result = { ok: false, code: 'original-sdk-result' }
    const gateway = { invoke: vi.fn(async () => result) }
    const store = await createRouteStorage({ gateway, directory, onError: () => {} })
    expect(store.snapshot().phase).toBe('degraded')
    expect(await store.invoke('flight.request', { action: 'land' })).toBe(result)
    expect(await readFile(path.join(directory, 'index.json'), 'utf8')).toBe('broken JSON')
    await store.close()
  }))

  it('leaves the in-memory route untouched when deleting cannot update the disk index', () => withDirectory(async directory => {
    const target = application()
    const store = await createRouteStorage({ gateway: target.gateway, directory, onError: () => {} })
    await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    await rm(path.join(directory, 'index.json'))
    await mkdir(path.join(directory, 'index.json'))
    expect(unwrap(await store.invoke('route.remove', { routeId: target.routes()[0].routeId }))).toMatchObject({ ok: false, code: 'ROUTE_STORAGE_FAILED' })
    expect(target.routes()).toHaveLength(1)
    await store.close(); await target.close()
  }))

  it('rolls back the disk index when the downstream gateway rejects import, preserving its exact reply', () => withDirectory(async directory => {
    const refusal = { ok: false, code: 'DISPOSED' }
    const store = await createRouteStorage({ gateway: { invoke: async () => refusal }, directory })
    expect(await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })).toBe(refusal)
    expect((await indexAt(directory)).routes).toEqual([])
    await store.close()
  }))

  it('protects further changes if a downstream refusal is followed by a disk rollback failure', () => withDirectory(async directory => {
    const refusal = { ok: true, value: { ok: false, code: 'ROUTE_ASSIGNED' } }
    const target = application()
    const gateway = { invoke: async (method: string, input: unknown) => {
      if (method !== 'route.remove') return target.gateway.invoke(method, input)
      await rm(path.join(directory, 'index.json'))
      await mkdir(path.join(directory, 'index.json'))
      return refusal
    } }
    const store = await createRouteStorage({ gateway, directory, onError: () => {} })
    await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    expect(await store.invoke('route.remove', { routeId: target.routes()[0].routeId })).toBe(refusal)
    expect(store.snapshot().phase).toBe('degraded')
    expect(target.routes()).toHaveLength(1)
    expect(unwrap(await store.invoke('route.import', { fileName: 'b.kml', bytes: kml(119) })).status).toBe('rejected')
    await store.close(); await target.close()
  }))

  it('does not hold flight calls behind pending route work and drains accepted work when closing', () => withDirectory(async directory => {
    const target = application()
    let release!: () => void
    const pending = new Promise<void>(resolve => { release = resolve })
    const entered = vi.fn()
    const flightReply = { ok: false, code: 'original-flight-reply' }
    const gateway = { invoke: async (method: string, input: unknown) => {
      if (method === 'flight.request') return flightReply
      if (method === 'route.import') { entered(); await pending }
      return target.gateway.invoke(method, input)
    } }
    const store = await createRouteStorage({ gateway, directory })
    const importing = store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })
    await vi.waitFor(() => expect(entered).toHaveBeenCalledOnce())
    expect(await store.invoke('flight.request', {})).toBe(flightReply)
    const closing = store.close()
    expect(unwrap(await store.invoke('route.import', { fileName: 'late.kml', bytes: kml(119) }))).toMatchObject({ status: 'rejected' })
    release()
    expect(unwrap(await importing).status).toBe('imported')
    await closing
    expect((await indexAt(directory)).routes).toHaveLength(1)
    await target.close()
  }))

  it('refuses a linked storage root without following or modifying its target', () => withDirectory(async directory => {
    const target = path.join(path.dirname(directory), 'target')
    await mkdir(target)
    await symlink(target, directory, 'junction')
    const store = await createRouteStorage({ gateway: { invoke: vi.fn() }, directory, onError: () => {} })
    expect(store.snapshot().phase).toBe('degraded')
    expect(unwrap(await store.invoke('route.import', { fileName: 'a.kml', bytes: kml() })).status).toBe('rejected')
    expect(await readdir(target)).toEqual([])
    await store.close()
  }))
})
