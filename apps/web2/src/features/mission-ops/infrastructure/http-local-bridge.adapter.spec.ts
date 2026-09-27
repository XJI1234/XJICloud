import { describe, expect, it, vi } from 'vitest'
import { createHttpLocalBridge } from './http-local-bridge.adapter'

describe('createHttpLocalBridge', () => {
  it('extracts the real confirmation ID and only dispatches confirm explicitly', async () => {
    const fetchImpl = vi.fn(async (_url: string, init?: RequestInit) => {
      const body = JSON.parse(String(init?.body))
      return new Response(JSON.stringify(body.method === 'flight.request'
        ? { ok: true, value: { ok: true, value: { ok: true, confirmation: { confirmationId: 'real-id' } } } }
        : { ok: true, value: { ok: true, value: { ok: true } } }), { status: 200 })
    })
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch })
    expect(await bridge.requestFlight('device-a', 'return-home')).toEqual([null, 'real-id'])
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(await bridge.confirmFlight('device-a', 'real-id')).toEqual([null, { accepted: true }])
    expect(JSON.parse(String(fetchImpl.mock.calls[1]?.[1]?.body))).toEqual({ method: 'flight.confirm', input: { deviceId: 'device-a', confirmationId: 'real-id' } })
  })

  it('rejects nested DJI business failures despite HTTP success', async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({ ok: true, value: { ok: true, value: { ok: false, code: 'REJECTED' } } }), { status: 200 }))
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch })
    const [error] = await bridge.confirmFlight('device-a', 'expired-id')
    expect(error?.code).toBe('BRIDGE_COMMAND_REJECTED')
  })
  it('maps a healthy agent response', async () => {
    const fetchImpl = vi.fn(async () =>
      new Response(JSON.stringify({ ok: true, version: '0.2.0', relayListening: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch, openExternal: () => undefined })
    const [error, health] = await bridge.probeHealth()
    expect(error).toBeNull()
    expect(health).toMatchObject({ online: true, version: '0.2.0', relayListening: true })
  })

  it('treats network failure as offline instead of throwing', async () => {
    const fetchImpl = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch, openExternal: () => undefined })
    const [error, health] = await bridge.probeHealth()
    expect(error).toBeNull()
    expect(health?.online).toBe(false)
  })

  it('opens the custom protocol when waking', async () => {
    const openExternal = vi.fn()
    const bridge = createHttpLocalBridge({
      fetchImpl: vi.fn() as unknown as typeof fetch,
      openExternal,
      wakeProtocol: 'skycommand://open',
    })
    const [error, data] = await bridge.wakeLocalAgent()
    expect(error).toBeNull()
    expect(data?.attempted).toBe(true)
    expect(openExternal).toHaveBeenCalledWith('skycommand://open')
  })

  it('does not send return-home from the unconfirmed dispatch path', async () => {
    const fetchImpl = vi.fn()
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch, openExternal: () => undefined })
    const [error] = await bridge.dispatchCommand('flight.return-home', 'device-a')
    expect(error?.code).toBe('BRIDGE_COMMAND_REJECTED')
    expect(fetchImpl).not.toHaveBeenCalled()
  })

  it('encodes an imported route without dropping bytes', async () => {
    const fetchImpl = vi.fn(async () => new Response(JSON.stringify({
      ok: true,
      value: { ok: true, value: { status: 'imported', route: { routeId: 'route-a' } } },
    }), { status: 200 }))
    const bridge = createHttpLocalBridge({ fetchImpl: fetchImpl as typeof fetch })
    const bytes = Uint8Array.from([0, 255, 10])
    const file = Object.assign(new File([bytes], 'orbit.kml'), {
      arrayBuffer: async () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength),
    })
    const [error, routeId] = await bridge.importRoute(file)
    expect(error).toBeNull()
    expect(routeId).toBe('route-a')
    const body = JSON.parse(String(fetchImpl.mock.calls[0]?.[1]?.body))
    expect(body.input.base64).toBe(btoa(String.fromCharCode(0, 255, 10)))
  })
})
