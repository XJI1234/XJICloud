import { describe, expect, it } from 'vitest'
import { MAX_RECENT_PROJECTS } from '../entities/project.entity'
import { recordRecentAccess, resolveRecentProjects } from './recent-projects.service'

describe('recent projects', () => {
  it('caps at 8 and moves the latest to front', () => {
    const seeded = Array.from({ length: MAX_RECENT_PROJECTS }, (_, index) => ({
      id: `p${index}`,
      openedAt: index,
    }))
    const next = recordRecentAccess(seeded, 'p0', 99)
    expect(next).toHaveLength(8)
    expect(next[0]).toEqual({ id: 'p0', openedAt: 99 })
    const overflow = recordRecentAccess(next, 'p-new', 100)
    expect(overflow).toHaveLength(8)
    expect(overflow[0].id).toBe('p-new')
    expect(overflow.map((entry) => entry.id)).not.toContain('p7')
  })

  it('drops unknown ids when resolving', () => {
    const recent = resolveRecentProjects(
      [{ id: 'a', name: 'A' }],
      [
        { id: 'missing', openedAt: 2 },
        { id: 'a', openedAt: 1 },
      ],
    )
    expect(recent).toEqual([{ id: 'a', name: 'A', openedAt: 1 }])
  })
})
