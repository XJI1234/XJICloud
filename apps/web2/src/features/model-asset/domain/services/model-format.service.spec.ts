import { describe, expect, it } from 'vitest'
import { detectModelFormat, assertModelFile } from './model-format.service'
import { uploadModelUseCase } from '../../application/use-cases/model-asset.usecase'
import { ok } from '@/shared/result'
import type { ModelAssetRepository } from '../repositories/model-asset.repository'

describe('model format', () => {
  it('accepts ply and spz only', () => {
    expect(detectModelFormat('a.ply')).toBe('PLY')
    expect(detectModelFormat('b.SPZ')).toBe('SPZ')
    expect(detectModelFormat('c.jpg')).toBeNull()
    expect(assertModelFile(new File(['x'], 'c.txt'))?.code).toBe('MODEL_INVALID_FORMAT')
  })
})

describe('upload model use case', () => {
  it('requires an active project', async () => {
    const [error] = await uploadModelUseCase(
      { models: {} as ModelAssetRepository },
      { projectId: null, file: new File(['x'], 'a.ply') },
    )
    expect(error?.code).toBe('MODEL_PROJECT_REQUIRED')
  })

  it('uploads after format check', async () => {
    const upload = async () =>
      ok({
        id: 'm1',
        projectId: 'p1',
        fileName: 'a.ply',
        format: 'PLY' as const,
        sizeBytes: 1,
        version: 1,
        createdAt: '',
        updatedAt: '',
      })
    const models = { upload } as unknown as ModelAssetRepository
    const [error, model] = await uploadModelUseCase(
      { models },
      { projectId: 'p1', file: new File(['x'], 'a.ply') },
    )
    expect(error).toBeNull()
    expect(model?.id).toBe('m1')
  })
})
