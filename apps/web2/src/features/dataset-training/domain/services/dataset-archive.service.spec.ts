import { describe, expect, it } from 'vitest'
import { buildDatasetArchive } from './dataset-archive.service'

function imageFile(name: string, relativePath: string, type = 'image/jpeg') {
  const file = new File(['xx'], name, { type })
  Object.defineProperty(file, 'webkitRelativePath', { value: relativePath })
  return file
}

describe('dataset archive', () => {
  it('returns DATASET_NO_IMAGES when folder has no images', () => {
    const [error] = buildDatasetArchive([new File(['a'], 'notes.txt', { type: 'text/plain' })])
    expect(error?.code).toBe('DATASET_NO_IMAGES')
  })

  it('sorts by webkitRelativePath and renames to 0001.ext', () => {
    const [error, archive] = buildDatasetArchive([
      imageFile('b.jpg', 'set/b.jpg'),
      imageFile('a.png', 'set/a.png', 'image/png'),
      new File(['z'], 'skip.txt', { type: 'text/plain' }),
    ])
    expect(error).toBeNull()
    expect(archive?.files.map((file) => file.archivedName)).toEqual(['0001.png', '0002.jpg'])
    expect(archive?.files[0]?.originalName).toBe('a.png')
    expect(archive?.manifest.version).toBe(1)
    expect(archive?.manifest.imageCount).toBe(2)
  })
})

// The filename contract remains JPG/PNG/WebP, including empty or misleading MIME values.
it.each(['photo.JPG', 'photo.jpeg', 'photo.PNG', 'photo.webp'])('accepts a supported extension: %s', name => {
  const [error, archive] = buildDatasetArchive([new File(['x'], name)])
  expect(error).toBeNull()
  expect(archive?.files).toHaveLength(1)
})
it.each(['a.gif', 'a.svg', 'a.bmp', 'a.txt'])('rejects unsupported image/* files: %s', name => {
  expect(buildDatasetArchive([new File(['x'], name, { type: 'image/gif' })])[0]?.code).toBe('DATASET_NO_IMAGES')
})
