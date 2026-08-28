import type { Web2Container } from './container.types'
import { createHttpClient, resolveApiBaseUrl } from '@/shared/infrastructure/http-client'
import { createHttpAuthRepository } from '@/features/identity/infrastructure/repositories/http-auth.repository'
import { createLocalStorageSessionStore } from '@/features/identity/infrastructure/repositories/local-storage-session.store'
import { createHttpProjectRepository } from '@/features/project/infrastructure/repositories/http-project.repository'
import { createLocalWorkspacePersistence } from '@/features/project/infrastructure/repositories/local-workspace.persistence'
import { createHttpTrainingJobRepository } from '@/features/dataset-training/infrastructure/repositories/http-training-job.repository'
import { createFetchJobEventAdapter } from '@/features/dataset-training/infrastructure/adapters/fetch-job-event.adapter'
import { createXhrObjectStorage } from '@/features/dataset-training/infrastructure/adapters/xhr-object-storage.adapter'
import { createJobWatchHub } from '@/features/dataset-training/application/job-watch-hub'
import { createHttpModelAssetRepository } from '@/features/model-asset/infrastructure/repositories/http-model-asset.repository'
import { createHttpViewerStorage } from '@/features/viewer/infrastructure/repositories/http-viewer-storage.repository'
import { createPostMessageEditorBridge } from '@/features/editor/infrastructure/post-message-editor.bridge'

export type CreateContainerOptions = {
  storage?: Storage
  fetchImpl?: typeof fetch
  baseUrl?: string
}

export function createWeb2Container(options: CreateContainerOptions = {}): Web2Container {
  const storage = options.storage ?? localStorage
  const session = createLocalStorageSessionStore(storage)
  const workspace = createLocalWorkspacePersistence(storage)
  const workspaceReset = {
    clearJobs: () => undefined as void,
  }
  const unauthorized = {
    notifyUnauthorized() {
      session.clear()
      workspace.clear()
      workspaceReset.clearJobs()
    },
  }

  const http = createHttpClient({
    baseUrl: options.baseUrl ?? resolveApiBaseUrl(),
    fetchImpl: options.fetchImpl,
    tokenProvider: session,
    unauthorized,
  })

  const jobs = createHttpTrainingJobRepository(http)
  const jobEvents = createFetchJobEventAdapter({
    baseUrl: options.baseUrl ?? resolveApiBaseUrl(),
    fetchImpl: options.fetchImpl,
    tokenProvider: session,
  })
  const jobWatch = createJobWatchHub({ jobs, events: jobEvents })
  workspaceReset.clearJobs = () => jobWatch.clear()
  const models = createHttpModelAssetRepository(http)

  const container: Web2Container = {
    auth: createHttpAuthRepository(http),
    session,
    projects: createHttpProjectRepository(http),
    workspace,
    jobs,
    jobEvents,
    objectStorage: createXhrObjectStorage(),
    jobWatch,
    models,
    viewerStorage: createHttpViewerStorage({ http, models }),
    editorBridge: createPostMessageEditorBridge(),
    resetWorkspace() {
      workspace.clear()
      jobWatch.clear()
    },
  }

  return container
}
