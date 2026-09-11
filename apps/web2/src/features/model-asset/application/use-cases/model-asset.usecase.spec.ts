import { describe, expect, it } from 'vitest'
import { ok } from '@/shared/result'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'
import {
  downloadModelVersionBytesUseCase,
  listModelVersionsUseCase,
  restoreModelVersionUseCase,
} from './model-asset.usecase'

describe('model version use cases', () => {
  it('lists versions from the repository', async () => {
    const models = {
      listVersions: async () =>
        ok([
          {
            id: 'current',
            fileName: 'scan.ply',
            sizeBytes: 8,
            createdAt: 't',
            current: true,
            version: 2,
          },
        ]),
    } as unknown as ModelAssetRepository
    const [error, versions] = await listModelVersionsUseCase({ models }, 'm1')
    expect(error).toBeNull()
    expect(versions?.[0]?.id).toBe('current')
  })

  it('rejects restore of the current sentinel', async () => {
    const models = {
      restoreVersion: async () => ok({} as never),
    } as unknown as ModelAssetRepository
    const [error] = await restoreModelVersionUseCase({ models }, 'm1', 'current')
    expect(error?.code).toBe('UNKNOWN')
  })

  it('downloads a historical version by id', async () => {
    const models = {
      downloadVersionBytes: async (_modelId: string, versionId: string) => {
        expect(versionId).toBe('v-uuid')
        return ok(new ArrayBuffer(4))
      },
    } as unknown as ModelAssetRepository
    const [error, buffer] = await downloadModelVersionBytesUseCase({ models }, 'm1', 'v-uuid')
    expect(error).toBeNull()
    expect(buffer?.byteLength).toBe(4)
  })
})
