import { afterEach, describe, expect, it, vi } from 'vitest'
import { uploadModelUseCase } from './model-asset.usecase'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import type { ModelAssetRepository } from '../../domain/repositories/model-asset.repository'

afterEach(() => vi.useRealTimers())
describe('upload cancellation boundaries', () => {
  function fixture() {
    const session = { sessionId: 's', chunkSizeBytes: 4, receivedBytes: 0, sizeBytes: 4 }
    const models = {
      createUploadSession: vi.fn(async () => ok(session)),
      putChunk: vi.fn(async () => ok({ receivedBytes: 4 })),
      getUploadSession: vi.fn(async () => ok(session)),
      completeUpload: vi.fn(async () => err(new DomainError('NETWORK'))),
      abortUpload: vi.fn(async () => ok(undefined)),
    }
    const owner = new AbortController()
    const run = () => uploadModelUseCase({ models: models as unknown as ModelAssetRepository }, { projectId: 'p', file: new File(['abcd'], 'a.ply'), signal: owner.signal })
    return { models, owner, run }
  }
  it('does not create a session when already cancelled', async () => {
    const { models, owner, run } = fixture(); owner.abort()
    expect((await run())[0]?.code).toBe('OPERATION_CANCELLED')
    expect(models.createUploadSession).not.toHaveBeenCalled()
  })
  it('maps cancellation while creating a session to a cancellation Result', async () => {
    const { models, owner, run } = fixture()
    models.createUploadSession.mockImplementation(async () => { owner.abort(); return err(new DomainError('NETWORK')) })
    expect((await run())[0]?.code).toBe('OPERATION_CANCELLED')
    expect(models.putChunk).not.toHaveBeenCalled()
  })
  it('does not complete after the last chunk if cancelled in flight', async () => {
    const { models, owner, run } = fixture()
    models.putChunk.mockImplementation(async () => { owner.abort(); return ok({ receivedBytes: 4 }) })
    expect((await run())[0]?.code).toBe('OPERATION_CANCELLED')
    expect(models.abortUpload).toHaveBeenCalledOnce()
    expect(models.completeUpload).not.toHaveBeenCalled()
  })
  it('interrupts completion retry backoff and never sends another complete', async () => {
    vi.useFakeTimers()
    const { models, owner, run } = fixture()
    const pending = run()
    await vi.advanceTimersByTimeAsync(0)
    expect(models.completeUpload).toHaveBeenCalledOnce()
    owner.abort()
    expect((await pending)[0]?.code).toBe('OPERATION_CANCELLED')
    expect(models.completeUpload).toHaveBeenCalledOnce()
    expect(models.abortUpload).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('does not retry unauthorized/business failures', async () => {
    const { models, run } = fixture()
    models.completeUpload.mockResolvedValue(err(new DomainError('AUTH_UNAUTHORIZED')))
    expect((await run())[0]?.code).toBe('AUTH_UNAUTHORIZED')
    expect(models.completeUpload).toHaveBeenCalledOnce()
  })
})
