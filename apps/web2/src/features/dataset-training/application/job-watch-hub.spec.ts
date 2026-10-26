import { afterEach, describe, expect, it, vi } from 'vitest'
import { createJobWatchHub } from './job-watch-hub'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import type { JobEventPort } from '../domain/repositories/job-event.port'
import type { TrainingJobRepository } from '../domain/repositories/training-job.repository'
import type { TrainingJob } from '../domain/entities/training-job.entity'

const job: TrainingJob = { id: 'j', projectId: 'p', name: 'job', status: 'RUNNING', progress: 1, stage: null, message: null, downloadUrl: null, errorMessage: null, createdAt: '', updatedAt: '' }
afterEach(() => vi.useRealTimers())
describe('job observer ownership and recovery', () => {
  function fixture(getById = vi.fn(async () => ok(job))) {
    const handlers: Array<{ event: Parameters<JobEventPort['subscribe']>[1]; close: Parameters<JobEventPort['subscribe']>[2] }> = []
    const unsubscribe = vi.fn()
    const subscribe = vi.fn<JobEventPort['subscribe']>((_id, event, close) => { handlers.push({ event, close }); return unsubscribe })
    const hub = createJobWatchHub({ jobs: { getById } as unknown as TrainingJobRepository, events: { subscribe } })
    hub.upsert(job); hub.watch(job.id)
    return { hub, handlers, subscribe, unsubscribe }
  }
  it('never resurrects a terminal refresh after clear', async () => {
    let finish!: (value: ReturnType<typeof ok<TrainingJob>>) => void
    const pending = new Promise<ReturnType<typeof ok<TrainingJob>>>(resolve => { finish = resolve })
    const { hub, handlers } = fixture(vi.fn(() => pending))
    handlers[0]!.event({ jobId: 'j', status: 'COMPLETED', progress: 100, stage: '', message: '' })
    hub.clear(); finish(ok({ ...job, status: 'COMPLETED' }))
    await pending; await Promise.resolve()
    expect(hub.jobs()).toEqual([])
  })
  it('recovers a job completed while disconnected through its snapshot', async () => {
    vi.useFakeTimers()
    const getById = vi.fn(async () => ok({ ...job, status: 'COMPLETED' as const }))
    const { hub, handlers, subscribe } = fixture(getById)
    handlers[0]!.close?.(new DomainError('SSE_CONNECT_FAILED'))
    expect(hub.connectionFailed('j')).toBe(true)
    await vi.advanceTimersByTimeAsync(1000)
    expect(hub.jobs()[0]?.status).toBe('COMPLETED')
    expect(hub.connectionFailed('j')).toBe(false)
    expect(subscribe).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('bounds automatic recovery, permits manual retry and cancels timers on stop', async () => {
    vi.useFakeTimers()
    const { hub, handlers, subscribe } = fixture(vi.fn(async () => err(new DomainError('NETWORK'))))
    for (let i = 0; i < 4; i++) {
      handlers[i]!.close?.()
      if (i < 3) await vi.advanceTimersByTimeAsync(1000 * 2 ** i)
    }
    expect(vi.getTimerCount()).toBe(0)
    hub.watch('j')
    expect(subscribe).toHaveBeenCalledTimes(5)
    handlers[4]!.close?.()
    hub.stop('j')
    await vi.advanceTimersByTimeAsync(10_000)
    expect(subscribe).toHaveBeenCalledTimes(5)
    hub.clear()
  })
})
