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
