import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import { mapHttpError } from '@/shared/infrastructure/http-client'
import type { ModelAssetRepository } from '@/features/model-asset/domain/repositories/model-asset.repository'
import type { ModelCacheRevision } from '@/features/model-asset/domain/entities/model-asset.entity'
import type { ViewerConfig, ViewerModelSummary } from '../../domain/entities/viewer-config.entity'
import type { ViewerStoragePort } from '../../domain/repositories/viewer-storage.port'
import { parseViewerConfig } from '../../domain/services/viewer-config.service'

type ViewerConfigPayloadDto = {
  jsonPayload?: string
  updatedAt?: string
}

export function createHttpViewerStorage(deps: {
  http: HttpClient
  models: ModelAssetRepository
}): ViewerStoragePort {
  const modelMetaCache = new Map<string, { fileName: string; revision?: ModelCacheRevision }>()
  function rememberModelMeta(modelId: string, fileName: string): Result<void> {
    modelMetaCache.set(modelId, { ...modelMetaCache.get(modelId), fileName })
    return ok(undefined)
  }
  return {
    rememberModelMeta,
    async listModels(projectId): Promise<Result<ViewerModelSummary[]>> {
      const [error, models] = await deps.models.list(projectId)
      if (error || !models) {
        return err(error ?? new DomainError('UNKNOWN'))
      }
      return ok(
        models.map((model) => {
          modelMetaCache.set(model.id, {
            fileName: model.fileName,
            revision: { fileName: model.fileName, sizeBytes: model.sizeBytes, updatedAt: model.updatedAt },
          })
          return {
            id: model.id,
            fileName: model.fileName,
            format: model.format,
            sizeBytes: model.sizeBytes,
            version: model.version,
            updatedAt: model.updatedAt,
          }
        }),
      )
    },

    async loadModelBytes(modelId, onProgress, options) {
      const meta = modelMetaCache.get(modelId)
      const [error, buffer] = await deps.models.downloadBytes(modelId, onProgress,
        meta?.revision ? { ...options, revision: meta.revision } : options)
      if (error || !buffer) {
        return err(error ?? new DomainError('UNKNOWN'))
      }
      const fileName = meta?.fileName ?? 'model.spz'
      return ok({
        file: new File([buffer], fileName),
        modelId,
      })
    },

    async loadViewerConfig(modelId, options) {
      try {
        const payload = await deps.http.request<ViewerConfigPayloadDto>(`/api/v1/models/${modelId}/viewer-config`, { signal: options?.signal })
        const raw = payload.jsonPayload ? (JSON.parse(payload.jsonPayload) as unknown) : {}
        return ok(parseViewerConfig(raw))
      } catch (error) {
        if (error instanceof SyntaxError) {
          return err(new DomainError('VIEWER_CONFIG_INVALID'))
        }
        return err(mapHttpError(error))
      }
    },

    async saveViewerConfig(modelId, config) {
      try {
        await deps.http.request(`/api/v1/models/${modelId}/viewer-config`, {
          method: 'PUT',
          body: JSON.stringify({ jsonPayload: JSON.stringify(config, null, 2) }),
        })
        return ok(undefined)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },

    async saveExport(modelId, bytes, fileName) {
      const [error] = await deps.models.uploadExport(
        modelId,
        new Blob([new Uint8Array(bytes)], { type: 'application/octet-stream' }),
        fileName,
      )
      if (error) {
        return err(error)
      }
      return ok(undefined)
    },
  }
}
