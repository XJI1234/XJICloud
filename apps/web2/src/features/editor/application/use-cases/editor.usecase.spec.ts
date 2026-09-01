import { describe, expect, it } from 'vitest'
import { ok } from '@/shared/result'
import type { ModelAssetRepository } from '@/features/model-asset/domain/repositories/model-asset.repository'
import { openEditorUseCase, prepareLocalEditorLaunch, saveEditorExportUseCase, importLocalEditorFileUseCase } from './editor.usecase'
import type { EditorBridgePort } from '../../domain/repositories/editor-bridge.port'
import { buildSuperSplatSrc, isDirtyResponse, isTrustedIframeMessage } from '../../infrastructure/supersplat-protocol'

describe('editor use cases', () => {
  it('opens via download token', async () => {
    const models = {
      createDownloadToken: async () => ok({ url: 'https://signed', expiresAt: 't' }),
    } as unknown as ModelAssetRepository
    const [error, token] = await openEditorUseCase({ models }, 'm1')
    expect(error).toBeNull()
    expect(token?.url).toBe('https://signed')
  })

  it('accepts a local ply file and rejects unknown formats', () => {
    const ply = new File(['ply'], 'scan.ply', { type: 'application/octet-stream' })
    const [error, prepared] = prepareLocalEditorLaunch(ply)
    expect(error).toBeNull()
    expect(prepared?.fileName).toBe('scan.ply')

    const [invalidError] = prepareLocalEditorLaunch(new File(['x'], 'notes.txt'))
    expect(invalidError?.code).toBe('MODEL_INVALID_FORMAT')
  })

  it('uploads export bytes from the bridge', async () => {
    const uploaded: Array<{ name: string }> = []
    const models = {
      uploadExport: async (_id: string, _blob: Blob, fileName: string) => {
        uploaded.push({ name: fileName })
        return ok({
          id: 'm1',
          projectId: 'p',
          fileName,
          format: 'PLY' as const,
          sizeBytes: 1,
          version: 2,
          createdAt: '',
          updatedAt: '',
        })
      },
    } as unknown as ModelAssetRepository
    const bridge: EditorBridgePort = {
      buildSrc: () => '/supersplat/index.html',
      isDirty: async () => ok(false),
      importLocal: async () => ok(undefined),
      exportPly: async () => ok({ blob: new Blob(['ply']), fileName: 'out.ply' }),
    }
    const [error] = await saveEditorExportUseCase(
      { models, bridge },
      { modelId: 'm1', frame: { contentWindow: {} as Window, src: '/supersplat/' } },
    )
    expect(error).toBeNull()
    expect(uploaded[0]?.name).toBe('out.ply')
  })
})

describe('supersplat protocol', () => {
  it('builds the iframe src and trusts same-origin iframe messages', () => {
    const src = buildSuperSplatSrc({
      signedUrl: 'https://x/file',
      fileName: 'a.ply',
      modelId: 'm1',
      lang: 'en-US',
    })
    expect(src).toContain('/supersplat/index.html')
    expect(src).toContain('embedded=1')
    expect(src).toContain('modelId=m1')
    expect(src).toContain('load=')
  })

  it('imports a local ply through the editor bridge', async () => {
    const ply = new File(['ply'], 'scan.ply', { type: 'application/octet-stream' })
    const imported: File[] = []
    const bridge: EditorBridgePort = {
      buildSrc: () => '/supersplat/index.html',
      isDirty: async () => ok(false),
      importLocal: async (_frame, file) => {
        imported.push(file)
        return ok(undefined)
      },
      exportPly: async () => ok({ blob: new Blob(['ply']), fileName: 'out.ply' }),
    }
    const [error] = await importLocalEditorFileUseCase(
      { bridge },
      { contentWindow: {} as Window, src: '/supersplat/' },
      ply,
    )
    expect(error).toBeNull()
    expect(imported[0]).toBe(ply)
  })

  it('builds a blank editor src without a remote load', () => {
    const src = buildSuperSplatSrc({ lang: 'zh-CN' })
    expect(src).toContain('/supersplat/index.html')
    expect(src).toContain('embedded=1')
    expect(src).not.toContain('load=')
    expect(src).not.toContain('modelId=')
  })

  it('trusts same-origin iframe messages', () => {
    const frame = { contentWindow: {} as Window }
    expect(
      isDirtyResponse({ type: 'supersplat:is-scene-dirty', result: true }),
    ).toBe(true)
    expect(
      isTrustedIframeMessage(
        { source: frame.contentWindow, origin: 'http://localhost', data: {} } as MessageEvent,
        frame,
        'http://localhost',
      ),
    ).toBe(true)
  })
})
