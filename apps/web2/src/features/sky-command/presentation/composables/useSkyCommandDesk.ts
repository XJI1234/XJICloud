import { inject, onBeforeUnmount, ref } from 'vue'
import { CONTAINER_KEY } from '@/shared/di'
import type { SkyDevice, SkyLinkStatus, SkyPhotoProgress } from '../../domain/repositories/sky-command.port'
import { connectSkyCommand, sendSkyCommand, uploadSkyMission } from '../../application/use-cases/sky-command.usecase'

export function useSkyCommandDesk() {
  const container = inject(CONTAINER_KEY)!
  const port = container.skyCommand
  const status = ref<SkyLinkStatus>(port.status())
  const devices = ref<readonly SkyDevice[]>(port.devices())
  const progress = ref<SkyPhotoProgress | null>(null)
  const unsubscribe = port.subscribe(() => {
    status.value = port.status()
    devices.value = port.devices()
  })
  onBeforeUnmount(unsubscribe)
  return {
    defaultUrl: port.defaultUrl,
    status,
    devices,
    progress,
    connect: (url: string) => connectSkyCommand(port, url),
    disconnect: () => port.disconnect(),
    send: (deviceId: string, action: Parameters<typeof sendSkyCommand>[1]['action'], rtmpUrl?: string) =>
      sendSkyCommand(port, {
        deviceId,
        action,
        ...(rtmpUrl === undefined ? {} : { rtmpUrl }),
        onProgress: (update) => { progress.value = update },
      }),
    upload: (deviceId: string, fileName: string, bytes: Uint8Array) => uploadSkyMission(port, deviceId, fileName, bytes),
  }
}
