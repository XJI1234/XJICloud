import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import i18n from '@/app/i18n'
import { ok } from '@/shared/result'
import { buildDatasetArchive } from '@/features/dataset-training/domain/services/dataset-archive.service'
import ModelUploadPanel from '@/features/model-asset/presentation/ModelUploadPanel.vue'
import DatasetUploadPanel from '@/features/dataset-training/presentation/DatasetUploadPanel.vue'
import TrainingJobPanel from '@/features/dataset-training/presentation/TrainingJobPanel.vue'

const mocks = vi.hoisted(() => ({ list: vi.fn(), upload: vi.fn(), submit: vi.fn(), upsert: vi.fn(), watch: vi.fn(), replaceAll: vi.fn(), clear: vi.fn() }))
vi.mock('@/features/project/presentation/composables/useProjectWorkspace', () => ({ useProjectWorkspace: () => ({ subscribe: () => () => undefined, activeProjectId: () => 'p' }) }))
vi.mock('@/features/model-asset/presentation/composables/useModelAssets', () => ({ useModelAssets: () => mocks }))
vi.mock('@/features/dataset-training/presentation/composables/useDatasetTraining', () => ({ useDatasetTraining: () => ({
  ...mocks, archiveFolder: buildDatasetArchive, isActiveJob: () => true,
  watchHub: { ...mocks, subscribe: () => () => undefined, jobs: () => [], connectionFailed: () => false },
}) }))
vi.mock('@/presentation/components/WaitOverlay.vue', () => ({ default: { render: () => null } }))
let app: App | undefined, host: HTMLElement | undefined
const flush = async () => { for (let i = 0; i < 15; i++) await nextTick() }
afterEach(() => { app?.unmount(); app = undefined; host?.remove(); vi.resetAllMocks() })
async function mount(component: typeof ModelUploadPanel | typeof DatasetUploadPanel | typeof TrainingJobPanel) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { render: () => null } }] })
  await router.push('/')
  host = document.createElement('div'); document.body.appendChild(host)
  app = createApp({ render: () => h(component, { projectId: 'p', projectName: 'p' }) }).use(router).use(i18n)
  app.mount(host); await flush()
}
describe('upload and list page owners', () => {
  it('aborts the model upload on unmount and ignores its progress', async () => {
    mocks.list.mockResolvedValue(ok([]))
    let signal!: AbortSignal
    mocks.upload.mockImplementation(input => { signal = input.signal; return new Promise(() => undefined) })
    await mount(ModelUploadPanel)
    const input = host!.querySelector('input[type=file]')!
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'a.ply')] })
    input.dispatchEvent(new Event('change')); await flush()
    app!.unmount(); app = undefined
    expect(signal.aborted).toBe(true)
  })
  it('does not repopulate or subscribe after a training list owner unmounts', async () => {
    let finish!: (value: unknown) => void
    mocks.list.mockReturnValue(new Promise(resolve => { finish = resolve }))
    await mount(TrainingJobPanel)
    app!.unmount(); app = undefined
    finish(ok([{ id: 'old', status: 'RUNNING' }])); await flush()
    expect(mocks.replaceAll).not.toHaveBeenCalled()
    expect(mocks.watch).not.toHaveBeenCalled()
  })
  it('cancels a dataset owner and rejects a completion delivered after unmount', async () => {
    let signal!: AbortSignal, finish!: (value: unknown) => void
    mocks.submit.mockImplementation(input => { signal = input.signal; return new Promise(resolve => { finish = resolve }) })
    await mount(DatasetUploadPanel)
    const input = host!.querySelector('input[type=file]')!
    Object.defineProperty(input, 'files', { value: [new File(['image'], 'a.jpg')] })
    input.dispatchEvent(new Event('change')); await flush()
    const start = [...host!.querySelectorAll('button')].find(button => button.textContent?.trim() === i18n.global.t('dataset.startUpload'))!
    start.click(); await flush()
    app!.unmount(); app = undefined
    expect(signal.aborted).toBe(true)
    finish(ok({ id: 'old', status: 'QUEUED' })); await flush()
    expect(mocks.upsert).not.toHaveBeenCalled()
    expect(mocks.watch).not.toHaveBeenCalled()
  })
})
