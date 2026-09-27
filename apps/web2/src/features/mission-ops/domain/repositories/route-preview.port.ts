import type { Result } from '@/shared/result'
import type { RoutePreview } from '../entities/route-preview.entity'

/** Preview only: importing a route here never stages or uploads it to a drone. */
export type RoutePreviewPort = {
  importKml: (file: File) => Promise<Result<RoutePreview>>
}
