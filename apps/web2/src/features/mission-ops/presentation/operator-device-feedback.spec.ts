import { readFileSync } from 'node:fs'
import path from 'node:path'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

const bundle = readFileSync(path.resolve(import.meta.dirname, '../../../../public/sky-operator/console.js'), 'utf8')
const start = bundle.indexOf('  var feedbackForDevice = ')
const source = bundle.slice(start, bundle.indexOf('  var flightActionLabel = ', start))

function harness() {
  document.body.innerHTML = '<p data-operation-feedback="mission-start"></p>'
  return runInNewContext(`${source}\n({ feedbackForDevice, renderOperationFeedback })`, {
    document, HTMLElement, feedbackByAction: new Map(),
  }) as {
    feedbackForDevice: (action: string, deviceId: string, epoch: number, feedback: object) => void
    renderOperationFeedback: (action: string, deviceId: string, epoch: number) => void
  }
}

describe('operator feedback per device', () => {
  it('retains the last command result for all four devices when switching selection', () => {
    const api = harness()
    for (const id of ['A', 'B', 'C', 'D']) {
      api.feedbackForDevice('mission-start', id, 1, { message: `result-${id}`, source: 'msdk', outcome: 'accepted' })
    }
    for (const id of ['A', 'B', 'C', 'D']) {
      api.renderOperationFeedback('mission-start', id, 1)
      expect(document.querySelector('[data-operation-feedback]')?.textContent).toBe(`result-${id}`)
    }
  })

  it('keeps a late result from an old connection from erasing the new connection result', () => {
    const api = harness()
    api.feedbackForDevice('mission-start', 'A', 2, { message: 'current', source: 'msdk', outcome: 'accepted' })
    api.feedbackForDevice('mission-start', 'A', 1, { message: 'stale', source: 'msdk', outcome: 'rejected' })
    api.renderOperationFeedback('mission-start', 'A', 2)
    expect(document.querySelector('[data-operation-feedback]')?.textContent).toBe('current')
    api.renderOperationFeedback('mission-start', 'A', 3)
    expect(document.querySelector('[data-operation-feedback]')?.textContent).toBe('')
  })
})
