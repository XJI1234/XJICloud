import { LOCAL_INSTALLER_PATH, type InstallerAssetPort } from '@/features/mission-ops/domain/repositories/installer-asset.port'

type FetchLike = typeof fetch

async function bodyAvailable(response: Response): Promise<boolean> {
  await response.body?.cancel()
  return response.ok || response.status === 206
}

export function createHttpInstallerAsset(fetchImpl: FetchLike = fetch): InstallerAssetPort {
  return {
    async isAvailable() {
      try {
        const head = await fetchImpl(LOCAL_INSTALLER_PATH, { method: 'HEAD' })
        if (head.ok) {
          await head.body?.cancel()
          return true
        }
        if (head.status !== 405 && head.status !== 501) return false
        const probe = await fetchImpl(LOCAL_INSTALLER_PATH, { method: 'GET', headers: { Range: 'bytes=0-0' } })
        return bodyAvailable(probe)
      } catch {
        return false
      }
    },
  }
}
