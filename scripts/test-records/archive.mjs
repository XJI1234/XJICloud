import { appendFile, mkdir, rename, rm, writeFile } from 'node:fs/promises'
import { createHash, randomUUID } from 'node:crypto'
import { homedir } from 'node:os'
import path from 'node:path'

export const defaultRoot = () => process.env.XJI_TEST_RECORDS_ROOT || (process.platform === 'win32' ? 'D:/XJI-Test-Records' : path.join(homedir(), 'XJI-Test-Records'))
const privateKey = /^(authorization|cookie|password|passwd|secret|token|credential|apiKey|accessToken|refreshToken|endpoint|playbackUrl|localAddress)$/i
export function evidenceValue(value, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object') return typeof value === 'bigint' ? String(value) : value
  if (ArrayBuffer.isView(value)) {
    const bytes = Buffer.from(value.buffer, value.byteOffset, value.byteLength)
    return { byteLength: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex') }
  }
  if (seen.has(value)) return '[circular]'
  seen.add(value)
  const copied = Array.isArray(value) ? value.map(item => evidenceValue(item, seen))
    : Object.fromEntries(Object.entries(value).filter(([key]) => !privateKey.test(key)).map(([key, item]) => [key, evidenceValue(item, seen)]))
  seen.delete(value)
  return copied
}

const fileWrites = new Map()
export function writeAtomic(file, content) {
  const key = path.resolve(file)
  const previous = fileWrites.get(key) ?? Promise.resolve()
  const completed = previous.catch(() => {}).then(async () => {
    const temporary = `${file}.${randomUUID()}.tmp`
    let published = false
    try {
      await writeFile(temporary, content, 'utf8')
      for (let attempt = 0; ; attempt++) {
        try { await rename(temporary, file); published = true; break }
        catch (error) {
          // Windows readers and antivirus may hold the destination without delete sharing.
          if (!['EPERM', 'EBUSY', 'EACCES', 'ENOTEMPTY'].includes(error.code) || attempt >= 7) throw error
          await new Promise(resolve => setTimeout(resolve, 25 * (attempt + 1)))
        }
      }
    } finally { if (!published) await rm(temporary, { force: true }).catch(() => {}) }
  })
  fileWrites.set(key, completed)
  const clear = () => { if (fileWrites.get(key) === completed) fileWrites.delete(key) }
  void completed.then(clear, clear)
  return completed
}
export const writeJson = (file, value) => writeAtomic(file, `${JSON.stringify(value, null, 2)}\n`)

export async function createSession({ root = defaultRoot(), kind, label, metadata = {}, onError = () => console.error('[test-records] 写入失败，测试档案不完整') }) {
  if (!['live', 'automated', 'import'].includes(kind) || typeof label !== 'string' || !label.trim()) throw new Error('Invalid test session')
  const startedAtMs = Date.now()
  const sessionId = `${new Date(startedAtMs).toISOString().replace(/[:.]/g, '-')}-${randomUUID()}`
  const day = new Date(startedAtMs).toLocaleDateString('en-CA', { timeZone: 'Asia/Shanghai' })
  const directory = path.join(root, day, sessionId)
  await mkdir(directory, { recursive: true })
  await writeJson(path.join(directory, 'manifest.json'), {
    schemaVersion: 1, sessionId, kind, label, startedAtMs, timeZone: 'Asia/Shanghai', pid: process.pid,
    clock: { receivedAtMs: 'computer Unix milliseconds', elapsedMs: 'computer monotonic clock', timestampMillis: 'phone clock; never subtract from computer time' },
    metadata: evidenceValue(metadata),
  })
  await writeFile(path.join(directory, 'events.ndjson'), '', { flag: 'wx' })
  const pending = []
  let sequence = 0
  let bufferedBytes = 0
  let bufferedCount = 0
  let dropped = 0
  let unreportedDrops = 0
  let writeErrors = 0
  let written = 0
  let writing = null
  let closing = false
  let finished = null
  const health = () => ({ schemaVersion: 1, sessionId, updatedAtMs: Date.now(), written, dropped, writeErrors, bufferedBytes, bufferedCount })
  const reportError = () => { writeErrors++; try { onError() } catch { /* evidence failure cannot affect control */ } }
  const pump = () => {
    if (writing !== null || !pending.length) return
    writing = (async () => {
      // Yield before disk work; producers only enqueue bounded, already-serialized records.
      await Promise.resolve()
      while (pending.length) {
        const batch = pending.splice(0, 64)
        let ok = false
        try {
          await appendFile(path.join(directory, 'events.ndjson'), batch.map(item => item.line).join(''), 'utf8')
          written += batch.length
          ok = true
        } catch { reportError() }
        for (const item of batch) {
          bufferedBytes -= item.bytes
          bufferedCount--
          item.resolve?.(ok)
        }
      }
      try { await writeJson(path.join(directory, 'health.json'), health()) } catch { reportError() }
    })().finally(() => { writing = null; pump() })
  }
  const enqueue = (eventKind, data, resolve) => {
    if (closing) { resolve?.(false); return false }
    let line
    try { line = `${JSON.stringify({ schemaVersion: 1, sessionId, sequence: sequence + 1, receivedAtMs: Date.now(), kind: eventKind, data: evidenceValue(data) })}\n` } catch {
      dropped++; unreportedDrops++; resolve?.(false); return false
    }
    const bytes = Buffer.byteLength(line)
    if (bytes > 2 * 1024 * 1024 || bufferedCount >= 512 || bufferedBytes + bytes > 8 * 1024 * 1024) {
      dropped++; unreportedDrops++; resolve?.(false); return false
    }
    sequence++
    bufferedBytes += bytes
    bufferedCount++
    pending.push({ line, bytes, resolve })
    pump()
    return true
  }
  const flush = async () => {
    while (writing !== null) await writing
    if (unreportedDrops > 0 && !closing) {
      const count = unreportedDrops
      unreportedDrops = 0
      enqueue('recording-gap', { dropped: count, reason: 'bounded-buffer-or-invalid-record' })
      while (writing !== null) await writing
    }
    return writeErrors === 0 && dropped === 0
  }
  const session = {
    sessionId, directory,
    record: (eventKind, data) => enqueue(eventKind, data),
    persist: (eventKind, data) => new Promise(resolve => { enqueue(eventKind, data, resolve) }),
    flush,
    health,
    finish: (outcome, details = {}) => {
      if (finished !== null) return finished
      finished = (async () => {
        enqueue('session-finished', { outcome })
        await flush()
        closing = true
        // A producer may enqueue while flush yields. Drain it before writing the final result.
        while (writing !== null) await writing
        const endedAtMs = Date.now()
        const result = { schemaVersion: 1, sessionId, ...evidenceValue(details), outcome: outcome === 'passed' && (dropped || writeErrors) ? 'incomplete' : outcome,
          requestedOutcome: outcome, recordingIntegrity: dropped || writeErrors ? 'incomplete' : 'no-recorder-errors', endedAtMs, durationMs: endedAtMs - startedAtMs, written, dropped, writeErrors }
        await writeJson(path.join(directory, 'result.json'), result)
        return result
      })()
      return finished
    },
  }
  enqueue('session-started', { kind, label })
  return session
}
