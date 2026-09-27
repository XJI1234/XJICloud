import type { BridgeHealth } from '../entities/bridge-status.entity'
import { isBridgeReady } from '../entities/bridge-status.entity'

export type BridgeReadiness = 'ready' | 'offline' | 'waking'

export function classifyBridgeReadiness(health: BridgeHealth, waking: boolean): BridgeReadiness {
  if (isBridgeReady(health)) {
    return 'ready'
  }
  return waking ? 'waking' : 'offline'
}

export function shouldAttemptWake(health: BridgeHealth): boolean {
  return !isBridgeReady(health)
}

export function shouldPollSnapshot(input: { busy: boolean; pollInFlight: boolean }): boolean {
  return !input.busy && !input.pollInFlight
}
