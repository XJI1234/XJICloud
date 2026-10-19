import type { LoadOperation, WebEngine } from '@native3dgs/web'
import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import { openLocalViewerFileUseCase } from '@/features/viewer/application/use-cases/open-local-viewer-file.usecase'
import { mapViewerEngineError } from './viewer-engine-error'
import { saveViewerScreenshot, type CapturedImage } from './viewer-screenshot'

export type ViewerSessionState = Readonly<{
  activeFile: File | null
  candidate: File | null
  opening: boolean
  closing: boolean
  capturing: boolean
  mode: 'orbit' | 'fly'
  flipY: boolean
  error: DomainError | null
}>
type ViewerEngine = Pick<WebEngine, 'open' | 'camera' | 'requestFrame' | 'fitScene' | 'closeScene' | 'capture' | 'recover' | 'getSnapshot'>
type SaveScreenshot = (image: CapturedImage, name: string, isCurrent: () => boolean) => Promise<void>

/** Host transactions only; decoding, navigation, rendering and GPU disposal belong to the SDK. */
export class ViewerSession {
  private state: ViewerSessionState
  private listeners = new Set<() => void>()
  private operation: LoadOperation | null = null
  private epoch = 0
  private alive = true

  constructor(private readonly engine: ViewerEngine, private readonly saveScreenshot: SaveScreenshot = saveViewerScreenshot) {
    this.state = { activeFile: null, candidate: null, opening: false, closing: false, capturing: false,
      mode: engine.camera.mode, flipY: engine.camera.flipY, error: null }
  }

  getSnapshot = (): ViewerSessionState => this.state
  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  private publish(patch: Partial<ViewerSessionState>) {
    if (!this.alive) return
    this.state = { ...this.state, ...patch }
    this.listeners.forEach((listener) => listener())
  }

  async openFile(file: File): Promise<Result<File>> {
    const [validationError] = openLocalViewerFileUseCase(file)
    if (validationError) { this.publish({ error: validationError }); return err(validationError) }
    if (!this.alive || this.state.closing) return err(new DomainError('VIEWER_CANCELLED'))
    const epoch = ++this.epoch
    this.operation?.cancel()
    this.publish({ candidate: file, opening: true, error: null })
    try {
      const operation = this.engine.open({ kind: 'blob', blob: file, name: file.name })
      this.operation = operation
      const result = await operation.result
      if (!this.alive || epoch !== this.epoch) return err(new DomainError('VIEWER_CANCELLED'))
      this.operation = null
      const error = result.ok ? null : mapViewerEngineError(result.error)
      this.publish({ candidate: null, opening: false, error: error?.code === 'VIEWER_CANCELLED' ? null : error,
        ...(result.ok ? { activeFile: file } : {}) })
      return error ? err(error) : ok(file)
    } catch {
      if (!this.alive || epoch !== this.epoch) return err(new DomainError('VIEWER_CANCELLED'))
      this.operation = null
      const error = new DomainError('VIEWER_LOAD_FAILED')
      this.publish({ candidate: null, opening: false, error })
      return err(error)
    }
  }

  cancel() {
    ++this.epoch
    this.operation?.cancel()
    this.operation = null
    this.publish({ candidate: null, opening: false, error: null })
  }

  private cameraAction(action: () => void) {
    if (!this.alive || this.state.closing) return
    try { action(); this.engine.requestFrame(); this.publish({ error: null }) }
    catch { this.publish({ error: new DomainError('VIEWER_ACTION_FAILED') }) }
  }
  setMode(mode: 'orbit' | 'fly') {
    this.cameraAction(() => { this.engine.camera.setMode(mode); this.publish({ mode }) })
  }
  flip() {
    this.cameraAction(() => {
      this.engine.camera.setFlipY(!this.engine.camera.flipY)
      this.publish({ flipY: this.engine.camera.flipY })
    })
  }
  zoom(steps: number) { this.cameraAction(() => this.engine.camera.dolly(steps)) }
  reset() { this.cameraAction(() => this.engine.camera.reset()) }
  fit() {
    if (!this.alive || this.state.closing) return
    try {
      const result = this.engine.fitScene()
      this.publish({ error: result.ok ? null : mapViewerEngineError(result.error) })
    } catch { this.publish({ error: new DomainError('VIEWER_ACTION_FAILED') }) }
  }

  async close(): Promise<boolean> {
    if (!this.alive || this.state.closing) return false
    this.cancel()
    this.publish({ closing: true })
    try {
      await this.engine.closeScene()
      const cleanup = this.engine.getSnapshot().error
      this.publish({ activeFile: null, error: cleanup ? mapViewerEngineError(cleanup) : null })
      return this.alive
    } catch { this.publish({ error: new DomainError('VIEWER_ACTION_FAILED') }); return false }
    finally { this.publish({ closing: false }) }
  }

  async capture(): Promise<void> {
    if (!this.alive || this.state.capturing || this.state.closing || this.state.opening || !this.state.activeFile) return
    const epoch = this.epoch
    const name = this.state.activeFile.name
    const isCurrent = () => this.alive && epoch === this.epoch
    this.publish({ capturing: true, error: null })
    try {
      const result = await this.engine.capture()
      if (!isCurrent()) return
      if (!result.ok) { this.publish({ error: mapViewerEngineError(result.error) }); return }
      await this.saveScreenshot(result.value, name, isCurrent)
    } catch { if (isCurrent()) this.publish({ error: new DomainError('VIEWER_SCREENSHOT_FAILED') }) }
    finally { this.publish({ capturing: false }) }
  }

  async recover() {
    if (!this.alive || this.state.closing) return
    const epoch = this.epoch
    try {
      const result = await this.engine.recover()
      if (this.alive && epoch === this.epoch) this.publish({ error: result.ok ? null : mapViewerEngineError(result.error) })
    } catch {
      if (this.alive && epoch === this.epoch) this.publish({ error: new DomainError('VIEWER_DEVICE_LOST') })
    }
  }

  dispose() {
    this.cancel()
    this.alive = false
    this.listeners.clear()
  }
}
