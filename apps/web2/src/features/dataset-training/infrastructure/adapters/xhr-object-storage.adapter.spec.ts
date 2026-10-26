import { afterEach, describe, expect, it, vi } from 'vitest'
import { createXhrObjectStorage } from './xhr-object-storage.adapter'

afterEach(() => vi.unstubAllGlobals())
describe('OSS request lifecycle', () => {
  it.each(['abort', 'load', 'error'])('settles %s and removes its abort listener', async event => {
    const owner = new AbortController()
    const remove = vi.spyOn(owner.signal, 'removeEventListener')
    const xhr = { open: vi.fn(), setRequestHeader: vi.fn(), upload: { onprogress: null }, send: vi.fn(), status: 200,
      onload: null as null | (() => void), onerror: null as null | (() => void), onabort: null as null | (() => void),
      abort() { this.onabort?.() },
    }
    vi.stubGlobal('XMLHttpRequest', class { constructor() { return xhr } })
    const pending = createXhrObjectStorage().put('https://oss.invalid/file', new Blob(['x']), 'image/jpeg', undefined, owner.signal)
    if (event === 'abort') owner.abort()
    else if (event === 'load') xhr.onload?.()
    else xhr.onerror?.()
    if (event === 'load') await expect(pending).resolves.toBeUndefined()
    else await expect(pending).rejects.toMatchObject({ code: event === 'abort' ? 'OPERATION_CANCELLED' : 'OSS_UPLOAD_FAILED' })
    expect(remove).toHaveBeenCalledWith('abort', expect.any(Function))
    expect(xhr.onload).toBeNull()
    expect(xhr.upload.onprogress).toBeNull()
  })
})
