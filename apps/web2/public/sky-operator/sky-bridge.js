const controlUrl = 'http://127.0.0.1:8767/invoke'

function encode(value) {
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

const skyCommand = {
  relayHint: 'ws://127.0.0.1:8080/relay',
  async invoke(method, input) {
    try {
      const response = await fetch(controlUrl, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ method, ...(input === undefined ? {} : { input: encode(input) }) }),
      })
      const result = await response.json()
      if (method === 'state-snapshot' && result.ok === true) {
        const status = result.value?.routeStorage
        const notice = document.getElementById('route-storage-status')
        if (notice && status) {
          notice.textContent = status.phase === 'degraded'
            ? '本机航线资料恢复不完整，已保护原文件；导入和删除暂不可用，请检查存储目录。已有有效航线仍可使用。'
            : `已保存 ${status.savedCount} 条航线，服务重启后自动恢复。`
          notice.setAttribute('role', status.phase === 'degraded' ? 'alert' : 'status')
        } else if (notice) {
          notice.textContent = '当前本地服务尚未启用航线自动保存，需更新并重启服务。'
        }
      }
      return result
    } catch {
      return { ok: false, code: 'DEPENDENCY_FAILURE' }
    }
  },
  selectRouteFile() {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.kml,.kmz,application/vnd.google-earth.kml+xml,application/vnd.google-earth.kmz'
    return new Promise((resolve) => {
      input.addEventListener('change', async () => {
        const picked = input.files?.[0]
        if (!picked) { resolve({ ok: false }); return }
        resolve({ ok: true, fileName: picked.name, bytes: new Uint8Array(await picked.arrayBuffer()) })
      }, { once: true })
      input.addEventListener('cancel', () => resolve({ ok: false }), { once: true })
      input.click()
    })
  },
}

window.skyCommand = skyCommand
fetch('/sky-relay-hints').then((response) => response.json()).then((listed) => {
  if (Array.isArray(listed) && listed.every((item) => typeof item === 'string' && item.startsWith('ws://')) && listed[0]) skyCommand.relayHint = listed[0]
}).catch(() => undefined)
