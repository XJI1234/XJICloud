// Historical evidence for baseline 63f4f9c only: assertions describe defects before the fixes.
// Do not add to CI or copy into the repaired suite; regression specs assert the corrected behavior.
// See ../2026-10-25-web2-review.md and ../2026-10-25-web2-fixes.md.
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import i18n from '@/app/i18n'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import { createJobWatchHub } from '@/features/dataset-training/application/job-watch-hub'
import { createFetchJobEventAdapter } from '@/features/dataset-training/infrastructure/adapters/fetch-job-event.adapter'
import { createLocalStorageSessionStore } from '@/features/identity/infrastructure/repositories/local-storage-session.store'
import { createLocalWorkspacePersistence } from '@/features/project/infrastructure/repositories/local-workspace.persistence'
import { logoutUseCase } from '@/features/identity/application/use-cases/auth.usecase'
import { createHttpModelAssetRepository } from '@/features/model-asset/infrastructure/repositories/http-model-asset.repository'
import { buildDatasetArchive } from '@/features/dataset-training/domain/services/dataset-archive.service'
import type { TrainingJobRepository } from '@/features/dataset-training/domain/repositories/training-job.repository'
import type { JobProgressEvent, TrainingJob } from '@/features/dataset-training/domain/entities/training-job.entity'
import type { HttpClient } from '@/shared/infrastructure/http-client'
import type { CloudModelCachePort } from '@/features/model-asset/domain/repositories/cloud-model-cache.port'
import EditorView from '@/features/editor/presentation/SuperSplatEditorView.vue'
import ModelUploadPanel from '@/features/model-asset/presentation/ModelUploadPanel.vue'
import { createPostMessageEditorBridge } from '@/features/editor/infrastructure/post-message-editor.bridge'
import { createOpfsModelCache } from '@/features/model-asset/infrastructure/repositories/opfs-model-cache'

const mocks = vi.hoisted(() => ({ list: vi.fn(), upload: vi.fn(), downloadBytes: vi.fn(), importLocal: vi.fn(), saveExport: vi.fn(), isDirty: vi.fn() }))
vi.mock('@/features/project/presentation/composables/useProjectWorkspace', () => ({
  useProjectWorkspace: () => ({ load: async () => [null, []], activeProjectId: () => 'p1' }),
}))
vi.mock('@/features/model-asset/presentation/composables/useModelAssets', () => ({ useModelAssets: () => mocks }))
vi.mock('@/features/editor/presentation/composables/useEditorSession', () => ({
  useEditorSession: () => ({
    blank: (lang: string) => ({ lang }), src: ({ lang }: { lang: string }) => [null, `/supersplat/index.html?lng=${lang}`],
    prepareLocal: (file: File) => [null, { fileName: file.name }], waitReady: async () => [null, undefined],
    importLocal: mocks.importLocal, saveExport: mocks.saveExport, isDirty: mocks.isDirty,
  }),
}))
vi.mock('@/presentation/components/WaitOverlay.vue', () => ({ default: { render: () => null } }))
vi.mock('@/presentation/components/AppSheet.vue', () => ({ default: defineComponent({
  props: { visible: Boolean }, setup(props, { slots }) { return () => props.visible ? h('section', [slots.default?.(), slots.footer?.()]) : null },
}) }))

let app: App | null = null
let host: HTMLDivElement | null = null
async function flush() { for (let i = 0; i < 12; i++) await nextTick() }
function click(label: string) {
  const button = [...host!.querySelectorAll('button')].find(el => el.textContent?.trim() === label)
  expect(button, label).toBeTruthy(); button!.click()
}
const model = (id: string) => ({ id, projectId: 'p1', fileName: `${id}.ply`, format: 'PLY', sizeBytes: 1, version: 1, updatedAt: '2026-10-25T00:00:00Z', createdAt: '2026-10-25T00:00:00Z' })
async function mountEditor() {
  i18n.global.locale.value = 'zh-CN'
  mocks.list.mockResolvedValue(ok([model('A'), model('B')]))
  mocks.downloadBytes.mockResolvedValue(ok(new ArrayBuffer(1)))
  mocks.importLocal.mockResolvedValue(ok(undefined))
  mocks.isDirty.mockResolvedValue(ok(true))
  mocks.saveExport.mockResolvedValue(ok(model('B')))
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/app/supersplat', component: EditorView }] })
  await router.push('/app/supersplat?modelId=A')
  host = document.createElement('div'); document.body.appendChild(host)
  app = createApp({ render: () => h(RouterView) }).use(router).use(i18n); app.mount(host)
  await flush()
  host.querySelector('iframe')!.dispatchEvent(new Event('load'))
  await flush()
  expect(mocks.importLocal).toHaveBeenCalledOnce()
  return router
}
afterEach(() => { app?.unmount(); app = null; host?.remove(); host = null; vi.restoreAllMocks(); vi.resetAllMocks(); vi.unstubAllGlobals(); localStorage.clear() })

describe('review probes documenting existing defects (not acceptance tests)', () => {
  it('editor can overwrite B with still-loaded A after B download fails', async () => {
    await mountEditor()
    mocks.downloadBytes.mockResolvedValueOnce(err(new DomainError('NETWORK')))
    click(i18n.global.t('supersplat.pickCloudModel')); await flush()
    host!.querySelectorAll<HTMLButtonElement>('.model-choice-card')[1].click(); await flush()
    expect(mocks.importLocal).toHaveBeenCalledTimes(1)
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('B.ply')
    click(i18n.global.t('supersplat.saveToCloud')); await flush()
    click(i18n.global.t('supersplat.confirmExport')); await flush()
    expect(mocks.saveExport).toHaveBeenCalledWith(expect.objectContaining({ modelId: 'B' }))
  })
  it('locale change reloads A without checking the dirty scene', async () => {
    await mountEditor()
    const frame = host!.querySelector('iframe')!
    i18n.global.locale.value = 'en-US'; await flush()
    expect(new URL(frame.src).searchParams.get('lng')).toBe('en')
    expect(mocks.downloadBytes).toHaveBeenCalledTimes(2)
    expect(mocks.isDirty).not.toHaveBeenCalled()
    frame.dispatchEvent(new Event('load')); await flush()
    expect(mocks.importLocal).toHaveBeenCalledTimes(2)
  })
  it('same-route modelId changes leave the previous editor model loaded', async () => {
    const router = await mountEditor()
    await router.push('/app/supersplat?modelId=B'); await flush()
    expect(router.currentRoute.value.query.modelId).toBe('B')
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('A.ply')
    expect(mocks.downloadBytes).toHaveBeenCalledTimes(1)
  })
  it('terminal job refresh repopulates hub after clear', async () => {
    let onEvent!: (event: JobProgressEvent) => void
    let finish!: (value: ReturnType<typeof ok<TrainingJob>>) => void
    const pending = new Promise<ReturnType<typeof ok<TrainingJob>>>(resolve => { finish = resolve })
    const jobs = { getById: () => pending } as unknown as TrainingJobRepository
    const hub = createJobWatchHub({ jobs, events: { subscribe: (_id, cb) => { onEvent = cb; return () => undefined } } })
    const job: TrainingJob = { id: 'old', projectId: 'p1', name: 'old', status: 'RUNNING', progress: 1, stage: null, message: null, downloadUrl: null, errorMessage: null, createdAt: '', updatedAt: '' }
    hub.upsert(job); hub.watch(job.id)
    onEvent({ jobId: job.id, status: 'COMPLETED', progress: 100, stage: '', message: '' })
    hub.clear(); finish(ok({ ...job, status: 'COMPLETED' })); await flush()
    expect(hub.jobs().map(item => item.id)).toEqual(['old'])
  })
  it('logout clears identity before the account-scoped workspace reset', () => {
    const session = createLocalStorageSessionStore(localStorage)
    session.persist({ accessToken: 't', tokenType: 'Bearer', expiresInMs: 0, userId: 'u1', username: 'u1', displayName: '' })
    const workspace = createLocalWorkspacePersistence(localStorage, () => session.read()?.userId ?? null)
    workspace.setActiveProjectId('old-project')
    logoutUseCase({ session, onWorkspaceReset: () => workspace.setActiveProjectId(null) })
    expect(localStorage.getItem('xjicloud_active_project_id:u1')).toBe('old-project')
  })
  it('cache read failure blocks a healthy network download', async () => {
    const downloadBytes = vi.fn(async () => new ArrayBuffer(1))
    const cache = { list: async () => { throw new DOMException('storage denied', 'SecurityError') } } as unknown as CloudModelCachePort
    const repo = createHttpModelAssetRepository({ downloadBytes } as unknown as HttpClient, cache)
    const [error] = await repo.downloadBytes('m1')
    expect(error).toBeTruthy(); expect(downloadBytes).not.toHaveBeenCalled()
  })
  it('successful server deletion is reported as failure if cache prune fails', async () => {
    const request = vi.fn(async () => undefined)
    const cache = { list: async () => { throw new Error('cache unavailable') } } as unknown as CloudModelCachePort
    const repo = createHttpModelAssetRepository({ request } as unknown as HttpClient, cache)
    const [error] = await repo.delete('m1')
    expect(request).toHaveBeenCalledOnce(); expect(error).toBeTruthy()
  })
  it('failed SSE watch remains registered and cannot restart', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const fetchImpl = vi.fn(async () => new Response('', { status: 503 }))
    const adapter = createFetchJobEventAdapter({ fetchImpl: fetchImpl as typeof fetch, tokenProvider: { getToken: () => 't' } })
    const hub = createJobWatchHub({ jobs: {} as TrainingJobRepository, events: adapter })
    hub.watch('j1'); await flush(); hub.watch('j1'); await flush()
    expect(fetchImpl).toHaveBeenCalledTimes(1)
    hub.clear()
  })
  it('archive allows GIF despite the documented JPG PNG WebP contract', () => {
    const [error, archive] = buildDatasetArchive([new File(['gif'], 'a.gif', { type: 'image/gif' })])
    expect(error).toBeNull(); expect(archive?.files[0].archivedName).toBe('0001.gif')
  })
  it('unmounting the model upload panel does not abort its upload signal', async () => {
    mocks.list.mockResolvedValue(ok([]))
    let signal!: AbortSignal
    mocks.upload.mockImplementation((input) => { signal = input.signal; return new Promise(() => undefined) })
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
    await router.push('/')
    host = document.createElement('div'); document.body.appendChild(host)
    app = createApp(ModelUploadPanel, { projectId: 'p1', projectName: 'p1' }).use(router).use(i18n); app.mount(host)
    await flush()
    const input = host.querySelector('input[type=file]')!
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'a.ply')] })
    input.dispatchEvent(new Event('change')); await flush()
    expect(signal).toBeDefined()
    app.unmount(); app = null
    expect(signal.aborted).toBe(false)
  })
  it('bridge file-read failure rejects instead of returning a Result', async () => {
    const file = new File(['x'], 'a.ply')
    Object.defineProperty(file, 'arrayBuffer', { value: () => Promise.reject(new Error('file read failed')) })
    const frame = { contentWindow: window, src: location.origin }
    await expect(createPostMessageEditorBridge().importLocal(frame, file)).rejects.toThrow('file read failed')
  })
  it('concurrent OPFS index updates lose one model entry', async () => {
    const files = new Map<string, string | ArrayBuffer>([['index.json', '{}']])
    let reads = 0
    let release!: () => void
    const barrier = new Promise<void>(resolve => { release = resolve })
    const dir = {
      async getFileHandle(name: string, options?: { create: boolean }) {
        if (!files.has(name) && !options?.create) throw new Error('missing')
        return {
          async getFile() {
            const value = files.get(name)!
            return { text: async () => {
              if (name === 'index.json' && reads < 2) { if (++reads === 2) release(); await barrier }
              return value as string
            } }
          },
          async createWritable() {
            let pending: string | ArrayBuffer
            return { write: async (value: string | ArrayBuffer) => { pending = value }, close: async () => { files.set(name, pending) } }
          },
        }
      },
    }
    vi.stubGlobal('navigator', { storage: { getDirectory: async () => ({ getDirectoryHandle: async () => dir }) } })
    const cache = createOpfsModelCache()
    const entry = (id: string) => ({ cacheKey: `live:${id}`, modelId: id, versionId: null, fileName: `${id}.ply`, sizeBytes: 1, updatedAt: 't', cachedAt: 't' })
    await Promise.all([cache.put(entry('a'), new ArrayBuffer(1)), cache.put(entry('b'), new ArrayBuffer(1))])
    expect((await cache.list())).toHaveLength(1)
    expect([...files.keys()].filter(key => key.endsWith('.bin'))).toHaveLength(2)
  })
})
