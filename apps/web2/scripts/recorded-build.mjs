import { spawn } from 'node:child_process'

// Same ordered build as before, with argv instead of a composed shell command.
for (const command of [
  ['../../scripts/copy-supersplat-dist.mjs', 'web2'],
  ['scripts/copy-native3dgs-assets.mjs'],
  ['node_modules/vue-tsc/bin/vue-tsc.js', '-b'],
  ['node_modules/vite/bin/vite.js', 'build'],
]) {
  const child = spawn(process.execPath, command, { stdio: 'inherit', windowsHide: true })
  const result = await new Promise(resolve => {
    child.once('error', () => resolve(1))
    child.once('exit', (code, signal) => resolve(code ?? (signal ? 1 : 0)))
  })
  if (result !== 0) { process.exitCode = result; break }
}
