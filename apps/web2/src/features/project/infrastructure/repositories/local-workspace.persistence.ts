import { MAX_RECENT_PROJECTS, type RecentProjectEntry } from '../../domain/entities/project.entity'
import type { WorkspacePersistence } from '../../domain/repositories/workspace-persistence.port'

export const ACTIVE_PROJECT_KEY = 'xjicloud_active_project_id'
export const RECENT_PROJECTS_KEY = 'xjicloud_recent_projects'

export function createLocalWorkspacePersistence(storage: Storage = localStorage): WorkspacePersistence {
  const listeners = new Set<() => void>()

  function notify() {
    listeners.forEach((listener) => listener())
  }

  function readRecentEntries(): RecentProjectEntry[] {
    try {
      const raw = storage.getItem(RECENT_PROJECTS_KEY)
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
      return storage.getItem(ACTIVE_PROJECT_KEY)
    },
    setActiveProjectId(id) {
      if (id) {
        storage.setItem(ACTIVE_PROJECT_KEY, id)
      } else {
        storage.removeItem(ACTIVE_PROJECT_KEY)
      }
      notify()
    },
    readRecentEntries,
    writeRecentEntries(entries) {
      storage.setItem(RECENT_PROJECTS_KEY, JSON.stringify(entries.slice(0, MAX_RECENT_PROJECTS)))
      notify()
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
    clear() {
      storage.removeItem(ACTIVE_PROJECT_KEY)
      storage.removeItem(RECENT_PROJECTS_KEY)
      notify()
    },
  }
}
