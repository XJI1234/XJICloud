export type EditorLaunchParams = {
  signedUrl?: string
  fileName?: string
  modelId?: string
  lang?: string
}

export type EditorExportResult = {
  blob: Blob
  fileName: string
}
