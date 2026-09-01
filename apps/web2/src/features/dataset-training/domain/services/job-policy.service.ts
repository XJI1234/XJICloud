import type { JobStatus, TrainingJob } from '../entities/training-job.entity'

const ACTIVE_STATUSES: readonly JobStatus[] = ['PENDING', 'UPLOADING', 'QUEUED', 'RUNNING']
const TERMINAL_STATUSES: readonly JobStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED']

export function isActiveJob(status: JobStatus): boolean {
  return ACTIVE_STATUSES.includes(status)
}

export function isTerminalJob(status: JobStatus): boolean {
  return TERMINAL_STATUSES.includes(status)
}

export function canCancelJob(job: Pick<TrainingJob, 'status'>): boolean {
  return isActiveJob(job.status)
}

export function canDeleteJob(job: Pick<TrainingJob, 'status'>): boolean {
  return !isActiveJob(job.status)
}

export function jobMutation(job: Pick<TrainingJob, 'status'>): 'cancel' | 'delete' {
  return canCancelJob(job) ? 'cancel' : 'delete'
}

export function applyJobProgress<T extends Pick<TrainingJob, 'status' | 'progress' | 'stage' | 'message'>>(
  job: T,
  event: { status: JobStatus; progress: number; stage: string; message: string },
): T {
  return {
    ...job,
    status: event.status,
    progress: event.progress,
    stage: event.stage,
    message: event.message,
  }
}

export function upsertJob<T extends { id: string }>(jobs: T[], job: T): T[] {
  const index = jobs.findIndex((item) => item.id === job.id)
  if (index >= 0) {
    const next = jobs.slice()
    next[index] = job
    return next
  }
  return [job, ...jobs]
}
