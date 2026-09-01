import { describe, expect, it, vi } from 'vitest'
import { ok } from '@/shared/result'
import type { DatasetArchive, DatasetUploadTicket, TrainingJob } from '../../domain/entities/training-job.entity'
import type { ObjectStoragePort } from '../../domain/repositories/object-storage.port'
import type { TrainingJobRepository } from '../../domain/repositories/training-job.repository'
import { submitDatasetUseCase } from './submit-dataset.usecase'
import { createJobWatchHub } from '../job-watch-hub'
import type { JobEventPort } from '../../domain/repositories/job-event.port'

function archive(): DatasetArchive {
  const file = new File(['img'], 'a.jpg', { type: 'image/jpeg' })
  return {
    files: [{ archivedName: '0001.jpg', originalName: 'a.jpg', contentType: 'image/jpeg', sizeBytes: 3, file }],
    manifest: {
      version: 1,
      imageCount: 1,
      files: [{ archivedName: '0001.jpg', originalName: 'a.jpg', contentType: 'image/jpeg', sizeBytes: 3 }],
    },
    manifestBlob: new Blob(['{}'], { type: 'application/json' }),
  }
}

function job(status: TrainingJob['status'] = 'QUEUED'): TrainingJob {
  return {
    id: 'job-1',
    projectId: 'p1',
    name: 'ds',
    status,
    progress: 0,
    stage: null,
    message: null,
    downloadUrl: null,
    errorMessage: null,
    createdAt: '',
    updatedAt: '',
  }
}

describe('submit dataset', () => {
  it('uploads files and manifest then completes', async () => {
    const puts: string[] = []
    const ticket: DatasetUploadTicket = {
      jobId: 'job-1',
      manifestUploadUrl: 'http://oss/manifest',
      uploads: [
        { archivedName: '0001.jpg', ossKey: 'k1', uploadUrl: 'http://oss/1', contentType: 'image/jpeg' },
        { archivedName: 'manifest.json', ossKey: 'km', uploadUrl: 'http://oss/m', contentType: 'application/json' },
      ],
    }
    const jobs: TrainingJobRepository = {
      createDataset: async () => ok(ticket),
      completeDataset: async () => ok(job('QUEUED')),
      listByProject: async () => ok([]),
      getById: async () => ok(job()),
      cancel: async () => ok(job('CANCELLED')),
      delete: async () => ok(undefined),
    }
    const objectStorage: ObjectStoragePort = {
      async put(url) {
        puts.push(url)
      },
    }
    const percents: number[] = []
    const [error, result] = await submitDatasetUseCase(
      { jobs, objectStorage },
      { projectId: 'p1', name: 'ds', archive: archive(), onProgress: ({ percent }) => percents.push(percent) },
    )
    expect(error).toBeNull()
    expect(result?.status).toBe('QUEUED')
    expect(puts).toEqual(['http://oss/1', 'http://oss/m'])
    expect(percents.at(-1)).toBe(100)
  })

  it('fails when a presigned url is missing', async () => {
    const jobs: TrainingJobRepository = {
      createDataset: async () =>
        ok({ jobId: 'job-1', manifestUploadUrl: '', uploads: [] }),
      completeDataset: async () => ok(job()),
      listByProject: async () => ok([]),
      getById: async () => ok(job()),
      cancel: async () => ok(job()),
      delete: async () => ok(undefined),
    }
    const [error] = await submitDatasetUseCase(
      { jobs, objectStorage: { put: async () => undefined } },
      { projectId: 'p1', name: 'ds', archive: archive() },
    )
    expect(error?.code).toBe('DATASET_MISSING_UPLOAD_URL')
  })
})

describe('job watch hub', () => {
  it('unsubscribes when a terminal event arrives and refreshes the job', async () => {
    const unsubscribe = vi.fn()
    let handler: ((event: { jobId: string; status: TrainingJob['status']; progress: number; stage: string; message: string }) => void) | undefined
    const events: JobEventPort = {
      subscribe(_jobId, onEvent) {
        handler = onEvent
        return unsubscribe
      },
    }
    const getById = vi.fn(async () => ok(job('COMPLETED')))
    const hub = createJobWatchHub({
      jobs: {
        createDataset: async () => ok({ jobId: '', manifestUploadUrl: '', uploads: [] }),
        completeDataset: async () => ok(job()),
        listByProject: async () => ok([]),
        getById,
        cancel: async () => ok(job()),
        delete: async () => ok(undefined),
      },
      events,
    })
    hub.replaceAll([job('RUNNING')])
    hub.watch('job-1')
    handler?.({ jobId: 'job-1', status: 'COMPLETED', progress: 100, stage: 'done', message: 'ok' })
    expect(unsubscribe).toHaveBeenCalledOnce()
    await vi.waitFor(() => expect(getById).toHaveBeenCalledWith('job-1'))
  })

  it('notifies subscribers when jobs change', () => {
    const events: JobEventPort = { subscribe: () => () => undefined }
    const hub = createJobWatchHub({
      jobs: {
        createDataset: async () => ok({ jobId: '', manifestUploadUrl: '', uploads: [] }),
        completeDataset: async () => ok(job()),
        listByProject: async () => ok([]),
        getById: async () => ok(job()),
        cancel: async () => ok(job()),
        delete: async () => ok(undefined),
      },
      events,
    })
    const listener = vi.fn()
    hub.subscribe(listener)
    hub.replaceAll([job('RUNNING')])
    expect(listener).toHaveBeenCalledOnce()
  })
})
