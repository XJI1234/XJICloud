import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')
const code = bundle.slice(
  bundle.indexOf('  // src/production/operator-console/flight-now-status/index.ts'),
  bundle.indexOf('  // src/production/operator-console/renderer/operation-feedback.ts'),
)
const control = runInNewContext(`${code}\nmonitorRouteControl`) as (input: Record<string, unknown>) => string

describe('mission terminal controls in the deployed renderer', () => {
  it.each(['running', 'pausing', 'paused', 'resuming', 'stopping'])('clears stale %s controls after interruption or completion', missionPhase => {
    for (const missionExecution of ['FAILED', 'FINISHED']) {
      expect(control({ missionPhase, routeExecutionStarted: true, missionExecution, djiMissionState: missionExecution === 'FAILED' ? 'INTERRUPTED' : 'FINISHED' })).toBe('choose')
    }
  })

  it('keeps terminal desktop state authoritative and lets a new task wait for its own execution', () => {
    for (const missionPhase of ['failed', 'completed']) expect(control({ missionPhase, routeExecutionStarted: true, missionExecution: 'EXECUTING', djiMissionState: 'EXECUTING' })).toBe('choose')
    expect(control({ missionPhase: 'starting', routeExecutionStarted: false, missionExecution: 'FINISHED', djiMissionState: 'FINISHED' })).toBe('await')
    expect(control({ missionPhase: 'idle', routeExecutionStarted: true, missionExecution: 'FINISHED', djiMissionState: 'EXECUTING' })).toBe('choose')
    expect(control({ missionPhase: 'paused', routeExecutionStarted: true, missionExecution: 'PAUSED', djiMissionState: 'INTERRUPTED' })).toBe('resume')
  })

  it('keeps a DJI interrupted pause resumable', () => {
    expect(control({ missionPhase: 'paused', routeExecutionStarted: true, missionExecution: 'PAUSED', djiMissionState: 'INTERRUPTED' })).toBe('resume')
    expect(control({ missionPhase: 'running', routeExecutionStarted: true, missionExecution: 'EXECUTING', djiMissionState: 'INTERRUPTED' })).toBe('resume')
  })

  it('leaves the route after DJI exits the wayline', () => {
    expect(control({ missionPhase: 'paused', routeExecutionStarted: true, missionExecution: 'PAUSED', djiMissionState: 'FINISHED' })).toBe('choose')
    expect(control({ missionPhase: 'paused', routeExecutionStarted: true, missionExecution: 'PAUSED', djiMissionState: 'READY' })).toBe('choose')
  })
})
