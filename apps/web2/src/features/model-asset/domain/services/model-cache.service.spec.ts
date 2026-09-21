import { describe, expect, it } from 'vitest'
import { isCacheRevisionHit, liveModelCacheKey, historyModelCacheKey } from './model-cache.service'

describe('model cache keys', () => {
  it('hits when revision matches or is omitted', () => {
    const entry = { updatedAt: 't1', sizeBytes: 12 }
    expect(isCacheRevisionHit(entry, { updatedAt: 't1', sizeBytes: 12 })).toBe(true)
    expect(isCacheRevisionHit(entry, undefined)).toBe(true)
    expect(isCacheRevisionHit(entry, { updatedAt: 't2', sizeBytes: 12 })).toBe(false)
    expect(isCacheRevisionHit(null, { updatedAt: 't1', sizeBytes: 12 })).toBe(false)
  })

  it('separates live and history keys', () => {
    expect(liveModelCacheKey('m1')).toBe('live:m1')
    expect(historyModelCacheKey('m1', 'v9')).toBe('hist:m1:v9')
  })
})
