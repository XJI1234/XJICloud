import { describe, expect, it } from 'vitest'
import { createBlankEditorLaunch, createRemoteEditorLaunch, hasRemoteScene } from './editor-launch.service'

describe('editor launch', () => {
  it('creates a blank session without a remote scene', () => {
    const launch = createBlankEditorLaunch('zh-CN')
    expect(launch.lang).toBe('zh-CN')
    expect(hasRemoteScene(launch)).toBe(false)
  })

  it('creates a remote session from a signed url', () => {
    const launch = createRemoteEditorLaunch({
      signedUrl: 'https://signed/file.ply',
      fileName: 'scan.ply',
      modelId: 'm1',
    })
    expect(hasRemoteScene(launch)).toBe(true)
    expect(launch.modelId).toBe('m1')
  })
})
