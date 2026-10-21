import { describe, expect, it } from 'vitest'
import { mapSessionFromDto } from '@/features/identity/infrastructure/mappers/auth.mapper'
import { mapProjectFromDto } from '@/features/project/infrastructure/mappers/project.mapper'
import {
  mapJobFromDto,
  mapProgressEventFromDto,
} from '@/features/dataset-training/infrastructure/mappers/job.mapper'
import { mapModelFromDto } from '@/features/model-asset/infrastructure/mappers/model-asset.mapper'

describe('acl mappers isolate nullish dto fields', () => {
  it('maps auth and project defaults', () => {
    expect(mapSessionFromDto({}).accessToken).toBe('')
    expect(mapProjectFromDto({}).name).toBe('')
  })

  it('maps job and model defaults', () => {
    expect(mapJobFromDto({}).status).toBe('PENDING')
    expect(mapModelFromDto({ fileName: 'a.unknown' }).format).toBe('PLY')
  })

  it('maps cloud lifecycle snapshots on REST jobs and progress events', () => {
    const dto = {
      id: 'job-1',
      jobId: 'job-1',
      status: 'CANCELLED',
      computeStatus: 'FAILED',
      cloudStatus: 'TERMINATING',
      computeStatusReason: 'Worker exited',
      cancellationPending: true,
      cleanupPending: true,
      updatedAt: '2026-10-18T10:00:00Z',
    }
    const lifecycleFields = {
      status: 'CANCELLED',
      computeStatus: 'FAILED',
      cloudStatus: 'TERMINATING',
      computeStatusReason: 'Worker exited',
      cancellationPending: true,
      cleanupPending: true,
      updatedAt: '2026-10-18T10:00:00Z',
    }

    for (const mapped of [mapJobFromDto(dto), mapProgressEventFromDto(dto)]) {
      expect(mapped).toMatchObject(lifecycleFields)
    }
  })

  it('defaults missing lifecycle booleans from older API responses to false', () => {
    const dto = { status: 'CANCELLED' }

    for (const mapped of [mapJobFromDto(dto), mapProgressEventFromDto(dto)]) {
      expect(mapped).toMatchObject({
        cancellationPending: false,
        cleanupPending: false,
      })
    }
  })
})
