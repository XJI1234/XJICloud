import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, type App } from 'vue'
import i18n from '@/app/i18n'
import { ok, err } from '@/shared/result'
import { DomainError } from '@/shared/domain-error'
import RegisterForm from './RegisterForm.vue'

const auth = vi.hoisted(() => ({
  getCaptcha: vi.fn(),
  sendRegistrationSms: vi.fn(),
  register: vi.fn(),
}))
vi.mock('@/features/identity/presentation/composables/useAuthSession', () => ({
  useAuthSession: () => auth,
}))

let app: App | null = null
let host: HTMLDivElement
let registered: ReturnType<typeof vi.fn>

async function flush() {
  for (let index = 0; index < 10; index += 1) await nextTick()
}
async function mount() {
  host = document.createElement('div')
  document.body.appendChild(host)
  registered = vi.fn()
  app = createApp(RegisterForm, { onRegistered: registered }).use(i18n)
  app.mount(host)
  await flush()
}
async function input(name: string, value: string) {
  const element = host.querySelector<HTMLInputElement>('input[name="' + name + '"]')!
  element.value = value
  element.dispatchEvent(new Event('input', { bubbles: true }))
  await flush()
}
function sendButton() {
  return host.querySelector<HTMLButtonElement>('.registration-sms-row button')!
}
async function sendInitialSms() {
  await input('phone', '13800138000')
  await input('captcha', 'ABCD')
  sendButton().click()
  await flush()
}
async function submit() {
  host.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flush()
}

beforeEach(() => {
  vi.useFakeTimers()
  auth.getCaptcha.mockResolvedValue(ok({ captchaKey: 'image-key', captchaImage: 'data:image/png;base64,AA==' }))
  auth.sendRegistrationSms.mockResolvedValue(ok({
    challengeId: 'sms-one', retryAfterSeconds: 60, expiresInSeconds: 300,
  }))
  auth.register.mockResolvedValue(ok(null))
})
afterEach(() => {
  app?.unmount()
  app = null
  host?.remove()
  vi.resetAllMocks()
  vi.useRealTimers()
})

describe('registration image verification flow', () => {
  it('hides the consumed image, focuses SMS and registers without another image challenge', async () => {
    await mount()
    await sendInitialSms()
    expect(auth.sendRegistrationSms).toHaveBeenCalledWith({
      phone: '13800138000', captchaKey: 'image-key', captchaCode: 'ABCD',
    })
    expect(host.querySelector('input[name="captcha"]')).toBeNull()
    expect(auth.getCaptcha).toHaveBeenCalledOnce()
    expect(document.activeElement).toBe(host.querySelector('input[name="smsCode"]'))
    expect(host.textContent).toContain('图形验证已完成')
    expect(sendButton().disabled).toBe(true)

    await input('smsCode', '012345')
    await input('password', 'Password123!')
    await input('confirmPassword', 'Password123!')
    await submit()
    expect(auth.register).toHaveBeenCalledWith({
      phone: '13800138000', password: 'Password123!', smsChallengeId: 'sms-one', smsCode: '012345',
    })
    expect(registered).toHaveBeenCalledOnce()
    expect(auth.getCaptcha).toHaveBeenCalledOnce()
  })

  it('waits for an explicit resend before requesting another image, and preserves the previous SMS on failure', async () => {
    await mount()
    await sendInitialSms()
    await input('smsCode', '012345')
    await vi.advanceTimersByTimeAsync(60_000)
    await flush()
    expect(host.querySelector('input[name="captcha"]')).toBeNull()
    expect(sendButton().disabled).toBe(false)
    expect(sendButton().textContent).toContain('重新发送')
    expect(auth.getCaptcha).toHaveBeenCalledOnce()

    sendButton().click()
    await flush()
    expect(auth.getCaptcha).toHaveBeenCalledTimes(2)
    expect(auth.sendRegistrationSms).toHaveBeenCalledOnce()
    expect(document.activeElement).toBe(host.querySelector('input[name="captcha"]'))

    auth.sendRegistrationSms.mockResolvedValueOnce(err(new DomainError('NETWORK')))
    await input('captcha', 'EFGH')
    sendButton().click()
    await flush()
    expect(host.querySelector('input[name="captcha"]')).not.toBeNull()
    expect(host.querySelector<HTMLInputElement>('input[name="smsCode"]')!.value).toBe('012345')
    expect(auth.getCaptcha).toHaveBeenCalledTimes(3)
    await input('password', 'Password123!')
    await input('confirmPassword', 'Password123!')
    await submit()
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ smsChallengeId: 'sms-one', smsCode: '012345' }))
  })

  it('clears the SMS challenge and reopens image verification when the phone changes', async () => {
    await mount()
    await sendInitialSms()
    await input('smsCode', '012345')
    await input('phone', '13800138001')
    expect(host.querySelector('input[name="captcha"]')).not.toBeNull()
    expect(host.querySelector<HTMLInputElement>('input[name="smsCode"]')!.value).toBe('')
    expect(host.textContent).not.toContain('图形验证已完成')
    expect(auth.getCaptcha).toHaveBeenCalledTimes(2)
    await input('password', 'Password123!')
    await input('confirmPassword', 'Password123!')
    await submit()
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({
      phone: '13800138001', smsChallengeId: '', smsCode: '',
    }))
  })

  it('keeps image verification visible after a failed first send', async () => {
    await mount()
    auth.sendRegistrationSms.mockResolvedValueOnce(err(new DomainError('NETWORK')))
    await sendInitialSms()
    expect(host.querySelector('input[name="captcha"]')).not.toBeNull()
    expect(host.querySelector<HTMLInputElement>('input[name="captcha"]')!.value).toBe('')
    expect(auth.getCaptcha).toHaveBeenCalledTimes(2)
    expect(host.querySelector('[role="alert"]')).not.toBeNull()
    expect(host.textContent).not.toContain('图形验证已完成')
  })
})
