import type { DomainError } from '@/shared/domain-error'
import type { JobProgressEvent } from '../entities/training-job.entity'

export interface JobEventPort {
  subscribe(jobId: string, onEvent: (event: JobProgressEvent) => void, onClose?: (error?: DomainError) => void): () => void
}
