import { MAX_RECENT_PROJECTS, type RecentProjectEntry } from '../../domain/entities/project.entity'
import type { WorkspacePersistence } from '../../domain/repositories/workspace-persistence.port'

export const ACTIVE_PROJECT_KEY = 'xjicloud_active_project_id'
export const RECENT_PROJECTS_KEY = 'xjicloud_recent_projects'

export function recentProjectsStorageKey(userId: string | null) {
  return userId ? `${RECENT_PROJECTS_KEY}:${userId}` : RECENT_PROJECTS_KEY
}

export function activeProjectStorageKey(userId: string | null) {
  return userId ? `${ACTIVE_PROJECT_KEY}:${userId}` : ACTIVE_PROJECT_KEY
}

export function createLocalWorkspacePersistence(
  storage: Storage = localStorage,
  getUserId: () => string | null = () => null,
): WorkspacePersistence {
  const listeners = new Set<() => void>()

  function notify() {
    listeners.forEach((listener) => listener())
  }

  function readRecentEntries(): RecentProjectEntry[] {
    const userId = getUserId()
    if (!userId) {
      return []
    }
    try {
      const raw = storage.getItem(recentProjectsStorageKey(userId))
      if (!raw) {
        return []
      }
      const parsed = JSON.parse(raw) as RecentProjectEntry[]
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }

  return {
    getActiveProjectId() {
      const userId = getUserId()
      if (!userId) {
        return null
      }
      return storage.getItem(activeProjectStorageKey(userId))
    },
    setActiveProjectId(id) {
      const userId = getUserId()
      if (!userId) {
        return
      }
      const key = activeProjectStorageKey(userId)
      if (id) {
        storage.setItem(key, id)
      } else {
        storage.removeItem(key)
      }
      notify()
    },
    readRecentEntries,
    writeRecentEntries(entries) {
      const userId = getUserId()
      if (!userId) {
        return
      }
      storage.setItem(recentProjectsStorageKey(userId), JSON.stringify(entries.slice(0, MAX_RECENT_PROJECTS)))
      notify()
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    clear() {
      const userId = getUserId()
      if (userId) {
        storage.removeItem(activeProjectStorageKey(userId))
      }
      notify()
    },
  }
}
