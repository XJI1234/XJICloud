import { err, ok, type Result } from '@/shared/result'
import { ApiError } from '@xjicloud/shared'
import { DomainError } from '@/shared/domain-error'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { AuthRepository, LoginCommand, RegisterCommand } from '../../domain/repositories/auth.repository'
import type { CaptchaChallenge } from '../../domain/entities/captcha.entity'
import type { UserSession } from '../../domain/entities/user-session.entity'
import { mapCaptchaFromDto, mapSessionFromDto, type AuthResponseDto, type CaptchaResponseDto } from '../mappers/auth.mapper'
import { mapProfileFromDto, mapSmsChallengeFromDto, type ProfileDto } from '../mappers/auth.mapper'
import type { SmsChallenge } from '../../domain/entities/account-profile.entity'

export function createHttpAuthRepository(http: HttpClient): AuthRepository {
  return {
    async login(command: LoginCommand): Promise<Result<UserSession>> {
      try {
        const dto = await http.request<AuthResponseDto>('/api/v1/auth/login', {
          method: 'POST',
          body: JSON.stringify({
            username: command.username,
            password: command.password,
            captchaKey: command.captchaKey,
            captchaCode: command.captchaCode,
          }),
        })
        return ok(mapSessionFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },

    async register(command: RegisterCommand): Promise<Result<UserSession>> {
      try {
        const dto = await http.request<AuthResponseDto>('/api/v1/auth/register', {
          method: 'POST',
          body: JSON.stringify(command),
        })
        return ok(mapSessionFromDto(dto))
      } catch (error) {
        return err(mapRegistrationError(error))
      }
    },

    async sendRegistrationSms(command) {
      try {
        const dto = await http.request<Partial<SmsChallenge>>('/api/v1/auth/registration-sms', {
          method: 'POST', body: JSON.stringify(command),
        })
        return ok(mapSmsChallengeFromDto(dto))
      } catch (error) {
        return err(mapRegistrationError(error))
      }
    },

    async getProfile() {
      try {
        return ok(mapProfileFromDto(await http.request<ProfileDto>('/api/v1/account')))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },

    async updateProfile(command) {
      try {
        return ok(mapProfileFromDto(await http.request<ProfileDto>('/api/v1/account', {
          method: 'PUT', body: JSON.stringify(command),
        })))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },

    async getCaptcha(): Promise<Result<CaptchaChallenge>> {
      try {
        const dto = await http.request<CaptchaResponseDto>('/api/v1/auth/captcha')
        return ok(mapCaptchaFromDto(dto))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },

    async needCaptcha(username: string): Promise<Result<boolean>> {
      try {
        const dto = await http.request<{ needCaptcha?: boolean }>(
          `/api/v1/auth/need-captcha?username=${encodeURIComponent(username)}`,
        )
        return ok(Boolean(dto.needCaptcha))
      } catch (error) {
        return err(mapHttpError(error))
      }
    },
  }
}

function mapRegistrationError(error: unknown): DomainError {
  if (error instanceof ApiError && error.status === 503) {
    return new DomainError('AUTH_SMS_UNAVAILABLE', undefined, { status: 503 })
  }
  return mapHttpError(error)
}
