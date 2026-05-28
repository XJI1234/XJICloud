type BinaryLike = ArrayBuffer | Uint8Array | number[]

interface ViewerDesktopApi {
  pickModelDirectory: () => Promise<string | null>
  pickExportPath: (defaultFileName: string) => Promise<string | null>
  listModelCandidates: (directoryPath: string) => Promise<DesktopModelCandidate[]>
  readModelFile: (filePath: string) => Promise<DesktopModelFilePayload>
  readTextFile: (path: string) => Promise<string | null>
  writeTextFile: (path: string, contents: string) => Promise<void>
  writeBinaryFile: (path: string, bytes: ArrayBuffer) => Promise<void>
}

type ElectronWindow = Window & typeof globalThis & {
  viewerDesktop?: ViewerDesktopApi
}

export interface DesktopModelCandidate {
  name: string
  path: string
}

interface DesktopModelFilePayload {
  name: string
  path: string
  directoryPath: string
  bytes: BinaryLike
}

function normalizeBinary(bytes: BinaryLike) {
  if (bytes instanceof Uint8Array) {
    return new Uint8Array(bytes)
  }

  if (bytes instanceof ArrayBuffer) {
    return new Uint8Array(bytes.slice(0))
  }

  return new Uint8Array(bytes)
}

function createDesktopFile(bytes: BinaryLike, name: string) {
  return new File([normalizeBinary(bytes)], name)
}

function getDesktopBridge() {
  const bridge = (window as ElectronWindow).viewerDesktop
  if (!bridge) {
    throw new Error('当前桌面桥接不可用')
  }

  return bridge
}

function toArrayBuffer(bytes: Uint8Array) {
  return new Uint8Array(bytes).buffer
}

export function isElectronDesktop() {
  return typeof window !== 'undefined' && Boolean((window as ElectronWindow).viewerDesktop)
}

export async function pickDesktopModelDirectory() {
  return getDesktopBridge().pickModelDirectory()
}

export async function pickDesktopExportPath(defaultFileName: string) {
  return getDesktopBridge().pickExportPath(defaultFileName)
}

export async function listDesktopModelCandidates(directoryPath: string) {
  return getDesktopBridge().listModelCandidates(directoryPath)
}

export async function readDesktopModelFile(filePath: string) {
  const payload = await getDesktopBridge().readModelFile(filePath)

  return {
    file: createDesktopFile(payload.bytes, payload.name),
    filePath: payload.path,
    directoryPath: payload.directoryPath,
  }
}

export async function readDesktopTextFile(path: string) {
  return getDesktopBridge().readTextFile(path)
}

export async function writeDesktopTextFile(path: string, contents: string) {
  await getDesktopBridge().writeTextFile(path, contents)
}

export async function writeDesktopBinaryFile(path: string, bytes: Uint8Array) {
  await getDesktopBridge().writeBinaryFile(path, toArrayBuffer(bytes))
}