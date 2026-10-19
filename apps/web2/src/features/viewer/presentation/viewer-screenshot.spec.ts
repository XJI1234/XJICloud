import { afterEach, describe, expect, it, vi } from 'vitest'
import { saveViewerScreenshot } from './viewer-screenshot'

const image = { width: 1, height: 1, rgba: new Uint8Array([128, 128, 128, 255]) }
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers() })
function setup() {
  vi.stubGlobal('ImageData', class { constructor(public bytes: Uint8ClampedArray, public width: number, public height: number) {} })
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ putImageData: vi.fn() } as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => callback(new Blob(['png'])))
  const create = vi.fn(() => 'blob:viewer-test')
  const revoke = vi.fn()
  const BaseURL = URL
  vi.stubGlobal('URL', class extends BaseURL {
    static createObjectURL = create
    static revokeObjectURL = revoke
  })
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  return { create, revoke, click }
}

describe('viewer PNG download lifecycle', () => {
  it('releases its object URL and removes the temporary download anchor', async () => {
    vi.useFakeTimers()
    const { revoke, click } = setup()
    await saveViewerScreenshot(image, 'scene.ply', () => true)
    expect(click).toHaveBeenCalledOnce()
    expect(document.querySelector('a[download]')).toBeNull()
    await vi.advanceTimersByTimeAsync(1000)
    expect(revoke).toHaveBeenCalledWith('blob:viewer-test')
  })
  it('does not create a download after the caller has closed or replaced its model', async () => {
    const { create, click } = setup()
    await saveViewerScreenshot(image, 'stale.spz', () => false)
    expect(create).not.toHaveBeenCalled()
    expect(click).not.toHaveBeenCalled()
  })
  it('reports PNG encoding failure rather than pretending a download occurred', async () => {
    const { click } = setup()
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => callback(null))
    await expect(saveViewerScreenshot(image, 'scene.ply', () => true)).rejects.toThrow('PNG encoding failed')
    expect(click).not.toHaveBeenCalled()
  })
})
