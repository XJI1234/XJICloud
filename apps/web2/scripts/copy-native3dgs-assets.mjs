import { cp, mkdir, readFile, rm } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { dirname, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const assets = resolve(dirname(fileURLToPath(import.meta.resolve('@native3dgs/web'))), 'assets')
const manifest = JSON.parse(await readFile(resolve(assets, 'manifest.json'), 'utf8'))
for (const file of manifest.files) {
  const source = resolve(assets, file.name)
  if (!source.startsWith(assets + sep)) throw new Error('SDK asset path escaped its directory')
  const bytes = await readFile(source)
  if (createHash('sha256').update(bytes).digest('hex') !== file.sha256) {
    throw new Error(`SDK asset checksum mismatch: ${file.name}`)
  }
}
const mode = process.argv[2]
if (mode && mode !== 'demo') throw new Error('Expected no argument or demo')
const destination = resolve(appRoot, mode === 'demo' ? 'demo/native3dgs/public/gs-assets' : 'public/gs-assets')
if (!destination.startsWith(appRoot + sep)) throw new Error('Generated asset path escaped web2')
await mkdir(dirname(destination), { recursive: true })
await rm(destination, { recursive: true, force: true })
await cp(assets, destination, { recursive: true })
console.log(`Copied verified Native3DGS Worker, WASM and licenses to ${destination}`)
