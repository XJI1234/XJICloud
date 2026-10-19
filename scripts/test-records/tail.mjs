import { open, stat } from 'node:fs/promises'
import { StringDecoder } from 'node:string_decoder'

export async function createJsonTail({ file, kind, record, fromEnd = true, chunkBytes = 1024 * 1024 }) {
  let offset = 0
  let identity = null
  let carry = ''
  let decoder = new StringDecoder('utf8')
  try { const info = await stat(file); offset = fromEnd ? info.size : 0; identity = `${info.dev}:${info.ino}:${info.birthtimeMs}` } catch (error) { if (error.code !== 'ENOENT') throw error }
  return async () => {
    let handle
    try {
      handle = await open(file, 'r')
      const info = await handle.stat()
      const nextIdentity = `${info.dev}:${info.ino}:${info.birthtimeMs}`
      if ((identity !== null && nextIdentity !== identity) || info.size < offset) {
        record('recording-gap', { dropped: 1, reason: 'legacy-file-truncated-or-rotated', source: kind })
        offset = 0; carry = ''; decoder = new StringDecoder('utf8')
      }
      identity = nextIdentity
      if (info.size <= offset) return
      const buffer = Buffer.alloc(Math.min(chunkBytes, info.size - offset))
      const { bytesRead } = await handle.read(buffer, 0, buffer.length, offset)
      offset += bytesRead
      const parts = (carry + decoder.write(buffer.subarray(0, bytesRead))).split('\n')
      carry = parts.pop()
      for (const line of parts) {
        if (!line.trim()) continue
        try { record(kind, JSON.parse(line)) }
        catch { record('recording-gap', { dropped: 1, reason: 'legacy-line-invalid', source: kind }) }
      }
      if (carry.length > 2 * 1024 * 1024) { carry = ''; record('recording-gap', { dropped: 1, reason: 'legacy-line-too-large', source: kind }) }
    } catch (error) { if (error.code !== 'ENOENT') record('source-unavailable', { source: kind, code: error.code }) }
    finally { await handle?.close() }
  }
}
