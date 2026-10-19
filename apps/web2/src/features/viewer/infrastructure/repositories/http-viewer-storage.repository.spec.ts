import { describe, expect, it, vi } from 'vitest'
import { ok } from '@/shared/result'
import type { ModelAssetRepository } from '@/features/model-asset/domain/repositories/model-asset.repository'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import { createHttpViewerStorage } from './http-viewer-storage.repository'
import { createHttpModelAssetRepository } from '@/features/model-asset/infrastructure/repositories/http-model-asset.repository'
import type { CloudModelCachePort } from '@/features/model-asset/domain/repositories/cloud-model-cache.port'

describe('viewer storage download options', () => {
  it('cancels a pending legacy configuration request without returning stale metadata', async () => {
    const controller = new AbortController()
    const http = {
      request: vi.fn((_path: string, init?: RequestInit) => new Promise((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('Cancelled', 'AbortError')), { once: true })
      })),
    } as unknown as HttpClient
    const repo = createHttpViewerStorage({ http, models: {} as ModelAssetRepository })
    const pending = repo.loadViewerConfig('m1', { signal: controller.signal })
    controller.abort()
    const [error, config] = await pending
    expect(error).not.toBeNull()
    expect(config).toBeNull()
  })

  it('retains the File source while forwarding the optional abort signal', async () => {
    const downloadBytes = vi.fn(async () => ok(new Uint8Array([1, 2]).buffer))
    const repo = createHttpViewerStorage({ http: {} as HttpClient, models: { downloadBytes } as unknown as ModelAssetRepository })
    const controller = new AbortController()
    const options = { signal: controller.signal }
    repo.rememberModelMeta('m1', 'cloud.ply')
    const [error, loaded] = await repo.loadModelBytes('m1', undefined, options)
    expect(error).toBeNull()
    expect(loaded?.file.name).toBe('cloud.ply')
    expect(loaded?.modelId).toBe('m1')
    expect(downloadBytes).toHaveBeenCalledWith('m1', undefined, options)
  })
})

describe('viewer model cache revisions', () => {
  it.each([
    ['2026-10-09T00:00:00Z', 1],
    ['2026-10-13T00:00:00Z', 0],
  ])('uses the current model bytes with cached revision %s', async (cachedAt, downloads) => {
    const latest = new Uint8Array([3, 4]).buffer
    const old = new Uint8Array([1, 2]).buffer
    const downloadBytes = vi.fn(async () => latest)
    const http = {
      request: vi.fn(async () => [{
        id: 'm1', projectId: 'p1', fileName: 'current.ply', format: 'PLY',
        sizeBytes: 2, updatedAt: '2026-10-13T00:00:00Z',
      }]),
      downloadBytes,
    } as unknown as HttpClient
    const cache = {
      list: vi.fn(async () => [{
        cacheKey: 'live:m1', modelId: 'm1', versionId: null, fileName: 'current.ply',
        sizeBytes: 2, updatedAt: cachedAt, cachedAt,
      }]),
      get: vi.fn(async () => old), put: vi.fn(async () => undefined), remove: vi.fn(),
    } as CloudModelCachePort
    const repo = createHttpViewerStorage({ http, models: createHttpModelAssetRepository(http, cache) })
    await repo.listModels('p1')
    repo.rememberModelMeta('m1', 'current.ply')
    const [error, result] = await repo.loadModelBytes('m1')
    expect(error).toBeNull()
    const reader = new FileReader()
    const bytes = new Promise<ArrayBuffer>((resolve, reject) => {
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.onerror = () => reject(reader.error)
    })
    reader.readAsArrayBuffer(result!.file)
    expect(new Uint8Array(await bytes)).toEqual(new Uint8Array(downloads ? latest : old))
    expect(downloadBytes).toHaveBeenCalledTimes(downloads)
  })
})
