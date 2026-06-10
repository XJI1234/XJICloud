import type { InjectionKey } from 'vue'
import {
  getViewerConfig,
  listModels,
  saveViewerConfig,
  uploadExport,
  type ModelSummary as ApiModelSummary,
} from '@/api/models'
import { downloadModelBytes } from '@/api/client'
import type { ModelSummary, ViewerConfigFile } from '@/types/viewer'

export type { ModelSummary } from '@/types/viewer'

export interface ViewerStoragePort {
  listModels(projectId: string): Promise<ModelSummary[]>
  loadModelBytes(modelId: string, onProgress?: (loaded: number, total: number) => void): Promise<{ file: File; modelId: string }>
  loadViewerConfig(modelId: string): Promise<ViewerConfigFile>
  loadViewerConfigRaw(modelId: string): Promise<string>
  saveViewerConfig(modelId: string, config: ViewerConfigFile): Promise<void>
  saveExport(modelId: string, bytes: Uint8Array, fileName: string): Promise<void>
}

export const VIEWER_STORAGE_KEY: InjectionKey<ViewerStoragePort> = Symbol('viewerStorage')

function mapModelSummary(model: ApiModelSummary): ModelSummary {
  return {
    id: model.id,
    fileName: model.fileName,
    format: model.format,
    sizeBytes: model.sizeBytes,
    version: model.version,
    updatedAt: model.updatedAt,
  }
}

export function createCloudViewerStorage(): ViewerStoragePort {
  return {
    async listModels(projectId) {
      const models = await listModels(projectId)
      return models.map(mapModelSummary)
    },

    async loadModelBytes(modelId, onProgress) {
      const buffer = await downloadModelBytes(modelId, onProgress)
      const fileName = modelMetaCache.get(modelId)?.fileName ?? 'model.spz'
      return {
        file: new File([buffer], fileName),
        modelId,
      }
    },

    async loadViewerConfigRaw(modelId) {
      const payload = await getViewerConfig(modelId)
      return payload.jsonPayload
    },

    async loadViewerConfig(modelId) {
      const raw = await this.loadViewerConfigRaw(modelId)
      return JSON.parse(raw) as ViewerConfigFile
    },

    async saveViewerConfig(modelId, config) {
      await saveViewerConfig(modelId, JSON.stringify(config, null, 2))
    },

    async saveExport(modelId, bytes, fileName) {
      await uploadExport(modelId, new Blob([new Uint8Array(bytes)], { type: 'application/octet-stream' }), fileName)
    },
  }
}

interface ModelMeta {
  fileName: string
  projectId: string
}

const modelMetaCache = new Map<string, ModelMeta>()

export function rememberModelMeta(modelId: string, meta: ModelMeta) {
  modelMetaCache.set(modelId, meta)
}
