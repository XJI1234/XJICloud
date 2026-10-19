import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { createSession, defaultRoot } from './archive.mjs'
import { attachFile, markSession, writeIndex, writeSessionReport } from './catalog.mjs'

// Reuse the existing workspace Playwright installation; no production dependency added.
const { chromium } = await import(new URL('../../../Sky Command/sdk/node_modules/playwright/index.mjs', import.meta.url).href)
const root = await mkdtemp(path.join(tmpdir(), 'xji-record-ui-'))
let browser
try {
  for (const [label, delay, sdkEvent] of [['旧版本降落验证', 1200, 'SDK_ON_FAILURE'], ['新版本降落验证', 30, 'SDK_ON_SUCCESS'], ['</script><script>globalThis.evidenceInjected=true</script>', 5, 'SDK_ON_SUCCESS']]) {
    const session = await createSession({ root, kind: 'live', label })
    session.record('operation-result', { method: 'flight-request', elapsedMs: delay, result: { ok: sdkEvent === 'SDK_ON_SUCCESS' } })
    session.record('sdk-diagnostic', { events: [{ eventCode: sdkEvent }] })
    await session.finish('unverified')
    await markSession(session.directory, { observation: '人工测试数据；不代表实际飞行', expected: '验证归档阅读路径' })
    await writeSessionReport(session.directory)
  }
  await writeIndex(root)
  browser = await chromium.launch({ headless: true, executablePath: process.env.XJI_TEST_BROWSER_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(pathToFileURL(path.join(root, 'index.html')).href)
  assert.equal(await page.getByRole('heading', { name: 'XJI 测试档案' }).count(), 1)
  assert.equal(await page.locator('tbody tr').count(), 3)
  assert.equal(await page.evaluate(() => globalThis.evidenceInjected), undefined)
  await page.getByPlaceholder('搜索日期、功能、版本或结果').fill('旧版本降落')
  assert.equal(await page.locator('tbody tr:visible').count(), 1)
  await page.getByPlaceholder('搜索日期、功能、版本或结果').fill('')
  await page.getByRole('button', { name: '比较选中的两次测试' }).click()
  assert.equal(await page.locator('#message').innerText(), '请选择两次测试')
  await page.getByRole('checkbox', { name: '选择 旧版本降落验证', exact: true }).check()
  await page.getByRole('checkbox', { name: '选择 新版本降落验证', exact: true }).check()
  await page.getByRole('button', { name: '比较选中的两次测试' }).click()
  const comparison = JSON.parse(await page.locator('#comparison').innerText())
  const delays = [comparison.a.operations['flight-request'].maxMs, comparison.b.operations['flight-request'].maxMs].sort((a, b) => a - b)
  assert.deepEqual(delays, [30, 1200])
  await page.getByRole('link', { name: '旧版本降落验证', exact: true }).click()
  assert.ok((await page.locator('body').innerText()).includes('SDK_ON_FAILURE'))
  assert.ok((await page.locator('body').innerText()).includes('人工测试数据'))
  assert.deepEqual(errors, [])
  await writeIndex()
  await page.goto(pathToFileURL(path.join(defaultRoot(), 'index.html')).href)
  if (process.env.XJI_TEST_RECORDS_DIRECTORY) {
    const screenshot = path.join(process.env.XJI_TEST_RECORDS_DIRECTORY, 'catalog-ui.png')
    await page.screenshot({ path: screenshot, fullPage: true })
    await attachFile(process.env.XJI_TEST_RECORDS_DIRECTORY, screenshot)
  }
  console.log('PASS: 离线索引、中文检索、双记录比较、详情阅读、原始回调和 HTML 注入防护；截图已归档。未发出任何飞机命令。')
} finally {
  await browser?.close()
  assert.ok(path.resolve(root).startsWith(path.resolve(tmpdir()) + path.sep))
  await rm(root, { recursive: true, force: true })
}
