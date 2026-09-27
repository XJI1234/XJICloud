import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  assignRouteUseCase,
  dispatchMissionCommandUseCase,
  ensureMissionBridgeReadyUseCase,
  loadMissionOpsSnapshotUseCase,
  probeMissionBridgeUseCase,
  stageMissionUseCase,
  startStreamUseCase,
  wakeMissionBridgeUseCase,
} from '@/features/mission-ops/application/use-cases/mission-ops.usecase'
import type { MissionCommandName } from '@/features/mission-ops/domain/entities/mission-command.entity'
import { previewRouteUseCase } from '@/features/mission-ops/application/use-cases/preview-route.usecase'
import { probeInstallerUseCase } from '@/features/mission-ops/application/use-cases/probe-installer.usecase'

export function useMissionOps() {
  const container = inject(CONTAINER_KEY)!
  return {
    probe: () => probeMissionBridgeUseCase(container.localBridge),
    wake: () => wakeMissionBridgeUseCase(container.localBridge),
    ensureReady: (options?: { wakeIfOffline?: boolean; settleMs?: number }) =>
      ensureMissionBridgeReadyUseCase(container.localBridge, options),
    loadSnapshot: () => loadMissionOpsSnapshotUseCase(container.localBridge),
    dispatch: (command: MissionCommandName, deviceId: string) => dispatchMissionCommandUseCase(container.localBridge, command, deviceId),
    stage: (deviceId: string) => stageMissionUseCase(container.localBridge, deviceId),
    assign: (deviceId: string, routeId: string) => assignRouteUseCase(container.localBridge, deviceId, routeId),
    startStream: (deviceId: string) => startStreamUseCase(container.localBridge, deviceId),
    importRoute: (file: File) => container.localBridge.importRoute(file),
    installerAvailable: () => probeInstallerUseCase(container.installerAsset),
    requestFlight: container.localBridge.requestFlight,
    confirmFlight: container.localBridge.confirmFlight,
    cancelFlight: container.localBridge.cancelFlight,
    previewRoute: (file: File) => previewRouteUseCase(container.routePreview, file),
  }
}
