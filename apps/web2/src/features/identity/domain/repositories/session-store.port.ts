import type { UserSession } from '../entities/user-session.entity'

export interface SessionStore {
  read(): UserSession | null
  persist(session: UserSession): void
  clear(): void
  getToken(): string | null
}
