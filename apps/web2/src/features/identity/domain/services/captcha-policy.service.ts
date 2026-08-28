import { DomainError } from '@/shared/domain-error'

export type AuthMode = 'login' | 'register'

export function assertCaptchaForMode(
  mode: AuthMode,
  captchaRequired: boolean,
  captchaKey?: string,
  captchaCode?: string,
): DomainError | null {
  const required = mode === 'register' || captchaRequired
  if (!required) {
    return null
  }
  if (!captchaKey) {
    return new DomainError('AUTH_CAPTCHA_LOAD_FAILED')
  }
  if (!captchaCode?.trim()) {
    return new DomainError('AUTH_CAPTCHA_REQUIRED')
  }
  return null
}

export function shouldShowCaptcha(mode: AuthMode, serverNeedCaptcha: boolean): boolean {
  return mode === 'register' || serverNeedCaptcha
}
