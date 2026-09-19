import { DomainError } from '@/shared/domain-error'
import type { ObjectStoragePort } from '../../domain/repositories/object-storage.port'

export function createXhrObjectStorage(): ObjectStoragePort {
  return {
    put(uploadUrl, blob, contentType, onProgress) {
      return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', uploadUrl)
        xhr.setRequestHeader('Content-Type', contentType)
        xhr.upload.onprogress = (event) => {
          if (event.lengthComputable) {
            onProgress?.(event.loaded, event.total)
          }
        }
        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) {
            resolve()
            return
          }
          reject(new DomainError('OSS_UPLOAD_FAILED'))
        }
        xhr.onerror = () => reject(new DomainError('OSS_UPLOAD_FAILED'))
        xhr.send(blob)
      })
    },
  }
}
