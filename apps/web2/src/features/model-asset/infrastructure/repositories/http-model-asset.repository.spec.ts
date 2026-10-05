import { describe, expect, it, vi } from 'vitest'
import { createHttpModelAssetRepository } from './http-model-asset.repository'
import { createMemoryModelCache } from './memory-model-cache'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import { liveModelCacheKey } from '../../domain/services/model-cache.service'

describe('http model asset cache', () => {
  it('skips the network when the live revision is cached', async () => {
    const downloadBytes = vi.fn()
    const cache = createMemoryModelCache()
    const bytes = new Uint8Array([1, 2, 3, 4]).buffer
    await cache.put(
      {
        cacheKey: liveModelCacheKey('m1'),
        modelId: 'm1',
        versionId: null,
        fileName: 'a.ply',
        sizeBytes: 4,
        updatedAt: 't1',
        cachedAt: 'now',
      },
      bytes,
    )
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [error, buffer] = await repo.downloadBytes('m1', undefined, {
      revision: { updatedAt: 't1', sizeBytes: 4, fileName: 'a.ply' },
    })
    expect(error).toBeNull()
    expect(buffer?.byteLength).toBe(4)
    expect(downloadBytes).not.toHaveBeenCalled()
  })

  it('downloads and stores a miss, then serves the next hit without revision', async () => {
    const payload = new Uint8Array([9, 8, 7]).buffer
    const downloadBytes = vi.fn().mockResolvedValue(payload)
    const cache = createMemoryModelCache()
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [missError] = await repo.downloadBytes('m1', undefined, {
      revision: { updatedAt: 't1', sizeBytes: 3, fileName: 'a.ply' },
    })
    expect(missError).toBeNull()
    expect(downloadBytes).toHaveBeenCalledTimes(1)
    const [hitError, hit] = await repo.downloadBytes('m1')
    expect(hitError).toBeNull()
    expect(hit?.byteLength).toBe(3)
    expect(downloadBytes).toHaveBeenCalledTimes(1)
  })

  it('refetches when the revision no longer matches', async () => {
    const downloadBytes = vi.fn().mockResolvedValue(new Uint8Array([1]).buffer)
    const cache = createMemoryModelCache()
    await cache.put(
      {
        cacheKey: liveModelCacheKey('m1'),
        modelId: 'm1',
        versionId: null,
        fileName: 'a.ply',
        sizeBytes: 4,
        updatedAt: 'old',
        cachedAt: 'now',
      },
      new Uint8Array([1, 2, 3, 4]).buffer,
    )
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    await repo.downloadBytes('m1', undefined, {
      revision: { updatedAt: 'new', sizeBytes: 1, fileName: 'a.ply' },
    })
    expect(downloadBytes).toHaveBeenCalledTimes(1)
  })
})
