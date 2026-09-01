export type UploadProgress = (loaded: number, total: number) => void

export interface ObjectStoragePort {
  put(uploadUrl: string, blob: Blob, contentType: string, onProgress?: UploadProgress): Promise<void>
}
