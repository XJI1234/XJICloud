export const VIEWER_CONFIG_VERSION = 2

export const PROJECT_INFO_BUILTIN_KEYS = ['coordinates', 'buildingName', 'floorCount', 'height'] as const

export type BuiltinProjectInfoKey = (typeof PROJECT_INFO_BUILTIN_KEYS)[number]

export type ProjectInfoField = {
  key: string
  label: string
  value: string
}

export type ProjectInfoConfig = {
  projectName: string
  fields: ProjectInfoField[]
}

export type StoredDefaultView = {
  position: [number, number, number]
  quaternion: [number, number, number, number]
}

export type PointAnnotation = {
  id: string
  text: string
  position: [number, number, number]
  edgeColor: string
}

export type CubeMarker = {
  id: string
  center: [number, number, number]
  size: number
  edgeColor: string
  annotationText?: string
}

export type ViewerConfig = {
  version: number
  defaultView: StoredDefaultView | null
  pointAnnotations: PointAnnotation[]
  cubeMarkers: CubeMarker[]
  projectInfo: ProjectInfoConfig
}

export type ViewerModelSummary = {
  id: string
  fileName: string
  format: string
  sizeBytes: number
  version: number
  updatedAt: string
}
