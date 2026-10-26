import { afterEach, describe, expect, it, vi } from 'vitest'
import { createWeb2Container } from './create-container'

afterEach(() => localStorage.clear())
const session = (token: string, userId: string) => ({ accessToken: token, userId, username: 'test', displayName: '', tokenType: 'Bearer', expiresInMs: 0 })
describe('session invalidation composition', () => {
  it('cleans the authenticated workspace before clearing identity and preserves recents', async () => {
    const unauthorized = vi.fn()
    const container = createWeb2Container({ storage: localStorage, fetchImpl: async () => new Response('{"success":false}', { status: 401 }), onUnauthorized: unauthorized })
    container.session.persist(session('old', 'a'))
    container.workspace.setActiveProjectId('project-a')
    container.workspace.writeRecentEntries([{ id: 'project-a', openedAt: 1 }])
    const changed = vi.fn()
    container.workspace.subscribe(changed)
    await container.models.list('project-a')
    expect(container.session.read()).toBeNull()
    expect(localStorage.getItem('xjicloud_active_project_id:a')).toBeNull()
    expect(localStorage.getItem('xjicloud_recent_projects:a')).toContain('project-a')
    expect(changed).toHaveBeenCalledOnce()
    expect(unauthorized).toHaveBeenCalledOnce()
    expect(container.jobWatch.jobs()).toEqual([])
  })
  it('ignores a late 401 from the previous login', async () => {
    let finish!: (value: Response) => void
    const pending = new Promise<Response>(resolve => { finish = resolve })
    const unauthorized = vi.fn()
    const container = createWeb2Container({ storage: localStorage, fetchImpl: () => pending, onUnauthorized: unauthorized })
    container.session.persist(session('old', 'a'))
    const request = container.models.list('project-a')
    container.session.persist(session('new', 'b'))
    container.workspace.setActiveProjectId('project-b')
    finish(new Response('{"success":false}', { status: 401 }))
    await request
    expect(container.session.getToken()).toBe('new')
    expect(container.workspace.getActiveProjectId()).toBe('project-b')
    expect(unauthorized).not.toHaveBeenCalled()
  })
})
