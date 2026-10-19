import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const appRoot = path.dirname(fileURLToPath(import.meta.url))
const headers = { 'Cross-Origin-Opener-Policy': 'same-origin', 'Cross-Origin-Embedder-Policy': 'require-corp' }
export default defineConfig({
  root: path.join(appRoot, 'demo/native3dgs'),
  base: process.env.VIEWER_BASE ?? '/',
  plugins: [vue()],
  resolve: { alias: { '@': path.join(appRoot, 'src') } },
  build: { outDir: 'dist', emptyOutDir: true },
  server: { host: '127.0.0.1', port: 5186, strictPort: true, headers },
  preview: { host: '127.0.0.1', port: 5186, strictPort: true, headers },
})
