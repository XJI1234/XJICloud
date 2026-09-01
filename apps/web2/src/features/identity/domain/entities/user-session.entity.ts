export type UserSession = {
  accessToken: string
  tokenType: string
  expiresInMs: number
  userId: string
  username: string
  displayName: string
}

export function isAuthenticated(session: UserSession | null): boolean {
  return Boolean(session?.accessToken)
}
