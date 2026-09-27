import type { InstallerAssetPort } from '@/features/mission-ops/domain/repositories/installer-asset.port'

export function probeInstallerUseCase(installer: InstallerAssetPort): Promise<boolean> {
  return installer.isAvailable()
}
