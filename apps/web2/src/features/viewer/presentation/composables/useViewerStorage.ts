import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import { loadViewerConfigUseCase, saveViewerConfigUseCase } from '../../application/use-cases/viewer-config.usecase'
import { openLocalViewerFileUseCase } from '../../application/use-cases/open-local-viewer-file.usecase'

export function useViewerStorage() {
  const container = inject(CONTAINER_KEY)!
  return {
    rememberMeta: (modelId: string, fileName: string) => container.viewerStorage.rememberModelMeta(modelId, fileName),
    openLocal: (file: File) => openLocalViewerFileUseCase(file),
    listModels: (projectId: string) => container.viewerStorage.listModels(projectId),
    loadBytes: (modelId: string, onProgress?: (loaded: number, total: number) => void, options?: { signal?: AbortSignal }) =>
      container.viewerStorage.loadModelBytes(modelId, onProgress, options),
    loadConfig: (modelId: string, options?: { signal?: AbortSignal }) =>
      loadViewerConfigUseCase({ storage: container.viewerStorage }, modelId, options),
    saveConfig: (modelId: string, config: Parameters<typeof saveViewerConfigUseCase>[2]) =>
      saveViewerConfigUseCase({ storage: container.viewerStorage }, modelId, config),
    saveExport: (modelId: string, bytes: Uint8Array, fileName: string) =>
      container.viewerStorage.saveExport(modelId, bytes, fileName),
  }
}
