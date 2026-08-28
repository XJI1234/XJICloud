import type { DatasetUploadTicket, JobProgressEvent, JobStatus, TrainingJob } from '../../domain/entities/training-job.entity'

export type JobResponseDto = {
  id?: string
  projectId?: string
  name?: string
  status?: JobStatus
  progress?: number
  stage?: string | null
  message?: string | null
  downloadUrl?: string | null
  errorMessage?: string | null
  createdAt?: string
  updatedAt?: string
}

export type CreateDatasetResponseDto = {
  jobId?: string
  manifestUploadUrl?: string
  uploads?: Array<{
    archivedName?: string
    ossKey?: string
    uploadUrl?: string
    contentType?: string
  }>
}

export type JobProgressEventDto = {
  jobId?: string
  status?: JobStatus
  progress?: number
  stage?: string
  message?: string
}

export function mapJobFromDto(dto: JobResponseDto): TrainingJob {
  return {
    id: dto.id ?? '',
    projectId: dto.projectId ?? '',
    name: dto.name ?? '',
    status: dto.status ?? 'PENDING',
    progress: dto.progress ?? 0,
    stage: dto.stage ?? null,
    message: dto.message ?? null,
    downloadUrl: dto.downloadUrl ?? null,
    errorMessage: dto.errorMessage ?? null,
    createdAt: dto.createdAt ?? '',
    updatedAt: dto.updatedAt ?? '',
  }
}

export function mapTicketFromDto(dto: CreateDatasetResponseDto): DatasetUploadTicket {
  return {
    jobId: dto.jobId ?? '',
    manifestUploadUrl: dto.manifestUploadUrl ?? '',
    uploads: (dto.uploads ?? []).map((item) => ({
      archivedName: item.archivedName ?? '',
      ossKey: item.ossKey ?? '',
      uploadUrl: item.uploadUrl ?? '',
      contentType: item.contentType ?? 'application/octet-stream',
    })),
  }
}

export function mapProgressEventFromDto(dto: JobProgressEventDto): JobProgressEvent {
  return {
    jobId: dto.jobId ?? '',
    status: dto.status ?? 'PENDING',
    progress: dto.progress ?? 0,
    stage: dto.stage ?? '',
    message: dto.message ?? '',
  }
}
