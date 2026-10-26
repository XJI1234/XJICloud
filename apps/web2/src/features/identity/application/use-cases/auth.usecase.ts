import { ok, err, type Result } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import type { AuthRepository } from '../../domain/repositories/auth.repository'
import type { SessionStore } from '../../domain/repositories/session-store.port'
import { assertCaptchaForMode } from '../../domain/services/captcha-policy.service'
import type { UserSession } from '../../domain/entities/user-session.entity'
import type { RegisterCommand, SendSmsCommand } from '../../domain/repositories/auth.repository'
import type { AccountProfile, ProfileCommand, SmsChallenge } from '../../domain/entities/account-profile.entity'
import { isRegistrationPassword, isRegistrationPhone } from '../../domain/services/registration-policy.service'

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
  input: RegisterCommand,
): Promise<Result<UserSession>> {
  if (!isRegistrationPhone(input.phone)) {
    return err(new DomainError('AUTH_PHONE_INVALID'))
  }
  if (!isRegistrationPassword(input.password)) {
    return err(new DomainError('AUTH_PASSWORD_INVALID'))
  }
  if (!input.smsChallengeId || !/^\d{6}$/.test(input.smsCode)) {
    return err(new DomainError('AUTH_SMS_REQUIRED'))
  }
  const [error, session] = await deps.auth.register(input)
  if (error || !session) return err(error ?? new DomainError('UNKNOWN'))
  deps.session.persist(session)
  return ok(session)
}

export async function sendRegistrationSmsUseCase(
  deps: { auth: AuthRepository }, input: SendSmsCommand,
): Promise<Result<SmsChallenge>> {
  if (!isRegistrationPhone(input.phone)) return err(new DomainError('AUTH_PHONE_INVALID'))
  const captchaError = assertCaptchaForMode('register', true, input.captchaKey, input.captchaCode)
  if (captchaError) return err(captchaError)
  return deps.auth.sendRegistrationSms({ ...input, captchaCode: input.captchaCode.trim() })
}

export async function loadProfileUseCase(
  deps: { auth: AuthRepository; session: SessionStore },
): Promise<Result<AccountProfile>> {
  const result = await deps.auth.getProfile()
  if (result[1]) syncProfileSession(deps.session, result[1])
  return result
}

export async function updateProfileUseCase(
  deps: { auth: AuthRepository; session: SessionStore }, input: ProfileCommand,
): Promise<Result<AccountProfile>> {
  const command = {
    displayName: input.displayName.trim(), email: input.email.trim(), organization: input.organization.trim(),
  }
  if (!command.displayName || command.displayName.length > 128
    || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(command.email) || command.email.length > 254
    || !command.organization || command.organization.length > 200) {
    return err(new DomainError('AUTH_PROFILE_INVALID'))
  }
  const result = await deps.auth.updateProfile(command)
  if (result[1]) syncProfileSession(deps.session, result[1])
  return result
}

function syncProfileSession(store: SessionStore, profile: AccountProfile) {
  const session = store.read()
  if (session && session.userId === profile.userId) {
    store.persist({ ...session, displayName: profile.displayName, profileRequired: profile.profileRequired })
  }
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
  profileRequired?: boolean
  isAccountPage?: boolean
}): { name: 'home' | 'account' } | { name: 'login'; query?: { redirect: string } } | null {
  if (input.isAuthenticated && input.profileRequired && !input.isAccountPage) {
    return { name: 'account' }
  }
  if (input.isPublicLogin && input.isAuthenticated) {
    return { name: 'home' }
  }
  if (input.requiresAuth && !input.isAuthenticated) {
    return { name: 'login', query: { redirect: input.fullPath } }
  }
  return null
}
