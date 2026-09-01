import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import { listModelsUseCase, uploadModelUseCase } from '../../application/use-cases/model-asset.usecase'

export function useModelAssets() {
  const container = inject(CONTAINER_KEY)!
  return {
    list: (projectId: string | null) => listModelsUseCase({ models: container.models }, projectId),
    upload: (input: { projectId: string | null; file: File }) => uploadModelUseCase({ models: container.models }, input),
    createDownloadToken: (modelId: string) => container.models.createDownloadToken(modelId),
  }
}
