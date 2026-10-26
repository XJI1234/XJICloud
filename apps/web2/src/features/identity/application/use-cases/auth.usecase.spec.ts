import { describe, expect, it } from 'vitest'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import type { AuthRepository } from '../../domain/repositories/auth.repository'
import type { SessionStore } from '../../domain/repositories/session-store.port'
import type { UserSession } from '../../domain/entities/user-session.entity'
import { loginUseCase, registerUseCase, resolveAuthNavigation, sendRegistrationSmsUseCase, updateProfileUseCase } from './auth.usecase'
import type { AccountProfile } from '../../domain/entities/account-profile.entity'

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

const profile: AccountProfile = {
  userId: 'u1', username: '13800138000', phone: '13800138000',
  displayName: 'Li', email: 'li@example.com', organization: 'XJI', profileRequired: false,
}

function repository(): AuthRepository {
  return {
    login: async () => ok(session()),
    register: async () => ok(session({ profileRequired: true })),
    getCaptcha: async () => ok({ captchaKey: 'k', captchaImage: 'i' }),
    needCaptcha: async () => ok(false),
    sendRegistrationSms: async () => ok({ challengeId: 'sms-id', expiresInSeconds: 300, retryAfterSeconds: 60 }),
    getProfile: async () => ok(profile),
    updateProfile: async () => ok(profile),
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
    const auth = repository()
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

  it('persists registration session with required profile completion', async () => {
    const auth = repository()
    const store = memorySession()
    store.persist(session())
    const [error] = await registerUseCase({ auth, session: store }, {
      phone: '13800138000',
      password: 'secret123',
      smsChallengeId: 'sms-id',
      smsCode: '123456',
    })
    expect(error).toBeNull()
    expect(store.getToken()).toBe('token')
    expect(store.read()?.profileRequired).toBe(true)
  })

  it('requires both phone and image verification before requesting an SMS', async () => {
    const auth = repository()
    expect((await sendRegistrationSmsUseCase({ auth }, {
      phone: 'wrong', captchaKey: 'k', captchaCode: 'ABCD',
    }))[0]?.code).toBe('AUTH_PHONE_INVALID')
    expect((await sendRegistrationSmsUseCase({ auth }, {
      phone: '13800138000', captchaKey: 'k', captchaCode: '',
    }))[0]?.code).toBe('AUTH_CAPTCHA_REQUIRED')
  })

  it('rejects missing SMS proof and an overlong UTF-8 password', async () => {
    const deps = { auth: repository(), session: memorySession() }
    expect((await registerUseCase(deps, {
      phone: '13800138000', password: 'secret123', smsChallengeId: '', smsCode: '123456',
    }))[0]?.code).toBe('AUTH_SMS_REQUIRED')
    expect((await registerUseCase(deps, {
      phone: '13800138000', password: '中'.repeat(25), smsChallengeId: 'id', smsCode: '123456',
    }))[0]?.code).toBe('AUTH_PASSWORD_INVALID')
    expect(deps.session.getToken()).toBeNull()
  })

  it('does not persist a session when the backend rejects registration', async () => {
    const auth = repository()
    auth.register = async () => err(new DomainError('UNKNOWN', 'Invalid SMS'))
    const store = memorySession()
    const [error] = await registerUseCase({ auth, session: store }, {
      phone: '13800138000', password: 'secret123', smsChallengeId: 'id', smsCode: '123456',
    })
    expect(error).not.toBeNull()
    expect(store.read()).toBeNull()
  })

  it('updates the shared session only after a successful profile save', async () => {
    const auth = repository()
    const store = memorySession()
    store.persist(session({ profileRequired: true }))
    await updateProfileUseCase({ auth, session: store }, { displayName: 'Li', email: 'li@example.com', organization: 'XJI' })
    expect(store.read()?.displayName).toBe('Li')
    expect(store.read()?.profileRequired).toBe(false)
    expect((await updateProfileUseCase({ auth, session: store }, { displayName: ' ', email: 'bad', organization: '' }))[0]?.code)
      .toBe('AUTH_PROFILE_INVALID')
  })

  it('maps route guards', () => {
    expect(resolveAuthNavigation({
      isAuthenticated: true, profileRequired: true, isAccountPage: false,
      isPublicLogin: false, requiresAuth: true, fullPath: '/app/projects',
    })).toEqual({ name: 'account' })
    expect(resolveAuthNavigation({
      isAuthenticated: true, profileRequired: true, isAccountPage: true,
      isPublicLogin: false, requiresAuth: true, fullPath: '/app/account',
    })).toBeNull()
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
