<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useAuthSession } from '@/features/identity/presentation/composables/useAuthSession'
import type { CaptchaChallenge } from '@/features/identity/domain/entities/captcha.entity'
import type { SmsChallenge } from '@/features/identity/domain/entities/account-profile.entity'

const emit = defineEmits<{ registered: []; busy: [value: boolean] }>()
const auth = useAuthSession()
const { t } = useI18n()
const phone = ref('')
const captcha = ref<CaptchaChallenge | null>(null)
const captchaCode = ref('')
const showImageCaptcha = ref(true)
const captchaLoading = ref(false)
const captchaInput = ref<HTMLInputElement | null>(null)
const smsInput = ref<HTMLInputElement | null>(null)
const sms = ref<SmsChallenge | null>(null)
const smsCode = ref('')
const password = ref('')
const confirmation = ref('')
const showPassword = ref(false)
const pending = ref(false)
const errorMessage = ref('')
const now = ref(Date.now())
const retryAt = ref(0)
const expiresAt = ref(0)
const remaining = computed(() => Math.max(0, Math.ceil((retryAt.value - now.value) / 1000)))
const expired = computed(() => Boolean(sms.value && now.value >= expiresAt.value))
let timer: ReturnType<typeof setInterval> | undefined

watch(pending, value => emit('busy', value))
watch(phone, () => {
  sms.value = null
  smsCode.value = ''
  errorMessage.value = ''
  if (!showImageCaptcha.value) {
    showImageCaptcha.value = true
    void refreshCaptcha()
  }
})

async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  captcha.value = null
  captchaCode.value = ''
  try {
    const [error, value] = await auth.getCaptcha()
    if (error) errorMessage.value ||= formatDomainError(t, error)
    else captcha.value = value
  } finally {
    captchaLoading.value = false
  }
}

async function sendSms() {
  if (pending.value || captchaLoading.value || remaining.value) return
  errorMessage.value = ''
  if (!showImageCaptcha.value) {
    showImageCaptcha.value = true
    await refreshCaptcha()
    await nextTick()
    captchaInput.value?.focus()
    return
  }
  pending.value = true
  try {
    const [error, value] = await auth.sendRegistrationSms({
      phone: phone.value.trim(), captchaKey: captcha.value?.captchaKey ?? '', captchaCode: captchaCode.value,
    })
    if (error || !value) {
      errorMessage.value = formatDomainError(t, error)
      // Failed attempts may consume the image challenge. Keep any previous SMS usable.
      await refreshCaptcha()
    } else {
      sms.value = value
      smsCode.value = ''
      now.value = Date.now()
      retryAt.value = now.value + value.retryAfterSeconds * 1000
      expiresAt.value = now.value + value.expiresInSeconds * 1000
      // This image challenge has served its purpose; only load another on an explicit resend.
      captcha.value = null
      captchaCode.value = ''
      showImageCaptcha.value = false
    }
  } finally {
    pending.value = false
  }
  if (!showImageCaptcha.value) {
    await nextTick()
    smsInput.value?.focus()
  }
}

async function submit() {
  if (pending.value) return
  errorMessage.value = ''
  if (password.value !== confirmation.value) {
    errorMessage.value = t('registration.passwordMismatch')
    return
  }
  if (expired.value) {
    errorMessage.value = t('registration.expired')
    return
  }
  pending.value = true
  try {
    const [error] = await auth.register({
      phone: phone.value.trim(), password: password.value,
      smsChallengeId: sms.value?.challengeId ?? '', smsCode: smsCode.value.trim(),
    })
    if (error) errorMessage.value = formatDomainError(t, error)
    else emit('registered')
  } finally {
    pending.value = false
  }
}

onMounted(() => {
  void refreshCaptcha()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => { clearInterval(timer) })
</script>

<template>
  <form class="login-fields" @submit.prevent="submit">
    <label class="cloud-field">
      <span>{{ t('registration.phone') }}</span>
      <input v-model="phone" class="cloud-input" name="phone" type="tel" inputmode="numeric"
        autocomplete="tel-national" maxlength="11" required :disabled="pending"
        :placeholder="t('registration.phoneHint')" />
    </label>
    <label v-if="showImageCaptcha" class="cloud-field">
      <span>{{ t('registration.imageCode') }}</span>
      <div class="login-captcha-row">
        <input ref="captchaInput" v-model="captchaCode" class="cloud-input" name="captcha" type="text" maxlength="8"
          autocomplete="off" :disabled="pending || captchaLoading" :placeholder="t('login.captchaPlaceholder')" />
        <button class="registration-captcha" type="button" :disabled="pending || captchaLoading"
          :aria-label="t('login.captchaRefresh')" @click="refreshCaptcha">
          <img v-if="captcha" class="captcha-image" :src="captcha.captchaImage" :alt="t('login.captchaAlt')" />
          <span v-else>{{ t('registration.refreshImage') }}</span>
        </button>
      </div>
    </label>
    <p v-if="showImageCaptcha && sms" class="registration-notice" role="status">
      {{ t('registration.resendVerification') }}
    </p>
    <label class="cloud-field">
      <span>{{ t('registration.smsCode') }}</span>
      <div class="registration-sms-row">
        <input ref="smsInput" v-model="smsCode" class="cloud-input" name="smsCode" inputmode="numeric" maxlength="6"
          autocomplete="one-time-code" required :disabled="pending" :placeholder="t('registration.smsHint')" />
        <AppButton type="button" :disabled="pending || captchaLoading || remaining > 0 || (showImageCaptcha && !captcha)" @click="sendSms">
          {{ remaining > 0 ? t('registration.resendIn', { seconds: remaining }) : t(showImageCaptcha ? 'registration.sendSms' : 'registration.resendSms') }}
        </AppButton>
      </div>
    </label>
    <p v-if="sms && !showImageCaptcha" class="registration-notice" role="status">
      {{ expired ? t('registration.expired') : t('registration.sent') }}
    </p>
    <label class="cloud-field">
      <span>{{ t('login.password') }}</span>
      <input v-model="password" class="cloud-input" name="password" :type="showPassword ? 'text' : 'password'"
        autocomplete="new-password" minlength="8" maxlength="64" required :disabled="pending"
        :placeholder="t('registration.passwordHint')" />
    </label>
    <label class="cloud-field">
      <span>{{ t('registration.confirmPassword') }}</span>
      <input v-model="confirmation" class="cloud-input" name="confirmPassword" :type="showPassword ? 'text' : 'password'"
        autocomplete="new-password" maxlength="64" required :disabled="pending" />
    </label>
    <label class="registration-show-password">
      <input v-model="showPassword" type="checkbox" /> {{ t('login.showPassword') }}
    </label>
    <p v-if="errorMessage" class="login-error" role="alert">{{ errorMessage }}</p>
    <AppButton class="login-submit" variant="ghost" type="submit" :disabled="pending">
      {{ pending ? t('common.processing') : t('registration.createAccount') }}
    </AppButton>
  </form>
</template>

<style scoped>
.registration-sms-row { display: flex; align-items: center; gap: 10px; }
.registration-sms-row .cloud-input { min-width: 0; flex: 1; width: 0; }
.registration-sms-row button { flex-shrink: 0; white-space: nowrap; }
.registration-captcha { padding: 0; border: 0; background: transparent; cursor: pointer; flex-shrink: 0; }
.registration-captcha img { display: block; }
.registration-notice { margin: 0; color: #3d6b8a; font-size: 13px; line-height: 1.6; }
.registration-show-password { display: flex; gap: 8px; align-items: center; font-size: 13px; }
</style>
