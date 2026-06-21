const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export interface ApiResponse<T> {
  success: boolean
  message: string | null
  data: T
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 400) {
    super(message)
    this.status = status
  }
}

function getToken() {
  return localStorage.getItem('xjicloud_token')
}

export function getAuthToken() {
  return getToken()
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem('xjicloud_token', token)
  } else {
    localStorage.removeItem('xjicloud_token')
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  responseType: 'json' | 'blob' = 'json',
): Promise<T> {
  const headers = new Headers(options.headers ?? {})
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (!(options.body instanceof FormData) && !headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  })

  if (responseType === 'blob') {
    if (!response.ok) {
      const message = await response.text()
      throw new ApiError(message || response.statusText, response.status)
    }
    return (await response.blob()) as T
  }

  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || !payload.success) {
    throw new ApiError(payload.message ?? response.statusText, response.status)
  }

  return payload.data
}

export async function downloadModelBytes(modelId: string, onProgress?: (loaded: number, total: number) => void) {
  const token = getToken()
  const headers: HeadersInit = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${API_BASE}/api/v1/models/${modelId}/download`, { headers })
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
