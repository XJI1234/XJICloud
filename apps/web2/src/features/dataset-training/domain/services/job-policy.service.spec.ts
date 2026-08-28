import { describe, expect, it } from 'vitest'
import { canCancelJob, canDeleteJob, isActiveJob, isTerminalJob, jobMutation } from './job-policy.service'
import { extractSseJsonPayloads } from './sse-buffer.service'

describe('job policy', () => {
  it('treats queued and running as active and cancellable', () => {
    expect(isActiveJob('QUEUED')).toBe(true)
    expect(isActiveJob('RUNNING')).toBe(true)
    expect(canCancelJob({ status: 'UPLOADING' })).toBe(true)
    expect(canDeleteJob({ status: 'UPLOADING' })).toBe(false)
    expect(jobMutation({ status: 'COMPLETED' })).toBe('delete')
    expect(isTerminalJob('FAILED')).toBe(true)
    expect(isTerminalJob('RUNNING')).toBe(false)
  })
})

describe('sse buffer', () => {
  it('parses complete events and keeps a partial remainder', () => {
    const { payloads, rest } = extractSseJsonPayloads('data: {"jobId":"j1","status":"RUNNING"}\n\ndata: {"jobId":"j1"')
    expect(payloads).toEqual([{ jobId: 'j1', status: 'RUNNING' }])
    expect(rest).toBe('data: {"jobId":"j1"')
  })
})
