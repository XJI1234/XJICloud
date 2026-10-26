import type { SessionStore } from '../../domain/repositories/session-store.port'
import type { UserSession } from '../../domain/entities/user-session.entity'

export const TOKEN_KEY = 'xjicloud_token'
export const USER_ID_KEY = 'xjicloud_user_id'
export const USERNAME_KEY = 'xjicloud_username'
export const DISPLAY_NAME_KEY = 'xjicloud_display_name'
const PROFILE_REQUIRED_KEY = 'xjicloud_profile_required'

export function createLocalStorageSessionStore(storage: Storage = localStorage): SessionStore {
  function read(): UserSession | null {
    const accessToken = storage.getItem(TOKEN_KEY)
    if (!accessToken) {
      return null
    }
    return {
      accessToken,
      tokenType: 'Bearer',
      expiresInMs: 0,
      userId: storage.getItem(USER_ID_KEY) ?? '',
      username: storage.getItem(USERNAME_KEY) ?? '',
      displayName: storage.getItem(DISPLAY_NAME_KEY) ?? '',
      profileRequired: storage.getItem(PROFILE_REQUIRED_KEY) === 'true',
    }
  }

  return {
    read,
    persist(session) {
      storage.setItem(TOKEN_KEY, session.accessToken)
      storage.setItem(USER_ID_KEY, session.userId)
      storage.setItem(USERNAME_KEY, session.username)
      storage.setItem(DISPLAY_NAME_KEY, session.displayName)
      storage.setItem(PROFILE_REQUIRED_KEY, String(session.profileRequired === true))
    },
    clear() {
      storage.removeItem(TOKEN_KEY)
      storage.removeItem(USER_ID_KEY)
      storage.removeItem(USERNAME_KEY)
      storage.removeItem(DISPLAY_NAME_KEY)
      storage.removeItem(PROFILE_REQUIRED_KEY)
    },
    getToken() {
      return storage.getItem(TOKEN_KEY)
    },
  }
}
