import { describe, expect, it, vi } from 'vitest'
import { createFetchJobEventAdapter } from './fetch-job-event.adapter'

describe('SSE close contract', () => {
  it.each([503, 401])('reports HTTP %s without swallowing it', async status => {
    const close = vi.fn(), unauthorized = vi.fn()
    const adapter = createFetchJobEventAdapter({ fetchImpl: async () => new Response('', { status }), tokenProvider: { getToken: () => 't' }, unauthorized: { notifyUnauthorized: unauthorized } })
    adapter.subscribe('j', vi.fn(), close)
    await vi.waitFor(() => expect(close).toHaveBeenCalledOnce())
    expect(close.mock.calls[0]?.[0]?.code).toBe(status === 401 ? 'AUTH_UNAUTHORIZED' : 'SSE_CONNECT_FAILED')
    expect(unauthorized).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
  })
  it.each(['', 'data: {bad}\n\n', 'data: {"jobId":"other","status":"RUNNING","progress":1}\n\n'])('reports EOF or invalid payload and releases the reader: %s', async body => {
    const event = vi.fn(), close = vi.fn()
    const adapter = createFetchJobEventAdapter({ fetchImpl: async () => new Response(body), tokenProvider: { getToken: () => null } })
    adapter.subscribe('j', event, close)
    await vi.waitFor(() => expect(close).toHaveBeenCalledOnce())
    expect(event).not.toHaveBeenCalled()
  })
  it('emits a valid fragmented event and keeps explicit stop quiet', async () => {
    let stream!: ReadableStreamDefaultController<Uint8Array>
    const body = new ReadableStream<Uint8Array>({ start(controller) { stream = controller } })
    const event = vi.fn(), close = vi.fn()
    const adapter = createFetchJobEventAdapter({ fetchImpl: async () => new Response(body), tokenProvider: { getToken: () => 't' } })
    const stop = adapter.subscribe('j', event, close)
    stream.enqueue(new TextEncoder().encode('data: {"jobId":"j","status":"RUN'))
    stream.enqueue(new TextEncoder().encode('NING","progress":20}\n\n'))
    await vi.waitFor(() => expect(event).toHaveBeenCalledOnce())
    stop()
    await vi.waitFor(() => expect(body.locked).toBe(false))
    expect(close).not.toHaveBeenCalled()
  })
})
