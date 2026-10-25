import type { Result } from '@/shared/result'
import type { EditorExportResult, EditorLaunchParams } from '../entities/editor-session.entity'

export type EditorFrame = {
  contentWindow: Window | null
  src: string
}

export interface EditorBridgePort {
  buildSrc(params: EditorLaunchParams): string
  /** Wait until SuperSplat's postMessage bridge is listening (iframe `load` alone is not enough). */
  waitReady(frame: EditorFrame, signal?: AbortSignal): Promise<Result<void>>
  isDirty(frame: EditorFrame, signal?: AbortSignal): Promise<Result<boolean>>
  importLocal(frame: EditorFrame, file: File, signal?: AbortSignal): Promise<Result<void>>
  exportPly(
    frame: EditorFrame,
    options?: {
      signal?: AbortSignal
      compressed?: boolean
      fileName?: string
      onProgress?: (loaded: number) => void
    },
  ): Promise<Result<EditorExportResult>>
}
