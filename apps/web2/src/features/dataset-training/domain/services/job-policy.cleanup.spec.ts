import { describe, expect, it } from 'vitest'
import type { JobStatus } from '../entities/training-job.entity'
import { canCancelJob, canDeleteJob, jobMutation } from './job-policy.service'

describe('job cleanup policy', () => {
  it.each(['QUEUED', 'RUNNING'] as const)(
    'allows cancelling an active %s job when its cloud resources still exist',
    (status: JobStatus) => {
      const job = { status, cancellationPending: false, cleanupPending: true }

      expect(canCancelJob(job)).toBe(true)
      expect(jobMutation(job)).toBe('cancel')
    },
  )

  it('blocks another cancellation after cancellation is already pending', () => {
    const job = { status: 'RUNNING' as const, cancellationPending: true, cleanupPending: true }

    expect(canCancelJob(job)).toBe(false)
    expect(jobMutation(job)).toBeNull()
  })

  it.each([
    {
      name: 'cancellation',
      job: { status: 'CANCELLED' as const, cancellationPending: true, cleanupPending: true },
    },
    {
      name: 'cleanup',
      job: { status: 'FAILED' as const, cancellationPending: false, cleanupPending: true },
    },
  ])('keeps terminal jobs with pending $name unavailable for deletion', ({ job }) => {
    expect(canDeleteJob(job)).toBe(false)
    expect(jobMutation(job)).toBeNull()
  })
})
