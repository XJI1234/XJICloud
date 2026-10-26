import type { Result } from '@/shared/result'
import type { CaptchaChallenge } from '../entities/captcha.entity'
import type { UserSession } from '../entities/user-session.entity'
import type { AccountProfile, ProfileCommand, SmsChallenge } from '../entities/account-profile.entity'

export type LoginCommand = {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
}

export type RegisterCommand = {
  phone: string
  password: string
  smsChallengeId: string
  smsCode: string
}

export type SendSmsCommand = {
  phone: string
  captchaKey: string
  captchaCode: string
}

export interface AuthRepository {
  login(command: LoginCommand): Promise<Result<UserSession>>
  register(command: RegisterCommand): Promise<Result<UserSession>>
  getCaptcha(): Promise<Result<CaptchaChallenge>>
  needCaptcha(username: string): Promise<Result<boolean>>
  sendRegistrationSms(command: SendSmsCommand): Promise<Result<SmsChallenge>>
  getProfile(): Promise<Result<AccountProfile>>
  updateProfile(command: ProfileCommand): Promise<Result<AccountProfile>>
}
