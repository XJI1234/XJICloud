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
      // #region agent log
      globalThis.fetch?.('http://127.0.0.1:7472/ingest/c56d38ea-12ae-41d7-a4b0-707021c1849e',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'67c29f'},body:JSON.stringify({sessionId:'67c29f',runId:'pre-fix',hypothesisId:'B',location:'local-workspace.persistence.ts:writeRecentEntries',message:'wrote per-account recents',data:{count:Math.min(entries.length, MAX_RECENT_PROJECTS),cappedAt:MAX_RECENT_PROJECTS},timestamp:Date.now()})})?.catch(()=>{});
      // #endregion
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
