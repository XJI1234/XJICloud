const IS_SCENE_DIRTY = 'supersplat:is-scene-dirty'
const EXPORT_PLY = 'supersplat:export-ply'
const EXPORT_PLY_RESULT = 'supersplat:export-ply-result'
const EXPORT_PLY_ERROR = 'supersplat:export-ply-error'
const CLOUD_SAVE_REQUEST = 'supersplat:cloud-save-request'
const CLOUD_SAVE_DONE = 'supersplat:cloud-save-done'
const CLOUD_SAVE_ERROR = 'supersplat:cloud-save-error'

const DEFAULT_TIMEOUT_MS = 120_000

interface DirtyResponse {
  type: typeof IS_SCENE_DIRTY
  result: boolean
}

interface ExportPlyResult {
  type: typeof EXPORT_PLY_RESULT
  fileName: string
  buffer: ArrayBuffer
}

interface ExportPlyError {
  type: typeof EXPORT_PLY_ERROR
  message: string
}

function resolveTargetOrigin(iframe: HTMLIFrameElement): string {
  try {
    const url = new URL(iframe.src, window.location.href)
    return url.origin
  } catch {
    return window.location.origin
  }
}

function isTrustedIframeMessage(event: MessageEvent, iframe: HTMLIFrameElement | null): boolean {
  if (!iframe?.contentWindow) {
    return false
  }
  if (event.source !== iframe.contentWindow) {
    return false
  }
  // Same-origin iframe (/supersplat/ on this host)
  return event.origin === window.location.origin
}

function buildIframeSrc(options: { signedUrl: string; fileName: string; modelId: string; lang?: string }) {
  const params = new URLSearchParams({
    lng: options.lang ?? 'zh-CN',
    embedded: '1',
    load: options.signedUrl,
    filename: options.fileName,
    modelId: options.modelId,
  })
  return `/supersplat/index.html?${params.toString()}`
}

function loadModelInIframe(
  iframe: HTMLIFrameElement,
  options: { signedUrl: string; fileName: string; modelId: string; lang?: string },
) {
  iframe.src = buildIframeSrc(options)
}

async function isDirty(iframe: HTMLIFrameElement): Promise<boolean> {
  const win = iframe.contentWindow
  if (!win) {
    return false
  }

  const targetOrigin = resolveTargetOrigin(iframe)

  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('查询编辑状态超时'))
    }, 10_000)

    function cleanup() {
      window.clearTimeout(timer)
      window.removeEventListener('message', onMessage)
    }

    function onMessage(event: MessageEvent) {
      if (!isTrustedIframeMessage(event, iframe)) {
        return
      }
      const data = event.data as DirtyResponse
      if (data?.type !== IS_SCENE_DIRTY) {
        return
      }
      cleanup()
      resolve(Boolean(data.result))
    }

    window.addEventListener('message', onMessage)
    win.postMessage({ type: IS_SCENE_DIRTY }, targetOrigin)
  })
}

async function requestPlyExport(
  iframe: HTMLIFrameElement,
  options?: { compressed?: boolean; fileName?: string },
): Promise<{ blob: Blob; fileName: string }> {
  const win = iframe.contentWindow
  if (!win) {
    throw new Error('编辑器尚未就绪')
  }

  const targetOrigin = resolveTargetOrigin(iframe)

  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('PLY 导出超时'))
    }, DEFAULT_TIMEOUT_MS)

    function cleanup() {
      window.clearTimeout(timer)
      window.removeEventListener('message', onMessage)
    }

    function onMessage(event: MessageEvent) {
      if (!isTrustedIframeMessage(event, iframe)) {
        return
      }
      const data = event.data as ExportPlyResult | ExportPlyError
      if (data?.type === EXPORT_PLY_RESULT) {
        cleanup()
        resolve({
          blob: new Blob([data.buffer], { type: 'application/octet-stream' }),
          fileName: data.fileName,
        })
        return
      }

      if (data?.type === EXPORT_PLY_ERROR) {
        cleanup()
        reject(new Error(data.message || 'PLY 导出失败'))
      }
    }

    window.addEventListener('message', onMessage)
    win.postMessage({
      type: EXPORT_PLY,
      compressed: options?.compressed ?? false,
      fileName: options?.fileName,
    }, targetOrigin)
  })
}

export {
  buildIframeSrc,
  CLOUD_SAVE_DONE,
  CLOUD_SAVE_ERROR,
  CLOUD_SAVE_REQUEST,
  isDirty,
  isTrustedIframeMessage,
  loadModelInIframe,
  requestPlyExport,
}
