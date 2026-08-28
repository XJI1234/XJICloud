import type { AuthRepository } from '@/features/identity/domain/repositories/auth.repository'
import type { SessionStore } from '@/features/identity/domain/repositories/session-store.port'
import type { ProjectRepository } from '@/features/project/domain/repositories/project.repository'
import type { WorkspacePersistence } from '@/features/project/domain/repositories/workspace-persistence.port'
import type { TrainingJobRepository } from '@/features/dataset-training/domain/repositories/training-job.repository'
import type { JobEventPort } from '@/features/dataset-training/domain/repositories/job-event.port'
import type { ObjectStoragePort } from '@/features/dataset-training/domain/repositories/object-storage.port'
import type { ModelAssetRepository } from '@/features/model-asset/domain/repositories/model-asset.repository'
import type { ViewerStoragePort } from '@/features/viewer/domain/repositories/viewer-storage.port'
import type { EditorBridgePort } from '@/features/editor/domain/repositories/editor-bridge.port'
import type { JobWatchHub } from '@/features/dataset-training/application/job-watch-hub'

export type Web2Container = {
  auth: AuthRepository
  session: SessionStore
  projects: ProjectRepository
  workspace: WorkspacePersistence
  jobs: TrainingJobRepository
  jobEvents: JobEventPort
  objectStorage: ObjectStoragePort
  jobWatch: JobWatchHub
  models: ModelAssetRepository
  viewerStorage: ViewerStoragePort
  editorBridge: EditorBridgePort
  resetWorkspace: () => void
}
