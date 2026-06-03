import { apiRequest } from './client'

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
