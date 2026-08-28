export type ModelFormat = 'PLY' | 'SPZ'

export type ModelAsset = {
  id: string
  projectId: string
  fileName: string
  format: ModelFormat
  sizeBytes: number
  version: number
  createdAt: string
  updatedAt: string
}

export type DownloadToken = {
  url: string
  expiresAt: string
}
