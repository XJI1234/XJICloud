import { createApp, defineComponent, h, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import i18n from '@/app/i18n'
import type { Web2Container } from '@/app/container.types'
import { createJobWatchHub } from '@/features/dataset-training/application/job-watch-hub'
import type { JobProgressEvent, TrainingJob } from '@/features/dataset-training/domain/entities/training-job.entity'
import type { JobEventPort } from '@/features/dataset-training/domain/repositories/job-event.port'
import type { TrainingJobRepository } from '@/features/dataset-training/domain/repositories/training-job.repository'
import { ok, type Result } from '@/shared/result'
import { CONTAINER_KEY } from '@/shared/di'
import DatasetUploadPanel from './DatasetUploadPanel.vue'

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
  await nextTick()
}

function queuedJob(projectId: string): TrainingJob {
  return {
    id: 'job-old-project',
    projectId,
    name: 'dataset',
    status: 'QUEUED',
    progress: 0,
    stage: 'queued',
    message: null,
    downloadUrl: null,
    errorMessage: null,
    createdAt: '2026-10-18T09:00:00Z',
    updatedAt: '2026-10-18T09:00:00Z',
  }
}

async function startUpload() {
  const completion = deferred<Result<TrainingJob>>()
  const projectId = ref('project-old')
  const jobs = {
    createDataset: vi.fn(async () => ok({
      jobId: 'job-old-project',
      manifestUploadUrl: 'https://oss.example/manifest',
      uploads: [
        { archivedName: '0001.jpg', ossKey: 'images/0001.jpg', uploadUrl: 'https://oss.example/image', contentType: 'image/jpeg' },
        { archivedName: 'manifest.json', ossKey: 'manifest.json', uploadUrl: 'https://oss.example/manifest', contentType: 'application/json' },
      ],
    })),
    completeDataset: vi.fn(() => completion.promise),
    listByProject: vi.fn(async () => ok([])),
    getById: vi.fn(async () => ok(queuedJob('project-old'))),
    cancel: vi.fn(async () => ok(queuedJob('project-old'))),
    delete: vi.fn(async () => ok(undefined)),
  } as unknown as TrainingJobRepository
  const objectStorage = { put: vi.fn(async () => undefined) }
  const subscribe: JobEventPort['subscribe'] = vi.fn(
    (_jobId: string, _listener: (event: JobProgressEvent) => void, _onDisconnect?: () => void) => () => undefined,
  )
  const jobWatch = createJobWatchHub({ jobs, events: { subscribe } })
  const container = { jobs, objectStorage, jobWatch } as unknown as Web2Container
  const root = document.createElement('div')
  const app = createApp(defineComponent(() => () =>
    h(DatasetUploadPanel, { projectId: projectId.value }),
  ))
  app.provide(CONTAINER_KEY, container)
  app.use(i18n)
  app.mount(root)

  let mounted = true
  const unmount = () => {
    if (mounted) {
      app.unmount()
      mounted = false
    }
  }

  const input = root.querySelector<HTMLInputElement>('input[type="file"]')
  if (!input) {
    throw new Error('Dataset folder input was not rendered')
  }
  const image = new File(['image'], 'photo.jpg', { type: 'image/jpeg' })
  Object.defineProperty(image, 'webkitRelativePath', { value: 'set/photo.jpg' })
  Object.defineProperty(input, 'files', { configurable: true, value: [image] })
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await nextTick()

  const startButton = [...root.querySelectorAll<HTMLButtonElement>('button')]
    .find((button) => button.textContent?.trim() === String(i18n.global.t('dataset.startUpload')))
  if (!startButton) {
    throw new Error('Dataset upload button was not rendered')
  }
  startButton.click()
  await vi.waitFor(() => expect(jobs.completeDataset).toHaveBeenCalledOnce())

  return { app, root, projectId, jobs, completion, jobWatch, subscribe, unmount }
}

describe('DatasetUploadPanel upload scope', () => {
  it.each(['watch hub clear', 'component unmount', 'project switch'] as const)(
    'does not restore a submitted job after %s while completeDataset is pending',
    async (invalidation) => {
      const harness = await startUpload()

      try {
        if (invalidation === 'watch hub clear') {
          harness.jobWatch.clear()
        } else if (invalidation === 'component unmount') {
          harness.unmount()
        } else {
          harness.projectId.value = 'project-new'
          await nextTick()
          harness.jobWatch.replaceAll([])
        }

        harness.completion.resolve(ok(queuedJob('project-old')))
        await flushPromises()

        expect(harness.jobWatch.jobs()).toEqual([])
        expect(harness.subscribe).not.toHaveBeenCalled()
      } finally {
        harness.unmount()
        harness.root.remove()
      }
    },
  )
})
