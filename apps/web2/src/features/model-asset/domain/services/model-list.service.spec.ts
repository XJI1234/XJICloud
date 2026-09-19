import { describe, expect, it } from 'vitest'
import type { ModelAsset } from '../entities/model-asset.entity'
import { sortModelsByUpdatedAtDesc } from './model-list.service'

function model(id: string, updatedAt: string): ModelAsset {
  return {
    id,
    projectId: 'p1',
    fileName: `${id}.ply`,
    format: 'PLY',
    sizeBytes: 1,
    version: 1,
    createdAt: updatedAt,
    updatedAt,
  }
}

describe('sortModelsByUpdatedAtDesc', () => {
  it('puts the most recently updated model first', () => {
    const sorted = sortModelsByUpdatedAtDesc([
      model('old', '2026-01-01T00:00:00.000Z'),
      model('new', '2026-09-01T00:00:00.000Z'),
    ])
    expect(sorted.map((item) => item.id)).toEqual(['new', 'old'])
  })
})
