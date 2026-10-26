import { afterEach, describe, expect, it, vi } from 'vitest'
import { createOpfsModelCache } from './opfs-model-cache'
import type { CachedModelFile } from '../../domain/entities/model-asset.entity'

function fakeOpfs() {
  let failIndex = false
  function directory() {
    const files = new Map<string, string | ArrayBuffer>()
    const children = new Map<string, ReturnType<typeof directory>>()
    return {
      files,
      async getDirectoryHandle(name: string) {
        if (!children.has(name)) children.set(name, directory())
        return children.get(name)!
      },
      async getFileHandle(name: string, options?: { create?: boolean }) {
        if (!files.has(name) && !options?.create) throw new DOMException('missing', 'NotFoundError')
        return {
          async getFile() { const bytes = files.get(name); return { text: async () => bytes as string, arrayBuffer: async () => bytes as ArrayBuffer } },
          async createWritable() {
            let pending: string | ArrayBuffer
            return { async write(bytes: string | ArrayBuffer) { await Promise.resolve(); pending = bytes },
              async close() { if (name === 'index.json' && failIndex) throw new Error('quota'); files.set(name, pending) },
              async abort() {},
            }
          },
        }
      },
      async removeEntry(name: string) { files.delete(name) },
      async *entries() { for (const name of files.keys()) yield [name, {}] as const },
    }
  }
  const root = directory()
  const tails = new Map<string, Promise<unknown>>()
  const locks = { request: async <T>(name: string, action: () => Promise<T>) => {
    const previous = tails.get(name) ?? Promise.resolve()
    const next = previous.catch(() => undefined).then(action)
    tails.set(name, next)
    return next
  } }
  vi.stubGlobal('navigator', { storage: { getDirectory: async () => root }, locks })
  return { root, breakIndex: (value: boolean) => { failIndex = value } }
}
const entry = (id: string): CachedModelFile => ({ cacheKey: `live:${id}`, modelId: id, versionId: null, fileName: `${id}.ply`, sizeBytes: 1, updatedAt: 'now', cachedAt: 'now' })
afterEach(() => vi.unstubAllGlobals())
describe('account cache and atomic index', () => {
  it('isolates accounts and disables anonymous access', async () => {
    fakeOpfs()
    const a = createOpfsModelCache('a'), b = createOpfsModelCache('b'), anonymous = createOpfsModelCache(null)
    await a.put(entry('m'), new ArrayBuffer(1))
    expect(await b.list()).toEqual([])
    expect(await b.get('live:m')).toBeNull()
    await b.remove(['live:m'])
    expect(await a.list()).toHaveLength(1)
    expect(await anonymous.list()).toEqual([])
  })
  it('preserves simultaneous writes across independent adapters and serializes removal', async () => {
    fakeOpfs()
    const one = createOpfsModelCache('a'), two = createOpfsModelCache('a')
    await Promise.all([one.put(entry('x'), new ArrayBuffer(1)), two.put(entry('y'), new ArrayBuffer(2))])
    expect((await one.list()).map(item => item.modelId).sort()).toEqual(['x', 'y'])
    await Promise.all([one.put(entry('z'), new ArrayBuffer(3)), two.remove(['live:x'])])
    expect((await one.list()).map(item => item.modelId).sort()).toEqual(['y', 'z'])
  })
  it('preserves old metadata and bytes when a new index cannot commit', async () => {
    const opfs = fakeOpfs(), cache = createOpfsModelCache('a')
    await cache.put(entry('m'), new Uint8Array([1]).buffer)
    opfs.breakIndex(true)
    await expect(cache.put({ ...entry('m'), updatedAt: 'new' }, new Uint8Array([2]).buffer)).rejects.toThrow('quota')
    opfs.breakIndex(false)
    expect(new Uint8Array((await cache.get('live:m'))!)).toEqual(new Uint8Array([1]))
    expect((await cache.list())[0]?.updatedAt).toBe('now')
  })
  it('rejects a corrupt index instead of overwriting its files with an empty index', async () => {
    const { root } = fakeOpfs()
    const dir = await (await root.getDirectoryHandle('xjicloud-model-cache-v2')).getDirectoryHandle('a')
    dir.files.set('index.json', '{bad')
    const cache = createOpfsModelCache('a')
    await expect(cache.put(entry('m'), new ArrayBuffer(1))).rejects.toThrow()
    expect(dir.files.size).toBe(1)
  })
  it('bypasses OPFS when cross-tab locking is unavailable', async () => {
    const getDirectory = vi.fn()
    vi.stubGlobal('navigator', { storage: { getDirectory } })
    const cache = createOpfsModelCache('a')
    await cache.put(entry('m'), new ArrayBuffer(1))
    expect(await cache.list()).toEqual([])
    expect(getDirectory).not.toHaveBeenCalled()
  })
})
