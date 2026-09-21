import type { CachedModelFile, ModelCacheRevision } from '../entities/model-asset.entity'

export function liveModelCacheKey(modelId: string): string {
  return `live:${modelId}`
}

export function historyModelCacheKey(modelId: string, versionId: string): string {
  return `hist:${modelId}:${versionId}`
}

export function isCacheRevisionHit(
  entry: Pick<CachedModelFile, 'updatedAt' | 'sizeBytes'> | null,
  revision: Pick<ModelCacheRevision, 'updatedAt' | 'sizeBytes'> | undefined,
): boolean {
  if (!entry) {
    return false
  }
  if (!revision) {
    return true
  }
  return entry.updatedAt === revision.updatedAt && entry.sizeBytes === revision.sizeBytes
}

export function cacheKeysForModel(entries: CachedModelFile[], modelId: string): string[] {
  return entries.filter((entry) => entry.modelId === modelId).map((entry) => entry.cacheKey)
}
