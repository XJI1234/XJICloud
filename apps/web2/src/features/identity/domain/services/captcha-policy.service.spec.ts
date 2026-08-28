import { describe, expect, it } from 'vitest'
import { assertCaptchaForMode, shouldShowCaptcha } from './captcha-policy.service'

describe('captcha policy', () => {
  it('requires captcha on register even if server flag is false', () => {
    expect(shouldShowCaptcha('register', false)).toBe(true)
    expect(assertCaptchaForMode('register', false, 'key', '1234')).toBeNull()
    expect(assertCaptchaForMode('register', false)?.code).toBe('AUTH_CAPTCHA_LOAD_FAILED')
    expect(assertCaptchaForMode('register', true, 'key', '  ')?.code).toBe('AUTH_CAPTCHA_REQUIRED')
  })

  it('requires captcha on login only when flagged', () => {
    expect(shouldShowCaptcha('login', false)).toBe(false)
    expect(assertCaptchaForMode('login', false)).toBeNull()
    expect(assertCaptchaForMode('login', true, 'key', 'ab')).toBeNull()
    expect(assertCaptchaForMode('login', true)?.code).toBe('AUTH_CAPTCHA_LOAD_FAILED')
  })
})
