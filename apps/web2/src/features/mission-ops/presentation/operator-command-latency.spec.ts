// @vitest-environment node
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it, vi } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')

// Run the shipped click handler with a pending redraw, without a physical relay.
function monitorHarness(which: 'home' | 'land', action: string) {
  const start = bundle.indexOf('  var confirmMonitorFlight = ')
  const confirm = bundle.slice(start, bundle.indexOf('  var monitorFactsOf = ', start))
  const bindStart = bundle.indexOf('  var bindMonitorDirect = ')
  const bind = bundle.slice(bindStart, bundle.indexOf('  bindMonitorDirect(', bindStart))
  const calls: string[] = []
  let click: () => Promise<void> = async () => {}
  let finishPaint = () => {}
  const paint = new Promise<void>((resolve) => { finishPaint = resolve })
  class Element { dataset = { monitorSlot: '0' } }
  const button = {
    disabled: false,
    closest: () => new Element(),
    addEventListener: (_type: string, handler: () => Promise<void>) => { click = handler },
  }
  const view = { devices: [{ deviceId: 'test-phone' }] }
  const render = vi.fn(() => paint)
  const invoke = vi.fn(async (method: string) => {
    calls.push(method)
    return method === 'flight-request'
      ? { ok: true, value: { confirmation: { deviceId: 'test-phone', action, confirmationId: 'test-confirmation' } } }
      : { ok: true, value: { code: 'SUCCEEDED' } }
  })
  runInNewContext(`${confirm}\n${bind}\nbindMonitorDirect('[data-monitor-${which}]', '${which}');`, {
    document: { querySelectorAll: () => [button] }, HTMLElement: Element,
    projectCached: () => view, monitorDevices: () => view.devices,
    text4: (value: unknown) => typeof value === 'string' ? value : null,
    read4: (value: Record<string, unknown> | null, key: string) => value?.[key],
    monitorHomeBusy: new Set(), monitorLandBusy: new Set(),
    monitorFactsOf: () => ({}),
    monitorDirectFlight: () => ({ [which]: { mode: action, label: action } }),
    monitorDirectRunningDetail: () => action, monitorDirectProgress: new Map(),
    monitorRailDeviceId: null, monitorRailKind: null,
    show: vi.fn(), callSign: () => 'test-phone', render,
    bridge: () => ({ invoke }),
    confirmationFromResult: (result: { value: { confirmation: unknown } }) => result.value.confirmation,
    captureFeedback: () => ({ outcome: 'accepted', message: 'accepted' }), feedbackDeviceEpoch: () => 1,
  })
  return { calls, render, click: () => click(), finishPaint }
}

describe('embedded flight command latency', () => {
  it.each([
    ['home', 'return-home'], ['home', 'stop-go-home'],
    ['land', 'land'], ['land', 'confirm-landing'],
  ] as const)('dispatches %s/%s without waiting for a pending redraw', async (which, action) => {
    const harness = monitorHarness(which, action)
    const completion = harness.click()
    try {
      await vi.waitFor(() => expect(harness.calls).toEqual(['flight-request', 'flight-confirm']), { timeout: 100 })
    } finally {
      harness.finishPaint()
      await completion
    }
    expect(harness.render).toHaveBeenCalled()
  })
})
