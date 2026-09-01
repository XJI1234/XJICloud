export interface ProjectInfoField {
  key: string
  label: string
  value: string
}

export interface ProjectInfoConfig {
  projectName: string
  fields: ProjectInfoField[]
}

export interface ViewerConfigFile {
  version: number
  defaultView: StoredDefaultView | null
  pointAnnotations: Array<{
    id: string
    text: string
    position: [number, number, number]
    edgeColor: string
  }>
  cubeMarkers: Array<{
    id: string
    center: [number, number, number]
    size: number
    edgeColor: string
    annotationText?: string
  }>
  projectInfo: ProjectInfoConfig
}

export interface StoredDefaultView {
  position: [number, number, number]
  quaternion: [number, number, number, number]
}

export interface ModelSummary {
  id: string
  fileName: string
  format: string
  sizeBytes: number
  version: number
  updatedAt: string
}
