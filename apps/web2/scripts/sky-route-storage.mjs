import { createHash, randomUUID } from 'node:crypto'
import { lstat, mkdir, open, readFile, rename, rm } from 'node:fs/promises'
import { homedir } from 'node:os'
import path from 'node:path'
import { RouteLibrary } from '@xjicloud/sky-command-sdk/routes'

const maxFileBytes = 104_857_600
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex')
export const routeStorageRoot = () => process.env.SKY_COMMAND_ROUTES_DIR
  || (process.env.LOCALAPPDATA ? path.join(process.env.LOCALAPPDATA, 'Sky Command', 'routes') : path.join(homedir(), '.sky-command', 'routes'))
const unwrap = result => result?.ok === true && result.value !== undefined ? unwrap(result.value) : result
const imported = result => unwrap(result)?.status === 'imported'
const rejected = result => {
  if (result?.ok === false) return true
  if (result?.ok === true && result.value !== undefined) return rejected(result.value)
  return false
}
const extension = name => typeof name === 'string' && /\.(kml|kmz)$/i.test(name) ? path.extname(name).toLowerCase() : null
const validEntry = entry => entry && /^[0-9a-f]{64}$/.test(entry.sha256)
  && typeof entry.fileName === 'string' && extension(entry.fileName) !== null
  && !/[\\/:\x00-\x1f\x7f]/.test(entry.fileName) && entry.fileName.trim().length > 0
  && Number.isSafeInteger(entry.sizeBytes) && entry.sizeBytes > 0 && entry.sizeBytes <= maxFileBytes
  && typeof entry.importedAt === 'string' && Number.isFinite(Date.parse(entry.importedAt))

async function readRegular(file, maximum) {
  const info = await lstat(file)
  if (!info.isFile() || info.isSymbolicLink() || info.size > maximum) throw new Error('INVALID_STORED_FILE')
  const bytes = await readFile(file)
  if (bytes.length > maximum) throw new Error('INVALID_STORED_FILE')
  return bytes
}

// The catalog is published only after the complete temporary file has been flushed.
async function writeAtomic(file, bytes) {
  try { const info = await lstat(file); if (!info.isFile() || info.isSymbolicLink()) throw new Error('INVALID_STORAGE_TARGET') }
  catch (error) { if (error.code !== 'ENOENT') throw error }
  const temporary = `${file}.${randomUUID()}.tmp`
  let published = false
  try {
    const handle = await open(temporary, 'wx')
    try { await handle.writeFile(bytes); await handle.sync() } finally { await handle.close() }
    for (let attempt = 0; ; attempt++) {
      try { await rename(temporary, file); published = true; break }
      catch (error) {
        if (!['EPERM', 'EBUSY', 'EACCES'].includes(error.code) || attempt >= 5) throw error
        await new Promise(resolve => setTimeout(resolve, 25 * (attempt + 1)))
      }
    }
  } finally { if (!published) await rm(temporary, { force: true }) }
}

export async function createRouteStorage({ gateway, directory = routeStorageRoot(), onError = code => console.error(`[routes] ${code}`) }) {
  if (!path.isAbsolute(directory)) throw new Error('Route storage directory must be absolute')
  const indexFile = path.join(directory, 'index.json')
  const routeIds = new Map()
  let entries = []
  let phase = 'ready'
  let failedCount = 0
  let restoredCount = 0
  let queue = Promise.resolve()
  let closed = false
  let closing = false
  const report = code => { try { onError(code) } catch { /* diagnostics cannot change business results */ } }
  const degraded = () => { phase = 'degraded'; failedCount++; report('ROUTE_STORAGE_READ_FAILED') }
  const snapshot = () => ({ phase, savedCount: entries.length, restoredCount, failedCount })
  const fileFor = entry => path.join(directory, `${entry.sha256}${extension(entry.fileName)}`)
  const commit = next => {
    if (next.length > 4096) throw new Error('ROUTE_CATALOG_LIMIT')
    const value = JSON.stringify({ schemaVersion: 1, routes: next }, null, 2) + '\n'
    if (Buffer.byteLength(value) > 4 * 1024 * 1024) throw new Error('ROUTE_CATALOG_LIMIT')
    return writeAtomic(indexFile, value)
  }
  const failure = method => method === 'route.import'
    ? { ok: true, value: { ok: true, value: { status: 'rejected', error: {
      code: 'ROUTE_STORAGE_FAILED', message: phase === 'degraded'
        ? '本机航线资料恢复不完整，已保护原文件，请检查航线存储目录后重启。'
        : '航线未能保存到本机，请检查磁盘空间和目录权限后重试。',
    } } } }
    : { ok: true, value: { ok: false, code: 'ROUTE_STORAGE_FAILED' } }
  const rollback = async previous => {
    try { await commit(previous) }
    catch { degraded(); report('ROUTE_STORAGE_ROLLBACK_FAILED') }
  }

  try {
    await mkdir(directory, { recursive: true })
    const root = await lstat(directory)
    if (!root.isDirectory() || root.isSymbolicLink()) throw new Error('INVALID_STORAGE_ROOT')
    let saved
    try { saved = JSON.parse((await readRegular(indexFile, 4 * 1024 * 1024)).toString('utf8')) }
    catch (error) { if (error.code !== 'ENOENT') throw error; saved = { schemaVersion: 1, routes: [] } }
    if (saved.schemaVersion !== 1 || !Array.isArray(saved.routes) || saved.routes.length > 4096) throw new Error('INVALID_ROUTE_CATALOG')
    entries = saved.routes
    const seen = new Set()
    let selected = null
    for (const entry of entries) {
      try {
        if (!validEntry(entry) || seen.has(entry.sha256)) throw new Error('INVALID_ROUTE_ENTRY')
        seen.add(entry.sha256)
        const bytes = await readRegular(fileFor(entry), maxFileBytes)
        if (bytes.length !== entry.sizeBytes || sha256(bytes) !== entry.sha256) throw new Error('ROUTE_INTEGRITY_FAILED')
        const result = await gateway.invoke('route.import', { fileName: entry.fileName, bytes: new Uint8Array(bytes) })
        if (!imported(result)) throw new Error('ROUTE_RESTORE_REJECTED')
        selected = unwrap(result).route.routeId
        routeIds.set(selected, entry.sha256)
        restoredCount++
      } catch { degraded() }
    }
    if (selected !== null && rejected(await gateway.invoke('route.select', { routeId: selected }))) degraded()
  } catch { degraded() }

  const importRoute = async input => {
    if (phase !== 'ready' || closed) return failure('route.import')
    // Separate SDK validator: an invalid file cannot enter either the disk catalog or live workflow.
    const validator = RouteLibrary.create()
    if (!validator.ok) return failure('route.import')
    let checked
    try { checked = await validator.value.importFile(input) } finally { validator.value.clear() }
    if (checked.status !== 'imported') return gateway.invoke('route.import', input)
    const entry = entries.find(item => item.sha256 === checked.route.sha256)
      ?? { sha256: checked.route.sha256, fileName: checked.route.displayName, sizeBytes: input.bytes.length, importedAt: checked.route.importedAt }
    const previous = entries
    const next = entries.some(item => item.sha256 === entry.sha256) ? entries : [...entries, entry]
    try {
      let stored = null
      try { stored = await readRegular(fileFor(entry), maxFileBytes) } catch (error) { if (error.code !== 'ENOENT') throw error }
      if (stored !== null && sha256(stored) !== entry.sha256) throw new Error('ROUTE_INTEGRITY_FAILED')
      if (stored === null) await writeAtomic(fileFor(entry), input.bytes)
      await commit(next)
    } catch { report('ROUTE_STORAGE_WRITE_FAILED'); return failure('route.import') }
    let result
    try { result = await gateway.invoke('route.import', { fileName: entry.fileName, bytes: input.bytes }) }
    catch (error) { await rollback(previous); throw error }
    if (!imported(result)) { await rollback(previous); return result }
    entries = next
    routeIds.set(unwrap(result).route.routeId, entry.sha256)
    return result
  }
  const removeRoute = async input => {
    if (phase !== 'ready' || closed) return failure('route.remove')
    const digest = routeIds.get(input?.routeId)
    if (digest === undefined) return gateway.invoke('route.remove', input)
    const previous = entries
    const next = entries.filter(entry => entry.sha256 !== digest)
    try { await commit(next) } catch { report('ROUTE_STORAGE_WRITE_FAILED'); return failure('route.remove') }
    let result
    try { result = await gateway.invoke('route.remove', input) }
    catch (error) { await rollback(previous); throw error }
    if (rejected(result)) { await rollback(previous); return result }
    entries = next
    routeIds.delete(input.routeId)
    return result
  }
  return {
    snapshot,
    invoke: (method, input) => {
      if (!['route.import', 'route.remove'].includes(method)) return gateway.invoke(method, input)
      if (closing || closed) return Promise.resolve(failure(method))
      // Preserve the original gateway's exact input validation and defensive byte copy.
      if (!input || Object.keys(input).length !== (method === 'route.import' ? 2 : 1)
        || (method === 'route.import' ? typeof input.fileName !== 'string' || !(input.bytes instanceof Uint8Array) || input.bytes.length > maxFileBytes
          : typeof input.routeId !== 'string')) return gateway.invoke(method, input)
      const copied = method === 'route.import' ? { fileName: input.fileName, bytes: new Uint8Array(input.bytes) } : { routeId: input.routeId }
      const operation = queue.then(() => method === 'route.import' ? importRoute(copied) : removeRoute(copied))
      queue = operation.catch(() => {})
      return operation
    },
    close: async () => { closing = true; await queue; closed = true },
  }
}
