import type { Result } from '@/shared/result'
import type { ViewerConfig, ViewerModelSummary } from '../entities/viewer-config.entity'

export interface ViewerStoragePort {
  listModels(projectId: string): Promise<Result<ViewerModelSummary[]>>
  loadModelBytes(
    modelId: string,
    onProgress?: (loaded: number, total: number) => void,
  ): Promise<Result<{ file: File; modelId: string }>>
  loadViewerConfig(modelId: string): Promise<Result<ViewerConfig>>
  saveViewerConfig(modelId: string, config: ViewerConfig): Promise<Result<void>>
  saveExport(modelId: string, bytes: Uint8Array, fileName: string): Promise<Result<void>>
}
