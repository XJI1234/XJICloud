import type { JobEventPort } from '../domain/repositories/job-event.port'
import type { TrainingJobRepository } from '../domain/repositories/training-job.repository'
import type { JobProgressEvent, TrainingJob } from '../domain/entities/training-job.entity'
import { applyJobProgress, isTerminalJob, upsertJob } from '../domain/services/job-policy.service'

export type JobWatchHub = {
  jobs: () => TrainingJob[]
  replaceAll: (jobs: TrainingJob[]) => void
  upsert: (job: TrainingJob) => void
  watch: (jobId: string) => void
  stop: (jobId: string) => void
  clear: () => void
  subscribe: (listener: () => void) => () => void
}

export function createJobWatchHub(deps: {
  jobs: TrainingJobRepository
  events: JobEventPort
  onJobsChanged?: (jobs: TrainingJob[]) => void
}): JobWatchHub {
  let items: TrainingJob[] = []
  const subscriptions = new Map<string, () => void>()
  const listeners = new Set<() => void>()

  function emit() {
    deps.onJobsChanged?.(items)
    listeners.forEach((listener) => listener())
  }

  function upsert(job: TrainingJob) {
    items = upsertJob(items, job)
    emit()
  }

  function stop(jobId: string) {
    subscriptions.get(jobId)?.()
    subscriptions.delete(jobId)
  }

  function applyEvent(event: JobProgressEvent) {
    const current = items.find((item) => item.id === event.jobId)
    if (!current) {
      return
    }
    items = upsertJob(items, applyJobProgress(current, event))
    emit()
    if (isTerminalJob(event.status)) {
      void deps.jobs.getById(event.jobId).then(([error, job]) => {
        if (!error && job) {
          upsert(job)
        }
      })
      stop(event.jobId)
    }
  }

  return {
    jobs: () => items,
    replaceAll(next) {
      items = next
      emit()
    },
    upsert,
    watch(jobId) {
      if (subscriptions.has(jobId)) {
        return
      }
      const unsubscribe = deps.events.subscribe(jobId, applyEvent)
      subscriptions.set(jobId, unsubscribe)
    },
    stop,
    subscribe(listener) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    clear() {
      for (const unsubscribe of subscriptions.values()) {
        unsubscribe()
      }
      subscriptions.clear()
      items = []
      emit()
    },
  }
}
