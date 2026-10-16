import { createSkyCommandClient, jsonFields, SkyCommandError, toPlainJson, type SkyCommandClient } from '@xjicloud/sky-command-sdk'
import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import { emptyMissionSnapshot, type BridgeHealth, type LinkSegmentState, type MissionOpsSnapshot } from '../domain/entities/bridge-status.entity'
import type { MissionCommandName } from '../domain/entities/mission-command.entity'
import type { FlightAction, LocalBridgePort } from '../domain/repositories/local-bridge.port'

const gatewayUrl = 'ws://127.0.0.1:8766/sdk'
const streamUrl = 'rtmp://127.0.0.1/live'
const waylineCommands: Partial<Record<MissionCommandName, string>> = {
  'mission.start': 'wayline.start',
  'mission.pause': 'wayline.pause',
  'mission.resume': 'wayline.resume',
  'mission.stop': 'wayline.stop',
  'mission.upload': 'wayline.upload',
}
const flightCommands: Record<FlightAction, string> = {
  takeoff: 'flight.takeoff',
  land: 'flight.land',
  'confirm-landing': 'flight.confirm-landing',
  'return-home': 'flight.return-home',
  'stop-takeoff': 'flight.stop-takeoff',
  'stop-auto-landing': 'flight.stop-auto-landing',
}

type StoredRoute = { id: string; name: string; fileName: string; bytes: Uint8Array }
type Plain = Record<string, unknown>
const text = (value: unknown): string | null => typeof value === 'string' && value.length > 0 ? value : null
const number = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null
const linkState = (value: unknown): LinkSegmentState => value === 'CONNECTED' ? 'up' : value === 'DISCONNECTED' ? 'down' : 'unknown'
const plainObject = (value: unknown): Plain => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Plain : {}

function rejected(): Result<never> {
  return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
}

export function createSdkLocalBridge(options: { url?: string; client?: SkyCommandClient } = {}): LocalBridgePort {
  let client = options.client ?? null
  const routes = new Map<string, StoredRoute>()
  let assigned: { deviceId: string; routeId: string } | null = null
  const pendingFlight = new Map<string, { action: FlightAction; confirmationId: string }>()
  const current = (): SkyCommandClient => {
    client ??= createSkyCommandClient({ url: options.url ?? gatewayUrl, connectTimeoutMs: 1500, reconnect: { maxAttempts: 4 } })
    return client
  }
  const connect = async (): Promise<boolean> => {
    const sdk = current()
    if (sdk.getSnapshot().status === 'connected') return true
    try {
      await sdk.connect()
      return true
    } catch {
      return false
    }
  }
  const health = (): BridgeHealth => {
    const sdk = current()
    const online = sdk.getSnapshot().status === 'connected'
    return { online, version: online ? '0.2.0' : null, relayListening: online && sdk.devices().length > 0, checkedAtMs: Date.now() }
  }
  const deviceId = (): string | null => current().devices()[0]?.deviceId ?? null
  const command = async (name: string, fields: Parameters<typeof jsonFields>[0], timeoutMs?: number): Promise<Result<{ accepted: true }>> => {
    const id = deviceId()
    if (!id) return err(new DomainError('BRIDGE_OFFLINE'))
    try {
      const outcome = await current().sendCommand(id, { name, fields: jsonFields(fields), ...(timeoutMs === undefined ? {} : { timeoutMs }) })
      return outcome.status === 'succeeded' ? ok({ accepted: true as const }) : rejected()
    } catch (error) {
      return err(new DomainError(error instanceof SkyCommandError && error.code === 'INVALID_INPUT' ? 'BRIDGE_COMMAND_REJECTED' : 'BRIDGE_OFFLINE'))
    }
  }
  return {
    probeHealth: async () => {
      await connect()
      return ok(health())
    },
    wakeLocalAgent: async () => (await connect() ? ok({ attempted: true as const }) : err(new DomainError('BRIDGE_LAUNCH_FAILED'))),
    fetchSnapshot: async () => {
      const bridge = health()
      if (!bridge.online) return ok(emptyMissionSnapshot(bridge))
      const id = deviceId()
      const telemetry = id ? current().latestTelemetry(id) : null
      const facts = plainObject(telemetry ? toPlainJson(telemetry.payload) : {})
      const phase = current().getSnapshot().relay?.missionPhases.find((item) => item.deviceId === id)
      const route = assigned && assigned.deviceId === id ? routes.get(assigned.routeId) : undefined
      const snapshot: MissionOpsSnapshot = {
        bridge,
        deviceId: id,
        link: { desktop: 'up', remoteController: linkState(facts.remoteController), aircraft: linkState(facts.flightController) },
        batteryPercent: number(facts.batteryPercent),
        missionPhase: phase?.phase ?? text(facts.missionPhase),
        liveStreamActive: facts.liveStreaming === true,
        routeId: route?.id ?? null,
        routeName: route?.name ?? null,
        routes: [...routes.values()].map((item) => ({ id: item.id, name: item.name })),
        videoUrl: null,
        altitudeMeters: number(facts.altitudeMeters),
        satelliteCount: number(facts.gpsSatelliteCount),
        flightMode: text(facts.flightMode),
      }
      return ok(snapshot)
    },
    dispatchCommand: async (name, targetDeviceId) => {
      if (deviceId() !== targetDeviceId) return err(new DomainError('BRIDGE_OFFLINE'))
      if (name === 'mission.upload') {
        const route = assigned?.deviceId === targetDeviceId ? routes.get(assigned.routeId) : undefined
        if (!route) return rejected()
        const payload = new ArrayBuffer(route.bytes.byteLength)
        new Uint8Array(payload).set(route.bytes)
        const digest = await crypto.subtle.digest('SHA-256', payload)
        const sha256 = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
        try {
          const sent = await current().sendMission(targetDeviceId, { missionId: route.id, fileName: route.fileName, bytes: new Uint8Array(payload), size: payload.byteLength, sha256 })
          if (sent.status !== 'succeeded') return rejected()
        } catch {
          return err(new DomainError('BRIDGE_OFFLINE'))
        }
      }
      const wire = waylineCommands[name]
      return wire ? command(wire, { confirm: true }, name === 'mission.upload' ? 120_000 : undefined) : rejected()
    },
    invoke: async (method, input) => {
      const body = plainObject(input)
      const id = text(body.deviceId)
      if (method === 'mission.stage') return id ? ok({ staged: true }) : rejected()
      if (method === 'assignment.assign') {
        const routeId = text(body.routeId)
        if (!id || !routeId || !routes.has(routeId)) return rejected()
        assigned = { deviceId: id, routeId }
        return ok({ assigned: true })
      }
      if (method === 'stream.start') return command('live-stream.start', { rtmpUrl: streamUrl })
      if (method === 'video.playback') return ok({})
      return rejected()
    },
    requestFlight: async (targetDeviceId, action) => {
      if (deviceId() !== targetDeviceId || !(action in flightCommands)) return rejected()
      const confirmationId = `sdk-${action}`
      pendingFlight.set(targetDeviceId, { action, confirmationId })
      return ok(confirmationId)
    },
    confirmFlight: async (targetDeviceId, confirmationId) => {
      const pending = pendingFlight.get(targetDeviceId)
      if (!pending || pending.confirmationId !== confirmationId) return rejected()
      pendingFlight.delete(targetDeviceId)
      return command(flightCommands[pending.action], { confirm: true })
    },
    cancelFlight: async (targetDeviceId, confirmationId) => {
      const pending = pendingFlight.get(targetDeviceId)
      if (!pending || pending.confirmationId !== confirmationId) return rejected()
      pendingFlight.delete(targetDeviceId)
      return ok({ cancelled: true })
    },
    importRoute: async (file) => {
      if (!/\.(kmz|kml)$/i.test(file.name) || file.size === 0 || file.size > 100 * 1024 * 1024) return rejected()
      const id = `route-${crypto.randomUUID()}`
      routes.set(id, { id, name: file.name, fileName: file.name, bytes: new Uint8Array(await file.arrayBuffer()) })
      return ok(id)
    },
  }
}
