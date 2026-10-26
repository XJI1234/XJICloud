import { DomainError } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
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

  const busy = new WeakSet<Window>()

  function exchange<T>(frame: EditorFrame, input: unknown, timeoutMs: number,
    receive: (data: unknown) => Result<T> | undefined,
    signal?: AbortSignal, transfer?: Transferable[], onProgress?: (loaded: number) => void,
  ): Promise<Result<T>> {
    const target = frame.contentWindow
    if (signal?.aborted) return Promise.resolve(err(new DomainError('OPERATION_CANCELLED')))
    if (!target || busy.has(target)) return Promise.resolve(err(new DomainError('EDITOR_NOT_READY')))
    busy.add(target)
    return new Promise((resolve) => {
      let settled = false
      let idle: ReturnType<typeof setTimeout> | undefined
      const timer = setTimeout(() => finish(err(new DomainError('EDITOR_TIMEOUT'))), timeoutMs)
      function finish(result: Result<T>) {
        if (settled) return
        settled = true
        clearTimeout(timer)
        clearTimeout(idle)
        removeListener('message', onMessage)
        signal?.removeEventListener('abort', abort)
        busy.delete(target!)
        resolve(result)
      }
      function abort() { finish(err(new DomainError('OPERATION_CANCELLED'))) }
      function onMessage(event: MessageEvent) {
        if (!isTrustedIframeMessage(event, frame, pageOrigin)) return
        if (onProgress && isExportProgress(event.data)) {
          clearTimeout(idle)
          onProgress(event.data.loaded)
          return
        }
        const result = receive(event.data)
        if (result) finish(result)
      }
      addListener('message', onMessage)
      signal?.addEventListener('abort', abort, { once: true })
      if (onProgress) idle = setTimeout(() => finish(err(new DomainError('EDITOR_TIMEOUT'))), EXPORT_TIMEOUT_MS)
      try {
        if (!postToFrame(frame, input, resolveTargetOrigin(frame, pageOrigin), transfer)) {
          finish(err(new DomainError('EDITOR_NOT_READY')))
        }
      } catch {
        finish(err(new DomainError('EDITOR_NOT_READY')))
      }
    })
  }

  return {
    buildSrc: buildSuperSplatSrc,
    async waitReady(frame, signal) {
      for (let attempt = 0; attempt < READY_MAX_ATTEMPTS; attempt++) {
        const [error] = await exchange(frame, { type: IS_SCENE_DIRTY }, READY_PING_TIMEOUT_MS,
          data => isDirtyResponse(data) ? ok(undefined) : undefined, signal)
        if (!error) return ok(undefined)
        if (error.code !== 'EDITOR_TIMEOUT') return err(error)
      }
      return err(new DomainError('EDITOR_TIMEOUT'))
    },
    isDirty(frame, signal) {
      return exchange(frame, { type: IS_SCENE_DIRTY }, DIRTY_QUERY_TIMEOUT_MS,
        data => isDirtyResponse(data) ? ok(data.result) : undefined, signal)
    },
    async importLocal(frame, file, signal) {
      try {
        if (signal?.aborted) return err(new DomainError('OPERATION_CANCELLED'))
        const buffer = await new Promise<ArrayBuffer>((resolve, reject) => {
          const finish = (error?: unknown, value?: ArrayBuffer) => {
            clearTimeout(timer)
            signal?.removeEventListener('abort', abort)
            if (error) reject(error)
            else resolve(value!)
          }
          const abort = () => finish(new DomainError('OPERATION_CANCELLED'))
          const timer = setTimeout(() => finish(new DomainError('EDITOR_TIMEOUT')), IMPORT_LOCAL_TIMEOUT_MS)
          signal?.addEventListener('abort', abort, { once: true })
          try { void file.arrayBuffer().then(value => finish(undefined, value), error => finish(error)) }
          catch (error) { finish(error) }
        })
        if (signal?.aborted) return err(new DomainError('OPERATION_CANCELLED'))
        return exchange(frame, { type: IMPORT_LOCAL, fileName: file.name, buffer }, IMPORT_LOCAL_TIMEOUT_MS,
          data => isImportLocalDone(data) ? ok(undefined) : isImportLocalError(data)
            ? err(new DomainError('EDITOR_IMPORT_FAILED', data.message)) : undefined, signal, [buffer])
      } catch (error) {
        return err(error instanceof DomainError ? error : new DomainError('EDITOR_IMPORT_FAILED'))
      }
    },
    async exportPly(frame, exportOptions) {
      const [readyError] = await this.waitReady(frame, exportOptions?.signal)
      if (readyError) return err(readyError)
      exportOptions?.onProgress?.(0)
      return exchange(frame, { type: EXPORT_PLY, compressed: exportOptions?.compressed ?? false,
        fileName: exportOptions?.fileName, spzVersion: exportOptions?.spzVersion }, EXPORT_MAX_TIMEOUT_MS,
        data => isExportResult(data) ? ok({ blob: new Blob([data.buffer], { type: 'application/octet-stream' }), fileName: data.fileName })
          : isExportError(data) ? err(new DomainError('EDITOR_EXPORT_FAILED', data.message)) : undefined,
        exportOptions?.signal, undefined, exportOptions?.onProgress ?? (() => undefined))
    },
  }
}
