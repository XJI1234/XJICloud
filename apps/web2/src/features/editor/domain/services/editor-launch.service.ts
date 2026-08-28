import type { EditorLaunchParams } from '../entities/editor-session.entity'

export function createBlankEditorLaunch(lang?: string): EditorLaunchParams {
  return { lang }
}

export function createRemoteEditorLaunch(input: {
  signedUrl: string
  fileName: string
  modelId?: string
  lang?: string
}): EditorLaunchParams {
  return {
    signedUrl: input.signedUrl,
    fileName: input.fileName,
    modelId: input.modelId,
    lang: input.lang,
  }
}

export function hasRemoteScene(params: EditorLaunchParams): boolean {
  return Boolean(params.signedUrl && params.fileName)
}
