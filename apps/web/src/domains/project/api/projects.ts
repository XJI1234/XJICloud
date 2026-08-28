import { apiRequest } from '@/shared/infrastructure/http/client'

export interface ProjectSummary {
  id: string
  name: string
  description: string
  createdAt: string
}

export function listProjects() {
  return apiRequest<ProjectSummary[]>('/api/v1/projects')
}

export function createProject(name: string, description = '') {
  return apiRequest<ProjectSummary>('/api/v1/projects', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  })
}

export function updateProject(projectId: string, name?: string, description?: string) {
  return apiRequest<ProjectSummary>(`/api/v1/projects/${projectId}`, {
    method: 'PATCH',
    body: JSON.stringify({ name, description }),
  })
}

export function deleteProject(projectId: string) {
  return apiRequest<void>(`/api/v1/projects/${projectId}`, {
    method: 'DELETE',
  })
}
