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

    async waitReady(frame) {
      const win = frame.contentWindow
      if (!win) {
        return err(new DomainError('EDITOR_NOT_READY'))
      }
      const targetOrigin = resolveTargetOrigin(frame, pageOrigin)

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
            win.postMessage({ type: IS_SCENE_DIRTY }, targetOrigin)
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
      const startedAt = Date.now()

      return new Promise((resolve) => {
        let lastProgressAt = startedAt
        let progressEvents = 0
        let idleTimer: ReturnType<typeof setTimeout> | undefined
        const maxTimer = setTimeout(() => {
          finish('max')
        }, EXPORT_MAX_TIMEOUT_MS)

        function armIdle() {
          if (idleTimer !== undefined) {
            clearTimeout(idleTimer)
          }
          idleTimer = setTimeout(() => {
            finish('idle')
          }, EXPORT_TIMEOUT_MS)
        }

        function finish(reason: 'idle' | 'max') {
          cleanup()
          // #region agent log
          fetch('http://127.0.0.1:7472/ingest/c56d38ea-12ae-41d7-a4b0-707021c1849e',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'14ec0c'},body:JSON.stringify({sessionId:'14ec0c',runId:'export-timeout',hypothesisId:reason==='idle'?'B':'A',location:'post-message-editor.bridge.ts:exportPly',message:'export timed out',data:{reason,elapsedMs:Date.now()-startedAt,progressEvents,sinceProgressMs:Date.now()-lastProgressAt,origin:targetOrigin},timestamp:Date.now()})}).catch(()=>{})
          // #endregion
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
            // #region agent log
            fetch('http://127.0.0.1:7472/ingest/c56d38ea-12ae-41d7-a4b0-707021c1849e',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'14ec0c'},body:JSON.stringify({sessionId:'14ec0c',runId:'export-timeout',hypothesisId:'C',location:'post-message-editor.bridge.ts:exportPly',message:'dropped untrusted export message',data:{origin:event.origin,expected:pageOrigin,type:(event.data as {type?:string})?.type},timestamp:Date.now()})}).catch(()=>{})
            // #endregion
            return
          }
          if (isExportProgress(event.data)) {
            lastProgressAt = Date.now()
            progressEvents += 1
            armIdle()
            exportOptions?.onProgress?.(event.data.loaded)
            return
          }
          if (isExportResult(event.data)) {
            cleanup()
            // #region agent log
            fetch('http://127.0.0.1:7472/ingest/c56d38ea-12ae-41d7-a4b0-707021c1849e',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'14ec0c'},body:JSON.stringify({sessionId:'14ec0c',runId:'export-timeout',hypothesisId:'A',location:'post-message-editor.bridge.ts:exportPly',message:'export result',data:{elapsedMs:Date.now()-startedAt,progressEvents,bytes:event.data.buffer?.byteLength??0},timestamp:Date.now()})}).catch(()=>{})
            // #endregion
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
        // #region agent log
        fetch('http://127.0.0.1:7472/ingest/c56d38ea-12ae-41d7-a4b0-707021c1849e',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'14ec0c'},body:JSON.stringify({sessionId:'14ec0c',runId:'export-timeout',hypothesisId:'A',location:'post-message-editor.bridge.ts:exportPly',message:'export started',data:{origin:targetOrigin,fileName:exportOptions?.fileName??null},timestamp:Date.now()})}).catch(()=>{})
        // #endregion
        armIdle()
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
