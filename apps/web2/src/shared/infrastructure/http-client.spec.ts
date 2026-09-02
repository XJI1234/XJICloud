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
