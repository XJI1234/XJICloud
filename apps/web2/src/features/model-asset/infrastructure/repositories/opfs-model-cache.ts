import type { CachedModelFile } from '../../domain/entities/model-asset.entity'
import type { CloudModelCachePort } from '../../domain/repositories/cloud-model-cache.port'

const DIR_NAME = 'xjicloud-model-cache'
const INDEX_FILE = 'index.json'

type CacheIndex = Record<string, CachedModelFile>

async function openDir(): Promise<FileSystemDirectoryHandle | null> {
  const storage = globalThis.navigator?.storage
  if (!storage?.getDirectory) {
    return null
  }
  const root = await storage.getDirectory()
  return root.getDirectoryHandle(DIR_NAME, { create: true })
}

async function readIndex(dir: FileSystemDirectoryHandle): Promise<CacheIndex> {
  try {
    const handle = await dir.getFileHandle(INDEX_FILE)
    const file = await handle.getFile()
    const parsed = JSON.parse(await file.text()) as CacheIndex
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

async function writeIndex(dir: FileSystemDirectoryHandle, index: CacheIndex) {
  const handle = await dir.getFileHandle(INDEX_FILE, { create: true })
  const writable = await handle.createWritable()
  await writable.write(JSON.stringify(index))
  await writable.close()
}

function blobName(cacheKey: string) {
  return `${encodeURIComponent(cacheKey)}.bin`
}

export function createOpfsModelCache(): CloudModelCachePort {
  return {
    async get(cacheKey) {
      const dir = await openDir()
      if (!dir) {
        return null
      }
      try {
        const index = await readIndex(dir)
        if (!index[cacheKey]) {
          return null
        }
        const handle = await dir.getFileHandle(blobName(cacheKey))
        const file = await handle.getFile()
        return await file.arrayBuffer()
      } catch {
        return null
      }
    },
    async put(entry, bytes) {
      const dir = await openDir()
      if (!dir) {
        return
      }
      const handle = await dir.getFileHandle(blobName(entry.cacheKey), { create: true })
      const writable = await handle.createWritable()
      await writable.write(bytes)
      await writable.close()
      const index = await readIndex(dir)
      index[entry.cacheKey] = entry
      await writeIndex(dir, index)
    },
    async list() {
      const dir = await openDir()
      if (!dir) {
        return []
      }
      const index = await readIndex(dir)
      return Object.values(index)
    },
    async remove(cacheKeys) {
      const dir = await openDir()
      if (!dir || cacheKeys.length === 0) {
        return
      }
      const index = await readIndex(dir)
      for (const key of cacheKeys) {
        delete index[key]
        try {
          await dir.removeEntry(blobName(key))
        } catch {
          // Missing blob is still a successful index prune.
        }
      }
      await writeIndex(dir, index)
    },
  }
}
