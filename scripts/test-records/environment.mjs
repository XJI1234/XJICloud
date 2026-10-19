import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { createHash } from 'node:crypto'
import { readFile, lstat } from 'node:fs/promises'
import { hostname, release } from 'node:os'
import path from 'node:path'

const execute = promisify(execFile)
const excluded = /(^|[/.\-_])(env|secret|credential|token|keystore|local\.properties)([/.\-_]|$)|\.(jks|p12|pem|key)$/i
const codeFile = /\.(ts|tsx|js|mjs|cjs|vue|kt|kts|java|json|yaml|yml|md|xml|gradle|ps1|sh|bat)$/i
const git = async (cwd, args) => (await execute('git', args, { cwd, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, windowsHide: true })).stdout.trimEnd()
export async function repositoryEvidence(cwd) {
  try {
    const topLevel = await git(cwd, ['rev-parse', '--show-toplevel'])
    const [commit, branch, status, changed] = await Promise.all([
      git(topLevel, ['rev-parse', 'HEAD']), git(topLevel, ['branch', '--show-current']),
      git(topLevel, ['status', '--porcelain=v1']), git(topLevel, ['diff', '--name-only', 'HEAD', '-z']),
    ])
    const files = changed.split('\0').filter(file => file && !excluded.test(file) && codeFile.test(file))
    let patch = ''
    let patchErrorCode = null
    try { patch = files.length ? await git(topLevel, ['diff', '--no-ext-diff', '--no-textconv', 'HEAD', '--', ...files]) : '' }
    catch (error) { patch = null; patchErrorCode = error.code ?? 'DIFF_UNAVAILABLE' }
    const untracked = (await git(topLevel, ['ls-files', '--others', '--exclude-standard', '-z'])).split('\0').filter(file => file && !excluded.test(file))
    const untrackedFiles = []
    for (const file of untracked) {
      const location = path.join(topLevel, file)
      try {
        const info = await lstat(location)
        untrackedFiles.push(info.isSymbolicLink() ? { file, content: 'not-collected-symlink' }
          : info.size <= 2 * 1024 * 1024
            ? { file, size: info.size, sha256: createHash('sha256').update(await readFile(location)).digest('hex') }
            : { file, size: info.size, content: 'not-collected-large-file' })
      } catch (error) { untrackedFiles.push({ file, content: 'unavailable', errorCode: error.code ?? 'READ_FAILED' }) }
    }
    return { topLevel, commit, branch, status, patch, patchErrorCode, untrackedFiles, excluded: 'credential/config secrets and non-source binary diffs' }
  } catch (error) { return { cwd, status: 'unavailable', errorCode: error.code ?? 'GIT_FAILED' } }
}
export async function environmentEvidence(cwd = process.cwd()) {
  const repository = await repositoryEvidence(cwd)
  let packageInfo = null
  try { const value = JSON.parse(await readFile(path.join(cwd, 'package.json'), 'utf8')); packageInfo = { name: value.name, version: value.version } } catch { /* non-Node tools have no package.json */ }
  return {
    computer: { hostname: hostname(), platform: process.platform, architecture: process.arch, osRelease: release(), node: process.version },
    repository, package: packageInfo, installedApk: 'unknown', aircraftFirmware: 'unknown',
  }
}
