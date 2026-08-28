import type { Result } from '@/shared/result'
import type { EditorExportResult, EditorLaunchParams } from '../entities/editor-session.entity'

export type EditorFrame = {
  contentWindow: Window | null
  src: string
}

export interface EditorBridgePort {
  buildSrc(params: EditorLaunchParams): string
  isDirty(frame: EditorFrame): Promise<Result<boolean>>
  importLocal(frame: EditorFrame, file: File): Promise<Result<void>>
  exportPly(frame: EditorFrame, options?: { compressed?: boolean; fileName?: string }): Promise<Result<EditorExportResult>>
}
