import { ok, err, type Result } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import type { AuthRepository } from '../../domain/repositories/auth.repository'
import type { SessionStore } from '../../domain/repositories/session-store.port'
import { assertCaptchaForMode } from '../../domain/services/captcha-policy.service'
import type { UserSession } from '../../domain/entities/user-session.entity'

export async function loginUseCase(
  deps: { auth: AuthRepository; session: SessionStore },
  input: { username: string; password: string; captchaKey?: string; captchaCode?: string; captchaRequired: boolean },
): Promise<Result<UserSession>> {
  const username = input.username.trim()
  if (!username || !input.password) {
    return err(new DomainError('AUTH_CREDENTIALS_REQUIRED'))
  }
  const captchaError = assertCaptchaForMode('login', input.captchaRequired, input.captchaKey, input.captchaCode)
  if (captchaError) {
    return err(captchaError)
  }
  const [error, session] = await deps.auth.login({
    username,
    password: input.password,
    captchaKey: input.captchaRequired ? input.captchaKey : undefined,
    captchaCode: input.captchaRequired ? input.captchaCode : undefined,
  })
  if (error || !session) {
    return err(error ?? new DomainError('UNKNOWN'))
  }
  deps.session.persist(session)
  return ok(session)
}

export async function registerUseCase(
  deps: { auth: AuthRepository; session: SessionStore },
  input: { username: string; password: string; displayName?: string; captchaKey?: string; captchaCode?: string },
): Promise<Result<{ registered: true }>> {
  const username = input.username.trim()
  if (!username || !input.password) {
    return err(new DomainError('AUTH_CREDENTIALS_REQUIRED'))
  }
  const captchaError = assertCaptchaForMode('register', true, input.captchaKey, input.captchaCode)
  if (captchaError) {
    return err(captchaError)
  }
  const [error] = await deps.auth.register({
    username,
    password: input.password,
    displayName: input.displayName?.trim() || undefined,
    captchaKey: input.captchaKey!,
    captchaCode: input.captchaCode!.trim(),
  })
  if (error) {
    return err(error)
  }
  deps.session.clear()
  return ok({ registered: true })
}

export function logoutUseCase(deps: { session: SessionStore; onWorkspaceReset?: () => void }) {
  deps.session.clear()
  deps.onWorkspaceReset?.()
}

export async function probeNeedCaptchaUseCase(
  deps: { auth: AuthRepository },
  username: string,
): Promise<Result<boolean>> {
  const name = username.trim()
  if (!name) {
    return ok(false)
  }
  return deps.auth.needCaptcha(name)
}

export function resolveAuthNavigation(input: {
  isAuthenticated: boolean
  isPublicLogin: boolean
  requiresAuth: boolean
  fullPath: string
}): { name: 'home' } | { name: 'login'; query?: { redirect: string } } | null {
  if (input.isPublicLogin && input.isAuthenticated) {
    return { name: 'home' }
  }
  if (input.requiresAuth && !input.isAuthenticated) {
    return { name: 'login', query: { redirect: input.fullPath } }
  }
  return null
}
