import { DomainError } from '@/shared/domain-error'
import { err, type Result } from '@/shared/result'
import type { ModelAsset } from '../../domain/entities/model-asset.entity'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'
import { nextChunkRange } from '../../domain/services/chunk-range.service'
import { assertModelFile } from '../../domain/services/model-format.service'

export type ModelUploadProgress = {
  loaded: number
  total: number
}

function wait(ms: number) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms)
  })
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

  const [sessionError, created] = await deps.models.createUploadSession(input.projectId, input.file.name, input.file.size)
  if (sessionError || !created) {
    return err(sessionError ?? new DomainError('UNKNOWN'))
  }

  let session = created
  input.onProgress?.({ loaded: session.receivedBytes, total: input.file.size })

  const abortSession = async () => {
    await deps.models.abortUpload(session.sessionId)
  }

  try {
    while (session.receivedBytes < input.file.size) {
      if (input.signal?.aborted) {
        await abortSession()
        return err(new DomainError('NETWORK'))
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
          return err(new DomainError('NETWORK'))
        }
        const [chunkError, chunkResult] = await deps.models.putChunk(
          session.sessionId,
          blob,
          { start: range.start, endInclusive: range.endInclusive, total: input.file.size },
          (loaded) => {
            input.onProgress?.({ loaded: range.start + loaded, total: input.file.size })
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
        lastError = chunkError ?? new DomainError('NETWORK')
        const [refreshError, refreshed] = await deps.models.getUploadSession(session.sessionId)
        if (!refreshError && refreshed) {
          session = refreshed
          if (session.receivedBytes > range.start) {
            lastError = null
            break
          }
        }
        await wait(500 * 2 ** attempt)
      }
      if (lastError) {
        return err(lastError)
      }
      input.onProgress?.({ loaded: session.receivedBytes, total: input.file.size })
    }

    return deps.models.completeUpload(session.sessionId)
  } catch (error) {
    await abortSession()
    return err(error instanceof DomainError ? error : new DomainError('NETWORK'))
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
