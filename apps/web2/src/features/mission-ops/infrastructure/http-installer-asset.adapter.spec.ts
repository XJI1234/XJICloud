import { describe, expect, it, vi } from 'vitest'
import { LOCAL_INSTALLER_PATH } from '@/features/mission-ops/domain/repositories/installer-asset.port'
import { createHttpInstallerAsset } from './http-installer-asset.adapter'

describe('createHttpInstallerAsset', () => {
  it('treats a successful HEAD as available and does not download the body', async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 200 }))
    const asset = createHttpInstallerAsset(fetchImpl as typeof fetch)
    await expect(asset.isAvailable()).resolves.toBe(true)
    expect(fetchImpl).toHaveBeenCalledWith(LOCAL_INSTALLER_PATH, { method: 'HEAD' })
  })

  it('reports missing when the installer is not published', async () => {
    const fetchImpl = vi.fn(async () => new Response(null, { status: 404 }))
    const asset = createHttpInstallerAsset(fetchImpl as typeof fetch)
    await expect(asset.isAvailable()).resolves.toBe(false)
  })
})
