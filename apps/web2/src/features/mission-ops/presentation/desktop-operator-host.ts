const controlUrl = 'http://127.0.0.1:8767/invoke'
let relayHint = 'ws://127.0.0.1:8080/relay'

function encode(value: unknown): unknown {
  if (value instanceof Uint8Array) {
    let binary = ''
    for (const byte of value) binary += String.fromCharCode(byte)
    return { __skyBytes: btoa(binary) }
  }
  if (Array.isArray(value)) return value.map(encode)
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, encode(item)]))
  }
  return value
}

async function selectRouteFile(): Promise<{ ok: boolean; fileName?: string; bytes?: Uint8Array }> {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.kml,.kmz,application/vnd.google-earth.kml+xml,application/vnd.google-earth.kmz'
  const picked = await new Promise<File | null>((resolve) => {
    input.addEventListener('change', () => resolve(input.files?.[0] ?? null), { once: true })
    input.addEventListener('cancel', () => resolve(null), { once: true })
    input.click()
  })
  if (!picked) return { ok: false }
  return { ok: true, fileName: picked.name, bytes: new Uint8Array(await picked.arrayBuffer()) }
}

async function invoke(method: unknown, input?: unknown) {
  const name = typeof method === 'string' ? method : ''
  try {
    const response = await fetch(controlUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ method: name, ...(input === undefined ? {} : { input: encode(input) }) }),
    })
    return await response.json()
  } catch {
    return { ok: false, code: 'DEPENDENCY_FAILURE' }
  }
}

const listed = await fetch('/sky-relay-hints').then((response) => response.json()).catch(() => [])
if (Array.isArray(listed) && listed.every((item) => typeof item === 'string' && item.startsWith('ws://'))) relayHint = listed[0] ?? relayHint

const desktopWindow = window as Window & { Cesium?: unknown; skyCommand?: { invoke: typeof invoke; selectRouteFile: typeof selectRouteFile; relayHint: string } }
if (desktopWindow.Cesium === undefined) throw new Error('Cesium 未加载')
desktopWindow.skyCommand = { invoke, selectRouteFile, relayHint }
await import('D:/Desktop/Sky Command/src/production/operator-console/renderer/main.ts')
