import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  deleteModelUseCase,
  listModelsUseCase,
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
    downloadBytes: (modelId: string, onProgress?: (loaded: number, total: number) => void) =>
      container.models.downloadBytes(modelId, onProgress),
    createDownloadToken: (modelId: string) => container.models.createDownloadToken(modelId),
  }
}
