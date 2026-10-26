export type AccountProfile = {
  userId: string
  username: string
  phone: string | null
  displayName: string
  email: string
  organization: string
  profileRequired: boolean
}

export type ProfileCommand = Pick<AccountProfile, 'displayName' | 'email' | 'organization'>

export type SmsChallenge = {
  challengeId: string
  expiresInSeconds: number
  retryAfterSeconds: number
}
