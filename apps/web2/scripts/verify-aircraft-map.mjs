// Run through scripts/test-records/run.mjs with the normal web2 dev gateway running.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { resolve, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { once } from 'node:events'

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const skyRoot = process.env.SKY_COMMAND_SOURCE_ROOT ?? resolve(appRoot, '../../../Sky-Command')
const requireSdk = createRequire(join(skyRoot, 'sdk/package.json'))
const requireWeb = createRequire(join(appRoot, 'package.json'))
const { chromium } = requireSdk('playwright')
const { WebSocket } = requireSdk('ws')
const { RelayFrameCodec } = requireWeb('@xjicloud/sky-command-sdk/protocol')
const { jsonFields } = requireWeb('@xjicloud/sky-command-sdk')
const evidence = process.env.XJI_TEST_RECORDS_DIRECTORY
assert.ok(evidence, 'Use the recorded runner')
await mkdir(join(evidence, 'screenshots'), { recursive: true })
const phones = []
const forbidden = []
let timer
let browser
const errors = []
async function phone(id, lat, lon, heading) {
  const socket = new WebSocket('ws://127.0.0.1:8080/relay')
  const send = frame => { const encoded = RelayFrameCodec.encode(frame); assert.ok(encoded.ok); socket.send(encoded.value) }
  const item = { id, socket, send, seq: 0, sample: 1, frozen: false, observed: Date.now(), heading, lat, lon, gps: true }
  phones.push(item)
  socket.on('message', bytes => {
    const decoded = RelayFrameCodec.decode(new Uint8Array(bytes))
    if (decoded.kind !== 'decoded') return
    const frame = decoded.frame
    if (frame.type === 'paired') item.paired = true
    if (frame.type === 'command') {
      if (frame.command.name !== 'telemetry.read') forbidden.push(frame.command.name)
      send({ type: 'command-result', id: frame.id, ok: true, detail: 'simulation read only' })
    }
  })
  await once(socket, 'open')
  send({ type: 'hello', deviceId: id, protocolVersion: '1' })
  await poll(() => item.paired)
  return item
}
async function poll(predicate, timeout = 15_000) {
  const end = Date.now() + timeout
  while (!await predicate()) { assert.ok(Date.now() < end, 'Condition timed out'); await new Promise(done => setTimeout(done, 100)) }
}
function publish(item) {
  if (item.socket.readyState !== WebSocket.OPEN) return
  if (!item.frozen) { item.sample++; item.observed = Date.now() }
  item.send({ type: 'telemetry', payload: { kind: 'object', fields: jsonFields({
    telemetrySequence: ++item.seq, sdkRegistered: true, sdkAvailability: 'READY',
    remoteController: 'CONNECTED', flightController: 'CONNECTED', aircraft: 'CONNECTED',
    airLink: 'CONNECTED', camera: 'CONNECTED', aircraftModel: 'DJI Mini 4 Pro', remoteControllerModel: 'RC-N2',
    isFlying: true, motorsOn: true, flightMode: 'GPS_NORMAL', battery: 'CONNECTED', batteryPercent: 80,
    latitude: item.lat, longitude: item.lon, altitudeMeters: 35,
    headingDegrees: item.heading, gpsIsValid: item.gps,
    positionSampleId: `${item.id}:${item.sample}`, positionAgeMillis: Date.now() - item.observed,
    locationAltitudeMeters: 35, takeoffAltitudeMeters: 80,
  }) }, capabilities: { kind: 'object', fields: jsonFields({}) } })
}
try {
  const first = await phone('map-sim-1', 32.0602, 118.7969, 0)
  const second = await phone('map-sim-2', 32.0607, 118.7980, 90)
  const missing = await phone('map-sim-no-gps', null, null, null)
  timer = setInterval(() => phones.forEach(publish), 300)
  phones.forEach(publish)
  console.log('SDK navigation build:', requireWeb('@xjicloud/sky-command-sdk/package.json').version)
  browser = await chromium.launch({ channel: process.env.AIRCRAFT_BROWSER_CHANNEL ?? 'chrome', headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  page.on('pageerror', error => errors.push(error.message))
  // Expose the existing viewer for assertions only; production files remain unchanged.
  await page.route('**/sky-operator/console.js*', async route => {
    const body = await readFile(join(appRoot, 'public/sky-operator/console.js'), 'utf8')
    const target = 'viewer = new Cesium.Viewer(host, createOrbitViewerOptions());'
    assert.ok(body.includes(target))
    await route.fulfill({ contentType: 'text/javascript', body: body.replace(target, `${target}\nglobalThis.__aircraftTestViewer = viewer;`) })
  })
  await page.goto('http://localhost:5176/sky-operator/index.html?workspace=routes')
  await page.waitForFunction(() => globalThis.__aircraftTestViewer?.dataSources.getByName('aircraft-positions')[0]?.entities.values.length === 2)
  const readMarkers = () => page.evaluate(() => {
    const viewer = globalThis.__aircraftTestViewer, time = Cesium.JulianDate.now()
    return viewer.dataSources.getByName('aircraft-positions')[0].entities.values.map(entity => {
      const point = Cesium.Cartographic.fromCartesian(entity.position.getValue(time))
      return { id: entity.id, label: entity.label.text.getValue(time), color: entity.billboard.color.getValue(time).toCssColorString(),
        image: entity.billboard.image.getValue(time), rotation: entity.billboard.rotation.getValue(time),
        latitude: Cesium.Math.toDegrees(point.latitude), longitude: Cesium.Math.toDegrees(point.longitude), height: point.height }
    })
  })
  let markers = await readMarkers()
  console.log('Initial simulated markers:', JSON.stringify(markers))
  const raw = await fetch('http://127.0.0.1:8767/invoke', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ method: 'state-snapshot' }) }).then(result => result.json())
  await writeFile(join(evidence, 'simulated-gateway-snapshot.json'), JSON.stringify(raw, null, 2))
  assert.ok(markers.every(marker => marker.label.includes('实时位置') && marker.label.includes('平面定位')))
  assert.ok(markers.every(marker => Math.abs(marker.height) < 0.01))
  await page.evaluate(() => globalThis.__aircraftTestViewer.camera.setView({ destination: Cesium.Cartesian3.fromDegrees(118.7974, 32.0604, 900) }))
  async function clickAircraft(id) {
    const point = await page.evaluate(id => {
      const viewer = globalThis.__aircraftTestViewer
      const entity = viewer.dataSources.getByName('aircraft-positions')[0].entities.getById(`aircraft:${id}`)
      const point = Cesium.SceneTransforms.worldToWindowCoordinates(viewer.scene, entity.position.getValue(Cesium.JulianDate.now()))
      const box = viewer.scene.canvas.getBoundingClientRect()
      return { x: point.x + box.left, y: point.y + box.top }
    }, id)
    await page.mouse.click(point.x, point.y)
    await page.locator('.aircraft-map-info').waitFor({ state: 'visible' })
  }
  await clickAircraft(first.id)
  assert.match(await page.locator('.aircraft-map-info').innerText(), /相对起飞点高度：35.0 米/)
  assert.match(await page.locator('.aircraft-map-info').innerText(), /机头航向：0.0°/)
  await page.screenshot({ path: join(evidence, 'screenshots/01-live-multiple.png') })
  first.lat += 0.00015; first.lon += 0.0002; first.heading = 180
  await poll(async () => (await readMarkers()).find(item => item.id.endsWith(first.id)).latitude > 32.0603)
  const prior = (await readMarkers()).find(item => item.id.endsWith(second.id)).rotation
  await page.evaluate(() => { const v = globalThis.__aircraftTestViewer; v.camera.setView({ orientation: { heading: Math.PI / 2, pitch: -Math.PI / 2, roll: 0 } }); v.scene.requestRender() })
  await poll(async () => Math.abs((await readMarkers()).find(item => item.id.endsWith(second.id)).rotation - prior) > 0.5)
  // A route layer clear must not remove the independent aircraft data source.
  await page.evaluate(() => globalThis.__aircraftTestViewer.entities.removeAll())
  assert.equal((await readMarkers()).length, 2)
  first.frozen = true
  await poll(async () => (await readMarkers()).find(item => item.id.endsWith(first.id)).label.includes('位置已过期'), 12_000)
  await page.screenshot({ path: join(evidence, 'screenshots/02-stale.png') })
  first.frozen = false
  await poll(async () => (await readMarkers()).find(item => item.id.endsWith(first.id)).label.includes('实时位置'))
  second.heading = null
  await poll(async () => decodeURIComponent((await readMarkers()).find(item => item.id.endsWith(second.id)).image).includes('<circle'))
  second.socket.close()
  await poll(async () => (await readMarkers()).find(item => item.id.endsWith(second.id)).label.includes('已断连'))
  await page.screenshot({ path: join(evidence, 'screenshots/03-disconnected.png') })
  await page.locator('[data-workspace="devices"]').click()
  await page.locator('[data-workspace="routes"]').click()
  assert.equal((await readMarkers()).length, 2)
  assert.deepEqual(forbidden, [], 'Marker viewing must not emit flight or task commands')
  assert.deepEqual(errors, [], 'No renderer exception')
  await writeFile(join(evidence, 'aircraft-browser-results.json'), JSON.stringify({
    passed: true, mode: 'simulated phone telemetry → real local gateway → shipped Cesium renderer',
    checks: ['no route', 'two aircraft', 'missing coordinates', 'planar height', 'read-only click', 'movement', 'heading', 'camera rotation', 'route clear isolation', 'stale despite battery telemetry', 'recovery', 'missing heading', 'disconnect retains last point', 'workspace switching', 'no flight/task commands'],
    markers: await readMarkers(), errors, forbidden,
    limitations: ['No physical aircraft', 'Imagery/building network availability and flight accuracy are not acceptance results'],
  }, null, 2))
  console.log('Aircraft browser simulation passed; screenshots and JSON saved in the test record.')
} finally {
  clearInterval(timer)
  phones.forEach(item => item.socket.terminate())
  await browser?.close()
}
