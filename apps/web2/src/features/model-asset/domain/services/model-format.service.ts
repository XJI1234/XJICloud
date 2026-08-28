import { DomainError } from '@/shared/domain-error'
import type { ModelFormat } from '../entities/model-asset.entity'

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
  return null
}
