import { appendFile, copyFile, mkdir, readFile, readdir } from 'node:fs/promises'
import { createReadStream } from 'node:fs'
import { createHash, randomUUID } from 'node:crypto'
import { createInterface } from 'node:readline'
import path from 'node:path'
import { defaultRoot, evidenceValue, writeAtomic, writeJson } from './archive.mjs'

const json = async file => JSON.parse(await readFile(file, 'utf8'))
const xmlText = value => value.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
async function automatedResults(directory, cwd) {
  try {
    const report = await json(path.join(directory, 'test-results.json'))
    const cases = (report.testResults ?? []).flatMap(suite => (suite.assertionResults ?? []).map(test => ({
      name: `${cwd && path.isAbsolute(suite.name ?? '') ? path.relative(cwd, suite.name).replaceAll('\\', '/') : suite.name}::${test.fullName ?? test.title}`, status: test.status, durationMs: test.duration ?? null, failures: test.failureMessages ?? [],
    })))
    return { source: 'vitest', counts: { total: report.numTotalTests, passed: report.numPassedTests, failed: report.numFailedTests, pending: report.numPendingTests }, cases }
  } catch (error) { if (error.code !== 'ENOENT') return { source: 'unreadable-vitest-report', counts: null, cases: [] } }
  const attachments = []
  await scan(path.join(directory, 'attachments.ndjson'), attachment => { if (attachment.fileName?.endsWith('.xml') && path.basename(attachment.fileName) === attachment.fileName) attachments.push(attachment.fileName) })
  const cases = []
  for (const file of attachments) {
    const xml = await readFile(path.join(directory, 'attachments', file), 'utf8')
    for (const match of xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
      const attributes = Object.fromEntries([...match[1].matchAll(/([a-zA-Z]+)="([^"]*)"/g)].map(value => [value[1], xmlText(value[2])]))
      const content = match[2] ?? ''
      cases.push({ name: `${attributes.classname ?? ''}::${attributes.name ?? ''}`, status: /<(failure|error)\b/.test(content) ? 'failed' : /<skipped\b/.test(content) ? 'pending' : 'passed', durationMs: Number.isFinite(Number(attributes.time)) ? Number(attributes.time) * 1000 : null, failures: /<(failure|error)\b/.test(content) ? [xmlText(content)] : [] })
    }
  }
  if (!attachments.length) return { source: 'no-case-report; consult raw stdout/stderr', counts: null, cases: [] }
  return { source: 'junit-xml', counts: { total: cases.length, passed: cases.filter(test => test.status === 'passed').length, failed: cases.filter(test => test.status === 'failed').length, pending: cases.filter(test => test.status === 'pending').length }, cases }
}
export async function readSession(directory) {
  const manifest = await json(path.join(directory, 'manifest.json'))
  if (manifest.schemaVersion !== 1 || typeof manifest.sessionId !== 'string') throw new Error('Invalid session manifest')
  let result = null
  let health = null
  try { result = await json(path.join(directory, 'result.json')) } catch (error) { if (error.code !== 'ENOENT') throw error }
  try { health = await json(path.join(directory, 'health.json')) } catch (error) { if (error.code !== 'ENOENT') throw error }
  return { ...manifest, directory, result, health, outcome: result?.outcome ?? 'unfinished' }
}
export async function listSessions(root = defaultRoot()) {
  let days
  try { days = await readdir(root, { withFileTypes: true }) } catch (error) { if (error.code === 'ENOENT') return []; throw error }
  const sessions = []
  for (const day of days.filter(item => item.isDirectory() && /^\d{4}-\d{2}-\d{2}$/.test(item.name))) {
    const folder = path.join(root, day.name)
    for (const entry of await readdir(folder, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue
      try { sessions.push(await readSession(path.join(folder, entry.name))) } catch {
        sessions.push({ directory: path.join(folder, entry.name), sessionId: entry.name, label: '档案损坏或尚未写完', outcome: 'unreadable', startedAtMs: 0 })
      }
    }
  }
  return sessions.sort((a, b) => b.startedAtMs - a.startedAtMs)
}
async function scan(file, callback) {
  let malformed = 0
  try {
    const input = createReadStream(file, { encoding: 'utf8' })
    const reader = createInterface({ input, crlfDelay: Infinity })
    for await (const line of reader) {
      if (!line.trim()) continue
      try { callback(JSON.parse(line)) } catch { malformed++ }
    }
  } catch (error) { if (error.code !== 'ENOENT') throw error }
  return malformed
}
export async function summarizeSession(directory) {
  const session = await readSession(directory)
  const operationSamples = Object.create(null)
  const sdkEvents = Object.create(null)
  const markers = []
  let gaps = 0
  const malformed = await scan(path.join(directory, 'events.ndjson'), event => {
    if (event.kind === 'operation-result' && typeof event.data?.method === 'string' && Number.isFinite(event.data.elapsedMs)) {
      const stats = operationSamples[event.data.method] ??= { count: 0, totalMs: 0, maxMs: 0, minMs: Infinity, failed: 0 }
      stats.count++; stats.totalMs += event.data.elapsedMs
      stats.maxMs = Math.max(stats.maxMs, event.data.elapsedMs); stats.minMs = Math.min(stats.minMs, event.data.elapsedMs)
      if (event.data.result?.ok === false || event.data.result?.value?.ok === false || event.data.threw === true) stats.failed++
    }
    if (event.kind === 'sdk-diagnostic') for (const item of event.data?.events ?? []) {
      if (typeof item.eventCode === 'string') sdkEvents[item.eventCode] = (sdkEvents[item.eventCode] ?? 0) + 1
    }
    if (event.kind === 'recording-gap') gaps += event.data?.dropped ?? 0
  })
  await scan(path.join(directory, 'markers.ndjson'), marker => markers.push(marker))
  const operations = Object.fromEntries(Object.entries(operationSamples).map(([key, stats]) => [key, { ...stats, meanMs: stats.totalMs / stats.count }]))
  return { sessionId: session.sessionId, label: session.label, kind: session.kind, startedAtMs: session.startedAtMs, outcome: session.outcome,
    metadata: session.metadata, result: session.result, health: session.health, operations, sdkEvents, markers, automated: await automatedResults(directory, session.metadata?.cwd ?? session.metadata?.repository?.topLevel), malformedLines: malformed, gaps,
    limits: '耗时是电脑入口到回执，不代表飞机动作或相机到屏幕延迟；没有事件不代表未发生。' }
}
export async function compareSessions(a, b) {
  const old = await summarizeSession(a)
  const next = await summarizeSession(b)
  const oldCases = new Map(old.automated.cases.map(test => [test.name, test]))
  const newCases = new Map(next.automated.cases.map(test => [test.name, test]))
  return { schemaVersion: 1, comparedAtMs: Date.now(), a: old, b: next, testCaseChanges: {
    comparable: old.automated.counts !== null && next.automated.counts !== null && old.automated.source === next.automated.source,
    added: [...newCases.keys()].filter(name => !oldCases.has(name)), removed: [...oldCases.keys()].filter(name => !newCases.has(name)),
    statusChanged: [...newCases.values()].filter(test => oldCases.has(test.name) && oldCases.get(test.name).status !== test.status).map(test => ({ name: test.name, before: oldCases.get(test.name), after: test })),
  } }
}
export async function markSession(directory, marker) {
  await readSession(directory)
  if (!marker || Array.isArray(marker) || typeof marker !== 'object' || !Object.keys(marker).some(key => ['observation', 'expected', 'inference', 'conclusion', 'label', 'environment'].includes(key))) throw new Error('Marker must identify observation/expected/inference/conclusion/label/environment')
  const line = JSON.stringify({ ...evidenceValue(marker), recordedAtMs: Date.now(), source: 'manual' })
  if (Buffer.byteLength(line) > 64 * 1024) throw new Error('Marker too large')
  await appendFile(path.join(directory, 'markers.ndjson'), `${line}\n`, 'utf8')
}
export async function attachFile(directory, file) {
  await readSession(directory)
  const targetDirectory = path.join(directory, 'attachments')
  await mkdir(targetDirectory, { recursive: true })
  const name = `${randomUUID()}-${path.basename(file)}`
  const target = path.join(targetDirectory, name)
  await copyFile(file, target, 1)
  const hash = createHash('sha256')
  let size = 0
  for await (const chunk of createReadStream(target)) { hash.update(chunk); size += chunk.length }
  const attachment = { fileName: name, size, sha256: hash.digest('hex'), attachedAtMs: Date.now() }
  await appendFile(path.join(directory, 'attachments.ndjson'), `${JSON.stringify(attachment)}\n`, 'utf8')
  return attachment
}
const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
const html = (title, body) => `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${escape(title)}</title><style>body{font:16px system-ui;max-width:1200px;margin:40px auto;padding:0 24px;color:#18232b;background:#f5f6f8}table{border-collapse:collapse;width:100%;background:white}td,th{padding:12px;text-align:left;border-bottom:1px solid #ddd}a{color:#245a82}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:white;padding:16px}input{padding:10px;width:60%;margin:16px 0}small{color:#52636f}</style><h1>${escape(title)}</h1>${body}</html>`
export async function writeSessionReport(directory) {
  const summary = await summarizeSession(directory)
  await writeJson(path.join(directory, 'summary.json'), summary)
  await writeJson(path.join(directory, 'comparison-input.json'), { operations: summary.operations, sdkEvents: summary.sdkEvents, markers: summary.markers })
  const links = ['manifest.json', 'events.ndjson', 'result.json', 'health.json', 'stdout.log', 'stderr.log', 'test-results.json', 'markers.ndjson', 'attachments.ndjson'].map(file => `<a href="${file}">${file}</a>`).join(' · ')
  await writeAtomic(path.join(directory, 'report.html'), html(summary.label, `<p>${links}</p><p>${escape(summary.limits)}</p><pre>${escape(JSON.stringify(summary, null, 2))}</pre>`))
}
export async function writeIndex(root = defaultRoot()) {
  await mkdir(root, { recursive: true })
  const sessions = await listSessions(root)
  const summaries = await Promise.all(sessions.map(async session => {
    let summary = null
    try { summary = await json(path.join(session.directory, 'summary.json')) } catch { /* unfinished or damaged report */ }
    return { sessionId: session.sessionId, label: session.label, startedAtMs: session.startedAtMs, outcome: session.outcome,
      commit: session.metadata?.repository?.commit ?? 'unknown', branch: session.metadata?.repository?.branch ?? 'unknown',
      sdk: session.metadata?.sdk ?? 'unknown', installedApk: session.metadata?.installedApk ?? 'unknown',
      operations: summary?.operations ?? {}, sdkEvents: summary?.sdkEvents ?? {}, markers: summary?.markers ?? [],
      automated: summary?.automated?.counts ?? null,
      gaps: Math.max(summary?.gaps ?? 0, session.health?.dropped ?? 0), writeErrors: session.health?.writeErrors ?? 0,
      summaryUpdatedAtMs: summary?.result?.endedAtMs ?? null,
      limits: '摘要生成时的记录；未结束会话可能仍有新数据。无事件不表示未发生；耗时仅为电脑入口到回执。' }
  }))
  const rows = sessions.map((session, i) => {
    const relative = path.relative(root, session.directory).split(path.sep).map(encodeURIComponent).join('/')
    return `<tr><td><input type="checkbox" class="selection" value="${i}" aria-label="选择 ${escape(session.label)}" style="width:auto"></td><td>${escape(new Date(session.startedAtMs).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }))}</td><td><a href="${relative}/report.html">${escape(session.label)}</a><br><small>${escape(session.sessionId)}</small></td><td>${escape(session.kind)}</td><td>${escape(session.outcome)}</td><td>${escape(session.metadata?.repository?.commit?.slice(0, 12) ?? 'unknown')}</td><td><a href="${relative}/manifest.json">版本与环境</a> · <a href="${relative}/events.ndjson">原始证据</a></td></tr>`
  }).join('')
  const safeData = JSON.stringify(summaries).replace(/</g, '\\u003c')
  const body = `<p>测试档案长期保留，不随临时日志轮换。unfinished 表示尚未正常结束，不能视为通过。时间显示为北京时间。</p><input id="filter" placeholder="搜索日期、功能、版本或结果"><button id="compare">比较选中的两次测试</button><p id="message" role="status"></p><table><thead><tr><th>选择</th><th>开始时间</th><th>测试</th><th>类型</th><th>结果</th><th>代码提交</th><th>证据</th></tr></thead><tbody>${rows}</tbody></table><pre id="comparison" hidden></pre><p>完整最新对比：node scripts/test-records/view.mjs compare &lt;档案A目录&gt; &lt;档案B目录&gt;。截图和录像通过 attach 归档。</p><script>const summaries=${safeData};document.getElementById('filter').addEventListener('input',event=>{for(const row of document.querySelectorAll('tbody tr'))row.hidden=!row.textContent.toLowerCase().includes(event.target.value.toLowerCase())});document.getElementById('compare').addEventListener('click',()=>{const selected=[...document.querySelectorAll('.selection:checked')];const message=document.getElementById('message');if(selected.length!==2){message.textContent='请选择两次测试';return}message.textContent='以下为两次测试的归档摘要。缺失证据保持未知。';const target=document.getElementById('comparison');target.hidden=false;target.textContent=JSON.stringify({a:summaries[Number(selected[0].value)],b:summaries[Number(selected[1].value)]},null,2);target.scrollIntoView({behavior:'smooth',block:'nearest'})})</script>`
  const file = path.join(root, 'index.html')
  // Each writer publishes a complete index. Per-session manifests remain the authority.
  await writeAtomic(file, html('XJI 测试档案', body))
  return file
}
