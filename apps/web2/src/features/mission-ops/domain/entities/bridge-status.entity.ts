export type BridgeHealth = {
  online: boolean
  version: string | null
  relayListening: boolean
  checkedAtMs: number
}

export type LinkSegmentState = 'up' | 'down' | 'unknown'

export type LinkChain = {
  desktop: LinkSegmentState
  remoteController: LinkSegmentState
  aircraft: LinkSegmentState
}

export type MissionOpsSnapshot = {
  bridge: BridgeHealth
  deviceId: string | null
  link: LinkChain
  batteryPercent: number | null
  missionPhase: string | null
  liveStreamActive: boolean
  routeId: string | null
  routeName: string | null
  routes: readonly { id: string; name: string }[]
  videoUrl: string | null
  altitudeMeters: number | null
  satelliteCount: number | null
  flightMode: string | null
}

export function offlineBridgeHealth(checkedAtMs = Date.now()): BridgeHealth {
  return {
    online: false,
    version: null,
    relayListening: false,
    checkedAtMs,
  }
}

export function emptyMissionSnapshot(bridge: BridgeHealth): MissionOpsSnapshot {
  return {
    bridge,
    deviceId: null,
    link: {
      desktop: bridge.online ? 'up' : 'down',
      remoteController: 'unknown',
      aircraft: 'unknown',
    },
    batteryPercent: null,
    missionPhase: null,
    liveStreamActive: false,
    routeId: null,
    routeName: null,
    routes: [],
    videoUrl: null,
    altitudeMeters: null,
    satelliteCount: null,
    flightMode: null,
  }
}

export function isBridgeReady(health: BridgeHealth): boolean {
  return health.online
}
