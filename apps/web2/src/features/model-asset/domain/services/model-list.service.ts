import type { ModelAsset } from '../entities/model-asset.entity'

export function sortModelsByUpdatedAtDesc(models: ModelAsset[]): ModelAsset[] {
  return [...models].sort((left, right) => {
    const rightTime = Date.parse(right.updatedAt) || Date.parse(right.createdAt) || 0
    const leftTime = Date.parse(left.updatedAt) || Date.parse(left.createdAt) || 0
    return rightTime - leftTime
  })
}
