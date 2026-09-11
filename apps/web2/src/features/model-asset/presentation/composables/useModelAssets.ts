import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  deleteModelUseCase,
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
      archiveName: string,
      onProgress?: (loaded: number, total: number) => void,
    ) => container.models.downloadVersionBytes(modelId, archiveName, onProgress),
    createDownloadToken: (modelId: string) => container.models.createDownloadToken(modelId),
    listVersions: (modelId: string) => listModelVersionsUseCase({ models: container.models }, modelId),
    restoreVersion: (modelId: string, archiveName: string) =>
      restoreModelVersionUseCase({ models: container.models }, modelId, archiveName),
  }
}
