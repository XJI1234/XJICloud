import { describe, expect, it } from 'vitest'
import { ok } from '@/shared/result'
import type { SkyCommandPort, SkyOutcome } from '../../domain/repositories/sky-command.port'
import { connectSkyCommand, sendSkyCommand, uploadSkyMission } from './sky-command.usecase'

function port(partial: Partial<SkyCommandPort> = {}): SkyCommandPort {
  return {
    defaultUrl: 'ws://127.0.0.1:8766/sdk',
    status: () => 'idle',
    devices: () => [],
    connect: async () => ok(undefined),
    disconnect: () => undefined,
    subscribe: () => () => undefined,
    send: async () => ok({ status: 'succeeded', detail: '' }),
    uploadMission: async () => ok({ status: 'succeeded', detail: '' }),
    ...partial,
  }
}

describe('sky command use cases', () => {
  it('rejects a gateway address that is not a websocket url', async () => {
    const [error] = await connectSkyCommand(port(), 'http://127.0.0.1:8766/sdk')
    expect(error?.code).toBe('SKY_INVALID')
  })

  it('adds confirmation before return-home and keeps photo progress attached', async () => {
    const sent: unknown[] = []
    const desk = port({
      send: async (deviceId, action, fields, options) => {
        sent.push({ deviceId, action, fields, progress: options?.onProgress !== undefined })
        return ok({ status: 'succeeded', detail: 'ok' } satisfies SkyOutcome)
      },
    })
    const [homeError] = await sendSkyCommand(desk, { deviceId: 'phone', action: 'flight.return-home' })
    const [photoError] = await sendSkyCommand(desk, { deviceId: 'phone', action: 'camera.photo.fetch', onProgress: () => undefined })
    expect(homeError).toBeNull()
    expect(photoError).toBeNull()
    expect(sent[0]).toMatchObject({ action: 'flight.return-home', fields: { confirm: true } })
    expect(sent[1]).toMatchObject({ action: 'camera.photo.fetch', fields: { knownPhotos: [] }, progress: true })
  })

  it('rejects an empty mission file', async () => {
    const [error] = await uploadSkyMission(port(), 'phone', 'route.kmz', new Uint8Array())
    expect(error?.code).toBe('SKY_INVALID')
  })
})
