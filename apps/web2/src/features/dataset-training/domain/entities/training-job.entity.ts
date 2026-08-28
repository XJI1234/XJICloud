export type JobStatus =
  | 'PENDING'
  | 'UPLOADING'
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export type TrainingJob = {
  id: string
  projectId: string
  name: string
  status: JobStatus
  progress: number
  stage: string | null
  message: string | null
  downloadUrl: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export type JobProgressEvent = {
  jobId: string
  status: JobStatus
  progress: number
  stage: string
  message: string
}

export type ArchivedImageFile = {
  archivedName: string
  originalName: string
  contentType: string
  sizeBytes: number
  file: File
}

export type DatasetManifest = {
  version: 1
  imageCount: number
  files: Array<{
    archivedName: string
    originalName: string
    contentType: string
    sizeBytes: number
  }>
}

export type DatasetArchive = {
  manifest: DatasetManifest
  files: ArchivedImageFile[]
  manifestBlob: Blob
}

export type PresignedUpload = {
  archivedName: string
  ossKey: string
  uploadUrl: string
  contentType: string
}

export type DatasetUploadTicket = {
  jobId: string
  manifestUploadUrl: string
  uploads: PresignedUpload[]
}
