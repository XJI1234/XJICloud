import { createApp, defineComponent, h, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import i18n from '@/app/i18n'
import type { Web2Container } from '@/app/container.types'
import { createJobWatchHub } from '@/features/dataset-training/application/job-watch-hub'
import type { TrainingJob } from '@/features/dataset-training/domain/entities/training-job.entity'
import type { TrainingJobRepository } from '@/features/dataset-training/domain/repositories/training-job.repository'
import { ok, type Result } from '@/shared/result'
import { CONTAINER_KEY } from '@/shared/di'
import TrainingJobPanel from './TrainingJobPanel.vue'

function job(id: string, projectId: string): TrainingJob {
  return {
    id,
    projectId,
    name: id,
    status: 'COMPLETED',
    progress: 100,
    stage: 'done',
    message: null,
    downloadUrl: null,
    errorMessage: null,
    createdAt: '2026-10-18T09:00:00Z',
    computeStatus: 'SUCCEEDED',
    cloudStatus: 'TERMINATED',
    computeStatusReason: null,
    cancellationPending: false,
    cleanupPending: false,
    updatedAt: '2026-10-18T09:00:00Z',
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
  await nextTick()
}

describe('TrainingJobPanel project loading', () => {
  it('does not replace the new project jobs with a late response from the old project', async () => {
    const oldProjectResponse = deferred<Result<TrainingJob[]>>()
    const newProjectResponse = deferred<Result<TrainingJob[]>>()
    const listByProject = vi.fn((projectId: string) =>
      projectId === 'project-old' ? oldProjectResponse.promise : newProjectResponse.promise,
    )
    const jobs = {
      createDataset: async () => ok({ jobId: 'job-1', manifestUploadUrl: '', uploads: [] }),
      completeDataset: async () => ok(job('job-1', 'project-old')),
      listByProject,
      getById: async () => ok(job('job-1', 'project-old')),
      cancel: async () => ok(job('job-1', 'project-old')),
      delete: async () => ok(undefined),
    } as unknown as TrainingJobRepository
    const jobWatch = createJobWatchHub({
      jobs,
      events: { subscribe: () => () => undefined },
    })
    const container = {
      jobs,
      objectStorage: { put: async () => undefined },
      jobWatch,
    } as unknown as Web2Container
    const projectId = ref<string | null>('project-old')
    const root = document.createElement('div')
    const app = createApp(defineComponent(() => () =>
      h(TrainingJobPanel, { projectId: projectId.value }),
    ))
    app.provide(CONTAINER_KEY, container)
    app.use(i18n)
    app.mount(root)

    try {
      await flushPromises()
      projectId.value = 'project-new'
      await nextTick()
      await flushPromises()
      newProjectResponse.resolve(ok([job('new-job', 'project-new')]))
      await flushPromises()
      oldProjectResponse.resolve(ok([job('old-job', 'project-old')]))
      await flushPromises()

      expect(listByProject).toHaveBeenNthCalledWith(1, 'project-old')
      expect(listByProject).toHaveBeenNthCalledWith(2, 'project-new')
      expect(jobWatch.jobs().map(({ id }) => id)).toEqual(['new-job'])
    } finally {
      app.unmount()
      root.remove()
    }
  })
})

function lifecycleJob(
  status: TrainingJob['status'],
  lifecycle: Pick<TrainingJob, 'cancellationPending' | 'cleanupPending'>,
): TrainingJob {
  return {
    ...job('job-1', 'project-1'),
    status,
    progress: status === 'RUNNING' ? 35 : 0,
    stage: status === 'RUNNING' ? 'training' : 'queued',
    ...lifecycle,
  }
}

async function mountPanelWithJob(trainingJob: TrainingJob) {
  const jobs = {
    createDataset: async () => ok({ jobId: 'job-1', manifestUploadUrl: '', uploads: [] }),
    completeDataset: async () => ok(trainingJob),
    listByProject: async () => ok([trainingJob]),
    getById: async () => ok(trainingJob),
    cancel: vi.fn(async () => ok({
      ...trainingJob,
      status: 'CANCELLED' as const,
      cancellationPending: true,
      cleanupPending: true,
    })),
    delete: vi.fn(async () => ok(undefined)),
  } as unknown as TrainingJobRepository
  const jobWatch = createJobWatchHub({
    jobs,
    events: { subscribe: () => () => undefined },
  })
  const container = {
    jobs,
    objectStorage: { put: async () => undefined },
    jobWatch,
  } as unknown as Web2Container
  const root = document.createElement('div')
  const app = createApp(defineComponent(() => () =>
    h(TrainingJobPanel, { projectId: 'project-1' }),
  ))
  app.provide(CONTAINER_KEY, container)
  app.use(i18n)
  app.mount(root)
  await flushPromises()

  return { app, root, jobs }
}

function jobActionButton(root: HTMLElement): HTMLButtonElement {
  const button = root.querySelector('.training-job-item__header-actions button')
  if (!(button instanceof HTMLButtonElement)) {
    throw new Error('Training job action button was not rendered')
  }
  return button
}

describe('TrainingJobPanel cleanup presentation', () => {
  it.each(['QUEUED', 'RUNNING'] as const)(
    'lets the user cancel an active %s job with cleanupPending resources',
    async (status) => {
      const item = lifecycleJob(status, { cancellationPending: false, cleanupPending: true })
      const { app, root, jobs } = await mountPanelWithJob(item)
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)

      try {
        const button = jobActionButton(root)
        expect(button.disabled).toBe(false)
        expect(button.textContent?.trim()).toBe(String(i18n.global.t('training.cancelRecord')))
        expect(root.textContent).not.toContain(String(i18n.global.t('training.cleanupPending')))

        button.click()
        await vi.waitFor(() => expect(jobs.cancel).toHaveBeenCalledWith('job-1'))
      } finally {
        confirm.mockRestore()
        app.unmount()
        root.remove()
      }
    },
  )

  it.each([
    {
      name: 'accepted cancellation',
      status: 'CANCELLED' as const,
      lifecycle: { cancellationPending: true, cleanupPending: true },
      visibleMessage: 'training.cancellationPending',
    },
    {
      name: 'terminal cleanup',
      status: 'FAILED' as const,
      lifecycle: { cancellationPending: false, cleanupPending: true },
      visibleMessage: 'training.cleanupPending',
    },
  ])('disables mutation and shows the $name state', async ({ status, lifecycle, visibleMessage }) => {
    const { app, root } = await mountPanelWithJob(lifecycleJob(status, lifecycle))

    try {
      expect(jobActionButton(root).disabled).toBe(true)
      expect(root.textContent).toContain(String(i18n.global.t(visibleMessage)))
    } finally {
      app.unmount()
      root.remove()
    }
  })
})
