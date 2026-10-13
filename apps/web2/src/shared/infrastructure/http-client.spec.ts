import { describe, expect, it } from 'vitest'
import { ApiError } from '@xjicloud/shared'
import { createHttpClient, mapHttpError } from './http-client'

describe('http client', () => {
  it('attaches bearer token and unwraps ApiResponse', async () => {
    const fetchImpl: typeof fetch = async (input, init) => {
      const headers = new Headers(init?.headers)
      expect(headers.get('Authorization')).toBe('Bearer abc')
      expect(String(input)).toBe('/api/v1/ping')
      return new Response(JSON.stringify({ success: true, message: null, data: { ok: true } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    const http = createHttpClient({
      fetchImpl,
      tokenProvider: { getToken: () => 'abc' },
      unauthorized: { notifyUnauthorized: () => undefined },
    })
    await expect(http.request<{ ok: boolean }>('/api/v1/ping')).resolves.toEqual({ ok: true })
  })

  it('notifies unauthorized only when a token was present', async () => {
    let notified = 0
    const fetchImpl: typeof fetch = async () =>
      new Response(JSON.stringify({ success: false, message: 'expired', data: null }), { status: 401 })
    const http = createHttpClient({
      fetchImpl,
      tokenProvider: { getToken: () => 'abc' },
      unauthorized: { notifyUnauthorized: () => {
        notified += 1
      } },
    })
    await expect(http.request('/api/v1/secure')).rejects.toBeInstanceOf(ApiError)
    expect(notified).toBe(1)
    expect(mapHttpError(new ApiError('expired', 401)).code).toBe('AUTH_UNAUTHORIZED')
    expect(mapHttpError(new ApiError('too large', 413)).code).toBe('MODEL_TOO_LARGE')
    expect(
      mapHttpError(
        new ApiError(
          'JDBC exception executing SQL [select json_payload from viewer_configs]',
          500,
        ),
      ).code,
    ).toBe('UNKNOWN')
  })

  it('cancels a download while preserving its bearer authorization', async () => {
    const controller = new AbortController()
    let authorization = ''
    let requestSignal: AbortSignal | null | undefined
    const fetchImpl: typeof fetch = async (_input, init) => {
      authorization = new Headers(init?.headers).get('Authorization') ?? ''
      requestSignal = init?.signal
      return new Response(new ReadableStream({
        start(stream) { stream.enqueue(new Uint8Array([1, 2])); stream.enqueue(new Uint8Array([3, 4])); stream.close() },
      }))
    }
    const http = createHttpClient({ fetchImpl, tokenProvider: { getToken: () => 'abc' }, unauthorized: { notifyUnauthorized: () => undefined } })
    await expect(http.downloadBytes('/download', () => controller.abort(), controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    expect(authorization).toBe('Bearer abc')
    expect(requestSignal).toBe(controller.signal)
  })

  it('does not start a request when its download signal was already aborted', async () => {
    const controller = new AbortController()
    controller.abort()
    let calls = 0
    const http = createHttpClient({ fetchImpl: async () => { calls++; return new Response('bytes') }, tokenProvider: { getToken: () => null }, unauthorized: { notifyUnauthorized: () => undefined } })
    await expect(http.downloadBytes('/download', undefined, controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    expect(calls).toBe(0)
  })

  it('still downloads bytes for old callers without a signal', async () => {
    const http = createHttpClient({ fetchImpl: async () => new Response(new Uint8Array([1, 2, 3])), tokenProvider: { getToken: () => null }, unauthorized: { notifyUnauthorized: () => undefined } })
    expect(new Uint8Array(await http.downloadBytes('/download'))).toEqual(new Uint8Array([1, 2, 3]))
  })

  it('maps empty or invalid JSON to a network error without leaking parse text', async () => {
    const http = createHttpClient({
      fetchImpl: async () => new Response('', { status: 502, statusText: 'Bad Gateway' }),
      tokenProvider: { getToken: () => 'abc' },
      unauthorized: { notifyUnauthorized: () => undefined },
    })
    await expect(http.request('/api/v1/projects')).rejects.toBeInstanceOf(ApiError)
    const mapped = mapHttpError(new SyntaxError("Failed to execute 'json' on 'Response'"))
    expect(mapped.code).toBe('NETWORK')
    expect(mapped.message).toBe('NETWORK')
  })
})
