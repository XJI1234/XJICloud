import { afterEach, describe, expect, it, vi } from 'vitest'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import { createJobWatchHub } from '@/features/dataset-training/application/job-watch-hub'
import { createLocalStorageSessionStore } from '@/features/identity/infrastructure/repositories/local-storage-session.store'
import { createLocalWorkspacePersistence } from '@/features/project/infrastructure/repositories/local-workspace.persistence'
import { logoutUseCase } from '@/features/identity/application/use-cases/auth.usecase'
import { createHttpModelAssetRepository } from '@/features/model-asset/infrastructure/repositories/http-model-asset.repository'
import { buildDatasetArchive } from '@/features/dataset-training/domain/services/dataset-archive.service'
import type { TrainingJobRepository } from '@/features/dataset-training/domain/repositories/training-job.repository'
import type { JobProgressEvent, TrainingJob } from '@/features/dataset-training/domain/entities/training-job.entity'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import type { CloudModelCachePort } from '@/features/model-asset/domain/repositories/cloud-model-cache.port'
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve() }
afterEach(() => { localStorage.clear(); vi.restoreAllMocks() })
describe('review regression contracts', () => {
  it('terminal job refresh cannot repopulate hub after clear', async () => {
    let onEvent!: (event: JobProgressEvent) => void
    let finish!: (value: ReturnType<typeof ok<TrainingJob>>) => void
    const pending = new Promise<ReturnType<typeof ok<TrainingJob>>>(resolve => { finish = resolve })
    const jobs = { getById: () => pending } as unknown as TrainingJobRepository
    const hub = createJobWatchHub({ jobs, events: { subscribe: (_id, cb) => { onEvent = cb; return () => undefined } } })
    const job: TrainingJob = { id: 'old', projectId: 'p1', name: 'old', status: 'RUNNING', progress: 1, stage: null, message: null, downloadUrl: null, errorMessage: null, createdAt: '', updatedAt: '' }
    hub.upsert(job); hub.watch(job.id)
    onEvent({ jobId: job.id, status: 'COMPLETED', progress: 100, stage: '', message: '' })
    hub.clear(); finish(ok({ ...job, status: 'COMPLETED' })); await flush()
    expect(hub.jobs().map(item => item.id)).toEqual([])
  })
  it('logout resets workspace while its account is known', () => {
    const session = createLocalStorageSessionStore(localStorage)
    session.persist({ accessToken: 't', tokenType: 'Bearer', expiresInMs: 0, userId: 'u1', username: 'u1', displayName: '' })
    const workspace = createLocalWorkspacePersistence(localStorage, () => session.read()?.userId ?? null)
    workspace.setActiveProjectId('old-project')
    logoutUseCase({ session, onWorkspaceReset: () => workspace.setActiveProjectId(null) })
    expect(localStorage.getItem('xjicloud_active_project_id:u1')).toBeNull()
  })
  it('cache read failure falls back to the network', async () => {
    const downloadBytes = vi.fn(async () => new ArrayBuffer(1))
    const cache = { list: async () => { throw new DOMException('storage denied', 'SecurityError') } } as unknown as CloudModelCachePort
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [error] = await repo.downloadBytes('m1')
    expect(error).toBeNull(); expect(downloadBytes).toHaveBeenCalledOnce()
  })
  it('successful server deletion survives cache prune failure', async () => {
    const request = vi.fn(async () => undefined)
    const cache = { list: async () => { throw new Error('cache unavailable') } } as unknown as CloudModelCachePort
    const repo = createHttpModelAssetRepository({ request } as unknown as HttpClient, cache)
    const [error] = await repo.delete('m1')
    expect(request).toHaveBeenCalledOnce(); expect(error).toBeNull()
  })
  it('archive rejects GIF outside the documented contract', () => {
    const [error, archive] = buildDatasetArchive([new File(['gif'], 'a.gif', { type: 'image/gif' })])
    expect(error?.code).toBe('DATASET_NO_IMAGES'); expect(archive).toBeNull()
  })
})
