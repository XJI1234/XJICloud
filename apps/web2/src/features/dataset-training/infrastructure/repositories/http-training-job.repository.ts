import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { DatasetArchive, DatasetUploadTicket, TrainingJob } from '../../domain/entities/training-job.entity'
import type { TrainingJobRepository } from '../../domain/repositories/training-job.repository'
import {
  mapJobFromDto,
  mapTicketFromDto,
  type CreateDatasetResponseDto,
  type JobResponseDto,
} from '../mappers/job.mapper'

export function createHttpTrainingJobRepository(http: HttpClient): TrainingJobRepository {
  return {
    async createDataset(projectId, archive, name): Promise<Result<DatasetUploadTicket>> {
      try {
        const dto = await http.request<CreateDatasetResponseDto>(`/api/v1/projects/${projectId}/datasets`, {
          method: 'POST',
          body: JSON.stringify({
            name,
            files: archive.manifest.files,
          }),
        })
        return ok(mapTicketFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async completeDataset(projectId, jobId): Promise<Result<TrainingJob>> {
      try {
        const dto = await http.request<JobResponseDto>(`/api/v1/projects/${projectId}/datasets/${jobId}/complete`, {
          method: 'POST',
        })
        return ok(mapJobFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async listByProject(projectId): Promise<Result<TrainingJob[]>> {
      try {
        const dto = await http.request<JobResponseDto[]>(`/api/v1/projects/${projectId}/jobs`)
        return ok((dto ?? []).map(mapJobFromDto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async getById(jobId): Promise<Result<TrainingJob>> {
      try {
        const dto = await http.request<JobResponseDto>(`/api/v1/jobs/${jobId}`)
        return ok(mapJobFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async cancel(jobId): Promise<Result<TrainingJob>> {
      try {
        const dto = await http.request<JobResponseDto>(`/api/v1/jobs/${jobId}/cancel`, { method: 'POST' })
        return ok(mapJobFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
    async delete(jobId): Promise<Result<void>> {
      try {
        await http.request<void>(`/api/v1/jobs/${jobId}`, { method: 'DELETE' })
        return ok(undefined)
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
  }
}
