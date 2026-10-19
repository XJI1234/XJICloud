export type CapturedImage = { width: number; height: number; rgba: Uint8Array }

export async function saveViewerScreenshot(image: CapturedImage, name: string, isCurrent: () => boolean): Promise<void> {
  const canvas = document.createElement('canvas')
  canvas.width = image.width
  canvas.height = image.height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Screenshot canvas is unavailable')
  context.putImageData(new ImageData(Uint8ClampedArray.from(image.rgba), image.width, image.height), 0, 0)
  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((result) => result ? resolve(result) : reject(new Error('PNG encoding failed')), 'image/png')
  })
  if (!isCurrent()) return
  const url = URL.createObjectURL(blob)
  try {
    const link = document.createElement('a')
    link.href = url
    link.download = `${name.replace(/\.(ply|spz)$/i, '') || 'model'}-view.png`
    document.body.appendChild(link)
    try { link.click() } finally { link.remove() }
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
}
