import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')
const start = bundle.indexOf('  // src/production/operator-console/flight-now-status/index.ts')
const statusCode = bundle.slice(start, bundle.indexOf('\n  // ', start + 5))
const notice = runInNewContext(`${statusCode}\ntypeof landingProtectionNotice === 'function' ? landingProtectionNotice : () => null`) as (input: typeof facts) => string | null
const facts = {
  flightController: 'connected', flying: 'flying', motorsOn: true,
  landingConfirmationNeeded: false, landingProtectionState: 'NOT_SAFE_TO_LAND',
  telemetryReceivedAtMs: 50_000 as number | null, noticeAtMs: 50_000 as number | null, nowMs: 51_000,
}

describe('embedded landing protection notices', () => {
  it('explains the current report without asserting that the aircraft has landed or prescribing confirmation', () => {
    expect(notice(facts)).toBe('飞机判定当前不适合降落，请检查下方地面并留意飞行状态。')
  })

  it('identifies a retained report as historical after the raw state returns to NONE', () => {
    expect(notice({ ...facts, landingProtectionState: 'NONE' })).toBe('刚才飞机曾判定不适合降落，请留意当前飞行状态。')
    expect(notice({ ...facts, landingProtectionState: 'NONE', noticeAtMs: null })).toBeNull()
    expect(notice({ ...facts, landingProtectionState: 'NONE', nowMs: 70_001 })).toBeNull()
  })

  it.each([
    { landingProtectionState: 'SAFE_TO_LAND' }, { landingConfirmationNeeded: true },
    { flying: 'grounded', motorsOn: false }, { flightController: 'disconnected' },
    { flightController: 'unknown' }, { telemetryReceivedAtMs: null },
    { telemetryReceivedAtMs: NaN }, { nowMs: 70_001 }, { nowMs: 49_999 },
  ])('does not show stale, resolved or unknown facts: %j', (change) => {
    expect(notice({ ...facts, ...change })).toBeNull()
  })

  it('renders per-aircraft warnings in the left rail, leaves the action panel intact, and clears recovered warnings', () => {
    document.body.innerHTML = '<aside class="monitor-rail"><div class="monitor-landing-notices" role="status" hidden></div><div class="monitor-rail-panel">航线上传状态</div></aside>'
    const rendererStart = bundle.indexOf('  function renderMonitorLandingNotices(')
    const renderer = rendererStart < 0 ? '' : bundle.slice(rendererStart, bundle.indexOf('  function renderMonitorRail(', rendererStart))
    const render = runInNewContext(`${renderer}\ntypeof renderMonitorLandingNotices === 'function' ? renderMonitorLandingNotices : () => {}`, {
      document, HTMLElement, Date: class extends Date { static now() { return 51_000 } },
      monitorDevices: (view: { devices: unknown[] }) => view.devices,
      read4: (value: Record<string, unknown>, key: string) => value?.[key],
      text4: (value: unknown) => typeof value === 'string' ? value : null,
      connectionOf: (device: { connection: unknown }) => device.connection,
      finiteNumber: (value: unknown) => typeof value === 'number' && Number.isFinite(value) ? value : null,
      monitorFactsOf: (device: { connection: object }) => ({ ...facts, ...device.connection }),
      callSign: (deviceId: string) => deviceId === 'phone-1' ? '采集 1' : '采集 2', landingProtectionNotice: notice,
    }) as (view: { devices: unknown[] }) => void
    const view = { devices: [
      { deviceId: 'phone-1', connection: facts },
      { deviceId: 'phone-2', connection: { ...facts, landingProtectionState: 'SAFE_TO_LAND' } },
    ] }
    render(view)
    const host = document.querySelector<HTMLElement>('.monitor-landing-notices')!
    expect(host.hidden).toBe(false)
    expect(host.textContent).toBe(`采集 1${notice(facts)}`)
    expect(host.querySelectorAll('p')).toHaveLength(1)
    const row = host.firstChild
    render(view)
    expect(host.firstChild).toBe(row)
    expect(document.querySelector('.monitor-rail-panel')!.textContent).toBe('航线上传状态')
    render({ devices: [] })
    expect(host.hidden).toBe(true)
    expect(host.textContent).toBe('')
    document.body.replaceChildren()
  })
})
