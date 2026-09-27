import type { Result } from '@/shared/result'
import type { BridgeHealth, MissionOpsSnapshot } from '../entities/bridge-status.entity'
import type { MissionCommandName } from '../entities/mission-command.entity'

export type FlightAction = 'takeoff' | 'land' | 'confirm-landing' | 'return-home' | 'stop-takeoff' | 'stop-auto-landing'

/**
 * Port to the Sky-Command local agent (tray / headless).
 * Cloud web never talks to DJI or phone MSDK directly.
 */
export type LocalBridgePort = {
  probeHealth: () => Promise<Result<BridgeHealth>>
  wakeLocalAgent: () => Promise<Result<{ attempted: true }>>
  fetchSnapshot: () => Promise<Result<MissionOpsSnapshot>>
  dispatchCommand: (command: MissionCommandName, deviceId: string) => Promise<Result<{ accepted: true }>>
  invoke: (method: string, input: unknown) => Promise<Result<unknown>>
  importRoute: (file: File) => Promise<Result<string>>
  requestFlight: (deviceId: string, action: FlightAction) => Promise<Result<string>>
  confirmFlight: (deviceId: string, confirmationId: string) => Promise<Result<{ accepted: true }>>
  cancelFlight: (deviceId: string, confirmationId: string) => Promise<Result<unknown>>
}

/** Default loopback HTTP base used by the future headless Sky-Command agent. */
export const LOCAL_BRIDGE_DEFAULT_BASE_URL = 'http://127.0.0.1:17890'

/** Custom protocol registered by the installed Sky-Command agent. */
export const LOCAL_BRIDGE_WAKE_PROTOCOL = 'skycommand://open'
