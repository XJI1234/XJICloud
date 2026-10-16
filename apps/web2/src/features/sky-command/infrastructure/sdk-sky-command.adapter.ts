import { createSkyCommandClient, jsonFields, SkyCommandError, type SkyCommandClient } from '@xjicloud/sky-command-sdk'
import { DomainError, type DomainErrorCode } from '@/shared/domain-error'
import { err, ok, type Result } from '@/shared/result'
import type { SkyCommandFields, SkyCommandPort, SkyDeskAction, SkyOutcome, SkyPhotoProgress } from '../domain/repositories/sky-command.port'

const defaultUrl = 'ws://127.0.0.1:8766/sdk'
const photoFetchTimeoutMs = 60 * 60 * 1000

function mapError(error: unknown): DomainError {
  const code: DomainErrorCode = error instanceof SkyCommandError && (error.code === 'NOT_CONNECTED' || error.code === 'DISCONNECTED' || error.code === 'SESSION_CHANGED')
    ? 'SKY_OFFLINE'
    : error instanceof SkyCommandError && error.code === 'INVALID_INPUT'
      ? 'SKY_INVALID'
      : 'SKY_COMMAND_FAILED'
  return new DomainError(code)
}

function outcomeOf(status: string, detail: string): SkyOutcome {
  return { status, detail }
}

export function createSdkSkyCommand(): SkyCommandPort {
  let client: SkyCommandClient | null = null
  let url = defaultUrl
  const listeners = new Set<() => void>()
  const publish = (): void => {
    for (const listener of [...listeners]) {
      try { listener() } catch { /* a desk subscriber cannot break the socket */ }
    }
  }
  const open = (nextUrl: string): SkyCommandClient => {
    if (client !== null && nextUrl === url) return client
    client?.dispose()
    url = nextUrl
    client = createSkyCommandClient({ url: nextUrl, reconnect: { maxAttempts: 8 } })
    client.subscribe(() => publish())
    return client
  }
  return {
    defaultUrl,
    status: () => client?.getSnapshot().status ?? 'idle',
    devices: () => (client?.devices() ?? []).map((device) => ({ deviceId: device.deviceId, sessionId: device.sessionId })),
    async connect(nextUrl) {
      try {
        await open(nextUrl).connect()
        publish()
        return ok(undefined)
      } catch (error) {
        publish()
        return err(mapError(error))
      }
    },
    disconnect() {
      client?.disconnect()
      publish()
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => { listeners.delete(listener) }
    },
    async send(deviceId, action: SkyDeskAction, fields: SkyCommandFields, options) {
      if (client === null) return err(new DomainError('SKY_OFFLINE'))
      const timeoutMs = action === 'camera.photo.fetch' ? photoFetchTimeoutMs : undefined
      try {
        const result = await client.sendCommand(deviceId, {
          name: action,
          fields: jsonFields(fields as Readonly<Record<string, null | boolean | number | string | readonly { readonly fileName: string; readonly sha256: string }[]>>),
          ...(timeoutMs === undefined ? {} : { timeoutMs }),
          ...(options?.onProgress === undefined ? {} : {
            onPhotoProgress: (progress) => options.onProgress?.({
              stage: progress.stage,
              photoIndex: progress.photoIndex,
              completedCount: progress.completedCount,
              fileName: progress.fileName,
              transferredBytes: progress.transferredBytes,
              totalBytes: progress.totalBytes,
            } satisfies SkyPhotoProgress),
          }),
        })
        return ok(outcomeOf(result.status, result.detail))
      } catch (error) {
        return err(mapError(error))
      }
    },
    async uploadMission(deviceId, fileName, bytes) {
      if (client === null) return err(new DomainError('SKY_OFFLINE'))
      try {
        const payload = new ArrayBuffer(bytes.byteLength)
        new Uint8Array(payload).set(bytes)
        const digest = await crypto.subtle.digest('SHA-256', payload)
        const sha256 = [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('')
        const result = await client.sendMission(deviceId, {
          missionId: crypto.randomUUID(),
          fileName,
          bytes: new Uint8Array(payload),
          size: bytes.byteLength,
          sha256,
        })
        return ok(outcomeOf(result.status, result.detail))
      } catch (error) {
        return err(mapError(error))
      }
    },
  }
}
