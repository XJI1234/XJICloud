import { describe, expect, it } from 'vitest'
import { ok } from '@/shared/result'
import type { AuthRepository } from '../../domain/repositories/auth.repository'
import type { SessionStore } from '../../domain/repositories/session-store.port'
import type { UserSession } from '../../domain/entities/user-session.entity'
import { loginUseCase, registerUseCase, resolveAuthNavigation } from './auth.usecase'

function session(overrides: Partial<UserSession> = {}): UserSession {
  return {
    accessToken: 'token',
    tokenType: 'Bearer',
    expiresInMs: 1,
    userId: 'u1',
    username: 'alice',
    displayName: 'Alice',
    ...overrides,
  }
}

function memorySession(): SessionStore & { current: UserSession | null } {
  const store: SessionStore & { current: UserSession | null } = {
    current: null,
    read: () => store.current,
    persist: (next) => {
      store.current = next
    },
    clear: () => {
      store.current = null
    },
    getToken: () => store.current?.accessToken ?? null,
  }
  return store
}

describe('auth use cases', () => {
  it('rejects empty credentials', async () => {
    const auth = {} as AuthRepository
    const [error] = await loginUseCase({ auth, session: memorySession() }, {
      username: ' ',
      password: '',
      captchaRequired: false,
    })
    expect(error?.code).toBe('AUTH_CREDENTIALS_REQUIRED')
  })

  it('persists session on login', async () => {
    const auth: AuthRepository = {
      login: async () => ok(session()),
      register: async () => ok(session()),
      getCaptcha: async () => ok({ captchaKey: 'k', captchaImage: 'i' }),
      needCaptcha: async () => ok(false),
    }
    const store = memorySession()
    const [error, data] = await loginUseCase({ auth, session: store }, {
      username: 'alice',
      password: 'secret',
      captchaRequired: false,
    })
    expect(error).toBeNull()
    expect(data?.username).toBe('alice')
    expect(store.getToken()).toBe('token')
  })

  it('clears session after register so the user must log in again', async () => {
    const auth: AuthRepository = {
      login: async () => ok(session()),
      register: async () => ok(session()),
      getCaptcha: async () => ok({ captchaKey: 'k', captchaImage: 'i' }),
      needCaptcha: async () => ok(false),
    }
    const store = memorySession()
    store.persist(session())
    const [error] = await registerUseCase({ auth, session: store }, {
      username: 'alice',
      password: 'secret',
      captchaKey: 'k',
      captchaCode: '1234',
    })
    expect(error).toBeNull()
    expect(store.getToken()).toBeNull()
  })

  it('maps route guards', () => {
    expect(resolveAuthNavigation({
      isAuthenticated: true,
      isPublicLogin: true,
      requiresAuth: false,
      fullPath: '/login',
    })).toEqual({ name: 'home' })
    expect(resolveAuthNavigation({
      isAuthenticated: false,
      isPublicLogin: false,
      requiresAuth: true,
      fullPath: '/app/home',
    })).toEqual({ name: 'login', query: { redirect: '/app/home' } })
  })
})
