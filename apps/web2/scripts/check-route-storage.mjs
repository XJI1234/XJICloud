import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { DesktopApplication, DesktopUiGateway } from '@xjicloud/sky-command-sdk/desktop'
import { createRouteStorage } from './sky-route-storage.mjs'
import { attachFile } from '../../../scripts/test-records/catalog.mjs'

// A separate SDK object graph with no start(), sockets, devices or aircraft commands.
function application() {
  const created = DesktopApplication.create({
    network: { listenPort: 19350, relayPort: 18081, manualHost: null },
    relay: { address: { host: '127.0.0.1', port: 18081 }, handshakeTimeoutMs: 1000, maxConnections: 2, commandTimeoutMs: 1000, missionTimeoutMs: 1000 },
    media: {
      dependencies: {
        rtmp: { listen: async () => {}, close: () => {} }, httpFlv: { listen: async () => {}, close: () => {} },
        fileFacts: { isExecutableFile: () => true }, processFactory: () => ({ launch: () => ({ terminate: () => {} }) }),
        player: { setSource: () => {}, clear: () => {} }, clock: () => 100,
      },
      options: { rtmpPort: 19350, httpFlvPort: 18080, health: { ingestTimeoutMs: 1000, playbackTimeoutMs: 1000 } },
      startInput: { interfaces: [{ name: 'isolated-test', enabled: true, internal: false, kind: 'wifi', ipv4: '192.168.1.8' }], manualHost: null, httpFlvRootDirectory: 'unused', ffmpegCandidates: [{ source: 'bundled', executablePath: 'unused' }] },
    },
    mission: { createMissionId: () => 'unused' }, flight: { now: () => 100, confirmation: { ttlMs: 1000, createConfirmationId: () => 'unused' } },
    hardwareReadiness: { lanAddressAvailable: true, legacyMediaAvailable: true }, now: () => 100,
  })
  assert.equal(created.ok, true)
  return { app: created.value, gateway: DesktopUiGateway.create({ application: created.value }) }
}
const { chromium } = await import(new URL('../../../../Sky Command/sdk/node_modules/playwright/index.mjs', import.meta.url).href)
const root = await mkdtemp(path.join(tmpdir(), 'xji-route-browser-'))
const directory = path.join(root, 'routes')
let current, store, browser
const methods = []
const errors = []
const bytes = Buffer.from('<kml xmlns="http://www.opengis.net/kml/2.2"><Document><Placemark><LineString><coordinates>120,30,10 121,31,20</coordinates></LineString></Placemark></Document></kml>')
async function rebuild() {
  await store?.close()
  current?.gateway.dispose()
  await current?.app.dispose()
  current = application()
  store = await createRouteStorage({ gateway: current.gateway, directory, onError: () => {} })
}
const decode = value => value && typeof value === 'object'
  ? typeof value.__skyBytes === 'string' ? new Uint8Array(Buffer.from(value.__skyBytes, 'base64'))
    : Object.fromEntries(Object.entries(value).map(([key, item]) => [key, decode(item)])) : value
try {
  await rebuild()
  browser = await chromium.launch({ headless: true, executablePath: process.env.XJI_TEST_BROWSER_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.on('pageerror', error => errors.push(error.message))
  await page.route('http://127.0.0.1:8767/invoke', async route => {
    const body = route.request().postDataJSON()
    methods.push(body.method)
    if (body.method === 'diagnostics-record') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"ok":true,"value":{"queued":true}}', headers: { 'access-control-allow-origin': '*' } })
      return
    }
    const method = { 'state-snapshot': 'state.snapshot', 'network-hint': 'network.hint', 'route-import': 'route.import', 'route-select': 'route.select', 'route-preview': 'route.preview', 'route-remove': 'route.remove', 'stream-refresh': 'stream.refresh', 'video-playback': 'video.playback' }[body.method]
    assert.ok(method, `Unexpected request: ${body.method}`)
    let result = await store.invoke(method, decode(body.input))
    if (method === 'state.snapshot' && result.ok) result = { ...result, value: { ...result.value, routeStorage: store.snapshot() } }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(result), headers: { 'access-control-allow-origin': '*' } })
  })
  // Remote imagery availability is outside the route-storage feature.
  await page.route(/https?:\/\/(?!localhost|127\.0\.0\.1)/, route => route.abort())
  const openRoutes = async () => {
    await page.goto('http://localhost:5176/sky-operator/index.html', { waitUntil: 'domcontentloaded' })
    await page.locator('[data-workspace="routes"]').click()
  }
  const savedCount = count => page.waitForFunction(count => document.getElementById('route-storage-status')?.textContent.includes(`已保存 ${count} 条`), count)
  const chooseFile = async () => {
    const chooser = page.waitForEvent('filechooser')
    await page.locator('#route-import').click()
    await (await chooser).setFiles({ name: '中文重启复测.kml', mimeType: 'application/vnd.google-earth.kml+xml', buffer: bytes })
  }
  const screenshot = async name => {
    if (!process.env.XJI_TEST_RECORDS_DIRECTORY) return
    const file = path.join(process.env.XJI_TEST_RECORDS_DIRECTORY, name)
    await page.screenshot({ path: file, fullPage: true })
    await attachFile(process.env.XJI_TEST_RECORDS_DIRECTORY, file)
  }
  await openRoutes(); await savedCount(0)
  await chooseFile(); await savedCount(1)
  await page.waitForFunction(() => document.getElementById('route-select-label')?.textContent.includes('中文重启复测.kml'))
  const index = JSON.parse(await readFile(path.join(directory, 'index.json'), 'utf8'))
  assert.deepEqual(await readFile(path.join(directory, `${index.routes[0].sha256}.kml`)), bytes)
  await openRoutes(); await savedCount(1)
  await rebuild(); await openRoutes(); await savedCount(1)
  await page.waitForFunction(() => document.getElementById('route-select-label')?.textContent.includes('中文重启复测.kml'))
  await screenshot('route-restored.png')
  await page.locator('#route-remove').click(); await savedCount(0)
  await rebuild(); await openRoutes(); await savedCount(0)
  assert.deepEqual(current.app.snapshot().workflow.routes, [])
  await screenshot('route-deleted.png')
  await store.close(); await writeFile(path.join(directory, 'index.json'), 'invalid-index')
  await rebuild(); await openRoutes()
  await page.waitForFunction(() => document.getElementById('route-storage-status')?.getAttribute('role') === 'alert')
  assert.equal(await readFile(path.join(directory, 'index.json'), 'utf8'), 'invalid-index')
  await screenshot('route-recovery-warning.png')
  await store.close(); await rm(path.join(directory, 'index.json')); await rebuild()
  await mkdir(path.join(directory, 'index.json'))
  await openRoutes(); await chooseFile()
  await page.waitForFunction(() => document.body.textContent.includes('航线未能保存到本机'))
  assert.deepEqual(current.app.snapshot().workflow.routes, [])
  assert.deepEqual(errors, [])
  assert.ok(methods.every(method => !/^(flight-|mission-|photo-|pairing-|stream-start|stream-stop)/.test(method)))
  console.log(JSON.stringify({ passed: ['browser import and exact file bytes', 'page reload', 'new SDK graph restores list and selection', 'delete survives restart', 'corrupt index shows warning without overwriting', 'save failure is visible and leaves no unsaved route'], browserErrors: errors, aircraftCommandsSent: 0 }, null, 2))
} finally {
  await browser?.close()
  await store?.close()
  current?.gateway.dispose()
  await current?.app.dispose()
  assert.ok(path.resolve(root).startsWith(path.resolve(tmpdir()) + path.sep))
  await rm(root, { recursive: true, force: true })
}
