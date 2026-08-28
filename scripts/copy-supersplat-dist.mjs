import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
/** Prebuilt SuperSplat static assets (committed; no source build). */
const source = path.join(root, 'vendors', 'supersplat')
const targets = [
  path.join(root, 'apps', 'web', 'public', 'supersplat'),
  path.join(root, 'apps', 'web2', 'public', 'supersplat'),
]

function copyRecursive(from, to) {
  fs.mkdirSync(to, { recursive: true })
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    // Skip docs / meta that might appear later; only copy web assets
    if (entry.name === 'README.md' || entry.name === '.gitkeep') {
      continue
    }
    const srcPath = path.join(from, entry.name)
    const destPath = path.join(to, entry.name)
    if (entry.isDirectory()) {
      copyRecursive(srcPath, destPath)
    } else {
      fs.copyFileSync(srcPath, destPath)
    }
  }
}

if (!fs.existsSync(source) || !fs.existsSync(path.join(source, 'index.html'))) {
  console.error(`SuperSplat static assets not found: ${source}`)
  console.error('Expected committed prebuilt files under vendors/supersplat/')
  process.exit(1)
}

for (const target of targets) {
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true })
  }
  copyRecursive(source, target)
  console.log(`Copied SuperSplat assets → ${target}`)
}
