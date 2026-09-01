import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import { buildDatasetArchive } from '../../domain/services/dataset-archive.service'
import { cancelOrDeleteJobUseCase, submitDatasetUseCase } from '../../application/use-cases/submit-dataset.usecase'
import { isActiveJob } from '../../domain/services/job-policy.service'

export function useDatasetTraining() {
  const container = inject(CONTAINER_KEY)!

  return {
    archiveFolder: buildDatasetArchive,
    submit: (input: Parameters<typeof submitDatasetUseCase>[1]) =>
      submitDatasetUseCase({ jobs: container.jobs, objectStorage: container.objectStorage }, input),
    list: (projectId: string) => container.jobs.listByProject(projectId),
    cancelOrDelete: (job: Parameters<typeof cancelOrDeleteJobUseCase>[1]) =>
      cancelOrDeleteJobUseCase({ jobs: container.jobs }, job),
    watchHub: container.jobWatch,
    isActiveJob,
  }
}
