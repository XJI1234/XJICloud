import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import {
  emptyMissionSnapshot,
  type BridgeHealth,
  type MissionOpsSnapshot,
} from '@/features/mission-ops/domain/entities/bridge-status.entity'
import type { MissionCommandName } from '@/features/mission-ops/domain/entities/mission-command.entity'
import { shouldAttemptWake } from '@/features/mission-ops/domain/services/bridge-readiness.service'
import { assertCanDispatchCommand } from '@/features/mission-ops/domain/services/command-policy.service'
import type { LocalBridgePort } from '@/features/mission-ops/domain/repositories/local-bridge.port'

export async function probeMissionBridgeUseCase(bridge: LocalBridgePort): Promise<Result<BridgeHealth>> {
  return bridge.probeHealth()
}

export async function wakeMissionBridgeUseCase(bridge: LocalBridgePort): Promise<Result<{ attempted: true }>> {
  return bridge.wakeLocalAgent()
}

export async function ensureMissionBridgeReadyUseCase(
  bridge: LocalBridgePort,
  options?: { wakeIfOffline?: boolean; settleMs?: number },
): Promise<Result<BridgeHealth>> {
  const wakeIfOffline = options?.wakeIfOffline ?? true
  const settleMs = options?.settleMs ?? 900

  const [probeError, health] = await bridge.probeHealth()
  if (probeError) {
    return err(probeError)
  }
  if (!health) {
    return err(new DomainError('BRIDGE_OFFLINE'))
  }
  if (!shouldAttemptWake(health) || !wakeIfOffline) {
    return ok(health)
  }

  const [wakeError] = await bridge.wakeLocalAgent()
  if (wakeError) {
    return err(wakeError)
  }

  if (settleMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, settleMs))
  }

  const [retryError, retryHealth] = await bridge.probeHealth()
  if (retryError) {
    return err(retryError)
  }
  if (!retryHealth?.online) {
    return err(new DomainError('BRIDGE_OFFLINE'))
  }
  return ok(retryHealth)
}

export async function loadMissionOpsSnapshotUseCase(bridge: LocalBridgePort): Promise<Result<MissionOpsSnapshot>> {
  const [healthError, health] = await bridge.probeHealth()
  if (healthError || !health) {
    return err(healthError ?? new DomainError('BRIDGE_OFFLINE'))
  }
  if (!health.online) {
    return ok(emptyMissionSnapshot(health))
  }
  return bridge.fetchSnapshot()
}

export async function dispatchMissionCommandUseCase(
  bridge: LocalBridgePort,
  command: MissionCommandName,
  deviceId: string,
): Promise<Result<{ accepted: true }>> {
  const [healthError, health] = await bridge.probeHealth()
  if (healthError || !health) {
    return err(healthError ?? new DomainError('BRIDGE_OFFLINE'))
  }
  const blocked = assertCanDispatchCommand(health, command)
  if (blocked) {
    return err(blocked)
  }
  if (!deviceId) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
  return bridge.dispatchCommand(command, deviceId)
}

async function requireOnlineDevice(bridge: LocalBridgePort, deviceId: string): Promise<Result<true>> {
  if (!deviceId) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
  const [healthError, health] = await bridge.probeHealth()
  if (healthError || !health?.online) {
    return err(healthError ?? new DomainError('BRIDGE_OFFLINE'))
  }
  return ok(true)
}

export async function stageMissionUseCase(bridge: LocalBridgePort, deviceId: string): Promise<Result<unknown>> {
  const [blocked] = await requireOnlineDevice(bridge, deviceId)
  if (blocked) return err(blocked)
  return bridge.invoke('mission.stage', { deviceId })
}

export async function assignRouteUseCase(
  bridge: LocalBridgePort,
  deviceId: string,
  routeId: string,
): Promise<Result<unknown>> {
  if (!routeId) return err(new DomainError('BRIDGE_COMMAND_REJECTED'))
  const [blocked] = await requireOnlineDevice(bridge, deviceId)
  if (blocked) return err(blocked)
  return bridge.invoke('assignment.assign', { deviceId, routeId })
}

export async function startStreamUseCase(bridge: LocalBridgePort, deviceId: string): Promise<Result<unknown>> {
  const [blocked] = await requireOnlineDevice(bridge, deviceId)
  if (blocked) return err(blocked)
  return bridge.invoke('stream.start', { deviceId })
}
