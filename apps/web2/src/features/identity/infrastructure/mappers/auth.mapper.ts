import type { CaptchaChallenge } from '../../domain/entities/captcha.entity'
import type { UserSession } from '../../domain/entities/user-session.entity'
import type { AccountProfile, SmsChallenge } from '../../domain/entities/account-profile.entity'

export type AuthResponseDto = {
  accessToken?: string
  tokenType?: string
  expiresInMs?: number
  userId?: string
  username?: string
  displayName?: string
  profileRequired?: boolean
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
    profileRequired: dto.profileRequired === true,
  }
}

export type ProfileDto = Partial<Omit<AccountProfile, 'phone'>> & { phone?: string | null }

export function mapProfileFromDto(dto: ProfileDto): AccountProfile {
  return {
    userId: dto.userId ?? '',
    username: dto.username ?? '',
    phone: dto.phone ?? null,
    displayName: dto.displayName ?? '',
    email: dto.email ?? '',
    organization: dto.organization ?? '',
    profileRequired: dto.profileRequired === true,
  }
}

export function mapSmsChallengeFromDto(dto: Partial<SmsChallenge>): SmsChallenge {
  return {
    challengeId: dto.challengeId ?? '',
    expiresInSeconds: dto.expiresInSeconds ?? 300,
    retryAfterSeconds: dto.retryAfterSeconds ?? 60,
  }
}

export function mapCaptchaFromDto(dto: CaptchaResponseDto): CaptchaChallenge {
  return {
    captchaKey: dto.captchaKey ?? '',
    captchaImage: dto.captchaImage ?? '',
  }
}
