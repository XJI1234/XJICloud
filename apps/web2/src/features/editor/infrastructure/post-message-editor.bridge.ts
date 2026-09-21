import { DomainError } from '@/shared/domain-error'
import { err, ok } from '@/shared/result'
import type { EditorBridgePort, EditorFrame } from '../domain/repositories/editor-bridge.port'
import {
  DIRTY_QUERY_TIMEOUT_MS,
  EXPORT_MAX_TIMEOUT_MS,
  EXPORT_PLY,
  EXPORT_TIMEOUT_MS,
  IMPORT_LOCAL,
  IMPORT_LOCAL_TIMEOUT_MS,
  IS_SCENE_DIRTY,
  READY_MAX_ATTEMPTS,
  READY_PING_TIMEOUT_MS,
  buildSuperSplatSrc,
  isDirtyResponse,
  isExportError,
  isExportProgress,
  isExportResult,
  isImportLocalDone,
  isImportLocalError,
  isTrustedIframeMessage,
} from './supersplat-protocol'

function resolveTargetOrigin(frame: EditorFrame, pageOrigin: string) {
  try {
    const src = frame.src
    if (!src || src === 'about:blank') {
      return pageOrigin
    }
    const origin = new URL(src, pageOrigin).origin
    return origin === 'null' ? pageOrigin : origin
  } catch {
    return pageOrigin
  }
}

function postToFrame(frame: EditorFrame, data: unknown, targetOrigin: string, transfer?: Transferable[]) {
  const win = frame.contentWindow
  if (!win) {
    return false
  }
  if (transfer?.length) {
    win.postMessage(data, targetOrigin, transfer)
  } else {
    win.postMessage(data, targetOrigin)
  }
  return true
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

    async waitReady(frame) {
      if (!frame.contentWindow) {
        return err(new DomainError('EDITOR_NOT_READY'))
      }

      for (let attempt = 0; attempt < READY_MAX_ATTEMPTS; attempt++) {
        const ready = await new Promise<boolean>((resolve) => {
          const timer = setTimeout(() => {
            cleanup()
            resolve(false)
          }, READY_PING_TIMEOUT_MS)

          function cleanup() {
            clearTimeout(timer)
            removeListener('message', onMessage)
          }

          function onMessage(event: MessageEvent) {
            if (!isTrustedIframeMessage(event, frame, pageOrigin) || !isDirtyResponse(event.data)) {
              return
            }
            cleanup()
            resolve(true)
          }

          addListener('message', onMessage)
          try {
            if (!postToFrame(frame, { type: IS_SCENE_DIRTY }, resolveTargetOrigin(frame, pageOrigin))) {
              cleanup()
              resolve(false)
            }
          } catch {
            cleanup()
            resolve(false)
          }
        })
        if (ready) {
          return ok(undefined)
        }
      }
      return err(new DomainError('EDITOR_TIMEOUT'))
    },

    isDirty(frame) {
      if (!frame.contentWindow) {
        return Promise.resolve(ok(false))
      }

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
        postToFrame(frame, { type: IS_SCENE_DIRTY }, resolveTargetOrigin(frame, pageOrigin))
      })
    },

    importLocal(frame, file) {
      if (!frame.contentWindow) {
        return Promise.resolve(err(new DomainError('EDITOR_NOT_READY')))
      }

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
          postToFrame(frame, { type: IMPORT_LOCAL, fileName: file.name, buffer }, resolveTargetOrigin(frame, pageOrigin), [
            buffer,
          ])
        })
      })
    },

    async exportPly(frame, exportOptions) {
      if (!frame.contentWindow) {
        return err(new DomainError('EDITOR_NOT_READY'))
      }
      const [readyError] = await this.waitReady(frame)
      if (readyError) {
        return err(readyError)
      }
      const targetOrigin = resolveTargetOrigin(frame, pageOrigin)

      return new Promise((resolve) => {
        let progressEvents = 0
        let idleTimer: ReturnType<typeof setTimeout> | undefined
        const maxTimer = setTimeout(() => {
          finish()
        }, EXPORT_MAX_TIMEOUT_MS)

        function armIdle() {
          if (progressEvents > 0) {
            if (idleTimer !== undefined) {
              clearTimeout(idleTimer)
              idleTimer = undefined
            }
            return
          }
          if (idleTimer !== undefined) {
            clearTimeout(idleTimer)
          }
          idleTimer = setTimeout(() => {
            finish()
          }, EXPORT_TIMEOUT_MS)
        }

        function finish() {
          cleanup()
          resolve(err(new DomainError('EDITOR_TIMEOUT')))
        }

        function cleanup() {
          if (idleTimer !== undefined) {
            clearTimeout(idleTimer)
          }
          clearTimeout(maxTimer)
          removeListener('message', onMessage)
        }

        function onMessage(event: MessageEvent) {
          if (!isTrustedIframeMessage(event, frame, pageOrigin)) {
            return
          }
          if (isExportProgress(event.data)) {
            progressEvents += 1
            armIdle()
            exportOptions?.onProgress?.(event.data.loaded)
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
        exportOptions?.onProgress?.(0)
        armIdle()
        postToFrame(
          frame,
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
