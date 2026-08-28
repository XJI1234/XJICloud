import { describe, expect, it } from 'vitest'
import { ok } from '@/shared/result'
import type { Project } from '../../domain/entities/project.entity'
import type { ProjectRepository } from '../../domain/repositories/project.repository'
import type { WorkspacePersistence } from '../../domain/repositories/workspace-persistence.port'
import type { RecentProjectEntry } from '../../domain/entities/project.entity'
import { createProjectUseCase, deleteProjectUseCase } from './project.usecase'

function memoryWorkspace(): WorkspacePersistence {
  let active: string | null = null
  let recent: RecentProjectEntry[] = []
  return {
    getActiveProjectId: () => active,
    setActiveProjectId: (id) => {
      active = id
    },
    readRecentEntries: () => recent,
    writeRecentEntries: (entries) => {
      recent = entries
    },
    subscribe: () => () => undefined,
    clear: () => {
      active = null
      recent = []
    },
  }
}

describe('project use cases', () => {
  it('rejects blank names', async () => {
    const [error] = await createProjectUseCase(
      { repo: {} as ProjectRepository, persistence: memoryWorkspace() },
      { name: '  ' },
    )
    expect(error?.code).toBe('PROJECT_NAME_REQUIRED')
  })

  it('sets active project and records recent on create', async () => {
    const created: Project = { id: 'p1', name: 'Alpha', description: '', createdAt: 't' }
    const repo: ProjectRepository = {
      list: async () => ok([]),
      create: async () => ok(created),
      update: async () => ok(created),
      delete: async () => ok(undefined),
    }
    const persistence = memoryWorkspace()
    const [error, project] = await createProjectUseCase({ repo, persistence }, { name: 'Alpha' })
    expect(error).toBeNull()
    expect(project?.id).toBe('p1')
    expect(persistence.getActiveProjectId()).toBe('p1')
    expect(persistence.readRecentEntries()[0]?.id).toBe('p1')
  })

  it('clears active id when deleting the current project', async () => {
    const repo: ProjectRepository = {
      list: async () => ok([]),
      create: async () => ok({ id: 'p1', name: 'A', description: '', createdAt: '' }),
      update: async () => ok({ id: 'p1', name: 'A', description: '', createdAt: '' }),
      delete: async () => ok(undefined),
    }
    const persistence = memoryWorkspace()
    persistence.setActiveProjectId('p1')
    persistence.writeRecentEntries([{ id: 'p1', openedAt: 1 }])
    const [error] = await deleteProjectUseCase({ repo, persistence }, 'p1')
    expect(error).toBeNull()
    expect(persistence.getActiveProjectId()).toBeNull()
    expect(persistence.readRecentEntries()).toEqual([])
  })
})
