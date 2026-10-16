import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { DesktopApplication, DesktopUiGateway, createMediaPorts, createRelayDiscovery, loadOrCreateServerId } from '@xjicloud/sky-command-sdk/desktop'
import { createNetworkMonitor } from './sky-command-network.mjs'

const methods = {
  'state-snapshot': 'state.snapshot',
  'network-hint': 'network.hint',
  'device-refresh': 'device.refresh',
  'device-link-measure': 'device.link.measure',
  'pairing-start': 'pairing.start',
  'pairing-stop': 'pairing.stop',
  'route-import': 'route.import',
  'route-preview': 'route.preview',
  'route-select': 'route.select',
  'route-remove': 'route.remove',
  'assignment-assign': 'assignment.assign',
  'assignment-clear': 'assignment.clear',
  'mission-stage': 'mission.stage',
  'mission-upload': 'mission.upload',
  'mission-start': 'mission.start',
  'mission-pause': 'mission.pause',
  'mission-resume': 'mission.resume',
  'mission-stop': 'mission.stop',
  'mission-release': 'mission.release',
  'stream-start': 'stream.start',
  'stream-stop': 'stream.stop',
  'stream-refresh': 'stream.refresh',
  'stream-select': 'stream.select',
  'stream-clear': 'stream.clear',
  'photo-capture': 'photo.capture',
  'photo-fetch': 'photo.fetch',
  'settings-transmission-read': 'settings.transmission.read',
  'settings-transmission-write': 'settings.transmission.write',
  'settings-camera-read': 'settings.camera.read',
  'settings-camera-write': 'settings.camera.write',
  'flight-request': 'flight.request',
  'flight-confirm': 'flight.confirm',
  'flight-cancel': 'flight.cancel',
  'video-playback': 'video.playback',
}

function lanCards() {
  const virtual = /vmware|vmnet|vbox|virtualbox|vethernet|hyper-v|loopback|docker|wsl|tun|mihomo|clash|openvpn|nord|wireguard|hamachi|zerotier|radmin|\bvpn\b/i
  const cards = []
  for (const [name, addresses] of Object.entries(networkMonitor.snapshot())) {
    if (virtual.test(name)) continue
    for (const address of addresses ?? []) {
      if (address.internal || (address.family !== 'IPv4' && address.family !== 4) || address.address.startsWith('192.168.56.')) continue
      const wifi = /wi-?fi|wlan|wireless|无线/i.test(name)
      cards.push({ name, enabled: true, internal: false, kind: wifi ? 'wifi' : 'physical', ipv4: address.address, score: wifi ? 0 : 10 })
    }
  }
  return cards.sort((left, right) => left.score - right.score || left.ipv4.localeCompare(right.ipv4))
}

function decode(value) {
  if (Array.isArray(value)) return value.map(decode)
  if (value !== null && typeof value === 'object') {
    if (typeof value.__skyBytes === 'string' && Object.keys(value).length === 1) return new Uint8Array(Buffer.from(value.__skyBytes, 'base64'))
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, decode(item)]))
  }
  return value
}

const networkMonitor = await createNetworkMonitor({
  onError: () => console.error('[network] 网卡读取失败，等待后台刷新或重启服务'),
})
const preferred = lanCards()[0]
if (preferred === undefined) {
  console.error('未检测到可用的局域网 IPv4。请连接 WLAN 后重开开发服务。')
  process.exit(1)
}

const dataRoot = path.join(tmpdir(), 'sky-command-web')
const httpFlvRoot = path.join(dataRoot, 'http-flv')
const photosRoot = path.join(dataRoot, 'photos')
mkdirSync(httpFlvRoot, { recursive: true })
mkdirSync(photosRoot, { recursive: true })

const mediaPorts = createMediaPorts((event) => console.error(`[media] ${event.kind} ${event.detail}`))
let nextConfirmationId = 0
const created = DesktopApplication.create({
  network: { listenPort: 19500, relayPort: 8080, manualHost: preferred.ipv4 },
  legacyMediaRequired: true,
  relay: {
    address: { host: '0.0.0.0', port: 8080 },
    handshakeTimeoutMs: 15_000,
    maxConnections: 8,
    commandTimeoutMs: 120_000,
    missionTimeoutMs: 600_000,
  },
  media: {
    dependencies: {
      rtmp: mediaPorts.rtmp,
      httpFlv: mediaPorts.httpFlv,
      player: { setSource: () => undefined, clear: () => undefined },
      clock: () => Date.now(),
      resolveEndpointHost: () => lanCards()[0]?.ipv4 ?? null,
    },
    options: { rtmpPort: 19500, httpFlvPort: 18080, health: { ingestTimeoutMs: 20_000, playbackTimeoutMs: 45_000 } },
    startInput: { interfaces: [preferred], manualHost: null, httpFlvRootDirectory: httpFlvRoot },
  },
  mission: { createMissionId: (deviceId, routeId) => `mission-${deviceId}-${routeId}` },
  flight: { now: () => Date.now(), confirmation: { ttlMs: 15_000, createConfirmationId: () => `confirm-${++nextConfirmationId}` } },
  hardwareReadiness: { lanAddressAvailable: true, legacyMediaAvailable: true },
  photos: { directory: photosRoot },
  now: () => Date.now(),
})
if (!created.ok) {
  console.error(`桌面操作模块配置无效: ${created.code}`)
  process.exit(1)
}
const started = await created.value.start()
if (!started.ok) {
  console.error(started.code === 'MEDIA_START_FAILED'
    ? '图传服务未能启动。请确认 19500（RTMP）与 18080（HTTP-FLV）未被占用。'
    : `桌面操作模块启动失败: ${started.code}`)
  process.exit(1)
}

const gateway = DesktopUiGateway.create({
  application: created.value,
  relayHint: () => lanCards().map((card) => `ws://${card.ipv4}:8080/relay`),
})
const allowedOrigins = new Set(['http://localhost:5176', 'http://127.0.0.1:5176'])
const control = createServer((request, response) => {
  const origin = request.headers.origin
  if (typeof origin === 'string' && allowedOrigins.has(origin)) {
    response.setHeader('Access-Control-Allow-Origin', origin)
    response.setHeader('Vary', 'Origin')
    response.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS')
    response.setHeader('Access-Control-Allow-Headers', 'Content-Type')
  }
  if (request.method === 'OPTIONS') {
    response.writeHead(204)
    response.end()
    return
  }
  if (request.method !== 'POST' || request.url !== '/invoke') {
    response.writeHead(404)
    response.end()
    return
  }
  const chunks = []
  let size = 0
  request.on('data', (chunk) => {
    size += chunk.length
    if (size > 120_000_000) request.destroy()
    else chunks.push(chunk)
  })
  request.on('end', () => {
    void (async () => {
      try {
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8'))
        const name = typeof body.method === 'string' ? body.method : ''
        if (name === 'diagnostics-record') {
          response.writeHead(200, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ ok: true, value: { recorded: true } }))
          return
        }
        const method = methods[name]
        if (method === undefined) {
          response.writeHead(200, { 'content-type': 'application/json' })
          response.end(JSON.stringify({ ok: false, code: 'METHOD_NOT_ALLOWED' }))
          return
        }
        const input = Object.hasOwn(body, 'input') ? decode(body.input) : undefined
        const result = await gateway.invoke(method, input)
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify(result))
      } catch {
        response.writeHead(200, { 'content-type': 'application/json' })
        response.end(JSON.stringify({ ok: false, code: 'DEPENDENCY_FAILURE' }))
      }
    })()
  })
})
await new Promise((resolve) => control.listen(8767, '127.0.0.1', resolve))
let stopDiscovery = () => {}
try {
  const discovery = createRelayDiscovery(loadOrCreateServerId(dataRoot), randomUUID(), 8080, {
    interfaces: networkMonitor.snapshot,
    diagnostic: (code) => console.error(`[discovery] ${code}`),
  })
  stopDiscovery = discovery.stop
  discovery.start()
} catch (error) {
  stopDiscovery()
  console.error(`[discovery] 自动发现启动失败，仍可使用手动连接：${error instanceof Error ? error.message : String(error)}`)
}
console.log(`Phone relay ws://0.0.0.0:8080/relay`)
console.log(`Video RTMP 19500, HTTP-FLV http://127.0.0.1:18080`)
console.log(`Operator control http://127.0.0.1:8767/invoke`)

async function stop() {
  stopDiscovery()
  networkMonitor.close()
  control.close()
  await created.value.stop()
  await created.value.dispose()
  process.exit(0)
}
process.on('SIGINT', () => { void stop() })
process.on('SIGTERM', () => { void stop() })
