import { describe, expect, it } from 'vitest'
import { openLocalViewerFileUseCase } from './open-local-viewer-file.usecase'

describe('openLocalViewerFileUseCase', () => {
  it('accepts ply and spz files', () => {
    const ply = new File(['splat'], 'scene.ply')
    const [error, file] = openLocalViewerFileUseCase(ply)
    expect(error).toBeNull()
    expect(file).toBe(ply)
  })

  it('rejects other formats', () => {
    const [error] = openLocalViewerFileUseCase(new File(['x'], 'notes.txt'))
    expect(error?.code).toBe('MODEL_INVALID_FORMAT')
  })
})
