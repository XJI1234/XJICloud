import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPostMessageEditorBridge } from './post-message-editor.bridge'

afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks() })
describe('editor bridge lifecycle', () => {
  const frame = () => ({ src: location.origin, contentWindow: { postMessage: vi.fn() } as unknown as Window })
  it.each([3, 4] as const)('sends SPZ v%s to the iframe exporter', async spzVersion => {
    const target = frame()
    const pending = createPostMessageEditorBridge().exportPly(target, { fileName: 'out.spz', spzVersion })
    window.dispatchEvent(new MessageEvent('message', { source: target.contentWindow, origin: location.origin,
      data: { type: 'supersplat:is-scene-dirty', result: false } }))
    await Promise.resolve(); await Promise.resolve()
    expect(target.contentWindow!.postMessage).toHaveBeenCalledWith(expect.objectContaining({ spzVersion }), location.origin)
    window.dispatchEvent(new MessageEvent('message', { source: target.contentWindow, origin: location.origin,
      data: { type: 'supersplat:export-ply-result', fileName: 'out.spz', buffer: new ArrayBuffer(1) } }))
    expect((await pending)[0]).toBeNull()
  })
  it('maps a file read rejection to a Result', async () => {
    const file = new File(['x'], 'a.ply')
    Object.defineProperty(file, 'arrayBuffer', { value: async () => { throw new Error('read failed') } })
    expect((await createPostMessageEditorBridge().importLocal(frame(), file))[0]?.code).toBe('EDITOR_IMPORT_FAILED')
  })
  it('cleans listeners and timers when postMessage throws', async () => {
    vi.useFakeTimers()
    const remove = vi.fn(window.removeEventListener.bind(window))
    const target = frame()
    target.contentWindow.postMessage = () => { throw new Error('closed') }
    expect((await createPostMessageEditorBridge({ removeListener: remove }).isDirty(target))[0]?.code).toBe('EDITOR_NOT_READY')
    expect(remove).toHaveBeenCalledOnce()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('rejects untrusted and malformed responses, and serializes requests to a frame', async () => {
    vi.useFakeTimers()
    const bridge = createPostMessageEditorBridge()
    const target = frame()
    const pending = bridge.isDirty(target)
    expect((await bridge.isDirty(target))[0]?.code).toBe('EDITOR_NOT_READY')
    const message = (origin: string, result: unknown) => window.dispatchEvent(new MessageEvent('message', {
      source: target.contentWindow, origin, data: { type: 'supersplat:is-scene-dirty', result },
    }))
    message('https://untrusted.invalid', true)
    message(location.origin, 'false')
    expect(vi.getTimerCount()).toBe(1)
    message(location.origin, true)
    expect(await pending).toEqual([null, true])
    expect(vi.getTimerCount()).toBe(0)
  })
  it('settles timeout and abort paths without residual listeners', async () => {
    vi.useFakeTimers()
    const bridge = createPostMessageEditorBridge()
    const target = frame()
    const pending = bridge.isDirty(target)
    await vi.advanceTimersByTimeAsync(10_000)
    expect((await pending)[0]?.code).toBe('EDITOR_TIMEOUT')
    const owner = new AbortController()
    const ready = bridge.waitReady(target, owner.signal)
    owner.abort()
    expect((await ready)[0]?.code).toBe('OPERATION_CANCELLED')
    expect(vi.getTimerCount()).toBe(0)
  })
  it('settles cancellation even when file reading never completes', async () => {
    vi.useFakeTimers()
    const owner = new AbortController()
    const file = new File(['x'], 'a.ply')
    Object.defineProperty(file, 'arrayBuffer', { value: () => new Promise(() => undefined) })
    const pending = createPostMessageEditorBridge().importLocal(frame(), file, owner.signal)
    owner.abort()
    expect((await pending)[0]?.code).toBe('OPERATION_CANCELLED')
    expect(vi.getTimerCount()).toBe(0)
  })
})
