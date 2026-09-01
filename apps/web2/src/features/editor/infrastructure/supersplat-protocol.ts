export const IS_SCENE_DIRTY = 'supersplat:is-scene-dirty'
export const EXPORT_PLY = 'supersplat:export-ply'
export const EXPORT_PLY_RESULT = 'supersplat:export-ply-result'
export const EXPORT_PLY_ERROR = 'supersplat:export-ply-error'
export const CLOUD_SAVE_REQUEST = 'supersplat:cloud-save-request'
export const CLOUD_SAVE_DONE = 'supersplat:cloud-save-done'
export const CLOUD_SAVE_ERROR = 'supersplat:cloud-save-error'
export const IMPORT_LOCAL = 'supersplat:import-local'
export const IMPORT_LOCAL_DONE = 'supersplat:import-local-done'
export const IMPORT_LOCAL_ERROR = 'supersplat:import-local-error'

export const DIRTY_QUERY_TIMEOUT_MS = 10_000
export const EXPORT_TIMEOUT_MS = 120_000
export const IMPORT_LOCAL_TIMEOUT_MS = 120_000

export type DirtyResponse = {
  type: typeof IS_SCENE_DIRTY
  result: boolean
}

export type ExportPlyResultMessage = {
  type: typeof EXPORT_PLY_RESULT
  fileName: string
  buffer: ArrayBuffer
}

export type ExportPlyErrorMessage = {
  type: typeof EXPORT_PLY_ERROR
  message: string
}

export type ImportLocalDoneMessage = {
  type: typeof IMPORT_LOCAL_DONE
}

export type ImportLocalErrorMessage = {
  type: typeof IMPORT_LOCAL_ERROR
  message: string
}

export function isDirtyResponse(data: unknown): data is DirtyResponse {
  return Boolean(data && typeof data === 'object' && (data as DirtyResponse).type === IS_SCENE_DIRTY)
}

export function isExportResult(data: unknown): data is ExportPlyResultMessage {
  return Boolean(data && typeof data === 'object' && (data as ExportPlyResultMessage).type === EXPORT_PLY_RESULT)
}

export function isExportError(data: unknown): data is ExportPlyErrorMessage {
  return Boolean(data && typeof data === 'object' && (data as ExportPlyErrorMessage).type === EXPORT_PLY_ERROR)
}

export function isImportLocalDone(data: unknown): data is ImportLocalDoneMessage {
  return Boolean(data && typeof data === 'object' && (data as ImportLocalDoneMessage).type === IMPORT_LOCAL_DONE)
}

export function isImportLocalError(data: unknown): data is ImportLocalErrorMessage {
  return Boolean(data && typeof data === 'object' && (data as ImportLocalErrorMessage).type === IMPORT_LOCAL_ERROR)
}

export function buildSuperSplatSrc(options: {
  origin?: string
  signedUrl?: string
  fileName?: string
  modelId?: string
  lang?: string
}): string {
  const params = new URLSearchParams({
    lng: options.lang ?? 'zh-CN',
    embedded: '1',
  })
  if (options.signedUrl && options.fileName) {
    params.set('load', options.signedUrl)
    params.set('filename', options.fileName)
  }
  if (options.modelId) {
    params.set('modelId', options.modelId)
  }
  return `/supersplat/index.html?${params.toString()}`
}

export function isTrustedIframeMessage(event: MessageEvent, frame: { contentWindow: Window | null }, pageOrigin: string) {
  if (!frame.contentWindow) {
    return false
  }
  if (event.source !== frame.contentWindow) {
    return false
  }
  return event.origin === pageOrigin
}
