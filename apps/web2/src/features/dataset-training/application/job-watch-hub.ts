import type { JobEventPort } from '../domain/repositories/job-event.port'
import type { TrainingJobRepository } from '../domain/repositories/training-job.repository'
import type { JobProgressEvent, TrainingJob } from '../domain/entities/training-job.entity'
import { applyJobProgress, isTerminalJob, mergeJobByUpdatedAt, shouldWatchJob, upsertJob } from '../domain/services/job-policy.service'

const REST_REFRESH_INTERVAL_MS = 30_000
const SSE_RECONNECT_DELAY_MS = 5_000

type JobWatch = {
  unsubscribe: () => void
  pollTimer: ReturnType<typeof setInterval>
  reconnectTimer: ReturnType<typeof setTimeout> | null
  connectionId: number
  connected: boolean
  refreshing: boolean
  terminalDetailsPending: boolean
}

export type JobWatchHub = {
  jobs: () => TrainingJob[]
  generation: () => number
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
  let generation = 0
  const watches = new Map<string, JobWatch>()
  const listeners = new Set<() => void>()

  function emit() {
    deps.onJobsChanged?.(items)
    listeners.forEach((listener) => listener())
  }

  function disconnect(watch: JobWatch) {
    if (!watch.connected && watch.reconnectTimer === null) {
      return
    }
    watch.connectionId += 1
    if (watch.reconnectTimer !== null) {
      clearTimeout(watch.reconnectTimer)
      watch.reconnectTimer = null
    }
    watch.unsubscribe()
    watch.unsubscribe = () => undefined
    watch.connected = false
  }

  function stop(jobId: string) {
    const watch = watches.get(jobId)
    if (!watch) {
      return
    }
    watches.delete(jobId)
    clearInterval(watch.pollTimer)
    disconnect(watch)
  }

  function connect(jobId: string, watch: JobWatch) {
    if (watch.connected || watch.reconnectTimer !== null || watches.get(jobId) !== watch) {
      return
    }
    const connectionId = ++watch.connectionId
    watch.connected = true
    const disconnected = () => {
      if (watches.get(jobId) !== watch || watch.connectionId !== connectionId || watch.reconnectTimer !== null) {
        return
      }
      watch.unsubscribe = () => undefined
      watch.connected = false
      watch.reconnectTimer = setTimeout(() => {
        watch.reconnectTimer = null
        const current = items.find((item) => item.id === jobId)
        if (watches.get(jobId) === watch && current && shouldWatchJob(current)) {
          connect(jobId, watch)
        }
      }, SSE_RECONNECT_DELAY_MS)
    }
    const unsubscribe = deps.events.subscribe(
      jobId,
      (event) => {
        if (watches.get(jobId) === watch && watch.connectionId === connectionId) {
          applyEvent(event)
        }
      },
      disconnected,
    )
    if (watches.get(jobId) === watch && watch.connectionId === connectionId) {
      watch.unsubscribe = unsubscribe
    } else {
      unsubscribe()
    }
  }

  function reconcileWatch(jobId: string) {
    const watch = watches.get(jobId)
    if (!watch) {
      return
    }
    const job = items.find((item) => item.id === jobId)
    if (!job) {
      stop(jobId)
      return
    }
    if (shouldWatchJob(job)) {
      if (!watch.connected && watch.reconnectTimer === null) {
        connect(jobId, watch)
      }
      return
    }
    disconnect(watch)
    if (!watch.terminalDetailsPending) {
      stop(jobId)
    }
  }

  function refresh(jobId: string, expectedGeneration: number) {
    const watch = watches.get(jobId)
    if (!watch || generation !== expectedGeneration) {
      return
    }
    if (watch.refreshing) {
      return
    }

    watch.refreshing = true
    void (async () => {
      try {
        const [error, snapshot] = await deps.jobs.getById(jobId)
        if (error || !snapshot || generation !== expectedGeneration || watches.get(jobId) !== watch) {
          return
        }
        const current = items.find((item) => item.id === jobId)
        if (!current || current.projectId !== snapshot.projectId) {
          return
        }
        const details = watch.terminalDetailsPending && current.status === snapshot.status && isTerminalJob(current.status)
          ? { ...snapshot, progress: current.progress, stage: current.stage, message: current.message }
          : snapshot
        const merged = mergeJobByUpdatedAt(current, details)
        if (merged !== details) {
          return
        }
        items = upsertJob(items, details)
        if (watch.terminalDetailsPending) {
          watch.terminalDetailsPending = false
        }
        emit()
      } catch (error) {
        console.error(error)
      } finally {
        if (watches.get(jobId) === watch) {
          watch.refreshing = false
          if (generation === expectedGeneration) {
            reconcileWatch(jobId)
          }
        }
      }
    })()
  }

  function applyEvent(event: JobProgressEvent) {
    const current = items.find((item) => item.id === event.jobId)
    const watch = watches.get(event.jobId)
    if (!current || !watch) {
      return
    }
    const next = applyJobProgress(current, event)
    if (mergeJobByUpdatedAt(current, next) !== next) {
      return
    }
    items = upsertJob(items, next)
    emit()
    if (isTerminalJob(event.status)) {
      watch.terminalDetailsPending = true
      refresh(event.jobId, generation)
    }
    reconcileWatch(event.jobId)
  }

  function watch(jobId: string) {
    const job = items.find((item) => item.id === jobId)
    if (watches.has(jobId) || !job || !shouldWatchJob(job)) {
      return
    }
    const watchGeneration = generation
    const watchState: JobWatch = {
      unsubscribe: () => undefined,
      pollTimer: setInterval(() => refresh(jobId, watchGeneration), REST_REFRESH_INTERVAL_MS),
      reconnectTimer: null,
      connectionId: 0,
      connected: false,
      refreshing: false,
      terminalDetailsPending: false,
    }
    watches.set(jobId, watchState)
    connect(jobId, watchState)
  }

  return {
    jobs: () => items,
    generation: () => generation,
    replaceAll(next) {
      const currentById = new Map(items.map((job) => [job.id, job]))
      const nextIds = new Set(next.map((job) => job.id))
      for (const jobId of watches.keys()) {
        if (!nextIds.has(jobId)) {
          stop(jobId)
        }
      }
      items = next.map((job) => {
        const merged = mergeJobByUpdatedAt(currentById.get(job.id), job)
        const watch = watches.get(job.id)
        if (watch && merged === job) {
          watch.terminalDetailsPending = false
        }
        return merged
      })
      for (const job of items) {
        reconcileWatch(job.id)
      }
      emit()
    },
    upsert(job) {
      const current = items.find((item) => item.id === job.id)
      const merged = mergeJobByUpdatedAt(current, job)
      if (merged !== job) {
        return
      }
      items = upsertJob(items, job)
      const watch = watches.get(job.id)
      if (watch) {
        watch.terminalDetailsPending = false
        reconcileWatch(job.id)
      }
      emit()
    },
    watch,
    stop,
    subscribe(listener) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    clear() {
      generation += 1
      for (const jobId of watches.keys()) {
        stop(jobId)
      }
      items = []
      emit()
    },
  }
}
