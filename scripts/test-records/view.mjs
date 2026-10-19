import { mkdir } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import path from 'node:path'
import { defaultRoot, writeJson } from './archive.mjs'
import { attachFile, compareSessions, listSessions, markSession, summarizeSession, writeIndex, writeSessionReport } from './catalog.mjs'

try {
  const [command, a, b] = process.argv.slice(2)
  if (command === 'list') {
    console.table((await listSessions()).map(({ startedAtMs, label, kind, outcome, directory }) => ({ time: new Date(startedAtMs).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }), label, kind, outcome, directory })))
  } else if (command === 'show' && a) { await writeSessionReport(a); console.log(JSON.stringify(await summarizeSession(a), null, 2)) }
  else if (command === 'mark' && a && b) { await markSession(a, JSON.parse(b)); await writeSessionReport(a); await writeIndex(); console.log('已保存人工记录') }
  else if (command === 'attach' && a && b) { console.log(await attachFile(a, b)); await writeSessionReport(a) }
  else if (command === 'compare' && a && b) {
    const comparison = await compareSessions(a, b)
    const directory = path.join(defaultRoot(), 'comparisons')
    await mkdir(directory, { recursive: true })
    const file = path.join(directory, `${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID()}.json`)
    await writeJson(file, comparison)
    console.log(JSON.stringify({ a: { label: comparison.a.label, outcome: comparison.a.outcome, tests: comparison.a.automated.counts }, b: { label: comparison.b.label, outcome: comparison.b.outcome, tests: comparison.b.automated.counts }, added: comparison.testCaseChanges.added.length, removed: comparison.testCaseChanges.removed.length, statusChanged: comparison.testCaseChanges.statusChanged.length }, null, 2))
    console.log(`完整对比已保存：${file}`)
  } else if (command === 'index') {
    for (const session of await listSessions()) if (session.outcome !== 'unreadable') await writeSessionReport(session.directory)
    console.log(await writeIndex())
  }
  else throw new Error('用法：view.mjs list | index | show <目录> | mark <目录> <JSON> | attach <目录> <文件> | compare <目录A> <目录B>')
} catch (error) { console.error(`[test-records] ${error.message}`); process.exitCode = 1 }
