import { describe, expect, it } from 'vitest'
import { createTransferRateTracker, formatTransferSpeed } from './transfer-rate'

describe('transfer rate', () => {
  it('returns null until enough time has elapsed', () => {
    const tracker = createTransferRateTracker(800)
    expect(tracker.push(0, 0)).toBeNull()
    expect(tracker.push(1000, 50)).toBeNull()
  })

  it('computes bytes per second over the window', () => {
    const tracker = createTransferRateTracker(800)
    tracker.push(0, 0)
    expect(tracker.push(8_000, 400)).toBe(20_000)
  })

  it('resets when loaded goes backwards', () => {
    const tracker = createTransferRateTracker(800)
    tracker.push(10_000, 0)
    expect(tracker.push(1_000, 400)).toBeNull()
    expect(tracker.push(3_000, 800)).toBe(5_000)
  })

  it('formats speeds for display', () => {
    expect(formatTransferSpeed(512)).toBe('512 B/s')
    expect(formatTransferSpeed(12_288)).toBe('12.0 KB/s')
    expect(formatTransferSpeed(2.5 * 1024 * 1024)).toBe('2.5 MB/s')
  })
})
