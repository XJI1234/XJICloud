import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  deleteModelUseCase,
  downloadModelVersionBytesUseCase,
  listModelsUseCase,
  listModelVersionsUseCase,
  restoreModelVersionUseCase,
  uploadModelUseCase,
  type ModelUploadProgress,
} from '../../application/use-cases/model-asset.usecase'

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
      options?: { cacheBust?: string | number },
    ) => container.models.downloadBytes(modelId, onProgress, options),
    downloadVersionBytes: (
      modelId: string,
      versionId: string,
      onProgress?: (loaded: number, total: number) => void,
    ) => downloadModelVersionBytesUseCase({ models: container.models }, modelId, versionId, onProgress),
    createDownloadToken: (modelId: string) => container.models.createDownloadToken(modelId),
    listVersions: (modelId: string) => listModelVersionsUseCase({ models: container.models }, modelId),
    restoreVersion: (modelId: string, versionId: string) =>
      restoreModelVersionUseCase({ models: container.models }, modelId, versionId),
  }
}
