import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { afterEach, describe, expect, it, vi } from 'vitest'

// Exercise the map code actually shipped by the embedded operator console.
const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')
const mapCode = bundle.slice(
  bundle.indexOf('  // src/production/operator-console/renderer/cesium-config.ts'),
  bundle.indexOf('  function showRoutePreview('),
)

function createMapHarness(pendingModels = false) {
  const listeners: Array<() => void> = []
  const moveStartListeners: Array<() => void> = []
  const renderedListeners: Array<() => void> = []
  const visibilityListeners: Array<() => void> = []
  const host = new EventTarget()
  const mapDocument = {
    baseURI: 'http://localhost:5176/sky-operator/index.html', hidden: false,
    addEventListener: (_event: string, listener: () => void) => visibilityListeners.push(listener),
  }
  let viewerOptions: { targetFrameRate?: number; msaaSamples?: number } = {}
  const tilesets: Array<{ show: boolean }> = []
  const requestRender = vi.fn()
  const mapViewer = {
    isDestroyed: () => false,
    resize: vi.fn(),
    useDefaultRenderLoop: true,
    targetFrameRate: undefined as number | undefined,
    camera: {
      viewMatrix: [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1],
      positionCartographic: { height: 4000 },
      setView: vi.fn(),
      moveEnd: { addEventListener: (listener: () => void) => listeners.push(listener) },
      moveStart: { addEventListener: (listener: () => void) => moveStartListeners.push(listener) },
    },
    imageryLayers: { removeAll: vi.fn(), addImageryProvider: vi.fn() },
    scene: {
      globe: {},
      screenSpaceCameraController: {},
      primitives: { add: (tileset: { show: boolean }) => { tilesets.push(tileset); return tileset } },
      requestRender,
      postRender: { addEventListener: (listener: () => void) => renderedListeners.push(listener) },
    },
  }
  const fromUrl = vi.fn((_url: string, options: { show?: boolean }) => pendingModels
    ? new Promise<never>(() => {})
    : Promise.resolve({ show: options.show ?? true }))
  const api = runInNewContext(`${mapCode}\n({ ensureRouteMap, setRouteMapVisible })`, {
    document: mapDocument,
    setTimeout: (callback: () => void, delay: number) => setTimeout(callback, delay),
    clearTimeout: (timer: ReturnType<typeof setTimeout>) => clearTimeout(timer),
    URL,
    Cesium: {
      Ion: {},
      Viewer: function (_host: object, options: typeof viewerOptions) {
        viewerOptions = options
        mapViewer.targetFrameRate = options.targetFrameRate
        return mapViewer
      },
      UrlTemplateImageryProvider: function () {},
      Credit: function () {},
      Color: { fromCssColorString: (color: string) => color },
      Cartesian3: { fromDegrees: vi.fn() },
      Matrix4: {
        clone: (value: number[], result: number[] = []) => Object.assign(result, value),
        equalsEpsilon: (a: number[], b: number[], epsilon: number) => a.every((v, i) => Math.abs(v - b[i]!) <= epsilon),
      },
      Cesium3DTileset: { fromUrl },
    },
  }) as {
    ensureRouteMap(host: object): Promise<void>
    setRouteMapVisible(visible: boolean): void
  }
  return {
    api, host, mapViewer, tilesets, requestRender, fromUrl,
    options: () => viewerOptions,
    startMoving(userInput = true) {
      if (userInput) host.dispatchEvent(new Event('wheel'))
      moveStartListeners.forEach((listener) => listener())
    },
    renderFrame() { renderedListeners.forEach((listener) => listener()) },
    setHidden(hidden: boolean) {
      mapDocument.hidden = hidden
      visibilityListeners.forEach((listener) => listener())
    },
    moveToHeight(height: number) {
      mapViewer.camera.positionCartographic.height = height
      listeners.forEach((listener) => listener())
    },
  }
}

describe('embedded operator map loading', () => {
  afterEach(() => { vi.useRealTimers() })
  it('makes the map usable without waiting for optional city metadata', async () => {
    const { api } = createMapHarness(true)
    let ready = false
    void api.ensureRouteMap(new EventTarget()).then(() => { ready = true })
    await Promise.resolve()
    await Promise.resolve()
    expect(ready).toBe(true)
  })

  it('keeps full-detail buildings enabled in the overview and after zooming', async () => {
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    await vi.waitFor(() => expect(harness.tilesets).toHaveLength(2))
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)

    harness.moveToHeight(1000)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    harness.moveToHeight(4000)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
  })

  it('keeps late city metadata enabled even during overview interaction', async () => {
    const harness = createMapHarness()
    const ready = harness.api.ensureRouteMap(harness.host)
    harness.api.setRouteMapVisible(true)
    harness.startMoving()
    harness.moveToHeight(4000)
    await ready
    await vi.waitFor(() => expect(harness.tilesets).toHaveLength(2))
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
  })

  it('keeps the base map usable when a city fails to load', async () => {
    const harness = createMapHarness()
    harness.fromUrl.mockRejectedValueOnce(new Error('city metadata unavailable'))
    await harness.api.ensureRouteMap(harness.host)
    await vi.waitFor(() => expect(harness.tilesets).toHaveLength(1))
    expect(harness.tilesets[0]!.show).toBe(true)
    harness.moveToHeight(1000)
    expect(harness.tilesets[0]!.show).toBe(true)
  })

  it('does not force a new frame on every unchanged workspace poll', async () => {
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(false)
    harness.api.setRouteMapVisible(true)
    expect(harness.requestRender).toHaveBeenCalled()
    harness.requestRender.mockClear()
    harness.mapViewer.resize.mockClear()
    harness.api.setRouteMapVisible(true)
    expect(harness.requestRender).not.toHaveBeenCalled()
    expect(harness.mapViewer.resize).not.toHaveBeenCalled()

    harness.api.setRouteMapVisible(false)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(false)
    harness.api.setRouteMapVisible(true)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(true)
    expect(harness.requestRender).toHaveBeenCalledOnce()
  })

  it('allows 60 FPS interaction with the existing geometry rendering settings', async () => {
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    expect(harness.options().targetFrameRate).toBe(60)
    expect(harness.options().msaaSamples).toBe(1)
  })

  it('keeps city geometry enabled throughout camera movement', async () => {
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    await vi.waitFor(() => expect(harness.tilesets).toHaveLength(2))
    harness.moveToHeight(1000)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    harness.startMoving()
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    harness.moveToHeight(1000)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
  })

  it('reduces idle updates and immediately wakes on map input', async () => {
    vi.useFakeTimers()
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    harness.api.setRouteMapVisible(true)
    harness.renderFrame()
    await vi.advanceTimersByTimeAsync(1500)
    expect(harness.mapViewer.targetFrameRate).toBe(5)
    harness.host.dispatchEvent(new Event('wheel'))
    expect(harness.mapViewer.targetFrameRate).toBe(60)
    await vi.advanceTimersByTimeAsync(1500)
    expect(harness.mapViewer.targetFrameRate).toBe(5)
    harness.renderFrame()
    expect(harness.mapViewer.targetFrameRate).toBe(5)
  })

  it('keeps buildings visible during automatic collision correction', async () => {
    vi.useFakeTimers()
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    await vi.advanceTimersByTimeAsync(0)
    harness.api.setRouteMapVisible(true)
    harness.moveToHeight(1000)
    await vi.advanceTimersByTimeAsync(1500)
    harness.startMoving(false)
    harness.moveToHeight(1000)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    expect(harness.mapViewer.targetFrameRate).toBe(5)
  })

  it('does not prolong high-frequency updates for imagery redraws after a stationary click', async () => {
    vi.useFakeTimers()
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    harness.api.setRouteMapVisible(true)
    harness.host.dispatchEvent(new Event('pointerdown'))
    for (let i = 0; i < 6; i++) {
      await vi.advanceTimersByTimeAsync(200)
      harness.renderFrame()
    }
    expect(harness.mapViewer.targetFrameRate).toBe(5)
  })

  it('keeps long camera interactions smooth until actual motion stops', async () => {
    vi.useFakeTimers()
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    harness.api.setRouteMapVisible(true)
    harness.startMoving()
    for (let i = 0; i < 6; i++) {
      await vi.advanceTimersByTimeAsync(400)
      harness.mapViewer.camera.viewMatrix[12]! += 1
      harness.renderFrame()
      expect(harness.mapViewer.targetFrameRate).toBe(60)
    }
    await vi.advanceTimersByTimeAsync(1200)
    expect(harness.mapViewer.targetFrameRate).toBe(5)
  })

  it('keeps buildings enabled during a click that does not move the camera', async () => {
    vi.useFakeTimers()
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    await vi.advanceTimersByTimeAsync(0)
    harness.api.setRouteMapVisible(true)
    harness.moveToHeight(1000)
    harness.host.dispatchEvent(new Event('pointerdown'))
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    await vi.advanceTimersByTimeAsync(1500)
    expect(harness.tilesets.every((tileset) => tileset.show)).toBe(true)
    expect(harness.mapViewer.targetFrameRate).toBe(5)
  })

  it('stops the render loop while the browser page is hidden', async () => {
    const harness = createMapHarness()
    await harness.api.ensureRouteMap(harness.host)
    harness.api.setRouteMapVisible(true)
    harness.setHidden(true)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(false)
    harness.setHidden(false)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(true)

    harness.api.setRouteMapVisible(false)
    harness.setHidden(true)
    harness.setHidden(false)
    expect(harness.mapViewer.useDefaultRenderLoop).toBe(false)
  })
})
