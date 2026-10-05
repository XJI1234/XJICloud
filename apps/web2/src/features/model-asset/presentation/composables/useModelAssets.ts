import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  deleteModelUseCase,
  downloadModelToDiskUseCase,
  downloadModelVersionBytesUseCase,
  listCachedModelsUseCase,
  listModelsUseCase,
  listModelVersionsUseCase,
  removeCachedModelsUseCase,
  restoreModelVersionUseCase,
  uploadModelUseCase,
  type ModelUploadProgress,
} from '../../application/use-cases/model-asset.usecase'
import type { ModelAsset, ModelCacheRevision } from '../../domain/entities/model-asset.entity'

export function useModelAssets() {
  const container = inject(CONTAINER_KEY)!
  return {
    list: (projectId: string | null) => listModelsUseCase({ models: container.models }, projectId),
    upload: (input: {
      projectId: string | null
      file: File
      onProgress?: (progress: ModelUploadProgress) => void
      signal?: AbortSignal
    }) => uploadModelUseCase({ models: container.models }, input),
    remove: (modelId: string) => deleteModelUseCase({ models: container.models }, modelId),
    downloadBytes: (
      modelId: string,
      onProgress?: (loaded: number, total: number) => void,
      options?: { cacheBust?: string | number; revision?: ModelCacheRevision },
    ) => container.models.downloadBytes(modelId, onProgress, options),
    downloadToDisk: (model: ModelAsset, onProgress?: (loaded: number, total: number) => void) =>
      downloadModelToDiskUseCase({ models: container.models }, model, onProgress),
    downloadVersionBytes: (
      modelId: string,
      versionId: string,
      onProgress?: (loaded: number, total: number) => void,
      revision?: ModelCacheRevision,
    ) => downloadModelVersionBytesUseCase({ models: container.models }, modelId, versionId, onProgress, revision),
    createDownloadToken: (modelId: string) => container.models.createDownloadToken(modelId),
    listVersions: (modelId: string) => listModelVersionsUseCase({ models: container.models }, modelId),
    restoreVersion: (modelId: string, versionId: string) =>
      restoreModelVersionUseCase({ models: container.models }, modelId, versionId),
    listCached: () => listCachedModelsUseCase({ models: container.models }),
    removeCached: (cacheKeys: string[]) => removeCachedModelsUseCase({ models: container.models }, cacheKeys),
  }
}
