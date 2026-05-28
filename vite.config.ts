import path from 'node:path'
import MagicString from 'magic-string'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import glsl from 'vite-plugin-glsl'

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
  base: './',
  clearScreen: false,
  plugins: [
    vue(),
    glsl({ include: ['**/*.glsl'] }),
    fixWasmDataUrl(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      'spark-rs': path.resolve(__dirname, 'rust/spark-rs/pkg'),
      'spark-worker-rs': path.resolve(__dirname, 'rust/spark-worker-rs/pkg'),
    },
  },
  worker: {
    plugins: () => [glsl({ include: ['**/*.glsl'] })],
  },
  optimizeDeps: {
    exclude: ['three'],
  },
  server: {
    watch: {
      usePolling: true,
    },
    port: 5174,
    strictPort: true,
  },
})
