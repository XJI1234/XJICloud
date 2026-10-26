import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, h, nextTick, ref, type App } from 'vue'
import i18n from '@/app/i18n'
import AppSheet from './AppSheet.vue'
import WaitOverlay from './WaitOverlay.vue'

vi.mock('@/presentation/motion', () => ({ prefersReducedMotion: () => true, materializeIn: () => undefined }))
let app: App | undefined, host: HTMLElement | undefined, trigger: HTMLButtonElement | undefined
const flush = async () => { for (let i = 0; i < 5; i++) await nextTick() }
afterEach(() => { app?.unmount(); app = undefined; host?.remove(); trigger?.remove(); vi.unstubAllGlobals() })
describe('modal keyboard lifecycle', () => {
  it('handles initially visible sheets, Tab wrap, background inert and focus restoration', async () => {
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => setTimeout(callback, 0))
    const visible = ref(true)
    trigger = document.createElement('button'); document.body.appendChild(trigger); trigger.focus()
    host = document.createElement('div'); document.body.appendChild(host)
    app = createApp({ render: () => h(AppSheet, { visible: visible.value, title: 'Dialog', onClose: () => { visible.value = false } },
      { default: () => [h('button', { id: 'first' }, 'First'), h('button', { id: 'last' }, 'Last')] }) })
    app.mount(host); await flush()
    expect(document.activeElement?.id).toBe('first')
    expect(host.inert).toBe(true)
    document.getElementById('last')!.focus()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }))
    expect(document.activeElement?.id).toBe('first')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flush()
    expect(document.activeElement).toBe(trigger)
    expect(host.inert).not.toBe(true)
  })
  it('lets only the top modal receive Escape and blocks background focus during a wait', async () => {
    const close = vi.fn(), dismiss = vi.fn()
    host = document.createElement('div'); document.body.appendChild(host)
    app = createApp({ render: () => [h(AppSheet, { visible: true, title: 'Base', onClose: close }),
      h(WaitOverlay, { visible: true, phase: 'busy', title: 'Waiting', onDismiss: dismiss })] }).use(i18n)
    app.mount(host); await flush()
    expect(document.activeElement?.classList.contains('wait-panel')).toBe(true)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    expect(close).not.toHaveBeenCalled()
    expect(dismiss).not.toHaveBeenCalled()
    app.unmount(); app = undefined
    expect(document.body.style.overflow).not.toBe('hidden')
  })
  it('offers explicit upload cancellation without cancelling on backdrop clicks', async () => {
    const cancel = vi.fn()
    host = document.createElement('div'); document.body.appendChild(host)
    app = createApp({ render: () => h(WaitOverlay, { visible: true, phase: 'busy', title: 'Uploading', cancellable: true, onCancel: cancel }) }).use(i18n)
    app.mount(host); await flush()
    document.querySelector<HTMLElement>('.wait-backdrop')!.click()
    expect(cancel).not.toHaveBeenCalled()
    const button = [...document.querySelectorAll<HTMLButtonElement>('.wait-panel button')].find(item => item.textContent?.trim() === i18n.global.t('common.cancel'))!
    expect(button.disabled).toBe(false)
    button.click()
    expect(cancel).toHaveBeenCalledOnce()
  })
})
