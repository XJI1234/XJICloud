import { describe, expect, it, vi } from 'vitest'
import { detectModelFormat, assertModelFile } from './model-format.service'
import { deleteModelUseCase, uploadModelUseCase } from '../../application/use-cases/model-asset.usecase'
import { ok } from '@/shared/result'
import type { ModelAssetRepository } from '../repositories/model-asset.repository'
import { MODEL_MAX_SIZE_BYTES } from './chunk-range.service'

describe('model format', () => {
  it('accepts ply and spz only', () => {
    expect(detectModelFormat('a.ply')).toBe('PLY')
    expect(detectModelFormat('b.SPZ')).toBe('SPZ')
    expect(detectModelFormat('c.jpg')).toBeNull()
    expect(assertModelFile(new File(['x'], 'c.txt'))?.code).toBe('MODEL_INVALID_FORMAT')
  })

  it('rejects files over 2GB', () => {
    const file = new File(['x'], 'a.ply')
    Object.defineProperty(file, 'size', { value: MODEL_MAX_SIZE_BYTES + 1 })
    expect(assertModelFile(file)?.code).toBe('MODEL_TOO_LARGE')
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

  it('uploads chunks then completes', async () => {
    const file = new File(['abcdefghij'], 'a.ply')
    const putChunk = vi.fn(async (_id: string, _chunk: Blob, range: { start: number; endInclusive: number }) =>
      ok({ receivedBytes: range.endInclusive + 1 }),
    )
    const models = {
      createUploadSession: async () =>
        ok({ sessionId: 's1', chunkSizeBytes: 4, receivedBytes: 0, sizeBytes: file.size }),
      putChunk,
      completeUpload: async () =>
        ok({
          id: 'm1',
          projectId: 'p1',
          fileName: 'a.ply',
          format: 'PLY' as const,
          sizeBytes: file.size,
          version: 1,
          createdAt: '',
          updatedAt: '',
        }),
      getUploadSession: async () =>
        ok({ sessionId: 's1', chunkSizeBytes: 4, receivedBytes: 0, sizeBytes: file.size }),
      abortUpload: async () => ok(undefined),
    } as unknown as ModelAssetRepository

    const [error, model] = await uploadModelUseCase({ models }, { projectId: 'p1', file })
    expect(error).toBeNull()
    expect(model?.id).toBe('m1')
    expect(putChunk).toHaveBeenCalledTimes(3)
  })
})

describe('delete model use case', () => {
  it('deletes by id', async () => {
    const remove = vi.fn(async () => ok(undefined))
    const models = { delete: remove } as unknown as ModelAssetRepository
    const [error] = await deleteModelUseCase({ models }, 'm1')
    expect(error).toBeNull()
    expect(remove).toHaveBeenCalledWith('m1')
  })
})
