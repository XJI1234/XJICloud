import type { Result } from '@/shared/result'
import type { DatasetArchive, DatasetUploadTicket, TrainingJob } from '../entities/training-job.entity'

export interface TrainingJobRepository {
  createDataset(projectId: string, archive: DatasetArchive, name: string): Promise<Result<DatasetUploadTicket>>
  completeDataset(projectId: string, jobId: string): Promise<Result<TrainingJob>>
  listByProject(projectId: string): Promise<Result<TrainingJob[]>>
  getById(jobId: string): Promise<Result<TrainingJob>>
  cancel(jobId: string): Promise<Result<TrainingJob>>
  delete(jobId: string): Promise<Result<void>>
}
