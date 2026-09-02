import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { DownloadToken, ModelAsset } from '../../domain/entities/model-asset.entity'
import type { ModelAssetRepository, ModelUploadSession } from '../../domain/repositories/model-asset.repository'
import {
  mapDownloadTokenFromDto,
  mapModelFromDto,
  mapUploadSessionFromDto,
  type DownloadTokenDto,
  type ModelSummaryDto,
  type UploadChunkDto,
  type UploadSessionDto,
} from '../mappers/model-asset.mapper'

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
    async createUploadSession(projectId, fileName, sizeBytes): Promise<Result<ModelUploadSession>> {
      try {
        const dto = await http.request<UploadSessionDto>(`/api/v1/projects/${projectId}/models/upload-sessions`, {
          method: 'POST',
          body: JSON.stringify({ fileName, sizeBytes }),
        })
        return ok(mapUploadSessionFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async getUploadSession(sessionId): Promise<Result<ModelUploadSession>> {
      try {
        const dto = await http.request<UploadSessionDto>(`/api/v1/models/upload-sessions/${sessionId}`)
        return ok(mapUploadSessionFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async putChunk(sessionId, chunk, range, onProgress, signal) {
      try {
        const dto = await http.uploadBytes<UploadChunkDto>(
          `/api/v1/models/upload-sessions/${sessionId}/chunks`,
          chunk,
          {
            'Content-Range': `bytes ${range.start}-${range.endInclusive}/${range.total}`,
            'Content-Type': 'application/octet-stream',
          },
          onProgress,
          signal,
        )
        return ok({ receivedBytes: dto?.receivedBytes ?? range.endInclusive + 1 })
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async completeUpload(sessionId): Promise<Result<ModelAsset>> {
      try {
        const dto = await http.request<ModelSummaryDto>(`/api/v1/models/upload-sessions/${sessionId}/complete`, {
          method: 'POST',
        })
        return ok(mapModelFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async abortUpload(sessionId): Promise<Result<void>> {
      try {
        await http.request(`/api/v1/models/upload-sessions/${sessionId}`, { method: 'DELETE' })
        return ok(undefined)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async delete(modelId): Promise<Result<void>> {
      try {
        await http.request(`/api/v1/models/${modelId}`, { method: 'DELETE' })
        return ok(undefined)
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
