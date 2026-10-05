import type { DownloadToken, ModelAsset, ModelFormat, ModelVersion } from '../../domain/entities/model-asset.entity'

export type ModelSummaryDto = {
  id?: string
  projectId?: string
  fileName?: string
  format?: ModelFormat
  sizeBytes?: number
  version?: number
  createdAt?: string
  updatedAt?: string
}

export type ModelVersionDto = {
  id?: string
  fileName?: string
  sizeBytes?: number
  createdAt?: string
  current?: boolean
  version?: number
}

export type DownloadTokenDto = {
  url?: string
  expiresAt?: string
}

export function mapModelFromDto(dto: ModelSummaryDto): ModelAsset {
  return {
    id: dto.id ?? '',
    projectId: dto.projectId ?? '',
    fileName: dto.fileName ?? '',
    format: dto.format === 'PLY' || dto.format === 'SPZ' ? dto.format : 'PLY',
    sizeBytes: dto.sizeBytes ?? 0,
    version: dto.version ?? 0,
    createdAt: dto.createdAt ?? '',
    updatedAt: dto.updatedAt ?? '',
  }
}

export function mapModelVersionFromDto(dto: ModelVersionDto): ModelVersion {
  return {
    id: dto.id ?? '',
    fileName: dto.fileName ?? '',
    sizeBytes: dto.sizeBytes ?? 0,
    createdAt: dto.createdAt ?? '',
    current: Boolean(dto.current),
    version: dto.version ?? 0,
  }
}

export type UploadSessionDto = {
  sessionId?: string
  chunkSizeBytes?: number
  receivedBytes?: number
  sizeBytes?: number
}

export type UploadChunkDto = {
  receivedBytes?: number
}

export function mapUploadSessionFromDto(dto: UploadSessionDto) {
  return {
    sessionId: dto.sessionId ?? '',
    chunkSizeBytes: dto.chunkSizeBytes ?? 8 * 1024 * 1024,
    receivedBytes: dto.receivedBytes ?? 0,
    sizeBytes: dto.sizeBytes ?? 0,
  }
}

export function mapDownloadTokenFromDto(dto: DownloadTokenDto): DownloadToken {
  return {
    url: dto.url ?? '',
    expiresAt: dto.expiresAt ?? '',
  }
}
