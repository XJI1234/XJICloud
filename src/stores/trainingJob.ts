import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ApiError } from '@/api/client'
import {
  getJob,
  listProjectJobs,
  subscribeJobEvents,
  type JobProgressEvent,
  type JobResponse,
} from '@/api/datasets'

export const useTrainingJobStore = defineStore('trainingJob', () => {
  const jobs = ref<JobResponse[]>([])
  const loading = ref(false)
  const errorMessage = ref('')
  const activeSubscriptions = new Map<string, () => void>()

  async function fetchJobs(projectId: string) {
    loading.value = true
    errorMessage.value = ''
    try {
      jobs.value = await listProjectJobs(projectId)
    } catch (error) {
      errorMessage.value = error instanceof ApiError ? error.message : '加载训练任务失败'
      throw error
    } finally {
      loading.value = false
    }
  }

  function upsertJob(job: JobResponse) {
    const index = jobs.value.findIndex((item) => item.id === job.id)
    if (index >= 0) {
      jobs.value[index] = job
      return
    }
    jobs.value.unshift(job)
  }

  function applyProgressEvent(event: JobProgressEvent) {
    const index = jobs.value.findIndex((item) => item.id === event.jobId)
    if (index < 0) {
      return
    }

    jobs.value[index] = {
      ...jobs.value[index],
      status: event.status,
      progress: event.progress,
      stage: event.stage,
      message: event.message,
    }
  }

  function watchJob(jobId: string) {
    if (activeSubscriptions.has(jobId)) {
      return
    }

    const unsubscribe = subscribeJobEvents(jobId, (event) => {
      applyProgressEvent(event)
      if (event.status === 'COMPLETED' || event.status === 'FAILED' || event.status === 'CANCELLED') {
        void refreshJob(jobId)
        stopWatching(jobId)
      }
    })

    activeSubscriptions.set(jobId, unsubscribe)
  }

  function stopWatching(jobId: string) {
    activeSubscriptions.get(jobId)?.()
    activeSubscriptions.delete(jobId)
  }

  async function refreshJob(jobId: string) {
    const job = await getJob(jobId)
    upsertJob(job)
    return job
  }

  function clearSubscriptions() {
    for (const unsubscribe of activeSubscriptions.values()) {
      unsubscribe()
    }
    activeSubscriptions.clear()
  }

  function resetOnLogout() {
    clearSubscriptions()
    jobs.value = []
    loading.value = false
    errorMessage.value = ''
  }

  return {
    jobs,
    loading,
    errorMessage,
    fetchJobs,
    upsertJob,
    watchJob,
    stopWatching,
    refreshJob,
    clearSubscriptions,
    resetOnLogout,
  }
})
