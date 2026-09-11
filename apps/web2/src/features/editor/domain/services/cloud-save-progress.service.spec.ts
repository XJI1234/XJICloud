import { describe, expect, it } from 'vitest'
import { mapCloudSaveBar } from './cloud-save-progress.service'

describe('mapCloudSaveBar', () => {
  it('keeps export in the first 40 percent while bytes are unknown or growing', () => {
    expect(mapCloudSaveBar('export', 0, 0)).toBe(5)
    expect(mapCloudSaveBar('export', 1, 0)).toBeGreaterThanOrEqual(8)
    expect(mapCloudSaveBar('export', 64 * 1024 * 1024, 0)).toBeLessThanOrEqual(40)
  })

  it('maps upload onto the remaining bar', () => {
    expect(mapCloudSaveBar('upload', 0, 100)).toBe(40)
    expect(mapCloudSaveBar('upload', 50, 100)).toBe(70)
    expect(mapCloudSaveBar('upload', 100, 100)).toBe(99)
  })
})
