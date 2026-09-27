import type { Result } from '@/shared/result'
import type { RoutePreview } from '@/features/mission-ops/domain/entities/route-preview.entity'
import type { RoutePreviewPort } from '@/features/mission-ops/domain/repositories/route-preview.port'

export function previewRouteUseCase(port: RoutePreviewPort, file: File): Promise<Result<RoutePreview>> {
  return port.importKml(file)
}
