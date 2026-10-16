import { DomainError } from '@/shared/domain-error'
import { err, type Result } from '@/shared/result'
import type { SkyCommandPort, SkyDeskAction, SkyOutcome, SkyPhotoProgress } from '../../domain/repositories/sky-command.port'

const actions = new Set<SkyDeskAction>([
  'telemetry.read',
  'pairing.status',
  'camera.photo.capture',
  'camera.photo.fetch',
  'flight.return-home',
  'live-stream.stop',
  'live-stream.start',
])

export function connectSkyCommand(port: SkyCommandPort, url: string): Promise<Result<void>> {
  const target = url.trim()
  if (!target.startsWith('ws://') && !target.startsWith('wss://')) {
    return Promise.resolve(err(new DomainError('SKY_INVALID')))
  }
  return port.connect(target)
}

export function sendSkyCommand(
  port: SkyCommandPort,
  input: {
    readonly deviceId: string
    readonly action: SkyDeskAction
    readonly rtmpUrl?: string
    readonly onProgress?: (progress: SkyPhotoProgress) => void
  },
): Promise<Result<SkyOutcome>> {
  if (!actions.has(input.action) || input.deviceId.trim().length === 0) {
    return Promise.resolve(err(new DomainError('SKY_INVALID')))
  }
  if (input.action === 'flight.return-home') {
    return port.send(input.deviceId, input.action, { confirm: true })
  }
  if (input.action === 'live-stream.start') {
    const rtmpUrl = input.rtmpUrl?.trim() ?? ''
    if (rtmpUrl.length === 0) return Promise.resolve(err(new DomainError('SKY_INVALID')))
    return port.send(input.deviceId, input.action, { rtmpUrl })
  }
  if (input.action === 'camera.photo.fetch') {
    return port.send(input.deviceId, input.action, { knownPhotos: [] }, input.onProgress === undefined ? undefined : { onProgress: input.onProgress })
  }
  return port.send(input.deviceId, input.action, {}, input.onProgress === undefined ? undefined : { onProgress: input.onProgress })
}

export function uploadSkyMission(
  port: SkyCommandPort,
  deviceId: string,
  fileName: string,
  bytes: Uint8Array,
): Promise<Result<SkyOutcome>> {
  if (deviceId.trim().length === 0 || !fileName.toLowerCase().endsWith('.kmz') || bytes.byteLength === 0 || bytes.byteLength > 100 * 1024 * 1024) {
    return Promise.resolve(err(new DomainError('SKY_INVALID')))
  }
  return port.uploadMission(deviceId, fileName, bytes)
}
