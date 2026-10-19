import { appendFile, mkdir, rename, stat } from 'node:fs/promises'
import path from 'node:path'

const events = new Set(['attach', 'detach', 'first-frame', 'sample', 'visibility', 'recovery', 'error', 'loading-complete', 'media-event'])
const reasons = new Set(['', 'source-change', 'media-error', 'transport-ended', 'initialization', 'stall', 'no-first-frame', 'pause', 'waiting', 'stalled', 'ended', 'playing', 'error', '画面停住', '长时间未出画'])
const identifier = value => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(value) ? value : null
const number = value => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= Number.MAX_SAFE_INTEGER ? value : null
const mediaMessages = new Map([
  ['rtmp-published', 'RTMP publish started'],
  ['rtmp-unpublished', 'RTMP publish ended'],
  ['http-flv-client-backpressure', 'HTTP-FLV player queue reached the limit; skipped until the next keyframe'],
])

export function playbackDiagnostic(input) {
  if (input?.action !== 'video-playback-diagnostic' || typeof input.reason !== 'string' || input.reason.length > 4096) return null
  let value
  try { value = JSON.parse(input.reason) } catch { return null }
  if (!value || !events.has(value.event) || !identifier(value.pageId) || number(value.atMs) === null || !Number.isSafeInteger(value.generation) || value.generation < 1) return null
  const reason = reasons.has(value.reason) ? value.reason
    : /^(NetworkError|MediaError|OtherError):(Exception|HttpStatusCodeInvalid|ConnectingTimeout|EarlyEof|UnrecoverableEarlyEof|MediaMSEError|MediaFormatError|MediaCodecUnsupported)$/.test(value.reason) ? value.reason : 'unclassified'
  const record = { kind: 'playback', pageId: value.pageId, generation: value.generation, atMs: value.atMs, event: value.event, reason,
    workspace: ['devices', 'routes', 'flight', 'monitor'].includes(value.workspace) ? value.workspace : null,
    deviceId: identifier(value.deviceId), slot: Number.isInteger(value.slot) && value.slot >= 0 && value.slot <= 3 ? value.slot : null }
  for (const key of ['time', 'bufferedEnd', 'frames', 'readyState', 'width', 'height', 'mediaError']) record[key] = number(value[key])
  for (const key of ['hidden', 'paused', 'ended', 'seeking']) record[key] = typeof value[key] === 'boolean' ? value[key] : null
  return record
}

export function mediaDiagnostic(event) {
  const fixed = mediaMessages.get(event.kind)
  if (fixed !== undefined ? event.detail !== fixed : !['http-flv-viewer-open', 'http-flv-viewer-close', 'rtmp-video-gap'].includes(event.kind)) return null
  if (typeof event.detail !== 'string' || !/^[a-zA-Z0-9_;= .-]{1,512}$/.test(event.detail)) return null
  return { kind: event.kind, atMs: Date.now(), deviceId: identifier(event.deviceId), detail: event.detail }
}

// One writer, at most 512 waiting records and one 512-record batch; rotate at 8 MiB.
export function createPlaybackDiagnosticLog(directory, onError = () => {}) {
  const file = path.join(directory, 'playback.ndjson')
  const pending = []
  let writing = null
  let dropped = 0
  const drain = async () => {
    try {
      await mkdir(directory, { recursive: true })
      while (pending.length) {
        const batch = pending.splice(0, 512)
        const loss = dropped
        dropped = 0
        let bytes = 0
        try { bytes = (await stat(file)).size } catch (error) { if (error.code !== 'ENOENT') throw error }
        if (bytes >= 8 * 1024 * 1024) await rename(file, `${file}.previous`)
        const records = loss ? [{ kind: 'dropped', atMs: Date.now(), count: loss }, ...batch] : batch
        await appendFile(file, `${records.map(record => JSON.stringify(record)).join('\n')}\n`, 'utf8')
      }
    } catch {
      pending.length = 0
      try { onError() } catch { /* diagnostics cannot affect control or media */ }
    } finally { writing = null }
  }
  const record = value => {
    if (pending.length >= 512) { dropped++; return false }
    pending.push(value)
    if (writing === null) writing = drain()
    return true
  }
  return {
    recordPlayback: input => { const value = playbackDiagnostic(input); return value !== null && record(value) },
    recordMedia: event => {
      const value = mediaDiagnostic(event)
      if (value) record(value)
    },
    flush: async () => { while (writing !== null) await writing },
  }
}
