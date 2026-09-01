export interface ArchivedImageFile {
  archivedName: string
  originalName: string
  contentType: string
  sizeBytes: number
  file: File
}

export interface DatasetManifest {
  version: 1
  imageCount: number
  files: Array<{
    archivedName: string
    originalName: string
    contentType: string
    sizeBytes: number
  }>
}

export interface DatasetArchive {
  manifest: DatasetManifest
  files: ArchivedImageFile[]
  manifestBlob: Blob
}

const IMAGE_EXTENSIONS = new Set(['.jpg', '.jpeg', '.png', '.webp'])

const MIME_BY_EXT: Record<string, string> = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.webp': 'image/webp',
}

function getExtension(name: string) {
  const index = name.lastIndexOf('.')
  return index >= 0 ? name.slice(index).toLowerCase() : ''
}

function detectContentType(name: string) {
  return MIME_BY_EXT[getExtension(name)] ?? 'application/octet-stream'
}

function isImageFile(file: File) {
  const extension = getExtension(file.name)
  if (IMAGE_EXTENSIONS.has(extension)) {
    return true
  }
  return file.type.startsWith('image/')
}

export function buildDatasetArchive(rawFiles: File[]): DatasetArchive {
  const imageFiles = rawFiles.filter(isImageFile)
  if (imageFiles.length === 0) {
    throw new Error('所选文件夹中没有可用的图片文件（支持 JPG / PNG / WebP）')
  }

  imageFiles.sort((left, right) => left.webkitRelativePath.localeCompare(right.webkitRelativePath))

  const files: ArchivedImageFile[] = imageFiles.map((file, index) => {
    const extension = getExtension(file.name) || '.jpg'
    const archivedName = `${String(index + 1).padStart(4, '0')}${extension}`
    return {
      archivedName,
      originalName: file.name,
      contentType: file.type || detectContentType(file.name),
      sizeBytes: file.size,
      file,
    }
  })

  const manifest: DatasetManifest = {
    version: 1,
    imageCount: files.length,
    files: files.map(({ archivedName, originalName, contentType, sizeBytes }) => ({
      archivedName,
      originalName,
      contentType,
      sizeBytes,
    })),
  }

  const manifestBlob = new Blob([JSON.stringify(manifest, null, 2)], { type: 'application/json' })
  return { manifest, files, manifestBlob }
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
