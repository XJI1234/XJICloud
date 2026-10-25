import type { CachedModelFile } from '../../domain/entities/model-asset.entity'
import type { CloudModelCachePort } from '../../domain/repositories/cloud-model-cache.port'

// Legacy shared cache is intentionally hidden; only an authenticated account can open v2.
const DIR_NAME = 'xjicloud-model-cache-v2'
const INDEX_FILE = 'index.json'
type RecordEntry = CachedModelFile & { blob: string }
type CacheIndex = Record<string, RecordEntry>

async function readIndex(dir: FileSystemDirectoryHandle): Promise<CacheIndex> {
  try {
    const file = await (await dir.getFileHandle(INDEX_FILE)).getFile()
    const parsed: unknown = JSON.parse(await file.text())
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('Invalid cache index')
    for (const [key, value] of Object.entries(parsed)) {
      const item = value as RecordEntry
      if (!item || item.cacheKey !== key || typeof item.modelId !== 'string' ||
          typeof item.fileName !== 'string' || typeof item.updatedAt !== 'string' || typeof item.cachedAt !== 'string' ||
          !Number.isFinite(item.sizeBytes) || item.sizeBytes < 0 || (item.versionId !== null && typeof item.versionId !== 'string') ||
          typeof item.blob !== 'string' || !/^[a-zA-Z0-9-]+\.bin$/.test(item.blob)) throw new Error('Invalid cache entry')
    }
    return parsed as CacheIndex
  } catch (error) {
    if (error instanceof DOMException && error.name === 'NotFoundError') return {}
    throw error
  }
}

async function writeIndex(dir: FileSystemDirectoryHandle, index: CacheIndex) {
  const writable = await (await dir.getFileHandle(INDEX_FILE, { create: true })).createWritable()
  try { await writable.write(JSON.stringify(index)); await writable.close() }
  catch (error) { await writable.abort().catch(() => undefined); throw error }
}

export function createOpfsModelCache(userId: string | null): CloudModelCachePort {
  // Without Web Locks, bypass caching. A per-tab mutex cannot protect an OPFS index.
  async function locked<T>(fallback: T, action: (dir: FileSystemDirectoryHandle) => Promise<T>): Promise<T> {
    const storage = globalThis.navigator?.storage
    const locks = globalThis.navigator?.locks
    if (!userId || !storage?.getDirectory || !locks?.request) return fallback
    const namespace = encodeURIComponent(userId)
    return locks.request(`${DIR_NAME}:${namespace}`, async () => {
      const root = await storage.getDirectory()
      const parent = await root.getDirectoryHandle(DIR_NAME, { create: true })
      const dir = await parent.getDirectoryHandle(namespace, { create: true })
      return action(dir)
    })
  }

  async function pruneOrphans(dir: FileSystemDirectoryHandle, index: CacheIndex) {
    const used = new Set(Object.values(index).map(item => item.blob))
    // Interrupted writes leave at most an orphan, recovered on the next mutation.
    for await (const [name] of dir.entries()) {
      if (name.endsWith('.bin') && !used.has(name)) await dir.removeEntry(name)
    }
  }

  return {
    get: key => locked<ArrayBuffer | null>(null, async dir => {
      const entry = (await readIndex(dir))[key]
      if (!entry) return null
      try { return await (await (await dir.getFileHandle(entry.blob)).getFile()).arrayBuffer() }
      catch (error) {
        if (error instanceof DOMException && error.name === 'NotFoundError') return null
        throw error
      }
    }),
    put: (entry, bytes) => locked<void>(undefined, async dir => {
      const index = await readIndex(dir)
      await pruneOrphans(dir, index)
      const blob = `${crypto.randomUUID()}.bin`
      const writable = await (await dir.getFileHandle(blob, { create: true })).createWritable()
      try {
        await writable.write(bytes)
        await writable.close()
        index[entry.cacheKey] = { ...entry, blob }
        await writeIndex(dir, index)
      } catch (error) {
        await writable.abort().catch(() => undefined)
        await dir.removeEntry(blob).catch(() => undefined)
        throw error
      }
      await pruneOrphans(dir, index)
    }),
    list: () => locked<CachedModelFile[]>([], async dir => Object.values(await readIndex(dir)).map(({ blob: _blob, ...entry }) => entry)),
    remove: keys => locked<void>(undefined, async dir => {
      const index = await readIndex(dir)
      for (const key of keys) delete index[key]
      // Commit metadata before deleting bytes; a failed index commit preserves the old entry.
      await writeIndex(dir, index)
      await pruneOrphans(dir, index)
    }),
  }
}
