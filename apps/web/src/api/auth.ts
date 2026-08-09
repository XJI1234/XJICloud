import { apiRequest } from './client'

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInMs: number
  userId: string
  username: string
  displayName: string
}

export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
}

export function getCaptcha() {
  return apiRequest<CaptchaResponse>('/api/v1/auth/captcha')
}

export function needCaptcha(username: string) {
  return apiRequest<{ needCaptcha: boolean }>(`/api/v1/auth/need-captcha?username=${encodeURIComponent(username)}`)
}

export function register(username: string, password: string, displayName: string | undefined, captchaKey: string, captchaCode: string) {
  return apiRequest<AuthResponse>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, displayName, captchaKey, captchaCode }),
  })
}

export function login(username: string, password: string, captchaKey?: string, captchaCode?: string) {
  return apiRequest<AuthResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password, captchaKey, captchaCode }),
  })
}
