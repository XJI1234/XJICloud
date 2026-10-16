import type { Result } from '@/shared/result'

export type SkyLinkStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'disposed'

export interface SkyDevice {
  readonly deviceId: string
  readonly sessionId: string
}

export interface SkyOutcome {
  readonly status: string
  readonly detail: string
}

export interface SkyPhotoProgress {
  readonly stage: string
  readonly photoIndex: number
  readonly completedCount: number
  readonly fileName: string | null
  readonly transferredBytes: number
  readonly totalBytes: number | null
}

export type SkyDeskAction =
  | 'telemetry.read'
  | 'pairing.status'
  | 'camera.photo.capture'
  | 'camera.photo.fetch'
  | 'flight.return-home'
  | 'live-stream.stop'
  | 'live-stream.start'

export type SkyCommandFields = Readonly<Record<string, string | boolean | readonly { readonly fileName: string; readonly sha256: string }[]>> | { readonly knownPhotos: readonly { readonly fileName: string; readonly sha256: string }[] }

export interface SkyCommandPort {
  readonly defaultUrl: string
  status(): SkyLinkStatus
  devices(): readonly SkyDevice[]
  connect(url: string): Promise<Result<void>>
  disconnect(): void
  subscribe(listener: () => void): () => void
  send(
    deviceId: string,
    action: SkyDeskAction,
    fields: SkyCommandFields,
    options?: { readonly onProgress?: (progress: SkyPhotoProgress) => void },
  ): Promise<Result<SkyOutcome>>
  uploadMission(deviceId: string, fileName: string, bytes: Uint8Array): Promise<Result<SkyOutcome>>
}
