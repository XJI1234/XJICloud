import { apiRequest } from './client'

export interface ModelSummary {
  id: string
  projectId: string
  fileName: string
  format: 'PLY' | 'SPZ'
  sizeBytes: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface ViewerConfigPayload {
  jsonPayload: string
  updatedAt: string
}

export function listModels(projectId: string) {
  return apiRequest<ModelSummary[]>(`/api/v1/projects/${projectId}/models`)
}

export function uploadModel(projectId: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiRequest<ModelSummary>(`/api/v1/projects/${projectId}/models/upload`, {
    method: 'POST',
    body: formData,
  })
}

export function getViewerConfig(modelId: string) {
  return apiRequest<ViewerConfigPayload>(`/api/v1/models/${modelId}/viewer-config`)
}

export function saveViewerConfig(modelId: string, jsonPayload: string) {
  return apiRequest<ViewerConfigPayload>(`/api/v1/models/${modelId}/viewer-config`, {
    method: 'PUT',
    body: JSON.stringify({ jsonPayload }),
  })
}

export interface DownloadTokenPayload {
  url: string
  expiresAt: string
}

export function createDownloadToken(modelId: string) {
  return apiRequest<DownloadTokenPayload>(`/api/v1/models/${modelId}/download-token`, {
    method: 'POST',
  })
}

export function uploadExport(modelId: string, file: Blob, fileName: string) {
  const formData = new FormData()
  formData.append('file', file, fileName)
  return apiRequest<ModelSummary>(`/api/v1/models/${modelId}/export`, {
    method: 'POST',
    body: formData,
  })
}
