import path from 'node:path'
import { fileURLToPath } from 'node:url'
import MagicString from 'magic-string'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import glsl from 'vite-plugin-glsl'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

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
  plugins: [
    vue(),
    glsl({ include: ['**/*.glsl'] }),
    fixWasmDataUrl(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'src'),
      '@/app': path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'src/app'),
      '@/shared': path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'src/shared'),
      '@/domains': path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'src/domains'),
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
    exclude: ['three'],
    entries: [path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'index.html')],
  },
  server: {
    watch: {
      usePolling: true,
      ignored: ['**/public/supersplat/**'],
    },
    port: 5174,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
