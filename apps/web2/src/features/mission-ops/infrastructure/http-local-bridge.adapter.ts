import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import { emptyMissionSnapshot, offlineBridgeHealth, type BridgeHealth, type LinkSegmentState, type MissionOpsSnapshot } from '@/features/mission-ops/domain/entities/bridge-status.entity'
import { commandRequiresConfirm, type MissionCommandName } from '@/features/mission-ops/domain/entities/mission-command.entity'
import { LOCAL_BRIDGE_DEFAULT_BASE_URL, LOCAL_BRIDGE_WAKE_PROTOCOL, type LocalBridgePort } from '@/features/mission-ops/domain/repositories/local-bridge.port'

type FetchLike = typeof fetch
type Envelope = { ok?: boolean; code?: string; value?: unknown }
type Row = Record<string, unknown>
const row = (value: unknown): Row => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Row : {}
const field = (value: unknown, key: string): unknown => row(value)[key]
const text = (value: unknown): string | null => typeof value === 'string' && value.length > 0 ? value : null
const number = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null
const link = (value: unknown): LinkSegmentState => value === 'connected' ? 'up' : value === 'disconnected' ? 'down' : 'unknown'
const timeout = (ms: number): AbortSignal => AbortSignal.timeout(ms)
const invokeTimeoutMs = (method: string): number => {
  if (method === 'video.playback') return 4_000
  return method.startsWith('mission.') ? 610_000 : 120_000
}
function bytesToBase64(bytes: Uint8Array): string {
  const chunkSize = 0x8000
  const pieces: string[] = []
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const chunk = bytes.subarray(index, index + chunkSize)
    const chars = new Array<string>(chunk.length)
    for (let offset = 0; offset < chunk.length; offset += 1) chars[offset] = String.fromCharCode(chunk[offset] ?? 0)
    pieces.push(chars.join(''))
  }
  return btoa(pieces.join(''))
}

function mapSnapshot(source: unknown, bridge: BridgeHealth): MissionOpsSnapshot {
  const workflow = field(source, 'workflow')
  const devices = field(workflow, 'devices')
  const device = Array.isArray(devices) ? devices[0] : undefined
  const connection = field(device, 'connection')
  const routes = field(workflow, 'routes')
  return {
    bridge, deviceId: text(field(device, 'deviceId')),
    link: { desktop: bridge.relayListening ? 'up' : 'down', remoteController: link(field(connection, 'remoteController')), aircraft: link(field(connection, 'flightController')) },
    batteryPercent: number(field(connection, 'batteryPercent')),
    missionPhase: text(field(field(device, 'mission'), 'phase')),
    liveStreamActive: field(field(connection, 'live'), 'streaming') === true,
    routeId: text(field(field(device, 'assignment'), 'routeId')),
    routeName: text(field(field(device, 'assignment'), 'routeName')),
    routes: Array.isArray(routes) ? routes.flatMap((route) => { const id = text(field(route, 'routeId')); return id ? [{ id, name: text(field(route, 'displayName')) ?? id }] : [] }) : [],
    videoUrl: null,
    altitudeMeters: number(field(field(connection, 'pose'), 'altitudeMeters')),
    satelliteCount: number(field(connection, 'gpsSatelliteCount')),
    flightMode: text(field(connection, 'flightMode')),
  }
}

export type HttpLocalBridgeOptions = { baseUrl?: string; wakeProtocol?: string; fetchImpl?: FetchLike; openExternal?: (url: string) => void }
export function createHttpLocalBridge(options: HttpLocalBridgeOptions = {}): LocalBridgePort {
  const baseUrl = (options.baseUrl ?? LOCAL_BRIDGE_DEFAULT_BASE_URL).replace(/\/$/, '')
  const fetchImpl = options.fetchImpl ?? fetch
  const openExternal = options.openExternal ?? ((url: string) => { const anchor = document.createElement('a'); anchor.href = url; anchor.style.display = 'none'; document.body.appendChild(anchor); anchor.click(); anchor.remove() })
  async function probeHealth(): Promise<Result<BridgeHealth>> {
    const checkedAtMs = Date.now()
    try {
      const response = await fetchImpl(`${baseUrl}/health`, { headers: { Accept: 'application/json' }, signal: timeout(1500) })
      if (!response.ok) return ok(offlineBridgeHealth(checkedAtMs))
      const data = row(await response.json())
      return ok({ online: data.ok === true, version: text(data.version), relayListening: data.relayListening === true, checkedAtMs })
    } catch { return ok(offlineBridgeHealth(checkedAtMs)) }
  }
  async function invoke(method: string, input: unknown): Promise<Result<unknown>> {
    try {
      const response = await fetchImpl(`${baseUrl}/api/v1/invoke`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ method, input }), signal: timeout(invokeTimeoutMs(method)) })
      const data = await response.json() as Envelope
      if (!response.ok || !data.ok || field(data.value, 'ok') === false || field(field(data.value, 'value'), 'ok') === false) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
      return ok(data.value)
    } catch { return err(new DomainError('BRIDGE_OFFLINE')) }
  }
  return {
    probeHealth,
    wakeLocalAgent: async () => { try { openExternal(options.wakeProtocol ?? LOCAL_BRIDGE_WAKE_PROTOCOL); return ok({ attempted: true as const }) } catch { return err(new DomainError('BRIDGE_LAUNCH_FAILED')) } },
    fetchSnapshot: async () => {
      const [error, health] = await probeHealth()
      if (error || !health) return err(error ?? new DomainError('BRIDGE_OFFLINE'))
      if (!health.online) return ok(emptyMissionSnapshot(health))
      try {
        const response = await fetchImpl(`${baseUrl}/api/v1/snapshot`, { headers: { Accept: 'application/json' }, signal: timeout(2500) })
        if (!response.ok) return err(new DomainError('BRIDGE_OFFLINE'))
        const data = await response.json() as Envelope
        if (!data.ok) return err(new DomainError('BRIDGE_OFFLINE'))
        const mapped = mapSnapshot(data.value, health)
        if (mapped.deviceId) {
          const [playbackError, playback] = await invoke('video.playback', { deviceId: mapped.deviceId })
          if (!playbackError) {
            const url = text(field(playback, 'url'))
            if (url && /^http:\/\/127\.0\.0\.1:18080\/live\/[A-Za-z0-9%._~-]+\.flv$/.test(url)) return ok({ ...mapped, videoUrl: url })
          }
        }
        return ok(mapped)
      } catch { return err(new DomainError('BRIDGE_OFFLINE')) }
    },
    dispatchCommand: async (command: MissionCommandName, deviceId: string) => {
      if (commandRequiresConfirm(command)) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
      const [error] = await invoke(command, { deviceId })
      return error ? err(error) : ok({ accepted: true as const })
    },
    invoke,
    requestFlight: async (deviceId, action) => {
      const [error, result] = await invoke('flight.request', { deviceId, action })
      if (error) return err(error)
      const confirmationId = text(field(field(field(result, 'value'), 'confirmation'), 'confirmationId'))
      return confirmationId ? ok(confirmationId) : err(new DomainError('BRIDGE_COMMAND_REJECTED'))
    },
    confirmFlight: async (deviceId, confirmationId) => {
      const [error] = await invoke('flight.confirm', { deviceId, confirmationId })
      return error ? err(error) : ok({ accepted: true as const })
    },
    cancelFlight: (deviceId, confirmationId) => invoke('flight.cancel', { deviceId, confirmationId }),
    importRoute: async (file: File) => {
      if (!/\.(kmz|kml)$/i.test(file.name) || file.size > 2 * 1024 * 1024) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
      try {
        const bytes = new Uint8Array(await file.arrayBuffer())
        const [error, result] = await invoke('route.import', { fileName: file.name, base64: bytesToBase64(bytes) })
        if (error) return err(error)
        const imported = field(result, 'value')
        const routeId = field(imported, 'status') === 'imported' ? text(field(field(imported, 'route'), 'routeId')) : null
        return routeId ? ok(routeId) : err(new DomainError('BRIDGE_COMMAND_REJECTED'))
      } catch { return err(new DomainError('BRIDGE_COMMAND_REJECTED')) }
    },
  }
}
