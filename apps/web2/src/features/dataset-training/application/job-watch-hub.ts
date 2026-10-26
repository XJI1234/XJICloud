import type { JobEventPort } from '../domain/repositories/job-event.port'
import type { TrainingJobRepository } from '../domain/repositories/training-job.repository'
import type { TrainingJob } from '../domain/entities/training-job.entity'
import { applyJobProgress, isTerminalJob, upsertJob } from '../domain/services/job-policy.service'

export type JobWatchHub = {
  connectionFailed: (jobId: string) => boolean
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
  type Watch = { unsubscribe?: () => void; timer?: ReturnType<typeof setTimeout>; attempts: number }
  const subscriptions = new Map<string, Watch>()
  const failures = new Set<string>()
  let epoch = 0
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
    const owner = subscriptions.get(jobId)
    subscriptions.delete(jobId)
    owner?.unsubscribe?.()
    clearTimeout(owner?.timer)
    failures.delete(jobId)
  }

  function connect(jobId: string, owner: Watch) {
    const current = () => subscriptions.get(jobId) === owner
    const generation = epoch
    owner.unsubscribe = deps.events.subscribe(jobId, event => {
      if (!current() || generation !== epoch) return
      const job = items.find(item => item.id === event.jobId)
      if (!job) return
      failures.delete(jobId)
      items = upsertJob(items, applyJobProgress(job, event))
      emit()
      if (isTerminalJob(event.status)) {
        owner.unsubscribe?.()
        clearTimeout(owner.timer)
        void deps.jobs.getById(jobId).then(([error, snapshot]) => {
          if (current() && generation === epoch) {
            if (!error && snapshot) upsert(snapshot)
            stop(jobId)
          }
        })
      }
    }, error => {
      if (!current() || generation !== epoch) return
      owner.unsubscribe?.()
      failures.add(jobId)
      emit()
      if (error?.code === 'AUTH_UNAUTHORIZED' || owner.attempts >= 3) {
        subscriptions.delete(jobId)
        return
      }
      owner.timer = setTimeout(async () => {
        if (!current()) return
        const [snapshotError, job] = await deps.jobs.getById(jobId)
        if (!current() || generation !== epoch) return
        if (!snapshotError && job) {
          failures.delete(jobId)
          upsert(job)
          if (isTerminalJob(job.status)) { stop(jobId); emit(); return }
        }
        connect(jobId, owner)
      }, 1000 * 2 ** owner.attempts++)
    })
  }

  return {
    connectionFailed: jobId => failures.has(jobId),
    jobs: () => items,
    replaceAll(next) {
      for (const id of subscriptions.keys()) {
        if (!next.some(job => job.id === id && !isTerminalJob(job.status))) stop(id)
      }
      items = next
      emit()
    },
    upsert,
    watch(jobId) {
      if (subscriptions.has(jobId)) {
        return
      }
      const owner: Watch = { attempts: 0 }
      failures.delete(jobId)
      subscriptions.set(jobId, owner)
      connect(jobId, owner)
    },
    stop,
    subscribe(listener) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    clear() {
      epoch++
      for (const id of subscriptions.keys()) stop(id)
      failures.clear()
      items = []
      emit()
    },
  }
}
