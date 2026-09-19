export function readJwtExpiryMs(token: string): number | null {
  const parts = token.split('.')
  if (parts.length < 2) {
    return null
  }
  try {
    const normalized = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
    const json = globalThis.atob(padded)
    const payload = JSON.parse(json) as { exp?: unknown }
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

export function isJwtExpired(token: string, nowMs = Date.now()): boolean {
  const expiry = readJwtExpiryMs(token)
  if (expiry == null) {
    return false
  }
  return expiry <= nowMs
}

export function shouldLogoutOnStatus(status: number, token: string | null, nowMs = Date.now()): boolean {
  if (!token) {
    return false
  }
  if (status === 401) {
    return true
  }
  return status === 403 && isJwtExpired(token, nowMs)
}
