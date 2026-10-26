import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { DatasetArchive, TrainingJob } from '../../domain/entities/training-job.entity'
import type { ObjectStoragePort } from '../../domain/repositories/object-storage.port'
import type { TrainingJobRepository } from '../../domain/repositories/training-job.repository'
import { totalArchiveBytes } from '../../domain/services/dataset-archive.service'
import { canCancelJob, canDeleteJob } from '../../domain/services/job-policy.service'

export type SubmitDatasetProgress = {
  percent: number
  loaded: number
  total: number
}

export async function submitDatasetUseCase(
  deps: { jobs: TrainingJobRepository; objectStorage: ObjectStoragePort },
  input: { projectId: string; name: string; archive: DatasetArchive; signal?: AbortSignal; onProgress?: (progress: SubmitDatasetProgress) => void },
): Promise<Result<TrainingJob>> {
  if (input.signal?.aborted) return err(new DomainError('OPERATION_CANCELLED'))
  const name = input.name.trim()
  const [ticketError, ticket] = await deps.jobs.createDataset(input.projectId, input.archive, name)
  if (ticketError || !ticket) {
    return err(ticketError ?? new DomainError('UNKNOWN'))
  }

  const totalBytes = totalArchiveBytes(input.archive)
  let completedBytes = 0
  const checkCancelled = () => { if (input.signal?.aborted) throw new DomainError('OPERATION_CANCELLED') }
  const report = (loaded: number) => {
    if (input.signal?.aborted) return
    const total = totalBytes || loaded
    input.onProgress?.({ percent: total > 0 ? Math.min(100, Math.round(loaded / total * 100)) : 0, loaded, total })
  }
  try {
    checkCancelled()
    for (const fileItem of input.archive.files) {
      checkCancelled()
      const upload = ticket.uploads.find(item => item.archivedName === fileItem.archivedName)
      if (!upload) throw new DomainError('DATASET_MISSING_UPLOAD_URL')
      await deps.objectStorage.put(upload.uploadUrl, fileItem.file, fileItem.contentType,
        loaded => report(completedBytes + loaded), input.signal)
      checkCancelled()
      completedBytes += fileItem.sizeBytes
      report(completedBytes)
    }
    checkCancelled()
    const manifestUrl = ticket.uploads.find(item => item.archivedName === 'manifest.json')?.uploadUrl || ticket.manifestUploadUrl
    if (!manifestUrl) throw new DomainError('DATASET_MISSING_UPLOAD_URL')
    await deps.objectStorage.put(manifestUrl, input.archive.manifestBlob, 'application/json',
      loaded => report(completedBytes + loaded), input.signal)
    checkCancelled()
    // Once completion reaches the server, cancellation cannot roll it back.
    const result = await deps.jobs.completeDataset(input.projectId, ticket.jobId)
    if (!result[0]) report(totalBytes)
    return result
  } catch (error) {
    const code = input.signal?.aborted ? 'OPERATION_CANCELLED' : error instanceof DomainError ? error.code : 'OSS_UPLOAD_FAILED'
    if (input.signal?.aborted) await deps.jobs.cancel(ticket.jobId)
    return err(new DomainError(code, undefined, { details: { jobId: ticket.jobId } }))
  }
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
