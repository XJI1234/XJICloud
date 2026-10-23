import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { afterEach, describe, expect, it, vi } from 'vitest'

const root = path.resolve(import.meta.dirname, '../../../../public/sky-operator')
const bundle = readFileSync(path.join(root, 'console.js'), 'utf8')
const html = readFileSync(path.join(root, 'index.html'), 'utf8')
function section(start: string, end: string) {
  const offset = bundle.indexOf(start)
  expect(offset).toBeGreaterThan(-1)
  const limit = bundle.indexOf(end, offset + start.length)
  expect(limit).toBeGreaterThan(offset)
  return bundle.slice(offset, limit)
}

function harness() {
  document.body.innerHTML = new DOMParser().parseFromString(html, 'text/html').body.innerHTML
  const controls = vi.fn()
  const scope = {
    document, HTMLElement,
    lastRoutePreview: null,
    connectionOf: (device: { connection: unknown } | undefined) => device?.connection,
    read4: (value: Record<string, unknown> | null, key: string) => value?.[key],
    text4: (value: unknown) => typeof value === 'string' ? value : null,
    callSign: (id: string) => id,
    renderMonitorLandingNotices: vi.fn(), renderMonitorRail: vi.fn(), applyMonitorView: vi.fn(),
    paintMonitorStreamButton: controls, paintMonitorRouteButton: controls,
    paintMonitorPhotoButton: controls, paintMonitorHoldButton: controls,
    paintMonitorDirectButtons: controls, paintMonitorFlightHint: controls,
  }
  const render = runInNewContext([
    section('  // src/production/operator-console/flight-now-status/index.ts', '  // src/production/operator-console/renderer/operation-feedback.ts'),
    section('  function waypointTotalFor(', '  function renderMissionRun('),
    section('  var monitorDevices = ', '  var monitorVideoElement = '),
    section('  var monitorFactsOf = ', '  document.querySelectorAll('),
    section('  function renderMonitor(view)', '  var deepUnwrap = '),
    'renderMonitor',
  ].join('\n'), scope) as (view: { devices: unknown[]; routes: unknown[] }) => void
  return { render, controls, slots: [...document.querySelectorAll<HTMLElement>('[data-monitor-slot]')] }
}

const devices = Array.from({ length: 4 }, (_, index) => ({
  deviceId: `phone-${index + 1}`,
  mission: { routeId: `route-${index + 1}` },
  connection: {
    flightController: 'connected', flightState: 'flying', motorsOn: true,
    flightMode: 'NAVI_GO', missionExecution: 'EXECUTING', missionDjiExecutionState: 'EXECUTING',
    currentWaypointIndex: index * 2, batteryPercent: 80 - index, pose: { altitudeMeters: 20 + index },
  },
}))
const routes = devices.map((device, index) => ({ routeId: device.mission.routeId, waypointCount: 10 + index }))

afterEach(() => document.body.replaceChildren())

describe('monitor slots in the shipped operator page', () => {
  it('renders each phone progress and HUD in every slot and follows device reordering', () => {
    const { render, slots, controls } = harness()
    expect(slots).toHaveLength(4)
    for (const order of [devices, [devices[3], devices[1], devices[0], devices[2]]]) {
      controls.mockClear()
      render({ devices: order, routes })
      slots.forEach((slot, index) => {
        const device = order[index]!
        const original = devices.indexOf(device)
        expect(slot.querySelector('[data-monitor-name]')?.textContent).toBe(device.deviceId)
        expect(slot.querySelector('[data-monitor-state]')?.textContent).toBe('执行航线')
        expect(slot.querySelector('[data-monitor-detail]')?.textContent).toBe(`航点 ${original * 2 + 1} / ${10 + original}`)
        expect(slot.querySelector('[data-monitor-hud="battery"]')?.textContent).toBe(`${80 - original}%`)
        expect(controls.mock.calls.some(([target, value]) => target === slot && value === device)).toBe(true)
      })
    }
  })

  it('does not borrow first-slot waypoints for a phone missing DJI callbacks and clears empty slots', () => {
    const { render, slots } = harness()
    render({ devices, routes })
    render({ devices: [devices[0], {
      ...devices[1], connection: { ...devices[1]!.connection, currentWaypointIndex: null, missionExecution: 'STARTING', missionDjiExecutionState: null },
    }], routes })
    expect(slots[0]!.querySelector('[data-monitor-detail]')?.textContent).toBe('航点 1 / 10')
    expect(slots[1]!.querySelector('[data-monitor-detail]')?.textContent).toBe('启动中 · 共 11 个航点')
    for (const slot of slots.slice(2)) {
      expect(slot.querySelector('[data-monitor-name]')?.textContent).toBe('空位')
      expect(slot.querySelector('[data-monitor-detail]')?.textContent).toBe('')
    }
  })
})
