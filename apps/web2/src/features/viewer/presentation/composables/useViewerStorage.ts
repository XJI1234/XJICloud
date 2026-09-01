import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import { rememberViewerModelMeta } from '../../infrastructure/repositories/http-viewer-storage.repository'
import { loadViewerConfigUseCase, saveViewerConfigUseCase } from '../../application/use-cases/viewer-config.usecase'
import { openLocalViewerFileUseCase } from '../../application/use-cases/open-local-viewer-file.usecase'

export function useViewerStorage() {
  const container = inject(CONTAINER_KEY)!
  return {
    rememberMeta: rememberViewerModelMeta,
    openLocal: (file: File) => openLocalViewerFileUseCase(file),
    listModels: (projectId: string) => container.viewerStorage.listModels(projectId),
    loadBytes: (modelId: string, onProgress?: (loaded: number, total: number) => void) =>
      container.viewerStorage.loadModelBytes(modelId, onProgress),
    loadConfig: (modelId: string) => loadViewerConfigUseCase({ storage: container.viewerStorage }, modelId),
    saveConfig: (modelId: string, config: Parameters<typeof saveViewerConfigUseCase>[2]) =>
      saveViewerConfigUseCase({ storage: container.viewerStorage }, modelId, config),
    saveExport: (modelId: string, bytes: Uint8Array, fileName: string) =>
      container.viewerStorage.saveExport(modelId, bytes, fileName),
  }
}
