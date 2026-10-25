import { describe, expect, it, vi } from 'vitest'
import { createHttpModelAssetRepository } from './http-model-asset.repository'
import { createMemoryModelCache } from './memory-model-cache'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import { liveModelCacheKey } from '../../domain/services/model-cache.service'

describe('http model asset cache', () => {
  it('rejects a cached read that completes after a successful mutation', async () => {
    const cache = createMemoryModelCache()
    await cache.put({ cacheKey: 'live:m', modelId: 'm', versionId: null, fileName: 'a.ply', sizeBytes: 1, updatedAt: '', cachedAt: '' }, new ArrayBuffer(1))
    let finish!: (value: ArrayBuffer) => void
    const get = vi.spyOn(cache, 'get').mockReturnValue(new Promise(resolve => { finish = resolve }))
    const repo = createHttpModelAssetRepository({ request: async () => undefined } as unknown as HttpClient, cache)
    const download = repo.downloadBytes('m')
    await vi.waitFor(() => expect(get).toHaveBeenCalledOnce())
    await repo.delete('m')
    finish(new ArrayBuffer(1))
    expect((await download)[0]?.code).toBe('OPERATION_CANCELLED')
  })
  it('forwards cancellation for historical files', async () => {
    const owner = new AbortController()
    const downloadBytes = vi.fn(async (_path, _progress, signal) => { expect(signal).toBe(owner.signal); owner.abort(); return new ArrayBuffer(1) })
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, createMemoryModelCache())
    expect((await repo.downloadVersionBytes('m', 'v', undefined, { signal: owner.signal }))[0]?.code).toBe('OPERATION_CANCELLED')
  })
  it('captures the account before cache awaits and rejects a changed owner', async () => {
    let owner = 'a'
    const cache = createMemoryModelCache()
    vi.spyOn(cache, 'list').mockImplementation(async () => { owner = 'b'; return [] })
    const downloadBytes = vi.fn()
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache, () => owner)
    expect((await repo.downloadBytes('m'))[0]?.code).toBe('OPERATION_CANCELLED')
    expect(downloadBytes).not.toHaveBeenCalled()
  })
  it('does not serve old bytes after successful deletion with failed cache invalidation', async () => {
    const cache = createMemoryModelCache()
    await cache.put({ cacheKey: 'live:m', modelId: 'm', versionId: null, fileName: 'm.ply', sizeBytes: 1, updatedAt: '', cachedAt: '' }, new ArrayBuffer(1))
    vi.spyOn(cache, 'remove').mockRejectedValue(new Error('denied'))
    const downloadBytes = vi.fn(async () => new ArrayBuffer(2))
    const repo = createHttpModelAssetRepository({ request: async () => undefined, downloadBytes } as unknown as HttpClient, cache)
    expect((await repo.delete('m'))[0]).toBeNull()
    expect((await repo.downloadBytes('m'))[1]?.byteLength).toBe(2)
    expect(downloadBytes).toHaveBeenCalledOnce()
  })
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

  it('forwards cancellation and does not cache a cancelled download', async () => {
    const controller = new AbortController()
    const cache = createMemoryModelCache()
    const put = vi.spyOn(cache, 'put')
    const downloadBytes = vi.fn(async (_url, _progress, signal) => {
      expect(signal).toBe(controller.signal)
      controller.abort()
      return new Uint8Array([1, 2, 3]).buffer
    })
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [error, bytes] = await repo.downloadBytes('m1', undefined, { signal: controller.signal })
    expect(error?.code).toBe('OPERATION_CANCELLED')
    expect(bytes).toBeNull()
    expect(put).not.toHaveBeenCalled()
  })

  it('does not return a cache result or start a network call after cancellation during cache lookup', async () => {
    const controller = new AbortController()
    const cache = createMemoryModelCache()
    vi.spyOn(cache, 'list').mockImplementation(async () => { controller.abort(); return [] })
    const downloadBytes = vi.fn()
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [error] = await repo.downloadBytes('m1', undefined, { signal: controller.signal })
    expect(error?.code).toBe('OPERATION_CANCELLED')
    expect(downloadBytes).not.toHaveBeenCalled()
  })

  it('also rejects cancellation in a cache-hit completion callback', async () => {
    const payload = new Uint8Array([1, 2, 3]).buffer
    const downloadBytes = vi.fn().mockResolvedValue(payload)
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, createMemoryModelCache())
    await repo.downloadBytes('m1')
    const controller = new AbortController()
    const [error, bytes] = await repo.downloadBytes('m1', () => controller.abort(), { signal: controller.signal })
    expect(error?.code).toBe('OPERATION_CANCELLED')
    expect(bytes).toBeNull()
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
