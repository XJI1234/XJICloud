import type { Result } from '@/shared/result'
import type { DownloadToken, ModelAsset } from '../entities/model-asset.entity'

export type ModelUploadSession = {
  sessionId: string
  chunkSizeBytes: number
  receivedBytes: number
  sizeBytes: number
}

export type ChunkProgress = (loaded: number, total: number) => void

export interface ModelAssetRepository {
  list(projectId: string): Promise<Result<ModelAsset[]>>
  createUploadSession(projectId: string, fileName: string, sizeBytes: number): Promise<Result<ModelUploadSession>>
  getUploadSession(sessionId: string): Promise<Result<ModelUploadSession>>
  putChunk(
    sessionId: string,
    chunk: Blob,
    range: { start: number; endInclusive: number; total: number },
    onProgress?: ChunkProgress,
    signal?: AbortSignal,
  ): Promise<Result<{ receivedBytes: number }>>
  completeUpload(sessionId: string): Promise<Result<ModelAsset>>
  abortUpload(sessionId: string): Promise<Result<void>>
  delete(modelId: string): Promise<Result<void>>
  createDownloadToken(modelId: string): Promise<Result<DownloadToken>>
  downloadBytes(modelId: string, onProgress?: (loaded: number, total: number) => void): Promise<Result<ArrayBuffer>>
  uploadExport(modelId: string, file: Blob, fileName: string): Promise<Result<ModelAsset>>
}
