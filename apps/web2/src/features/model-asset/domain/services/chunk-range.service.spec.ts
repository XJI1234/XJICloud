import { describe, expect, it } from 'vitest'
import { nextChunkRange } from './chunk-range.service'

describe('nextChunkRange', () => {
  it('splits a file into sequential ranges', () => {
    expect(nextChunkRange(0, 20, 8)).toEqual({ start: 0, endInclusive: 7, endExclusive: 8 })
    expect(nextChunkRange(8, 20, 8)).toEqual({ start: 8, endInclusive: 15, endExclusive: 16 })
    expect(nextChunkRange(16, 20, 8)).toEqual({ start: 16, endInclusive: 19, endExclusive: 20 })
    expect(nextChunkRange(20, 20, 8)).toBeNull()
  })
})
