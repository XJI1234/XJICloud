import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')
const statusCode = bundle.slice(
  bundle.indexOf('  // src/production/operator-console/flight-now-status/index.ts'),
  bundle.indexOf('  // src/production/operator-console/renderer/operation-feedback.ts'),
)
const FlightNowStatus = runInNewContext(`${statusCode}\nFlightNowStatus`) as {
  waypointProgress(index: number | null, total: number | null): { label: string; caption: string; value: number | null; max: number | null } | null
  missionRunPhase(dji: string | null, phone: string | null): string | null
  monitorAircraftStatus(input: Record<string, unknown>): { label: string; detail: string }
}

const flyingMission = {
  flightController: 'connected',
  flightMode: 'GPS_NORMAL',
  flying: 'flying',
  motorsOn: true,
  djiMissionState: 'EXECUTING',
  missionExecution: 'EXECUTING',
  landingProtectionState: null,
  landingConfirmationNeeded: false,
  lowBatteryRthState: null,
}

describe('mission execution progress', () => {
  it('shows a 1-based position against the route total', () => {
    expect(FlightNowStatus.waypointProgress(0, 32)).toEqual({ label: '1 / 32', caption: '航点 1 / 32', value: 1, max: 32 })
    expect(FlightNowStatus.waypointProgress(31, 32)).toMatchObject({ label: '32 / 32', value: 32, max: 32 })
  })

  it('does not invent a fraction without a usable total', () => {
    expect(FlightNowStatus.waypointProgress(3, null)).toEqual({ label: '航点 3', caption: '航点 3', value: null, max: null })
    expect(FlightNowStatus.waypointProgress(32, 32)).toMatchObject({ label: '航点 32', max: null })
    expect(FlightNowStatus.waypointProgress(null, 32)).toMatchObject({ label: '共 32 个', max: null })
    expect(FlightNowStatus.waypointProgress(null, null)).toBeNull()
  })

  it('names the execution phase separately from upload', () => {
    expect(FlightNowStatus.missionRunPhase('EXECUTING', 'EXECUTING')).toBe('执行中')
    expect(FlightNowStatus.missionRunPhase('PAUSED', 'EXECUTING')).toBe('已暂停')
    expect(FlightNowStatus.missionRunPhase('INTERRUPTED', 'PAUSED')).toBe('已暂停')
    expect(FlightNowStatus.missionRunPhase('INTERRUPTED', 'EXECUTING')).toBe('已暂停')
    expect(FlightNowStatus.missionRunPhase('INTERRUPTED', 'FAILED')).toBe('已中断')
    expect(FlightNowStatus.missionRunPhase('EXECUTING', 'PAUSED')).toBe('执行中')
    expect(FlightNowStatus.missionRunPhase(null, 'NOT_STARTED')).toBeNull()
  })

  it('puts the fraction on the monitor aircraft detail while the route is executing', () => {
    expect(FlightNowStatus.monitorAircraftStatus({ ...flyingMission, waypointIndex: 0, waypointTotal: 32 })).toEqual({
      label: '执行航线',
      detail: '航点 1 / 32',
    })
    expect(FlightNowStatus.monitorAircraftStatus({ ...flyingMission, waypointIndex: 4, waypointTotal: null }).detail).toBe('航点 4')
  })
})
