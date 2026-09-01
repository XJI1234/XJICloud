import { DomainError } from '@/shared/domain-error'
import { err, ok } from '@/shared/result'
import type { EditorBridgePort, EditorFrame } from '../domain/repositories/editor-bridge.port'
import {
  DIRTY_QUERY_TIMEOUT_MS,
  EXPORT_PLY,
  EXPORT_TIMEOUT_MS,
  IMPORT_LOCAL,
  IMPORT_LOCAL_TIMEOUT_MS,
  IS_SCENE_DIRTY,
  buildSuperSplatSrc,
  isDirtyResponse,
  isExportError,
  isExportResult,
  isImportLocalDone,
  isImportLocalError,
  isTrustedIframeMessage,
} from './supersplat-protocol'

function resolveTargetOrigin(frame: EditorFrame, pageOrigin: string) {
  try {
    return new URL(frame.src, pageOrigin).origin
  } catch {
    return pageOrigin
  }
}

export function createPostMessageEditorBridge(options?: {
  pageOrigin?: string
  addListener?: typeof window.addEventListener
  removeListener?: typeof window.removeEventListener
}): EditorBridgePort {
  const pageOrigin = options?.pageOrigin ?? (typeof window !== 'undefined' ? window.location.origin : 'http://localhost')
  const addListener = options?.addListener ?? window.addEventListener.bind(window)
  const removeListener = options?.removeListener ?? window.removeEventListener.bind(window)

  return {
    buildSrc(params) {
      return buildSuperSplatSrc(params)
    },

    isDirty(frame) {
      const win = frame.contentWindow
      if (!win) {
        return Promise.resolve(ok(false))
      }
      const targetOrigin = resolveTargetOrigin(frame, pageOrigin)

      return new Promise((resolve) => {
        const timer = setTimeout(() => {
          cleanup()
          resolve(err(new DomainError('EDITOR_TIMEOUT')))
        }, DIRTY_QUERY_TIMEOUT_MS)

        function cleanup() {
          clearTimeout(timer)
          removeListener('message', onMessage)
        }

        function onMessage(event: MessageEvent) {
          if (!isTrustedIframeMessage(event, frame, pageOrigin) || !isDirtyResponse(event.data)) {
            return
          }
          cleanup()
          resolve(ok(Boolean(event.data.result)))
        }

        addListener('message', onMessage)
        win.postMessage({ type: IS_SCENE_DIRTY }, targetOrigin)
      })
    },

    importLocal(frame, file) {
      const win = frame.contentWindow
      if (!win) {
        return Promise.resolve(err(new DomainError('EDITOR_NOT_READY')))
      }
      const targetOrigin = resolveTargetOrigin(frame, pageOrigin)

      return file.arrayBuffer().then((buffer) => {
        return new Promise((resolve) => {
          const timer = setTimeout(() => {
            cleanup()
            resolve(err(new DomainError('EDITOR_TIMEOUT')))
          }, IMPORT_LOCAL_TIMEOUT_MS)

          function cleanup() {
            clearTimeout(timer)
            removeListener('message', onMessage)
          }

          function onMessage(event: MessageEvent) {
            if (!isTrustedIframeMessage(event, frame, pageOrigin)) {
              return
            }
            if (isImportLocalDone(event.data)) {
              cleanup()
              resolve(ok(undefined))
              return
            }
            if (isImportLocalError(event.data)) {
              cleanup()
              resolve(err(new DomainError('EDITOR_IMPORT_FAILED', event.data.message)))
            }
          }

          addListener('message', onMessage)
          win.postMessage({ type: IMPORT_LOCAL, fileName: file.name, buffer }, targetOrigin, [buffer])
        })
      })
    },

    exportPly(frame, exportOptions) {
      const win = frame.contentWindow
      if (!win) {
        return Promise.resolve(err(new DomainError('EDITOR_NOT_READY')))
      }
      const targetOrigin = resolveTargetOrigin(frame, pageOrigin)

      return new Promise((resolve) => {
        const timer = setTimeout(() => {
          cleanup()
          resolve(err(new DomainError('EDITOR_TIMEOUT')))
        }, EXPORT_TIMEOUT_MS)

        function cleanup() {
          clearTimeout(timer)
          removeListener('message', onMessage)
        }

        function onMessage(event: MessageEvent) {
          if (!isTrustedIframeMessage(event, frame, pageOrigin)) {
            return
          }
          if (isExportResult(event.data)) {
            cleanup()
            resolve(
              ok({
                blob: new Blob([event.data.buffer], { type: 'application/octet-stream' }),
                fileName: event.data.fileName,
              }),
            )
            return
          }
          if (isExportError(event.data)) {
            cleanup()
            resolve(err(new DomainError('EDITOR_EXPORT_FAILED', event.data.message)))
          }
        }

        addListener('message', onMessage)
        win.postMessage(
          {
            type: EXPORT_PLY,
            compressed: exportOptions?.compressed ?? false,
            fileName: exportOptions?.fileName,
          },
          targetOrigin,
        )
      })
    },
  }
}
