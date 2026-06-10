import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = path.join(root, 'modules', 'supersplat', 'dist')
const target = path.join(root, 'public', 'supersplat')

function copyRecursive(from, to) {
  fs.mkdirSync(to, { recursive: true })
  for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
    const srcPath = path.join(from, entry.name)
    const destPath = path.join(to, entry.name)
    if (entry.isDirectory()) {
      copyRecursive(srcPath, destPath)
    } else {
      fs.copyFileSync(srcPath, destPath)
    }
  }
}

if (!fs.existsSync(source)) {
  console.error(`SuperSplat dist not found: ${source}`)
  process.exit(1)
}

if (fs.existsSync(target)) {
  fs.rmSync(target, { recursive: true, force: true })
}

copyRecursive(source, target)
console.log(`Copied SuperSplat dist to ${target}`)
