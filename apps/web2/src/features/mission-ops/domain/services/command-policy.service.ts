import { DomainError } from '@/shared/domain-error'
import type { BridgeHealth } from '../entities/bridge-status.entity'
import { commandRequiresConfirm, type MissionCommandName } from '../entities/mission-command.entity'

export function assertCanDispatchCommand(health: BridgeHealth, command: MissionCommandName): DomainError | null {
  if (!health.online) {
    return new DomainError('BRIDGE_OFFLINE')
  }
  if (commandRequiresConfirm(command)) {
    return new DomainError('BRIDGE_COMMAND_REJECTED')
  }
  return null
}
