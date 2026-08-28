import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { DatasetArchive, TrainingJob } from '../../domain/entities/training-job.entity'
import type { ObjectStoragePort } from '../../domain/repositories/object-storage.port'
import type { TrainingJobRepository } from '../../domain/repositories/training-job.repository'
import { totalArchiveBytes } from '../../domain/services/dataset-archive.service'
import { canCancelJob, canDeleteJob } from '../../domain/services/job-policy.service'

export type SubmitDatasetProgress = {
  percent: number
}

export async function submitDatasetUseCase(
  deps: { jobs: TrainingJobRepository; objectStorage: ObjectStoragePort },
  input: { projectId: string; name: string; archive: DatasetArchive; onProgress?: (progress: SubmitDatasetProgress) => void },
): Promise<Result<TrainingJob>> {
  const name = input.name.trim()
  const [ticketError, ticket] = await deps.jobs.createDataset(input.projectId, input.archive, name)
  if (ticketError || !ticket) {
    return err(ticketError ?? new DomainError('UNKNOWN'))
  }

  const totalBytes = totalArchiveBytes(input.archive)
  let completedBytes = 0

  for (const fileItem of input.archive.files) {
    const uploadItem = ticket.uploads.find((item) => item.archivedName === fileItem.archivedName)
    if (!uploadItem) {
      return err(new DomainError('DATASET_MISSING_UPLOAD_URL', undefined, { details: { name: fileItem.archivedName } }))
    }
    try {
      await deps.objectStorage.put(uploadItem.uploadUrl, fileItem.file, fileItem.contentType, (loaded) => {
        input.onProgress?.({
          percent: Math.min(100, Math.round(((completedBytes + loaded) / totalBytes) * 100)),
        })
      })
    } catch (error) {
      return err(
        error instanceof DomainError
          ? error
          : new DomainError('OSS_UPLOAD_FAILED', error instanceof Error ? error.message : String(error)),
      )
    }
    completedBytes += fileItem.sizeBytes
    input.onProgress?.({ percent: Math.min(100, Math.round((completedBytes / totalBytes) * 100)) })
  }

  const manifestUpload = ticket.uploads.find((item) => item.archivedName === 'manifest.json')
  if (manifestUpload) {
    try {
      await deps.objectStorage.put(
        manifestUpload.uploadUrl,
        input.archive.manifestBlob,
        'application/json',
        (loaded) => {
          input.onProgress?.({
            percent: Math.min(100, Math.round(((completedBytes + loaded) / totalBytes) * 100)),
          })
        },
      )
    } catch (error) {
      return err(
        error instanceof DomainError
          ? error
          : new DomainError('OSS_UPLOAD_FAILED', error instanceof Error ? error.message : String(error)),
      )
    }
  }

  input.onProgress?.({ percent: 100 })
  return deps.jobs.completeDataset(input.projectId, ticket.jobId)
}

export async function cancelOrDeleteJobUseCase(
  deps: { jobs: TrainingJobRepository },
  job: TrainingJob,
): Promise<Result<TrainingJob | void>> {
  if (canCancelJob(job)) {
    return deps.jobs.cancel(job.id)
  }
  if (canDeleteJob(job)) {
    return deps.jobs.delete(job.id)
  }
  return err(new DomainError('JOB_CANNOT_DELETE'))
}
