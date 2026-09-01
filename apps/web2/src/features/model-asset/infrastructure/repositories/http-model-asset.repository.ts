import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { DownloadToken, ModelAsset } from '../../domain/entities/model-asset.entity'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'
import { mapDownloadTokenFromDto, mapModelFromDto, type DownloadTokenDto, type ModelSummaryDto } from '../mappers/model-asset.mapper'

export function createHttpModelAssetRepository(http: HttpClient): ModelAssetRepository {
  return {
    async list(projectId): Promise<Result<ModelAsset[]>> {
      try {
        const dto = await http.request<ModelSummaryDto[]>(`/api/v1/projects/${projectId}/models`)
        return ok((dto ?? []).map(mapModelFromDto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async upload(projectId, file): Promise<Result<ModelAsset>> {
      try {
        const formData = new FormData()
        formData.append('file', file)
        const dto = await http.request<ModelSummaryDto>(`/api/v1/projects/${projectId}/models/upload`, {
          method: 'POST',
          body: formData,
        })
        return ok(mapModelFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async createDownloadToken(modelId): Promise<Result<DownloadToken>> {
      try {
        const dto = await http.request<DownloadTokenDto>(`/api/v1/models/${modelId}/download-token`, {
          method: 'POST',
        })
        return ok(mapDownloadTokenFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async downloadBytes(modelId, onProgress): Promise<Result<ArrayBuffer>> {
      try {
        const buffer = await http.downloadBytes(`/api/v1/models/${modelId}/download`, onProgress)
        return ok(buffer)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async uploadExport(modelId, file, fileName): Promise<Result<ModelAsset>> {
      try {
        const formData = new FormData()
        formData.append('file', file, fileName)
        const dto = await http.request<ModelSummaryDto>(`/api/v1/models/${modelId}/export`, {
          method: 'POST',
          body: formData,
        })
        return ok(mapModelFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
  }
}
