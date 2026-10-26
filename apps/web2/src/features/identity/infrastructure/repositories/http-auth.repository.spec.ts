import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@xjicloud/shared'
import i18n from '@/app/i18n'
import { formatDomainError } from '@/presentation/errors'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import { createHttpAuthRepository } from './http-auth.repository'

describe('phone registration HTTP contract', () => {
  it('sends SMS proof and persists no legacy image or username registration fields', async () => {
    const request = vi.fn().mockResolvedValue({
      accessToken: 'token', userId: 'u1', username: '13800138000', profileRequired: true,
    })
    const repo = createHttpAuthRepository({ request } as unknown as HttpClient)
    const command = { phone: '13800138000', password: 'Password123!', smsChallengeId: 'id', smsCode: '123456' }
    const [error, session] = await repo.register(command)
    expect(error).toBeNull()
    expect(session?.profileRequired).toBe(true)
    expect(request).toHaveBeenCalledWith('/api/v1/auth/register', {
      method: 'POST', body: JSON.stringify(command),
    })
  })

  it('shows a translated unavailable message without exposing provider details', async () => {
    const request = vi.fn().mockRejectedValue(new ApiError('secret provider details', 503))
    const repo = createHttpAuthRepository({ request } as unknown as HttpClient)
    const [error] = await repo.sendRegistrationSms({ phone: '13800138000', captchaKey: 'k', captchaCode: 'ABCD' })
    expect(error?.code).toBe('AUTH_SMS_UNAVAILABLE')
    expect(error?.message).not.toContain('secret')
    const message = formatDomainError(key => i18n.global.t(key), error)
    expect(message).toContain('短信注册')
    expect(message).not.toContain('errors.')
  })
})
