import type { Result } from '@/shared/result'
import type { CaptchaChallenge } from '../entities/captcha.entity'
import type { UserSession } from '../entities/user-session.entity'

export type LoginCommand = {
  username: string
  password: string
  captchaKey?: string
  captchaCode?: string
}

export type RegisterCommand = {
  username: string
  password: string
  displayName?: string
  captchaKey: string
  captchaCode: string
}

export interface AuthRepository {
  login(command: LoginCommand): Promise<Result<UserSession>>
  register(command: RegisterCommand): Promise<Result<UserSession>>
  getCaptcha(): Promise<Result<CaptchaChallenge>>
  needCaptcha(username: string): Promise<Result<boolean>>
}
