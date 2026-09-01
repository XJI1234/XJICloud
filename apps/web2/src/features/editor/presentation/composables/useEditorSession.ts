import { inject } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import {
  blankEditorLaunch,
  confirmLeaveIfDirtyUseCase,
  editorSrc,
  importLocalEditorFileUseCase,
  openEditorUseCase,
  prepareLocalEditorLaunch,
  saveEditorExportUseCase,
} from '../../application/use-cases/editor.usecase'
import type { EditorFrame } from '../../domain/repositories/editor-bridge.port'

export function useEditorSession() {
  const container = inject(CONTAINER_KEY)!
  return {
    open: (modelId: string) => openEditorUseCase({ models: container.models }, modelId),
    src: (params: Parameters<typeof editorSrc>[1]) => editorSrc({ bridge: container.editorBridge }, params),
    blank: (lang?: string) => blankEditorLaunch(lang),
    prepareLocal: (file: File) => prepareLocalEditorLaunch(file),
    importLocal: (frame: EditorFrame, file: File) =>
      importLocalEditorFileUseCase({ bridge: container.editorBridge }, frame, file),
    isDirty: (frame: EditorFrame) => confirmLeaveIfDirtyUseCase({ bridge: container.editorBridge }, frame),
    saveExport: (input: Parameters<typeof saveEditorExportUseCase>[1]) =>
      saveEditorExportUseCase({ models: container.models, bridge: container.editorBridge }, input),
  }
}
