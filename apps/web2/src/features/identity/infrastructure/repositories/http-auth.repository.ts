import { err, ok, type Result } from '@/shared/result'
import { mapHttpError, type HttpClient } from '@/shared/infrastructure/http-client'
import type { AuthRepository, LoginCommand, RegisterCommand } from '../../domain/repositories/auth.repository'
import type { CaptchaChallenge } from '../../domain/entities/captcha.entity'
import type { UserSession } from '../../domain/entities/user-session.entity'
import { mapCaptchaFromDto, mapSessionFromDto, type AuthResponseDto, type CaptchaResponseDto } from '../mappers/auth.mapper'

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
          body: JSON.stringify({
            username: command.username,
            password: command.password,
            displayName: command.displayName,
            captchaKey: command.captchaKey,
            captchaCode: command.captchaCode,
          }),
        })
        return ok(mapSessionFromDto(dto))
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
