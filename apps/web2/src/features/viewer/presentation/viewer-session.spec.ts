import { describe, expect, it, vi } from 'vitest'
import { Camera, type Result as EngineResult, type WebEngine } from '@native3dgs/web'
import { ViewerSession } from './viewer-session'

const success: EngineResult<void> = { ok: true, value: undefined }
const file = (name: string) => new File(['model'], name)
function fixture(save = vi.fn(async () => undefined)) {
  const completions: Array<(result: EngineResult<void>) => void> = []
  const cancels: Array<ReturnType<typeof vi.fn>> = []
  const engine = {
    camera: new Camera(), requestFrame: vi.fn(), fitScene: vi.fn(() => success),
    getSnapshot: () => ({ error: null }), closeScene: vi.fn(async () => undefined),
    recover: vi.fn(async () => success),
    capture: vi.fn(async () => ({ ok: true as const, value: { width: 1, height: 1, rgba: new Uint8Array(4) } })),
    open: vi.fn(() => {
      const cancel = vi.fn()
      cancels.push(cancel)
      return { requestId: completions.length + 1, cancel,
        result: new Promise<EngineResult<void>>((resolve) => completions.push(resolve)) }
    }),
  }
  return { session: new ViewerSession(engine as unknown as WebEngine, save), engine, completions, cancels, save }
}

describe('Native3DGS viewer host session', () => {
  it('only commits the latest candidate and retains the active model during replacement', async () => {
    const { session, completions, cancels } = fixture()
    const first = file('first.ply'), latest = file('latest.spz')
    const opened = session.openFile(first); completions[0](success); await opened
    const stale = session.openFile(file('stale.ply'))
    const next = session.openFile(latest)
    expect(session.getSnapshot().activeFile).toBe(first)
    expect(cancels[1]).toHaveBeenCalledOnce()
    completions[2](success); await next
    completions[1](success); await stale
    expect(session.getSnapshot().activeFile).toBe(latest)
  })
  it('keeps the old model on failed replacement and clears only the candidate', async () => {
    const { session, completions } = fixture()
    const first = file('first.ply')
    const opened = session.openFile(first); completions[0](success); await opened
    const failed = session.openFile(file('bad.ply'))
    completions[1]({ ok: false, error: { code: 'UnsupportedFormat', stage: 'Decode', diagnostic: 'internal technical text' } })
    await failed
    expect(session.getSnapshot().activeFile).toBe(first)
    expect(session.getSnapshot().candidate).toBeNull()
    expect(session.getSnapshot().error?.code).toBe('VIEWER_UNSUPPORTED_FORMAT')
    expect(session.getSnapshot().error?.message).not.toContain('internal')
  })
  it('cancellation ignores a late success without displaying an error', async () => {
    const { session, completions, cancels } = fixture()
    const pending = session.openFile(file('cancel.ply'))
    session.cancel(); completions[0](success)
    expect((await pending)[0]?.code).toBe('VIEWER_CANCELLED')
    expect(cancels[0]).toHaveBeenCalledOnce()
    expect(session.getSnapshot().activeFile).toBeNull()
    expect(session.getSnapshot().error).toBeNull()
  })
  it('close cancels loading and cannot resurrect a late model', async () => {
    const { session, completions, engine } = fixture()
    const pending = session.openFile(file('close.spz'))
    await session.close(); completions[0](success); await pending
    expect(engine.closeScene).toHaveBeenCalledOnce()
    expect(session.getSnapshot().activeFile).toBeNull()
    expect(session.getSnapshot().closing).toBe(false)
  })
  it('uses SDK reflection and navigation without modifying the canonical pose', () => {
    const { session, engine } = fixture()
    const pose = engine.camera.getPose()
    session.flip(); session.setMode('fly'); session.reset()
    expect(engine.camera.flipY).toBe(true)
    expect(engine.camera.mode).toBe('fly')
    expect(engine.camera.getPose()).toEqual(pose)
  })
  it('rejects invalid input without opening an SDK transaction', async () => {
    const { session, engine } = fixture()
    expect((await session.openFile(file('not-model.txt')))[0]?.code).toBe('MODEL_INVALID_FORMAT')
    expect(engine.open).not.toHaveBeenCalled()
  })
  it('reports PNG failures, clears capturing and permits retry', async () => {
    const save = vi.fn(async () => { throw new Error('encoder failed') })
    const { session, completions, engine } = fixture(save)
    const opened = session.openFile(file('shot.ply')); completions[0](success); await opened
    await session.capture()
    expect(session.getSnapshot().error?.code).toBe('VIEWER_SCREENSHOT_FAILED')
    expect(session.getSnapshot().capturing).toBe(false)
    await session.capture()
    expect(engine.capture).toHaveBeenCalledTimes(2)
  })
  it('clears loading when an SDK call throws before creating its operation', async () => {
    const { session, engine } = fixture()
    engine.open.mockImplementation(() => { throw new Error('unexpected failure') })
    expect((await session.openFile(file('throws.ply')))[0]?.code).toBe('VIEWER_LOAD_FAILED')
    expect(session.getSnapshot().opening).toBe(false)
    expect(session.getSnapshot().candidate).toBeNull()
  })

  it('disposal cancels pending work and suppresses late notifications', async () => {
    const { session, completions, cancels } = fixture()
    const listener = vi.fn(); session.subscribe(listener)
    const pending = session.openFile(file('pending.ply'))
    session.dispose()
    const calls = listener.mock.calls.length
    completions[0](success); await pending
    expect(cancels[0]).toHaveBeenCalledOnce()
    expect(listener).toHaveBeenCalledTimes(calls)
    expect(session.getSnapshot().activeFile).toBeNull()
  })
})
