import { spawn, type ChildProcess } from 'node:child_process'
import { createReadStream, existsSync, statSync } from 'node:fs'
import { networkInterfaces } from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import MagicString from 'magic-string'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import glsl from 'vite-plugin-glsl'
const appDir = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(appDir, '../..')

let skyGateway: ChildProcess | undefined

function stopSkyGateway() {
  if (skyGateway === undefined || skyGateway.exitCode !== null) return
  skyGateway.kill()
  skyGateway = undefined
}

function lanRelayHints(): string[] {
  const virtual = /vmware|vmnet|vbox|virtualbox|vethernet|hyper-v|loopback|docker|wsl|tun|mihomo|clash|openvpn|nord|wireguard|hamachi|zerotier|radmin|\bvpn\b/i
  const hints: Array<{ url: string; score: number }> = []
  for (const [name, addresses] of Object.entries(networkInterfaces())) {
    if (virtual.test(name)) continue
    for (const address of addresses ?? []) {
      if (address.internal || address.family !== 'IPv4' || address.address.startsWith('192.168.56.')) continue
      const wifi = /wi-?fi|wlan|wireless|无线/i.test(name)
      hints.push({ url: `ws://${address.address}:8080/relay`, score: wifi ? 0 : 10 })
    }
  }
  return hints.sort((left, right) => left.score - right.score || left.url.localeCompare(right.url)).map((item) => item.url)
}

function skyCommandGateway(): Plugin {
  return {
    name: 'sky-command-gateway',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/sky-relay-hints', (_request, response) => {
        response.setHeader('content-type', 'application/json')
        response.end(JSON.stringify(lanRelayHints()))
      })
      stopSkyGateway()
      skyGateway = spawn(process.execPath, [path.resolve(appDir, 'scripts/sky-command-gateway.mjs')], {
        cwd: appDir,
        stdio: 'inherit',
        env: process.env,
      })
      skyGateway.once('exit', () => {
        skyGateway = undefined
      })
      server.httpServer?.once('close', stopSkyGateway)
    },
  }
}

function serveDirectory(urlPath: string, root: string): Plugin {
  return {
    name: `serve-${urlPath.replaceAll('/', '')}`,
    configureServer(server) {
      server.middlewares.use(urlPath, (request, response, next) => {
        const raw = decodeURIComponent((request.url ?? '/').split('?')[0] ?? '/')
        const file = path.resolve(root, raw.replace(/^\/+/, ''))
        if (file !== root && !file.startsWith(`${root}${path.sep}`)) {
          response.statusCode = 403
          response.end()
          return
        }
        if (!existsSync(file) || !statSync(file).isFile()) {
          next()
          return
        }
        const type = file.endsWith('.json') ? 'application/json' : file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.wasm') ? 'application/wasm' : 'application/octet-stream'
        response.setHeader('content-type', type)
        response.setHeader('cache-control', 'public, max-age=86400')
        createReadStream(file).pipe(response)
      })
    },
  }
}

function fixWasmDataUrl(): Plugin {
  return {
    name: 'fix-wasm-data-url',
    renderChunk(code) {
      const dataUrlPattern = /new\s+URL\(\s*("data:[^"]*")\s*,\s*import\.meta\.url\s*\)/g
      const matches = [...code.matchAll(dataUrlPattern)]
      if (matches.length === 0) {
        return null
      }

      const magicString = new MagicString(code)
      for (const match of matches) {
        if (match.index === undefined) {
          continue
        }
        const start = match.index
        const end = start + match[0].length
        magicString.overwrite(start, end, `new URL(${match[1]})`)
      }

      return {
        code: magicString.toString(),
        map: magicString.generateMap({ hires: true }),
      }
    },
  }
}

export default defineConfig({
  base: '/',
  clearScreen: false,
  define: { CESIUM_BASE_URL: JSON.stringify('/cesium/') },
  plugins: [
    vue(),
    skyCommandGateway(),
    serveDirectory('/cesium', path.resolve(appDir, 'node_modules/cesium/Build/Cesium')),
    serveDirectory('/city-tiles', path.resolve('D:/Desktop/Sky Command/public/city-tiles')),
    glsl({ include: ['**/*.glsl'] }),
    fixWasmDataUrl(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(appDir, 'src'),
      '@xjicloud/spark': path.resolve(rootDir, 'packages/spark/src'),
      '@xjicloud/shared': path.resolve(rootDir, 'packages/shared/src'),
      'spark-rs': path.resolve(rootDir, 'rust/spark-rs/pkg'),
      'spark-worker-rs': path.resolve(rootDir, 'rust/spark-worker-rs/pkg'),
    },
  },
  worker: {
    plugins: () => [glsl({ include: ['**/*.glsl'] })],
  },
  optimizeDeps: {
    exclude: ['three', 'cesium'],
    entries: [path.resolve(appDir, 'index.html')],
  },
  server: {
    fs: {
      allow: [rootDir, 'D:/Desktop/Sky Command'],
    },
    watch: {
      ignored: ['**/node_modules/**', '**/dist/**', '**/public/supersplat/**', '**/public/gs-assets/**', '**/public/sky-operator/cesium/**', '**/public/sky-operator/city-tiles/**'],
    },
    port: 5176,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        timeout: 0,
        proxyTimeout: 0,
      },
    },
  },
})
