import type { EngineError } from '@native3dgs/web'
import { DomainError } from '@/shared/domain-error'

export function mapViewerEngineError(error: EngineError): DomainError {
  if (error.stage === 'StorageCleanup') return new DomainError('VIEWER_CLEANUP_FAILED')
  switch (error.code) {
    case 'UnsupportedCapability': return new DomainError('VIEWER_WEBGPU_UNAVAILABLE')
    case 'UnsupportedFormat':
    case 'InvalidInput': return new DomainError('VIEWER_UNSUPPORTED_FORMAT')
    case 'ResourceLimit':
    case 'OutOfMemory': return new DomainError('VIEWER_RESOURCE_LIMIT')
    case 'DeviceLost': return new DomainError('VIEWER_DEVICE_LOST')
    case 'Cancelled': return new DomainError('VIEWER_CANCELLED')
    case 'Timeout': return new DomainError('VIEWER_TIMEOUT')
    default: return new DomainError('VIEWER_LOAD_FAILED')
  }
}
