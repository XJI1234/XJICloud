import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  createProjectUseCase,
  deleteProjectUseCase,
  listRecentProjectsUseCase,
  loadProjectsUseCase,
  setActiveProjectUseCase,
  updateProjectUseCase,
} from '../../application/use-cases/project.usecase'

export function useProjectWorkspace() {
  const container = inject(CONTAINER_KEY)!
  const ws = { repo: container.projects, persistence: container.workspace }

  return {
    load: () => loadProjectsUseCase(ws),
    create: (input: { name: string; description?: string }) => createProjectUseCase(ws, input),
    update: (input: { id: string; name?: string; description?: string }) => updateProjectUseCase(ws, input),
    remove: (id: string) => deleteProjectUseCase(ws, id),
    open: (id: string) => setActiveProjectUseCase(ws, id),
    activeProjectId: () => container.workspace.getActiveProjectId(),
    recent: (projects: Parameters<typeof listRecentProjectsUseCase>[0]) =>
      listRecentProjectsUseCase(projects, container.workspace.readRecentEntries()),
  }
}
