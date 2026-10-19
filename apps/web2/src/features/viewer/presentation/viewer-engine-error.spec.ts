import { describe, expect, it } from 'vitest'
import { mapViewerEngineError } from './viewer-engine-error'

describe('viewer engine error boundary', () => {
  it('reports storage cleanup failures even when closeScene resolved', () => {
    expect(mapViewerEngineError({ code: 'ResourceLimit', stage: 'StorageCleanup', diagnostic: 'OPFS delete failed' }).code).toBe('VIEWER_CLEANUP_FAILED')
  })
  it.each([
    ['UnsupportedCapability', 'VIEWER_WEBGPU_UNAVAILABLE'],
    ['UnsupportedFormat', 'VIEWER_UNSUPPORTED_FORMAT'],
    ['ResourceLimit', 'VIEWER_RESOURCE_LIMIT'],
    ['OutOfMemory', 'VIEWER_RESOURCE_LIMIT'],
    ['DeviceLost', 'VIEWER_DEVICE_LOST'],
    ['Cancelled', 'VIEWER_CANCELLED'],
    ['Timeout', 'VIEWER_TIMEOUT'],
    ['DecoderFailure', 'VIEWER_LOAD_FAILED'],
  ] as const)('maps %s without leaking diagnostics', (code, expected) => {
    const result = mapViewerEngineError({ code, stage: 'InternalStage', diagnostic: 'http://private.example/secret/path' })
    expect(result.code).toBe(expected)
    expect(result.message).toBe(expected)
  })
})
