import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { Project } from '../../domain/entities/project.entity'
import type { ProjectRepository } from '../../domain/repositories/project.repository'
import { mapProjectFromDto, type ProjectSummaryDto } from '../mappers/project.mapper'

export function createHttpProjectRepository(http: HttpClient): ProjectRepository {
  return {
    async list(): Promise<Result<Project[]>> {
      try {
        const dto = await http.request<ProjectSummaryDto[]>('/api/v1/projects')
        return ok((dto ?? []).map(mapProjectFromDto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async create(name, description): Promise<Result<Project>> {
      try {
        const dto = await http.request<ProjectSummaryDto>('/api/v1/projects', {
          method: 'POST',
          body: JSON.stringify({ name, description }),
        })
        return ok(mapProjectFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async update(id, name, description): Promise<Result<Project>> {
      try {
        const dto = await http.request<ProjectSummaryDto>(`/api/v1/projects/${id}`, {
          method: 'PATCH',
          body: JSON.stringify({ name, description }),
        })
        return ok(mapProjectFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async delete(id): Promise<Result<void>> {
      try {
        await http.request<void>(`/api/v1/projects/${id}`, { method: 'DELETE' })
        return ok(undefined)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
  }
}
