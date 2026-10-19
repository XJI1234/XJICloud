import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { listSessions, markSession, summarizeSession, writeIndex, writeSessionReport } from './catalog.mjs'

const live = (await listSessions()).find(session => session.kind === 'live' && session.metadata?.coverage?.source === 'gateway' && session.outcome === 'unfinished')
assert.ok(live, 'A running integrated gateway recording must exist')
const response = await fetch('http://127.0.0.1:8767/invoke', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{"method":"state-snapshot"}', signal: AbortSignal.timeout(3000) })
const snapshot = await response.json()
assert.equal(snapshot.ok, true)
assert.equal(snapshot.value.phase, 'running')
const devices = snapshot.value.workflow.devices
assert.ok(devices.some(device => device.connection.relay === 'online'), 'Phone reconnected')
const events = (await readFile(path.join(live.directory, 'events.ndjson'), 'utf8')).trim().split('\n').map(line => JSON.parse(line))
assert.ok(events.some(event => event.kind === 'application-state'))
assert.ok(events.some(event => event.kind === 'sdk-diagnostic'), 'A real phone diagnostic batch has been durably recorded')
const operations = events.filter(event => event.kind === 'operation-result')
for (const operation of operations) assert.ok(events.some(request => request.kind === 'operation-request' && request.data.operationId === operation.data.operationId))
const health = JSON.parse(await readFile(path.join(live.directory, 'health.json'), 'utf8'))
assert.equal(health.dropped, 0)
assert.equal(health.writeErrors, 0)
await markSession(live.directory, { label: '长期测试档案启用验收', observation: '用户已确认落地停桨并授权重启。网关重启后手机在线；已核对真实手机诊断、状态、操作回执落盘。', conclusion: '记录链路通过只读验收；未执行飞行复测。' })
await writeSessionReport(live.directory)
await writeIndex()
const summary = await summarizeSession(live.directory)
console.log(JSON.stringify({ directory: live.directory, phonesOnline: devices.filter(device => device.connection.relay === 'online').length, stateRecords: events.filter(event => event.kind === 'application-state').length, sdkDiagnosticBatches: events.filter(event => event.kind === 'sdk-diagnostic').length, recordedOperations: operations.length, sdkEventTypes: Object.keys(summary.sdkEvents), health, aircraftCommandsSentByThisCheck: 0 }, null, 2))
