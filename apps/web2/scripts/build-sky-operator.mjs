import { readFile, writeFile, access } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { dirname, resolve, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

const appRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const sourceRoot = process.env.SKY_COMMAND_SOURCE_ROOT
  ? resolve(process.env.SKY_COMMAND_SOURCE_ROOT)
  : resolve(appRoot, '../../../Sky-Command')
const rendererRoot = join(sourceRoot, 'src/production/operator-console/renderer')
await access(join(rendererRoot, 'aircraft-map.ts'))
const requireSource = createRequire(join(sourceRoot, 'package.json'))
const { build } = requireSource('esbuild')
const preservedMap = await readFile(join(appRoot, 'scripts/embedded-route-map.mjs'), 'utf8')
await build({
  absWorkingDir: sourceRoot,
  entryPoints: [join(rendererRoot, 'main.ts')],
  outfile: join(appRoot, 'public/sky-operator/console.js'),
  bundle: true, platform: 'browser', format: 'iife', logLevel: 'info',
  plugins: [{ name: 'preserve-shipped-web-map', setup(plugin) {
    plugin.onLoad({ filter: /[\\/]renderer[\\/]route-map\.ts$/ }, () => ({
      contents: preservedMap, loader: 'js', resolveDir: rendererRoot,
    }))
  } }],
})
const sdkPackage = JSON.parse(await readFile(join(sourceRoot, 'sdk/package.json'), 'utf8'))
await writeFile(join(appRoot, 'public/sky-operator/operator-build.json'), JSON.stringify({
  sourceRepository: 'XJI1234/Sky-Command',
  sourceCommit: execFileSync('git', ['rev-parse', 'HEAD'], { cwd: sourceRoot, encoding: 'utf8', windowsHide: true }).trim(),
  sourceDirty: execFileSync('git', ['status', '--porcelain'], { cwd: sourceRoot, encoding: 'utf8', windowsHide: true }).trim().length > 0,
  sdkVersion: sdkPackage.version,
  mapSource: 'apps/web2/scripts/embedded-route-map.mjs',
}, null, 2) + '\n')
