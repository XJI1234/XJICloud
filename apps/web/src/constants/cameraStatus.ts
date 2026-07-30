import type { InjectionKey, Ref } from 'vue'

export interface CameraStatus {
  longitude: string
  latitude: string
  elevation: string
  viewHeight: string
}

export const CAMERA_STATUS_KEY: InjectionKey<Ref<CameraStatus>> = Symbol('cameraStatus')
