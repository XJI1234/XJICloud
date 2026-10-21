import { afterEach, describe, expect, it, vi } from 'vitest'
import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { JobProgressEvent, TrainingJob } from '../domain/entities/training-job.entity'
import type { JobEventPort } from '../domain/repositories/job-event.port'
import type { TrainingJobRepository } from '../domain/repositories/training-job.repository'
import { createJobWatchHub } from './job-watch-hub'

type LifecycleFields = {
  computeStatus: string | null
  cloudStatus: string | null
  computeStatusReason: string | null
  cancellationPending: boolean
  cleanupPending: boolean
  updatedAt: string
}

type LifecycleEvent = JobProgressEvent & LifecycleFields

function job(status: TrainingJob['status'], fields: Partial<LifecycleFields> = {}): TrainingJob & LifecycleFields {
  return {
    id: 'job-1',
    projectId: 'project-1',
    name: 'dataset',
    status,
    progress: 20,
    stage: 'training',
    message: 'working',
    downloadUrl: null,
    errorMessage: null,
    createdAt: '2026-10-18T09:00:00Z',
    computeStatus: null,
    cloudStatus: null,
    computeStatusReason: null,
    cancellationPending: false,
    cleanupPending: false,
    updatedAt: '2026-10-18T09:00:00Z',
    ...fields,
  } as TrainingJob & LifecycleFields
}

function event(fields: Partial<LifecycleEvent> = {}): LifecycleEvent {
  return {
    jobId: 'job-1',
    status: 'RUNNING',
    progress: 20,
    stage: 'training',
    message: 'working',
    computeStatus: null,
    cloudStatus: null,
    computeStatusReason: null,
    cancellationPending: false,
    cleanupPending: false,
    updatedAt: '2026-10-18T09:00:00Z',
    ...fields,
  } as LifecycleEvent
}

function createRepository(getById: TrainingJobRepository['getById'] = async () => ok(job('RUNNING'))) {
  return {
    createDataset: async () => ok({ jobId: 'job-1', manifestUploadUrl: '', uploads: [] }),
    completeDataset: async () => ok(job('QUEUED')),
    listByProject: async () => ok([]),
    getById,
    cancel: async () => ok(job('CANCELLED')),
    delete: async () => ok(undefined),
  } as unknown as TrainingJobRepository
}

function createHarness(getById?: TrainingJobRepository['getById']) {
  let onEvent: ((event: JobProgressEvent) => void) | undefined
  const unsubscribe = vi.fn()
  const events: JobEventPort = {
    subscribe(_jobId, listener) {
      onEvent = listener
      return unsubscribe
    },
  }
  const hub = createJobWatchHub({
    jobs: createRepository(getById),
    events,
  })

  return {
    hub,
    unsubscribe,
    send: (next: LifecycleEvent) => onEvent?.(next),
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await Promise.resolve()
}

describe('job watch hub lifecycle synchronization', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps watching a cancelled job until cleanup is confirmed', () => {
    const { hub, send, unsubscribe } = createHarness()
    hub.upsert(job('RUNNING'))
    hub.watch('job-1')

    send(event({
      status: 'CANCELLED',
      stage: 'cleanup',
      cancellationPending: true,
      cleanupPending: true,
      updatedAt: '2026-10-18T09:01:00Z',
    }))

    expect(hub.jobs()[0]).toMatchObject({
      status: 'CANCELLED',
      cancellationPending: true,
      cleanupPending: true,
    })
    expect(unsubscribe).not.toHaveBeenCalled()

    send(event({
      status: 'CANCELLED',
      stage: 'cancelled',
      cancellationPending: false,
      cleanupPending: false,
      updatedAt: '2026-10-18T09:02:00Z',
    }))

    expect(hub.jobs()[0]).toMatchObject({
      status: 'CANCELLED',
      cancellationPending: false,
      cleanupPending: false,
    })
    expect(unsubscribe).toHaveBeenCalledOnce()
  })

  it('ignores an older SSE event after a newer REST snapshot', () => {
    const { hub, send } = createHarness()
    hub.replaceAll([job('RUNNING', { updatedAt: '2026-10-18T09:02:00Z' })])
    hub.upsert({
      ...job('RUNNING'),
      progress: 82,
      stage: 'refining',
      message: 'latest snapshot',
      updatedAt: '2026-10-18T09:02:00Z',
    } as TrainingJob & LifecycleFields)
    hub.watch('job-1')

    send(event({
      status: 'RUNNING',
      progress: 15,
      stage: 'starting',
      message: 'delayed event',
      updatedAt: '2026-10-18T09:01:00Z',
    }))

    expect(hub.jobs()[0]).toMatchObject({
      progress: 82,
      stage: 'refining',
      message: 'latest snapshot',
      updatedAt: '2026-10-18T09:02:00Z',
    })
  })

  it('uses same-version REST snapshots to complete status and details omitted by SSE', () => {
    const { hub } = createHarness()
    const updatedAt = '2026-10-18T09:02:00.123456789Z'
    const running = {
      ...job('RUNNING', { updatedAt }),
      id: 'completed-job',
      progress: 91,
      downloadUrl: null,
      errorMessage: null,
    }
    const failed = {
      ...job('FAILED', { updatedAt }),
      id: 'failed-job',
      errorMessage: null,
    }
    const signedUrl = 'https://oss.example/model.ply?signature=short-lived-token'

    hub.replaceAll([running, failed])
    hub.replaceAll([
      { ...running, status: 'COMPLETED', progress: 100, downloadUrl: signedUrl },
      { ...failed, errorMessage: 'Worker exited with code 2' },
    ])

    expect(hub.jobs().find(({ id }) => id === 'completed-job')).toMatchObject({
      status: 'COMPLETED',
      progress: 100,
      downloadUrl: signedUrl,
      updatedAt,
    })
    expect(hub.jobs().find(({ id }) => id === 'failed-job')).toMatchObject({
      status: 'FAILED',
      errorMessage: 'Worker exited with code 2',
      updatedAt,
    })
  })

  it('compares full Instant precision when rejecting stale job updates', () => {
    const { hub } = createHarness()
    const newer = job('RUNNING', { updatedAt: '2026-10-18T09:02:00.123456790Z' })
    const older = {
      ...job('RUNNING', { updatedAt: '2026-10-18T09:02:00.123456789Z' }),
      progress: 10,
      stage: 'stale event',
    }

    hub.replaceAll([newer])
    hub.upsert(older)

    expect(hub.jobs()[0]).toMatchObject({
      progress: 20,
      stage: 'training',
      updatedAt: '2026-10-18T09:02:00.123456790Z',
    })
  })

  it('refreshes terminal details after an older in-flight RUNNING refresh is rejected', async () => {
    vi.useFakeTimers()
    const staleResponse = deferred<Result<TrainingJob>>()
    const finalSnapshot = {
      ...job('COMPLETED', {
        computeStatus: 'SUCCEEDED',
        updatedAt: '2026-10-18T09:03:00.000000002Z',
      }),
      downloadUrl: 'https://oss.example/final.ply?signature=completed-token',
    }
    const getById = vi.fn()
      .mockImplementationOnce(() => staleResponse.promise)
      .mockResolvedValue(ok(finalSnapshot))
    const { hub, send } = createHarness(getById)
    hub.upsert(job('RUNNING', { updatedAt: '2026-10-18T09:00:00.000000000Z' }))
    hub.watch('job-1')

    await vi.advanceTimersByTimeAsync(30_000)
    expect(getById).toHaveBeenCalledTimes(1)

    send(event({
      status: 'COMPLETED',
      progress: 100,
      stage: 'done',
      message: 'completed',
      updatedAt: '2026-10-18T09:02:00.000000000Z',
    }))
    staleResponse.resolve(ok(job('RUNNING', { updatedAt: '2026-10-18T09:00:00.000000000Z' })))
    await flushPromises()
    expect(hub.jobs()[0]).toMatchObject({
      status: 'COMPLETED',
      updatedAt: '2026-10-18T09:02:00.000000000Z',
      downloadUrl: null,
    })
    await vi.advanceTimersByTimeAsync(30_000)

    expect(getById).toHaveBeenCalledTimes(2)
    expect(hub.jobs()[0]).toMatchObject({
      status: 'COMPLETED',
      progress: 100,
      downloadUrl: 'https://oss.example/final.ply?signature=completed-token',
      updatedAt: '2026-10-18T09:03:00.000000002Z',
    })
  })

  it('recovers terminal details after a temporary REST failure', async () => {
    vi.useFakeTimers()
    const finalSnapshot = {
      ...job('FAILED', {
        computeStatus: 'FAILED',
        computeStatusReason: 'Training process exited',
        updatedAt: '2026-10-18T09:03:00Z',
      }),
      errorMessage: 'Training process exited',
    }
    const getById = vi.fn()
      .mockResolvedValueOnce(err(new DomainError('NETWORK')))
      .mockResolvedValue(ok(finalSnapshot))
    const { hub, send } = createHarness(getById)
    hub.upsert(job('RUNNING'))
    hub.watch('job-1')

    send(event({
      status: 'FAILED',
      stage: 'failed',
      message: 'Training failed',
      updatedAt: '2026-10-18T09:02:00Z',
    }))
    await flushPromises()
    expect(getById).toHaveBeenCalledTimes(1)
    expect(hub.jobs()[0]).toMatchObject({
      status: 'FAILED',
      errorMessage: null,
      updatedAt: '2026-10-18T09:02:00Z',
    })

    await vi.advanceTimersByTimeAsync(30_000)

    expect(getById).toHaveBeenCalledTimes(2)
    expect(hub.jobs()[0]).toMatchObject({
      status: 'FAILED',
      errorMessage: 'Training process exited',
      computeStatusReason: 'Training process exited',
    })
  })

  it('refreshes through REST when SSE misses events and stops after cleanup completes', async () => {
    vi.useFakeTimers()
    const finalSnapshot = job('COMPLETED', {
      computeStatus: 'SUCCEEDED',
      cloudStatus: 'TERMINATED',
      updatedAt: '2026-10-18T09:03:00Z',
    })
    const getById = vi.fn(async () => ok(finalSnapshot))
    const { hub, unsubscribe } = createHarness(getById)
    hub.upsert(job('RUNNING'))

    hub.watch('job-1')
    await vi.advanceTimersByTimeAsync(120_000)

    expect(getById).toHaveBeenCalledWith('job-1')
    expect(hub.jobs()[0]).toMatchObject({
      status: 'COMPLETED',
      computeStatus: 'SUCCEEDED',
      cloudStatus: 'TERMINATED',
    })
    expect(unsubscribe).toHaveBeenCalledOnce()
  })

  it('does not restore a job when a REST refresh resolves after clear', async () => {
    const pending: Array<(result: Result<TrainingJob>) => void> = []
    const getById = vi.fn(() => new Promise<Result<TrainingJob>>((resolve) => pending.push(resolve)))
    const { hub, send } = createHarness(getById)
    hub.upsert(job('RUNNING'))
    hub.watch('job-1')

    send(event({ status: 'CANCELLED', cleanupPending: true, cancellationPending: true }))
    expect(pending.length).toBeGreaterThan(0)
    hub.clear()
    pending.forEach((resolve) => resolve(ok(job('CANCELLED'))))
    await Promise.resolve()
    await Promise.resolve()

    expect(hub.jobs()).toEqual([])
  })
})
