import { describe, expect, it } from 'vitest'
import { createLocalWorkspacePersistence } from './local-workspace.persistence'

function memoryStorage(): Storage {
  const map = new Map<string, string>()
  return {
    get length() {
      return map.size
    },
    clear() {
      map.clear()
    },
    getItem(key) {
      return map.has(key) ? map.get(key)! : null
    },
    key(index) {
      return [...map.keys()][index] ?? null
    },
    removeItem(key) {
      map.delete(key)
    },
    setItem(key, value) {
      map.set(key, String(value))
    },
  }
}

describe('local workspace persistence', () => {
  it('notifies subscribers when the active project changes', () => {
    const persistence = createLocalWorkspacePersistence(memoryStorage())
    const seen: Array<string | null> = []
    const unsubscribe = persistence.subscribe(() => {
      seen.push(persistence.getActiveProjectId())
    })

    persistence.setActiveProjectId('p1')
    persistence.setActiveProjectId(null)
    unsubscribe()
    persistence.setActiveProjectId('p2')

    expect(seen).toEqual(['p1', null])
  })

  it('notifies subscribers on clear', () => {
    const persistence = createLocalWorkspacePersistence(memoryStorage())
    persistence.setActiveProjectId('p1')
    let calls = 0
    persistence.subscribe(() => {
      calls += 1
    })
    persistence.clear()
    expect(calls).toBe(1)
    expect(persistence.getActiveProjectId()).toBeNull()
  })
})
