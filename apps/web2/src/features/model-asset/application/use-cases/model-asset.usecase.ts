import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { CachedModelFile, ModelAsset, ModelVersion } from '../../domain/entities/model-asset.entity'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'
import { nextChunkRange } from '../../domain/services/chunk-range.service'
import { assertModelFile } from '../../domain/services/model-format.service'

export type ModelUploadProgress = {
  loaded: number
  total: number
}

function wait(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DomainError('OPERATION_CANCELLED')) }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve() }, ms)
    if (signal?.aborted) abort()
    else signal?.addEventListener('abort', abort, { once: true })
  })
}

function retryable(error: DomainError) {
  return (error.code === 'NETWORK' && (error.status == null || error.status >= 500)) ||
    (error.code === 'UNKNOWN' && (error.status ?? 0) >= 500)
}

export async function uploadModelUseCase(
  deps: { models: ModelAssetRepository },
  input: {
    projectId: string | null
    file: File
    onProgress?: (progress: ModelUploadProgress) => void
    signal?: AbortSignal
  },
): Promise<Result<ModelAsset>> {
  if (!input.projectId) {
    return err(new DomainError('MODEL_PROJECT_REQUIRED'))
  }
  const formatError = assertModelFile(input.file)
  if (formatError) {
    return err(formatError)
  }

  if (input.signal?.aborted) return err(new DomainError('OPERATION_CANCELLED'))
  const [sessionError, created] = await deps.models.createUploadSession(input.projectId, input.file.name, input.file.size, input.signal)
  if (sessionError || !created) {
    return err(input.signal?.aborted ? new DomainError('OPERATION_CANCELLED') : sessionError ?? new DomainError('UNKNOWN'))
  }

  let session = created
  if (!input.signal?.aborted) input.onProgress?.({ loaded: session.receivedBytes, total: input.file.size })

  const abortSession = async () => {
    await deps.models.abortUpload(session.sessionId)
  }

  try {
    while (session.receivedBytes < input.file.size) {
      if (input.signal?.aborted) {
        await abortSession()
        return err(new DomainError('OPERATION_CANCELLED'))
      }
      const range = nextChunkRange(session.receivedBytes, input.file.size, session.chunkSizeBytes)
      if (!range) {
        break
      }
      const blob = input.file.slice(range.start, range.endExclusive)
      let lastError: DomainError | null = null
      for (let attempt = 0; attempt < 3; attempt += 1) {
        if (input.signal?.aborted) {
          await abortSession()
          return err(new DomainError('OPERATION_CANCELLED'))
        }
        const [chunkError, chunkResult] = await deps.models.putChunk(
          session.sessionId,
          blob,
          { start: range.start, endInclusive: range.endInclusive, total: input.file.size },
          (loaded) => {
            if (input.signal?.aborted) return
            const total = input.file.size
            const sent = range.start + loaded
            input.onProgress?.({
              loaded: sent >= total && total > 0 ? total - 1 : sent,
              total,
            })
          },
          input.signal,
        )
        if (!chunkError && chunkResult) {
          session = {
            ...session,
            receivedBytes: chunkResult.receivedBytes,
          }
          lastError = null
          break
        }
        if (input.signal?.aborted) throw new DomainError('OPERATION_CANCELLED')
        lastError = chunkError ?? new DomainError('NETWORK')
        if (!retryable(lastError)) return err(lastError)
        const [refreshError, refreshed] = await deps.models.getUploadSession(session.sessionId, input.signal)
        if (input.signal?.aborted) throw new DomainError('OPERATION_CANCELLED')
        if (!refreshError && refreshed) {
          session = refreshed
          if (session.receivedBytes > range.start) {
            lastError = null
            break
          }
        }
        await wait(500 * 2 ** attempt, input.signal)
      }
      if (lastError) {
        return err(lastError)
      }
      const total = input.file.size
      input.onProgress?.({
        loaded: session.receivedBytes >= total && total > 0 ? total - 1 : session.receivedBytes,
        total,
      })
    }

    if (input.signal?.aborted) {
      await abortSession()
      return err(new DomainError('OPERATION_CANCELLED'))
    }

    let completeError: DomainError | null = null
    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (input.signal?.aborted) throw new DomainError('OPERATION_CANCELLED')
      const [error, model] = await deps.models.completeUpload(session.sessionId, input.signal)
      if (!error && model) {
        input.onProgress?.({ loaded: input.file.size, total: input.file.size })
        return ok(model)
      }
      if (input.signal?.aborted) throw new DomainError('OPERATION_CANCELLED')
      completeError = error ?? new DomainError('NETWORK')
      if (!retryable(completeError)) return err(completeError)
      await wait(500 * 2 ** attempt, input.signal)
    }
    return err(completeError ?? new DomainError('NETWORK'))
  } catch (error) {
    if (input.signal?.aborted || session.receivedBytes < input.file.size) {
      await abortSession()
    }
    return err(input.signal?.aborted ? new DomainError('OPERATION_CANCELLED') : error instanceof DomainError ? error : new DomainError('NETWORK'))
  }
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

export async function deleteModelUseCase(
  deps: { models: ModelAssetRepository },
  modelId: string,
): Promise<Result<void>> {
  if (!modelId) {
    return err(new DomainError('UNKNOWN'))
  }
  return deps.models.delete(modelId)
}

export async function listModelVersionsUseCase(
  deps: { models: ModelAssetRepository },
  modelId: string,
): Promise<Result<ModelVersion[]>> {
  if (!modelId) {
    return err(new DomainError('UNKNOWN'))
  }
  return deps.models.listVersions(modelId)
}

export async function restoreModelVersionUseCase(
  deps: { models: ModelAssetRepository },
  modelId: string,
  versionId: string,
): Promise<Result<ModelAsset>> {
  if (!modelId || !versionId || versionId === 'current') {
    return err(new DomainError('UNKNOWN'))
  }
  return deps.models.restoreVersion(modelId, versionId)
}

export async function downloadModelVersionBytesUseCase(
  deps: { models: ModelAssetRepository },
  modelId: string,
  versionId: string,
  onProgress?: (loaded: number, total: number) => void,
  revision?: { updatedAt: string; sizeBytes: number; fileName: string },
  signal?: AbortSignal,
): Promise<Result<ArrayBuffer>> {
  if (!modelId || !versionId || versionId === 'current') {
    return err(new DomainError('UNKNOWN'))
  }
  return deps.models.downloadVersionBytes(modelId, versionId, onProgress, { revision, signal })
}

export async function downloadModelToDiskUseCase(
  deps: { models: ModelAssetRepository },
  model: ModelAsset,
  onProgress?: (loaded: number, total: number) => void,
): Promise<Result<{ blob: Blob; fileName: string }>> {
  if (!model.id) {
    return err(new DomainError('UNKNOWN'))
  }
  const [error, buffer] = await deps.models.downloadBytes(model.id, onProgress, {
    cacheBust: `${model.version}-${model.updatedAt || Date.now()}`,
    revision: {
      updatedAt: model.updatedAt,
      sizeBytes: model.sizeBytes,
      fileName: model.fileName,
    },
  })
  if (error || !buffer) {
    return err(error ?? new DomainError('UNKNOWN'))
  }
  return ok({ blob: new Blob([buffer]), fileName: model.fileName })
}

export async function listCachedModelsUseCase(
  deps: { models: ModelAssetRepository },
): Promise<Result<CachedModelFile[]>> {
  return deps.models.listCached()
}

export async function removeCachedModelsUseCase(
  deps: { models: ModelAssetRepository },
  cacheKeys: string[],
): Promise<Result<void>> {
  if (cacheKeys.length === 0) {
    return ok(undefined)
  }
  return deps.models.removeCached(cacheKeys)
}
