import type { CachedModelFile } from '../entities/model-asset.entity'

export interface CloudModelCachePort {
  get(cacheKey: string): Promise<ArrayBuffer | null>
  put(entry: CachedModelFile, bytes: ArrayBuffer): Promise<void>
  list(): Promise<CachedModelFile[]>
  remove(cacheKeys: string[]): Promise<void>
}
