import { describe, expect, it } from 'vitest'
import { offlineBridgeHealth } from '../entities/bridge-status.entity'
import { classifyBridgeReadiness, shouldAttemptWake, shouldPollSnapshot } from './bridge-readiness.service'

describe('bridge-readiness.service', () => {
  it('classifies online health as ready', () => {
    expect(
      classifyBridgeReadiness(
        { online: true, version: '1', relayListening: true, checkedAtMs: 1 },
        false,
      ),
    ).toBe('ready')
  })

  it('classifies offline + waking', () => {
    const health = offlineBridgeHealth(1)
    expect(classifyBridgeReadiness(health, true)).toBe('waking')
    expect(classifyBridgeReadiness(health, false)).toBe('offline')
    expect(shouldAttemptWake(health)).toBe(true)
  })

  it('skips a background poll while a command or another poll is in flight', () => {
    expect(shouldPollSnapshot({ busy: false, pollInFlight: false })).toBe(true)
    expect(shouldPollSnapshot({ busy: true, pollInFlight: false })).toBe(false)
    expect(shouldPollSnapshot({ busy: false, pollInFlight: true })).toBe(false)
  })
})
