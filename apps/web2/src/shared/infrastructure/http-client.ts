import { ApiError, type ApiResponse } from '@xjicloud/shared'
import { DomainError } from '../domain-error'

export type TokenProvider = {
  getToken(): string | null
}

export type UnauthorizedHandler = {
  notifyUnauthorized(): void
}

export type HttpClient = {
  request<T>(path: string, options?: RequestInit, responseType?: 'json' | 'blob'): Promise<T>
  downloadBytes(path: string, onProgress?: (loaded: number, total: number) => void): Promise<ArrayBuffer>
}

export function createHttpClient(options: {
  baseUrl?: string
  fetchImpl?: typeof fetch
  tokenProvider: TokenProvider
  unauthorized: UnauthorizedHandler
}): HttpClient {
  const baseUrl = options.baseUrl ?? ''
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)

  async function request<T>(
    path: string,
    init: RequestInit = {},
    responseType: 'json' | 'blob' = 'json',
  ): Promise<T> {
    const headers = new Headers(init.headers ?? {})
    const token = options.tokenProvider.getToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    if (!(init.body instanceof FormData) && !headers.has('Content-Type') && init.body) {
      headers.set('Content-Type', 'application/json')
    }

    const response = await fetchImpl(`${baseUrl}${path}`, { ...init, headers })

    if (response.status === 401 && token) {
      options.unauthorized.notifyUnauthorized()
    }

    if (responseType === 'blob') {
      if (!response.ok) {
        const message = await response.text()
        throw new ApiError(message || response.statusText, response.status)
      }
      return (await response.blob()) as T
    }

    const text = await response.text()
    let payload: ApiResponse<T>
    try {
      payload = (text ? JSON.parse(text) : { success: false, message: null, data: null }) as ApiResponse<T>
    } catch {
      throw new ApiError(response.statusText || 'Invalid response', response.status)
    }
    if (!response.ok || !payload.success) {
      throw new ApiError(payload.message ?? response.statusText, response.status)
    }
    return payload.data
  }

  async function downloadBytes(path: string, onProgress?: (loaded: number, total: number) => void) {
    const headers: HeadersInit = {}
    const token = options.tokenProvider.getToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    const response = await fetchImpl(`${baseUrl}${path}`, { headers })
    if (response.status === 401 && token) {
      options.unauthorized.notifyUnauthorized()
    }
    if (!response.ok) {
      const message = await response.text()
      throw new ApiError(message || response.statusText, response.status)
    }

    const total = Number(response.headers.get('Content-Length') ?? 0)
    if (!response.body) {
      const buffer = await response.arrayBuffer()
      onProgress?.(buffer.byteLength, total || buffer.byteLength)
      return buffer
    }

    const reader = response.body.getReader()
    const chunks: Uint8Array[] = []
    let loaded = 0
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      if (value) {
        chunks.push(value)
        loaded += value.length
        onProgress?.(loaded, total || loaded)
      }
    }

    const merged = new Uint8Array(loaded)
    let offset = 0
    for (const chunk of chunks) {
      merged.set(chunk, offset)
      offset += chunk.length
    }
    return merged.buffer
  }

  return { request, downloadBytes }
}

function isTechnicalNetworkMessage(message: string): boolean {
  return /json|fetch|network|failed to execute|econnrefused|invalid response|empty response|bad gateway|internal server|service unavailable/i.test(
    message,
  )
}

export function mapHttpError(error: unknown): DomainError {
  if (error instanceof DomainError) {
    return error
  }
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return new DomainError('AUTH_UNAUTHORIZED', error.message, { status: error.status })
    }
    const safeMessage = error.message && !isTechnicalNetworkMessage(error.message) ? error.message : undefined
    return new DomainError('NETWORK', safeMessage, { status: error.status })
  }
  if (error instanceof TypeError || error instanceof SyntaxError) {
    return new DomainError('NETWORK')
  }
  if (error instanceof Error && isTechnicalNetworkMessage(error.message)) {
    return new DomainError('NETWORK')
  }
  return new DomainError('NETWORK', error instanceof Error ? error.message : undefined)
}

export function resolveApiBaseUrl(): string {
  try {
    return import.meta.env.VITE_API_BASE_URL ?? ''
  } catch {
    return ''
  }
}
