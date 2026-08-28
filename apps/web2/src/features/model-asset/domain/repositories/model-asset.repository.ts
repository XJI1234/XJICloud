import type { Result } from '@/shared/result'
import type { DownloadToken, ModelAsset } from '../entities/model-asset.entity'

export interface ModelAssetRepository {
  list(projectId: string): Promise<Result<ModelAsset[]>>
  upload(projectId: string, file: File): Promise<Result<ModelAsset>>
  createDownloadToken(modelId: string): Promise<Result<DownloadToken>>
  downloadBytes(modelId: string, onProgress?: (loaded: number, total: number) => void): Promise<Result<ArrayBuffer>>
  uploadExport(modelId: string, file: Blob, fileName: string): Promise<Result<ModelAsset>>
}
