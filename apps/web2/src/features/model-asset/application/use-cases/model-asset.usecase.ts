import { DomainError } from '@/shared/domain-error'
import { err, type Result } from '@/shared/result'
import type { ModelAsset } from '../../domain/entities/model-asset.entity'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'
import { assertModelFile } from '../../domain/services/model-format.service'

export async function uploadModelUseCase(
  deps: { models: ModelAssetRepository },
  input: { projectId: string | null; file: File },
): Promise<Result<ModelAsset>> {
  if (!input.projectId) {
    return err(new DomainError('MODEL_PROJECT_REQUIRED'))
  }
  const formatError = assertModelFile(input.file)
  if (formatError) {
    return err(formatError)
  }
  return deps.models.upload(input.projectId, input.file)
}

export async function listModelsUseCase(
  deps: { models: ModelAssetRepository },
  projectId: string | null,
): Promise<Result<ModelAsset[]>> {
  if (!projectId) {
    return err(new DomainError('MODEL_PROJECT_REQUIRED'))
  }
  return deps.models.list(projectId)
}
