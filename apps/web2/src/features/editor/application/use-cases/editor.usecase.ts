import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { DownloadToken, ModelAsset } from '@/features/model-asset/domain/entities/model-asset.entity'
import type { ModelAssetRepository } from '@/features/model-asset/domain/repositories/model-asset.repository'
import { uploadModelUseCase } from '@/features/model-asset/application/use-cases/model-asset.usecase'
import { assertModelFile } from '@/features/model-asset/domain/services/model-format.service'
import type { EditorBridgePort, EditorFrame } from '../../domain/repositories/editor-bridge.port'
import type { EditorLaunchParams } from '../../domain/entities/editor-session.entity'
import { createBlankEditorLaunch } from '../../domain/services/editor-launch.service'

export function prepareLocalEditorLaunch(file: File): Result<{ fileName: string }> {
  const formatError = assertModelFile(file)
  if (formatError) {
    return err(formatError)
  }
  return ok({ fileName: file.name })
}

export function blankEditorLaunch(lang?: string): EditorLaunchParams {
  return createBlankEditorLaunch(lang)
}

export async function openEditorUseCase(
  deps: { models: ModelAssetRepository },
  modelId: string,
): Promise<Result<DownloadToken>> {
  return deps.models.createDownloadToken(modelId)
}

export async function confirmLeaveIfDirtyUseCase(
  deps: { bridge: EditorBridgePort },
  frame: EditorFrame,
): Promise<Result<boolean>> {
  return deps.bridge.isDirty(frame)
}

export async function saveEditorExportUseCase(
  deps: { models: ModelAssetRepository; bridge: EditorBridgePort },
  input: {
    modelId: string
    frame: EditorFrame
    compressed?: boolean
    fileName?: string
    onProgress?: (progress: { phase: 'export' | 'upload'; loaded: number; total: number }) => void
  },
): Promise<Result<ModelAsset>> {
  const [exportError, exported] = await deps.bridge.exportPly(input.frame, {
    compressed: input.compressed,
    fileName: input.fileName,
    onProgress: (loaded) => input.onProgress?.({ phase: 'export', loaded, total: 0 }),
  })
  if (exportError || !exported) {
    return err(exportError ?? new DomainError('EDITOR_EXPORT_FAILED'))
  }
  return deps.models.uploadExport(input.modelId, exported.blob, exported.fileName, (loaded, total) => {
    input.onProgress?.({ phase: 'upload', loaded, total })
  })
}

export async function saveEditorAsNewModelUseCase(
  deps: { models: ModelAssetRepository; bridge: EditorBridgePort },
  input: {
    projectId: string | null
    frame: EditorFrame
    compressed?: boolean
    fileName?: string
    onProgress?: (progress: { phase: 'export' | 'upload'; loaded: number; total: number }) => void
    signal?: AbortSignal
  },
): Promise<Result<ModelAsset>> {
  if (!input.projectId) {
    return err(new DomainError('MODEL_PROJECT_REQUIRED'))
  }
  const [exportError, exported] = await deps.bridge.exportPly(input.frame, {
    compressed: input.compressed,
    fileName: input.fileName,
    onProgress: (loaded) => input.onProgress?.({ phase: 'export', loaded, total: 0 }),
  })
  if (exportError || !exported) {
    return err(exportError ?? new DomainError('EDITOR_EXPORT_FAILED'))
  }
  const file = new File([exported.blob], exported.fileName, { type: 'application/octet-stream' })
  return uploadModelUseCase(
    { models: deps.models },
    {
      projectId: input.projectId,
      file,
      onProgress: (progress) => input.onProgress?.({ phase: 'upload', loaded: progress.loaded, total: progress.total }),
      signal: input.signal,
    },
  )
}

export function editorSrc(deps: { bridge: EditorBridgePort }, params: EditorLaunchParams) {
  return ok(deps.bridge.buildSrc(params))
}

export async function importLocalEditorFileUseCase(
  deps: { bridge: EditorBridgePort },
  frame: EditorFrame,
  file: File,
): Promise<Result<void>> {
  const formatError = assertModelFile(file)
  if (formatError) {
    return err(formatError)
  }
  return deps.bridge.importLocal(frame, file)
}
