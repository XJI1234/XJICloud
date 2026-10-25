import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import i18n from '@/app/i18n'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import EditorView from '@/features/editor/presentation/SuperSplatEditorView.vue'

const mocks = vi.hoisted(() => ({ list: vi.fn(), upload: vi.fn(), downloadBytes: vi.fn(), importLocal: vi.fn(), saveExport: vi.fn(), isDirty: vi.fn(), waitReady: vi.fn(), listVersions: vi.fn(), downloadVersionBytes: vi.fn(), restoreVersion: vi.fn() }))
vi.mock('@/features/project/presentation/composables/useProjectWorkspace', () => ({
  useProjectWorkspace: () => ({ load: async () => [null, []], activeProjectId: () => 'p1' }),
}))
vi.mock('@/features/model-asset/presentation/composables/useModelAssets', () => ({ useModelAssets: () => mocks }))
vi.mock('@/features/editor/presentation/composables/useEditorSession', () => ({
  useEditorSession: () => ({
    blank: (lang: string) => ({ lang }), src: ({ lang }: { lang: string }) => [null, `/supersplat/index.html?lng=${lang}`],
    prepareLocal: (file: File) => [null, { fileName: file.name }], waitReady: mocks.waitReady,
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
  mocks.waitReady.mockResolvedValue(ok(undefined))
  mocks.isDirty.mockResolvedValue(ok(true))
  mocks.saveExport.mockResolvedValue(ok(model('A')))
  mocks.listVersions.mockResolvedValue(ok([{ id: 'old', current: false, fileName: 'A.ply', createdAt: '2026-10-25T00:00:00Z', sizeBytes: 1, version: 1 }]))
  mocks.downloadVersionBytes.mockResolvedValue(ok(new ArrayBuffer(1)))
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
afterEach(() => { app?.unmount(); app = null; host?.remove(); host = null; vi.restoreAllMocks(); vi.resetAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); localStorage.clear() })

describe('editor scene transactions', () => {
  it('keeps A as the overwrite target when downloading B fails', async () => {
    await mountEditor()
    mocks.downloadBytes.mockResolvedValueOnce(err(new DomainError('NETWORK')))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    click(i18n.global.t('supersplat.pickCloudModel')); await flush()
    host!.querySelectorAll<HTMLButtonElement>('.model-choice-card')[1].click(); await flush()
    expect(mocks.importLocal).toHaveBeenCalledTimes(1)
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('A.ply')
    click(i18n.global.t('supersplat.saveToCloud')); await flush()
    click(i18n.global.t('supersplat.confirmExport')); await flush()
    expect(mocks.saveExport).toHaveBeenCalledWith(expect.objectContaining({ modelId: 'A', sceneModelId: 'A' }))
  })
  it('preserves the scene when the host language changes', async () => {
    await mountEditor()
    const frame = host!.querySelector('iframe')!
    i18n.global.locale.value = 'en-US'; await flush()
    expect(new URL(frame.src).searchParams.get('lng')).toBe('zh-CN')
    expect(mocks.downloadBytes).toHaveBeenCalledTimes(1)
    expect(mocks.isDirty).not.toHaveBeenCalled()
    frame.dispatchEvent(new Event('load')); await flush()
    expect(mocks.importLocal).toHaveBeenCalledTimes(1)
  })
  it('loads a model selected through the same route', async () => {
    const router = await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const navigation = router.push('/app/supersplat?modelId=B');
    await vi.waitFor(() => expect(mocks.downloadBytes).toHaveBeenCalledTimes(2)); await flush()
    host!.querySelector('iframe')!.dispatchEvent(new Event('load')); await flush(); await navigation
    expect(router.currentRoute.value.query.modelId).toBe('B')
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('B.ply')
    expect(mocks.downloadBytes).toHaveBeenCalledTimes(2)
  })
  it('keeps the scene and URL when a dirty replacement is refused', async () => {
    const router = await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    click(i18n.global.t('supersplat.blankEditor')); await flush()
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('A.ply')
    await router.push('/app/supersplat?modelId=B')
    expect(router.currentRoute.value.query.modelId).toBe('A')
    expect(mocks.downloadBytes).toHaveBeenCalledOnce()
  })
  it.each(['ready', 'import'])('never authorizes B after %s failure', async phase => {
    await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const failure = err(new DomainError('EDITOR_IMPORT_FAILED'))
    if (phase === 'ready') mocks.waitReady.mockResolvedValueOnce(failure)
    else mocks.importLocal.mockResolvedValueOnce(failure)
    click(i18n.global.t('supersplat.pickCloudModel')); await flush()
    host!.querySelectorAll<HTMLButtonElement>('.model-choice-card')[1].click(); await flush()
    host!.querySelector('iframe')!.dispatchEvent(new Event('load')); await flush()
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).not.toBe('B.ply')
    const save = [...host!.querySelectorAll('button')].find(button => button.textContent?.trim() === i18n.global.t('supersplat.saveToCloud'))!
    expect(save.disabled).toBe(true)
    expect(mocks.saveExport).not.toHaveBeenCalled()
  })
  it('bounds a missing iframe load instead of leaving the page busy forever', async () => {
    await mountEditor()
    vi.useFakeTimers()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    click(i18n.global.t('supersplat.blankEditor')); await flush()
    await vi.advanceTimersByTimeAsync(30_000); await flush()
    expect(host!.querySelector('.supersplat-chrome__error')?.textContent).toBeTruthy()
    expect(vi.getTimerCount()).toBe(0)
  })
  it('refuses local replacement without changing the committed scene', async () => {
    await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const input = host!.querySelector('input[type=file]')!
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'local.ply')] })
    input.dispatchEvent(new Event('change')); await flush()
    expect(mocks.importLocal).toHaveBeenCalledOnce()
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('A.ply')
  })
  it.each(['previewVersion', 'restoreVersion'])('obtains dirty permission before %s', async action => {
    await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValueOnce(action === 'restoreVersion').mockReturnValue(false)
    click(i18n.global.t('supersplat.versions')); await flush()
    click(i18n.global.t(`supersplat.${action}`)); await flush()
    expect(mocks.downloadVersionBytes).not.toHaveBeenCalled()
    expect(mocks.restoreVersion).not.toHaveBeenCalled()
    expect(host!.querySelector('.supersplat-chrome__title')?.textContent).toBe('A.ply')
  })
  it('keeps A as the overwrite target after a successful history preview', async () => {
    await mountEditor()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    click(i18n.global.t('supersplat.versions')); await flush()
    click(i18n.global.t('supersplat.previewVersion')); await flush()
    host!.querySelector('iframe')!.dispatchEvent(new Event('load')); await flush()
    expect(mocks.importLocal).toHaveBeenCalledTimes(2)
    click(i18n.global.t('supersplat.saveToCloud')); await flush()
    click(i18n.global.t('supersplat.confirmExport')); await flush()
    expect(mocks.saveExport).toHaveBeenCalledWith(expect.objectContaining({ modelId: 'A', sceneModelId: 'A' }))
  })
  it('does not treat a dirty-query timeout as permission to leave', async () => {
    const router = await mountEditor()
    mocks.isDirty.mockResolvedValue(err(new DomainError('EDITOR_TIMEOUT')))
    const confirm = vi.spyOn(window, 'confirm')
    await router.push('/app/supersplat?modelId=B')
    expect(router.currentRoute.value.query.modelId).toBe('A')
    expect(confirm).not.toHaveBeenCalled()
    expect(mocks.downloadBytes).toHaveBeenCalledOnce()
  })
})
