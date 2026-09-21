import type { CachedModelFile } from '../../domain/entities/model-asset.entity'
import type { CloudModelCachePort } from '../../domain/repositories/cloud-model-cache.port'

export function createMemoryModelCache(): CloudModelCachePort {
  const bytesByKey = new Map<string, ArrayBuffer>()
  const metaByKey = new Map<string, CachedModelFile>()

  return {
    async get(cacheKey) {
      const stored = bytesByKey.get(cacheKey)
      return stored ? stored.slice(0) : null
    },
    async put(entry, bytes) {
      metaByKey.set(entry.cacheKey, { ...entry })
      bytesByKey.set(entry.cacheKey, bytes.slice(0))
    },
    async list() {
      return [...metaByKey.values()].map((entry) => ({ ...entry }))
    },
    async remove(cacheKeys) {
      for (const key of cacheKeys) {
        metaByKey.delete(key)
        bytesByKey.delete(key)
      }
    },
  }
}
