export type EditorLaunchParams = {
  signedUrl?: string
  fileName?: string
  modelId?: string
  lang?: string
}

export type SpzVersion = 3 | 4

export type EditorExportResult = {
  blob: Blob
  fileName: string
}
