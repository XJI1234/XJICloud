import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { Project, RecentProjectEntry } from '../../domain/entities/project.entity'
import type { ProjectRepository } from '../../domain/repositories/project.repository'
import type { WorkspacePersistence } from '../../domain/repositories/workspace-persistence.port'
import { recordRecentAccess, resolveRecentProjects } from '../../domain/services/recent-projects.service'

export type ProjectWorkspace = {
  repo: ProjectRepository
  persistence: WorkspacePersistence
}

export async function loadProjectsUseCase(ws: ProjectWorkspace): Promise<Result<Project[]>> {
  return ws.repo.list()
}

export async function createProjectUseCase(
  ws: ProjectWorkspace,
  input: { name: string; description?: string },
): Promise<Result<Project>> {
  const name = input.name.trim()
  if (!name) {
    return err(new DomainError('PROJECT_NAME_REQUIRED'))
  }
  const [error, project] = await ws.repo.create(name, input.description?.trim() ?? '')
  if (error || !project) {
    return err(error ?? new DomainError('UNKNOWN'))
  }
  setActiveProjectUseCase(ws, project.id)
  return ok(project)
}

export async function updateProjectUseCase(
  ws: ProjectWorkspace,
  input: { id: string; name?: string; description?: string },
): Promise<Result<Project>> {
  if (input.name !== undefined && !input.name.trim()) {
    return err(new DomainError('PROJECT_NAME_REQUIRED'))
  }
  return ws.repo.update(input.id, input.name, input.description)
}

export async function deleteProjectUseCase(ws: ProjectWorkspace, projectId: string): Promise<Result<void>> {
  const [error] = await ws.repo.delete(projectId)
  if (error) {
    return err(error)
  }
  if (ws.persistence.getActiveProjectId() === projectId) {
    ws.persistence.setActiveProjectId(null)
  }
  const nextRecent = ws.persistence.readRecentEntries().filter((entry) => entry.id !== projectId)
  ws.persistence.writeRecentEntries(nextRecent)
  return ok(undefined)
}

export function setActiveProjectUseCase(ws: ProjectWorkspace, projectId: string | null, now = Date.now()) {
  ws.persistence.setActiveProjectId(projectId)
  if (projectId) {
    const next = recordRecentAccess(ws.persistence.readRecentEntries(), projectId, now)
    ws.persistence.writeRecentEntries(next)
  }
}

export function listRecentProjectsUseCase(projects: Project[], entries: RecentProjectEntry[]) {
  return resolveRecentProjects(projects, entries)
}

export function resetProjectWorkspaceUseCase(persistence: WorkspacePersistence) {
  persistence.clear()
}
