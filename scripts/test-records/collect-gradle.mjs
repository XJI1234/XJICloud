import { readdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { attachFile } from './catalog.mjs'

const [workspace, session] = process.argv.slice(2)
if (!workspace || !session) throw new Error('collect-gradle.mjs <workspace> <session>')
const started = JSON.parse(await (await import('node:fs/promises')).readFile(path.join(session, 'manifest.json'), 'utf8')).startedAtMs
let collected = 0
async function visit(directory, insideResults = false) {
  let entries
  try { entries = await readdir(directory, { withFileTypes: true }) } catch (error) { if (error.code === 'ENOENT') return; throw error }
  for (const entry of entries) {
    if (entry.isSymbolicLink()) continue
    const location = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      if (insideResults || entry.name === 'test-results') await visit(location, true)
      else if (!['.git', '.gradle', '.idea', '.worktrees', 'node_modules', 'tmp', 'intermediates', 'kotlin', 'generated', 'outputs'].includes(entry.name) && !entry.name.startsWith('tmp-')) await visit(location)
    } else if (insideResults && entry.name.endsWith('.xml') && (await stat(location)).mtimeMs >= started - 2000) {
      await attachFile(session, location); collected++
    }
  }
}
await visit(workspace)
console.log(`[test-records] 已保存 ${collected} 份本次 Gradle 测试 XML（UP-TO-DATE 任务不会冒充重新执行）`)
