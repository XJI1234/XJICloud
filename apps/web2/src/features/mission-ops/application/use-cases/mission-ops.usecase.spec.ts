import { describe, expect, it, vi } from 'vitest'
import { DomainError } from '@/shared/domain-error'
import { err, ok } from '@/shared/result'
import { offlineBridgeHealth, type BridgeHealth } from '@/features/mission-ops/domain/entities/bridge-status.entity'
import type { LocalBridgePort } from '@/features/mission-ops/domain/repositories/local-bridge.port'
import { ensureMissionBridgeReadyUseCase, loadMissionOpsSnapshotUseCase, dispatchMissionCommandUseCase, stageMissionUseCase } from './mission-ops.usecase'

function onlineHealth(overrides: Partial<BridgeHealth> = {}): BridgeHealth {
  return {
    online: true,
    version: '0.1.0',
    relayListening: true,
    checkedAtMs: 1,
    ...overrides,
  }
}

describe('ensureMissionBridgeReadyUseCase', () => {
  it('returns health when bridge is already online', async () => {
    const health = onlineHealth()
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(health)),
      wakeLocalAgent: vi.fn(async () => ok({ attempted: true })),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }

    const [error, data] = await ensureMissionBridgeReadyUseCase(bridge, { settleMs: 0 })
    expect(error).toBeNull()
    expect(data).toEqual(health)
    expect(bridge.wakeLocalAgent).not.toHaveBeenCalled()
  })

  it('wakes then re-probes when offline', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi
        .fn()
        .mockResolvedValueOnce(ok(offlineBridgeHealth(1)))
        .mockResolvedValueOnce(ok(onlineHealth({ checkedAtMs: 2 }))),
      wakeLocalAgent: vi.fn(async () => ok({ attempted: true })),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }

    const [error, data] = await ensureMissionBridgeReadyUseCase(bridge, { settleMs: 0 })
    expect(error).toBeNull()
    expect(data?.online).toBe(true)
    expect(bridge.wakeLocalAgent).toHaveBeenCalledOnce()
    expect(bridge.probeHealth).toHaveBeenCalledTimes(2)
  })

  it('returns BRIDGE_OFFLINE when wake does not bring the agent up', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(offlineBridgeHealth())),
      wakeLocalAgent: vi.fn(async () => ok({ attempted: true })),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }

    const [error] = await ensureMissionBridgeReadyUseCase(bridge, { settleMs: 0 })
    expect(error).toBeInstanceOf(DomainError)
    expect(error?.code).toBe('BRIDGE_OFFLINE')
  })
})

describe('loadMissionOpsSnapshotUseCase', () => {
  it('returns empty snapshot without calling remote snapshot when offline', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(offlineBridgeHealth(9))),
      wakeLocalAgent: vi.fn(),
      fetchSnapshot: vi.fn(async () => err(new DomainError('UNKNOWN'))),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(),
    }

    const [error, data] = await loadMissionOpsSnapshotUseCase(bridge)
    expect(error).toBeNull()
    expect(data?.bridge.online).toBe(false)
    expect(bridge.fetchSnapshot).not.toHaveBeenCalled()
  })
})

describe('dispatchMissionCommandUseCase', () => {
  it('does not call the agent when the bridge is offline', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(offlineBridgeHealth())),
      wakeLocalAgent: vi.fn(),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }
    const [error] = await dispatchMissionCommandUseCase(bridge, 'mission.start', 'device-a')
    expect(error?.code).toBe('BRIDGE_OFFLINE')
    expect(bridge.dispatchCommand).not.toHaveBeenCalled()
  })

  it('forwards a non-flight command when the bridge is online', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(onlineHealth())),
      wakeLocalAgent: vi.fn(),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }
    const [error, data] = await dispatchMissionCommandUseCase(bridge, 'mission.start', 'device-a')
    expect(error).toBeNull()
    expect(data?.accepted).toBe(true)
    expect(bridge.dispatchCommand).toHaveBeenCalledWith('mission.start', 'device-a')
  })

  it('rejects return-home until the confirmation flow accepts it', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(onlineHealth())),
      wakeLocalAgent: vi.fn(),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(async () => ok({ accepted: true })),
    }
    const [error] = await dispatchMissionCommandUseCase(bridge, 'flight.return-home', 'device-a')
    expect(error?.code).toBe('BRIDGE_COMMAND_REJECTED')
    expect(bridge.dispatchCommand).not.toHaveBeenCalled()
  })
})

describe('stageMissionUseCase', () => {
  it('does not invoke the agent when the bridge is offline', async () => {
    const bridge: LocalBridgePort = {
      probeHealth: vi.fn(async () => ok(offlineBridgeHealth())),
      wakeLocalAgent: vi.fn(),
      fetchSnapshot: vi.fn(),
      requestFlight: vi.fn(),
      confirmFlight: vi.fn(),
      cancelFlight: vi.fn(),
      invoke: vi.fn(async () => ok(undefined)),
      importRoute: vi.fn(),
      dispatchCommand: vi.fn(),
    }
    const [error] = await stageMissionUseCase(bridge, 'device-a')
    expect(error?.code).toBe('BRIDGE_OFFLINE')
    expect(bridge.invoke).not.toHaveBeenCalled()
  })
})
