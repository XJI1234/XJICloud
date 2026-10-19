import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App, type PropType } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import i18n from '@/app/i18n'
import { ok } from '@/shared/result'
import type { ViewerModelSummary } from '@/features/viewer/domain/entities/viewer-config.entity'
import type { Result } from '@/shared/result'
import LayerViewerView from './LayerViewerView.vue'

const storage = vi.hoisted(() => ({
  openLocal: vi.fn((file: File) => [null, file]),
  listModels: vi.fn(),
  loadBytes: vi.fn(),
  loadConfig: vi.fn(),
  rememberMeta: vi.fn(),
}))
vi.mock('@/features/viewer/presentation/composables/useViewerStorage', () => ({
  useViewerStorage: () => storage,
}))
vi.mock('@/features/project/presentation/composables/useProjectWorkspace', () => ({
  useProjectWorkspace: () => ({ activeProjectId: () => 'p1' }),
}))
vi.mock('@/features/viewer/presentation/components/Native3DGSViewer.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      props: { file: Object as PropType<File | null> },
      emits: ['cancel', 'closed'],
      setup(props, { emit }) {
        return () => h('div', { 'data-file': props.file?.name ?? '' }, [
          h('button', { onClick: () => emit('cancel') }, 'Cancel'),
          h('button', { onClick: () => emit('closed') }, 'Close'),
        ])
      },
    }),
  }
})
vi.mock('@/presentation/components/AppSheet.vue', () => ({ default: { render: () => null } }))

let app: App | null = null
let host: HTMLDivElement
let router: Router
async function mount() {
  storage.loadConfig.mockResolvedValue(ok({ defaultView: null }))
  storage.loadBytes.mockImplementation(async (modelId: string) => ok({
    modelId, file: new File(['model'], `${modelId}.ply`),
  }))
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/app/layer', component: LayerViewerView }],
  })
  await router.push('/app/layer')
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(LayerViewerView).use(router).use(i18n)
  app.mount(host)
}
function pendingList() {
  let resolve!: (result: Result<ViewerModelSummary[]>) => void
  const promise = new Promise<Result<ViewerModelSummary[]>>((done) => { resolve = done })
  storage.listModels.mockReturnValueOnce(promise)
  return (id: string) => resolve(ok([{
    id, fileName: `${id}.ply`, format: 'PLY', sizeBytes: 5, version: 1, updatedAt: '2026-10-13',
  }]))
}
async function flush() {
  for (let index = 0; index < 6; index += 1) await nextTick()
}
afterEach(() => { app?.unmount(); app = null; host?.remove(); vi.resetAllMocks() })

describe('route-driven viewer model selection', () => {
  it.each(['Cancel', 'Close'])('ignores a late model list after the user selects %s', async (action) => {
    await mount()
    const finish = pendingList()
    await router.push('/app/layer?modelId=old')
    await nextTick()
    expect(storage.listModels).toHaveBeenCalledOnce()
    const button = [...host.querySelectorAll('button')].find((item) => item.textContent === action)!
    button.click()
    finish('old')
    await flush()
    expect(storage.loadBytes).not.toHaveBeenCalled()
    expect(host.querySelector('[data-file]')?.getAttribute('data-file')).toBe('')
  })

  it('ignores a late model list when modelId is removed from the route', async () => {
    await mount()
    const finish = pendingList()
    await router.push('/app/layer?modelId=old')
    await nextTick()
    await router.push('/app/layer')
    finish('old')
    await flush()
    expect(storage.loadBytes).not.toHaveBeenCalled()
  })

  it('cancels the old download immediately when a newer route is waiting for its list', async () => {
    await mount()
    let oldSignal: AbortSignal | undefined
    storage.listModels.mockResolvedValueOnce(ok([{
      id: 'old', fileName: 'old.ply', format: 'PLY', sizeBytes: 5, version: 1, updatedAt: '2026-10-13',
    }]))
    storage.loadBytes.mockImplementationOnce((_id, _progress, options: { signal: AbortSignal }) => {
      oldSignal = options.signal
      return new Promise(() => undefined)
    })
    await router.push('/app/layer?modelId=old')
    await flush()
    expect(oldSignal?.aborted).toBe(false)
    const finish = pendingList()
    await router.push('/app/layer?modelId=new')
    await nextTick()
    expect(oldSignal?.aborted).toBe(true)
    finish('new')
    await flush()
    expect(host.querySelector('[data-file]')?.getAttribute('data-file')).toBe('new.ply')
  })
})
