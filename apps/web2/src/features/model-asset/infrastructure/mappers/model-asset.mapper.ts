import type { DownloadToken, ModelAsset, ModelFormat } from '../../domain/entities/model-asset.entity'

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

export function mapDownloadTokenFromDto(dto: DownloadTokenDto): DownloadToken {
  return {
    url: dto.url ?? '',
    expiresAt: dto.expiresAt ?? '',
  }
}
