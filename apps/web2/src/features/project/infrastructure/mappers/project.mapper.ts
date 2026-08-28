import type { Project } from '../../domain/entities/project.entity'

export type ProjectSummaryDto = {
  id?: string
  name?: string
  description?: string
  createdAt?: string
}

export function mapProjectFromDto(dto: ProjectSummaryDto): Project {
  return {
    id: dto.id ?? '',
    name: dto.name ?? '',
    description: dto.description ?? '',
    createdAt: dto.createdAt ?? '',
  }
}
