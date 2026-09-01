import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { ViewerConfig } from '../../domain/entities/viewer-config.entity'
import type { ViewerStoragePort } from '../../domain/repositories/viewer-storage.port'
import { parseViewerConfig } from '../../domain/services/viewer-config.service'

export async function loadViewerConfigUseCase(
  deps: { storage: ViewerStoragePort },
  modelId: string,
): Promise<Result<ViewerConfig>> {
  const [error, config] = await deps.storage.loadViewerConfig(modelId)
  if (error) {
    return err(error)
  }
  if (!config) {
    return err(new DomainError('VIEWER_CONFIG_INVALID'))
  }
  return ok(parseViewerConfig(config))
}

export async function saveViewerConfigUseCase(
  deps: { storage: ViewerStoragePort },
  modelId: string,
  config: ViewerConfig,
) {
  return deps.storage.saveViewerConfig(modelId, parseViewerConfig(config))
}
