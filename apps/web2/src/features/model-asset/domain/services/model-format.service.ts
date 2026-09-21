import { DomainError } from '@/shared/domain-error'
import type { ModelFormat } from '../entities/model-asset.entity'
import { MODEL_MAX_SIZE_BYTES } from './chunk-range.service'

export function detectModelFormat(fileName: string): ModelFormat | null {
  const index = fileName.lastIndexOf('.')
  const ext = index >= 0 ? fileName.slice(index).toLowerCase() : ''
  if (ext === '.ply') {
    return 'PLY'
  }
  if (ext === '.spz') {
    return 'SPZ'
  }
  return null
}

export function assertModelFile(file: File) {
  const format = detectModelFormat(file.name)
  if (!format) {
    return new DomainError('MODEL_INVALID_FORMAT')
  }
  if (file.size < 1 || file.size > MODEL_MAX_SIZE_BYTES) {
    return new DomainError('MODEL_TOO_LARGE')
  }
  return null
}

export type ExportModelFormat = 'ply' | 'spz'

export function withModelExtension(fileName: string, format: ExportModelFormat): string {
  const trimmed = fileName.trim()
  const stem = trimmed.replace(/\.(ply|spz)$/i, '')
  return `${stem}.${format}`
}

export function assertExportFileName(fileName: string, format: ExportModelFormat) {
  const trimmed = fileName.trim()
  if (!trimmed) {
    return new DomainError('MODEL_INVALID_FORMAT')
  }
  return detectModelFormat(withModelExtension(trimmed, format)) ? null : new DomainError('MODEL_INVALID_FORMAT')
}
