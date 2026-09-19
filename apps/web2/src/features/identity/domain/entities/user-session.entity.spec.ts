import { describe, expect, it } from 'vitest'
import { isAuthenticated, type UserSession } from './user-session.entity'

function tokenWithExp(expSeconds: number) {
  const payload = btoa(JSON.stringify({ exp: expSeconds }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `header.${payload}.sig`
}

function session(token: string): UserSession {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresInMs: 1,
    userId: 'u1',
    username: 'alice',
    displayName: 'Alice',
  }
}

describe('user session', () => {
  it('treats an expired jwt as logged out', () => {
    const now = 1_800_000_000_000
    expect(isAuthenticated(session(tokenWithExp(1_700_000_000)), now)).toBe(false)
    expect(isAuthenticated(session(tokenWithExp(1_900_000_000)), now)).toBe(true)
    expect(isAuthenticated(null, now)).toBe(false)
  })
})
