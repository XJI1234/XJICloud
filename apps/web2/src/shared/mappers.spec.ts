import { describe, expect, it } from 'vitest'
import { mapSessionFromDto } from '@/features/identity/infrastructure/mappers/auth.mapper'
import { mapProjectFromDto } from '@/features/project/infrastructure/mappers/project.mapper'
import { mapJobFromDto } from '@/features/dataset-training/infrastructure/mappers/job.mapper'
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
})
