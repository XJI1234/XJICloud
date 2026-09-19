import { describe, expect, it } from 'vitest'
import { isJwtExpired, readJwtExpiryMs, shouldLogoutOnStatus } from './jwt-expiry'

function tokenWithExp(expSeconds: number) {
  const payload = btoa(JSON.stringify({ exp: expSeconds }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
  return `header.${payload}.sig`
}

describe('jwt expiry', () => {
  it('reads exp and treats past exp as expired', () => {
    const token = tokenWithExp(1_700_000_000)
    expect(readJwtExpiryMs(token)).toBe(1_700_000_000_000)
    expect(isJwtExpired(token, 1_700_000_000_000)).toBe(true)
    expect(isJwtExpired(token, 1_699_999_999_000)).toBe(false)
  })

  it('logs out on 401, and on 403 only when the token is expired', () => {
    const live = tokenWithExp(Math.floor(Date.now() / 1000) + 3600)
    const dead = tokenWithExp(Math.floor(Date.now() / 1000) - 10)
    expect(shouldLogoutOnStatus(401, live)).toBe(true)
    expect(shouldLogoutOnStatus(403, live)).toBe(false)
    expect(shouldLogoutOnStatus(403, dead)).toBe(true)
    expect(shouldLogoutOnStatus(403, null)).toBe(false)
  })
})
