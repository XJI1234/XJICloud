import type { CaptchaChallenge } from '../../domain/entities/captcha.entity'
import type { UserSession } from '../../domain/entities/user-session.entity'

export type AuthResponseDto = {
  accessToken?: string
  tokenType?: string
  expiresInMs?: number
  userId?: string
  username?: string
  displayName?: string
}

export type CaptchaResponseDto = {
  captchaKey?: string
  captchaImage?: string
}

export function mapSessionFromDto(dto: AuthResponseDto): UserSession {
  return {
    accessToken: dto.accessToken ?? '',
    tokenType: dto.tokenType ?? 'Bearer',
    expiresInMs: dto.expiresInMs ?? 0,
    userId: dto.userId ?? '',
    username: dto.username ?? '',
    displayName: dto.displayName ?? '',
  }
}

export function mapCaptchaFromDto(dto: CaptchaResponseDto): CaptchaChallenge {
  return {
    captchaKey: dto.captchaKey ?? '',
    captchaImage: dto.captchaImage ?? '',
  }
}
