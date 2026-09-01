import type { Result } from '@/shared/result'
import type { Project } from '../entities/project.entity'

export interface ProjectRepository {
  list(): Promise<Result<Project[]>>
  create(name: string, description: string): Promise<Result<Project>>
  update(id: string, name?: string, description?: string): Promise<Result<Project>>
  delete(id: string): Promise<Result<void>>
}
