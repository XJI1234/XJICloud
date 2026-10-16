import { describe, expect, it, vi } from 'vitest'
import type { SkyCommandClient } from '@xjicloud/sky-command-sdk'

vi.mock('@xjicloud/sky-command-sdk', () => ({
  jsonFields: (value: object) => value,
  toPlainJson: (value: unknown) => value,
  SkyCommandError: class SkyCommandError extends Error { code = 'INVALID_INPUT' },
  createSkyCommandClient: () => { throw new Error('gateway is injected in tests') },
}))

import { createSdkLocalBridge } from './sdk-local-bridge.adapter'

function client(status: 'connected' | 'disconnected' = 'connected'): SkyCommandClient {
  const sent: unknown[] = []
  const sdk = {
    sent,
    connect: async () => undefined,
    getSnapshot: () => ({ status, relay: { devices: status === 'connected' ? [{ deviceId: 'phone', sessionId: 's' }] : [], missionPhases: [] } }),
    devices: () => (status === 'connected' ? [{ deviceId: 'phone', sessionId: 's' }] : []),
    latestTelemetry: () => null,
    sendCommand: async (_id: string, request: unknown) => {
      sent.push(request)
      return { status: 'succeeded', detail: '' }
    },
    sendMission: async () => ({ status: 'succeeded', detail: '' }),
  }
  return sdk as unknown as SkyCommandClient
}

describe('sdk local bridge', () => {
  it('reports the gateway offline when it cannot connect', async () => {
    const sdk = client('disconnected')
    sdk.connect = async () => { throw new Error('down') }
    const bridge = createSdkLocalBridge({ client: sdk })
    const [error, health] = await bridge.probeHealth()
    expect(error).toBeNull()
    expect(health?.online).toBe(false)
  })

  it('sends return-home only after the operator confirms', async () => {
    const sdk = client()
    const bridge = createSdkLocalBridge({ client: sdk })
    const [requestError, confirmationId] = await bridge.requestFlight('phone', 'return-home')
    expect(requestError).toBeNull()
    expect((sdk as unknown as { sent: unknown[] }).sent).toEqual([])
    const [error] = await bridge.confirmFlight('phone', confirmationId ?? '')
    expect(error).toBeNull()
    expect((sdk as unknown as { sent: { name: string }[] }).sent[0]?.name).toBe('flight.return-home')
  })
})
