import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type {
  CachedModelFile,
  DownloadToken,
  ModelAsset,
  ModelCacheRevision,
  ModelVersion,
} from '../../domain/entities/model-asset.entity'
import type { CloudModelCachePort } from '../../domain/repositories/cloud-model-cache.port'
import type { ModelAssetRepository, ModelUploadSession } from '../../domain/repositories/model-asset.repository'
import {
  cacheKeysForModel,
  historyModelCacheKey,
  isCacheRevisionHit,
  liveModelCacheKey,
} from '../../domain/services/model-cache.service'
import {
  mapDownloadTokenFromDto,
  mapModelFromDto,
  mapModelVersionFromDto,
  mapUploadSessionFromDto,
  type DownloadTokenDto,
  type ModelSummaryDto,
  type ModelVersionDto,
  type UploadChunkDto,
  type UploadSessionDto,
} from '../mappers/model-asset.mapper'

async function readCached(
  cache: CloudModelCachePort | undefined,
  cacheKey: string,
  revision: ModelCacheRevision | undefined,
): Promise<ArrayBuffer | null> {
  if (!cache) {
    return null
  }
  const entries = await cache.list()
  const meta = entries.find((entry) => entry.cacheKey === cacheKey) ?? null
  if (!isCacheRevisionHit(meta, revision)) {
    return null
  }
  return cache.get(cacheKey)
}

async function writeCached(
  cache: CloudModelCachePort | undefined,
  entry: Omit<CachedModelFile, 'cachedAt'>,
  bytes: ArrayBuffer,
) {
  if (!cache) {
    return
  }
  try {
    await cache.put(
      {
        ...entry,
        cachedAt: new Date().toISOString(),
      },
      bytes,
    )
  } catch {
    // Quota or missing OPFS must not fail the download itself.
  }
}

async function dropModelCache(cache: CloudModelCachePort | undefined, modelId: string) {
  if (!cache) {
    return
  }
  const keys = cacheKeysForModel(await cache.list(), modelId)
  if (keys.length > 0) {
    await cache.remove(keys)
  }
}

export function createHttpModelAssetRepository(
  http: HttpClient,
  cache?: CloudModelCachePort,
): ModelAssetRepository {
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
        await dropModelCache(cache, modelId)
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
    async downloadBytes(modelId, onProgress, options): Promise<Result<ArrayBuffer>> {
      const cacheKey = liveModelCacheKey(modelId)
      try {
        const cached = await readCached(cache, cacheKey, options?.revision)
        if (cached) {
          onProgress?.(cached.byteLength, cached.byteLength)
          return ok(cached)
        }
        const params = new URLSearchParams()
        if (options?.cacheBust != null && options.cacheBust !== '') {
          params.set('v', String(options.cacheBust))
        }
        params.set('_', String(Date.now()))
        const query = params.toString()
        const buffer = await http.downloadBytes(`/api/v1/models/${modelId}/download?${query}`, onProgress)
        await writeCached(
          cache,
          {
            cacheKey,
            modelId,
            versionId: null,
            fileName: options?.revision?.fileName ?? 'model.bin',
            sizeBytes: options?.revision?.sizeBytes ?? buffer.byteLength,
            updatedAt: options?.revision?.updatedAt ?? '',
          },
          buffer,
        )
        return ok(buffer)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async downloadVersionBytes(modelId, versionId, onProgress, options): Promise<Result<ArrayBuffer>> {
      const cacheKey = historyModelCacheKey(modelId, versionId)
      try {
        const cached = await readCached(cache, cacheKey, options?.revision)
        if (cached) {
          onProgress?.(cached.byteLength, cached.byteLength)
          return ok(cached)
        }
        const params = new URLSearchParams({
          _: String(Date.now()),
        })
        const buffer = await http.downloadBytes(
          `/api/v1/models/${modelId}/versions/${encodeURIComponent(versionId)}/file?${params.toString()}`,
          onProgress,
        )
        await writeCached(
          cache,
          {
            cacheKey,
            modelId,
            versionId,
            fileName: options?.revision?.fileName ?? 'history.bin',
            sizeBytes: options?.revision?.sizeBytes ?? buffer.byteLength,
            updatedAt: options?.revision?.updatedAt ?? '',
          },
          buffer,
        )
        return ok(buffer)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async listCached(): Promise<Result<CachedModelFile[]>> {
      if (!cache) {
        return ok([])
      }
      try {
        return ok(await cache.list())
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async removeCached(cacheKeys): Promise<Result<void>> {
      if (!cache) {
        return ok(undefined)
      }
      try {
        await cache.remove(cacheKeys)
        return ok(undefined)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async uploadExport(modelId, file, fileName, onProgress): Promise<Result<ModelAsset>> {
      try {
        const formData = new FormData()
        formData.append('file', file, fileName)
        const dto = await http.uploadBytes<ModelSummaryDto>(
          `/api/v1/models/${modelId}/export`,
          formData,
          {},
          onProgress,
        )
        await dropModelCache(cache, modelId)
        return ok(mapModelFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async listVersions(modelId): Promise<Result<ModelVersion[]>> {
      try {
        const dto = await http.request<ModelVersionDto[]>(`/api/v1/models/${modelId}/versions`)
        return ok((dto ?? []).map(mapModelVersionFromDto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async restoreVersion(modelId, versionId): Promise<Result<ModelAsset>> {
      try {
        const dto = await http.request<ModelSummaryDto>(`/api/v1/models/${modelId}/versions/restore`, {
          method: 'POST',
          body: JSON.stringify({ versionId }),
        })
        await dropModelCache(cache, modelId)
        return ok(mapModelFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
  }
}
