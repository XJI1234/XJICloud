import { apiRequest } from './client'

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInMs: number
  userId: string
  username: string
  displayName: string
}

export function register(username: string, password: string, displayName?: string) {
  return apiRequest<AuthResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, displayName }),
  })
}

export function login(username: string, password: string) {
  return apiRequest<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
