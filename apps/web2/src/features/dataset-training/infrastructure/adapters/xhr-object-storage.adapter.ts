import { DomainError } from '@/shared/domain-error'
import type { ObjectStoragePort } from '../../domain/repositories/object-storage.port'

export function createXhrObjectStorage(): ObjectStoragePort {
  return {
    put(uploadUrl, blob, contentType, onProgress, signal) {
      return new Promise((resolve, reject) => {
        if (signal?.aborted) { reject(new DomainError('OPERATION_CANCELLED')); return }
        const xhr = new XMLHttpRequest()
        const abort = () => xhr.abort()
        const cleanup = () => {
          signal?.removeEventListener('abort', abort)
          xhr.onload = xhr.onerror = xhr.onabort = null
          xhr.upload.onprogress = null
        }
        xhr.open('PUT', uploadUrl)
        xhr.setRequestHeader('Content-Type', contentType)
        xhr.upload.onprogress = (event) => {
          if (event.lengthComputable) {
            onProgress?.(event.loaded, event.total)
          }
        }
        xhr.onload = () => {
          cleanup()
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve()
            return
          }
          reject(new DomainError('OSS_UPLOAD_FAILED'))
        }
        xhr.onerror = () => { cleanup(); reject(new DomainError('OSS_UPLOAD_FAILED')) }
        xhr.onabort = () => { cleanup(); reject(new DomainError('OPERATION_CANCELLED')) }
        signal?.addEventListener('abort', abort, { once: true })
        try { xhr.send(blob) } catch { cleanup(); reject(new DomainError('OSS_UPLOAD_FAILED')) }
      })
    },
  }
}
