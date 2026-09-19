import { isJwtExpired } from '@/shared/jwt-expiry'

export type UserSession = {
  accessToken: string
  tokenType: string
  expiresInMs: number
  userId: string
  username: string
  displayName: string
}

export function isAuthenticated(session: UserSession | null, nowMs = Date.now()): boolean {
  if (!session?.accessToken) {
    return false
  }
  return !isJwtExpired(session.accessToken, nowMs)
}
