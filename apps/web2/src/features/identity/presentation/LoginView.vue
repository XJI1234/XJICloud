<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { PhEye, PhEyeSlash } from '@phosphor-icons/vue'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useAuthSession } from '@/features/identity/presentation/composables/useAuthSession'
import LoginCreatures from '@/features/identity/presentation/components/LoginCreatures.vue'
import LocaleToggle from '@/presentation/components/LocaleToggle.vue'
import type { CaptchaChallenge } from '@/features/identity/domain/entities/captcha.entity'
import './login-page.css'

const auth = useAuthSession()
const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const errorMessage = ref('')
const pending = ref(false)
const showCaptcha = ref(false)
const captcha = ref<CaptchaChallenge | null>(null)
const captchaInput = ref('')
const showPassword = ref(false)
const curious = ref(false)
const hiding = ref(false)

const layoutTick = computed(() => (mode.value === 'register' ? 1 : 0) + (showCaptcha.value ? 2 : 0))

function onFormFocus(event: FocusEvent) {
  const target = event.target
  if (!(target instanceof HTMLInputElement)) return
  hiding.value = target.name === 'password'
  curious.value = target.name !== 'password'
}

function onFormBlur(event: FocusEvent) {
  const next = event.relatedTarget
  if (next instanceof HTMLInputElement && event.currentTarget instanceof HTMLElement && event.currentTarget.contains(next)) {
    return
  }
  curious.value = false
  hiding.value = false
}

async function loadCaptcha() {
  const [error, data] = await auth.getCaptcha()
  captcha.value = error ? null : data
  captchaInput.value = ''
}

async function ensureCaptchaShown() {
  showCaptcha.value = true
  if (!captcha.value) {
    await loadCaptcha()
  }
}

async function checkNeedCaptcha() {
  if (mode.value !== 'login') {
    return
  }
  const [error, needed] = await auth.probeNeedCaptcha(username.value)
  if (!error && needed) {
    await ensureCaptchaShown()
  }
}

watch(mode, async (next) => {
  errorMessage.value = ''
  captchaInput.value = ''
  if (next === 'register') {
    await ensureCaptchaShown()
  } else {
    showCaptcha.value = false
    captcha.value = null
    await checkNeedCaptcha()
  }
})

async function submit() {
  errorMessage.value = ''
  if (!username.value.trim() || !password.value) {
    errorMessage.value = t('login.fillRequired')
    return
  }

  if (mode.value === 'login') {
    await checkNeedCaptcha()
  }

  if (showCaptcha.value) {
    if (!captcha.value) {
      errorMessage.value = t('login.captchaLoadFailed')
      await loadCaptcha()
      return
    }
    if (!captchaInput.value.trim()) {
      errorMessage.value = t('login.captchaRequired')
      return
    }
  }

  pending.value = true
  try {
    if (mode.value === 'register') {
      const challenge = captcha.value
      if (!challenge) {
        errorMessage.value = t('login.captchaLoadFailed')
        await loadCaptcha()
        return
      }
      const [error] = await auth.register({
        username: username.value.trim(),
        password: password.value,
        displayName: displayName.value.trim() || undefined,
        captchaKey: challenge.captchaKey,
        captchaCode: captchaInput.value.trim(),
      })
      if (error) {
        errorMessage.value = formatDomainError(t, error)
        password.value = ''
        await ensureCaptchaShown()
        await loadCaptcha()
        return
      }
      username.value = ''
      password.value = ''
      displayName.value = ''
      mode.value = 'login'
      return
    }

    const [error] = await auth.login({
      username: username.value.trim(),
      password: password.value,
      captchaRequired: showCaptcha.value,
      captchaKey: showCaptcha.value ? captcha.value?.captchaKey : undefined,
      captchaCode: showCaptcha.value ? captchaInput.value.trim() : undefined,
    })
    if (error) {
      errorMessage.value = formatDomainError(t, error)
      password.value = ''
      await ensureCaptchaShown()
      await loadCaptcha()
      return
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/home'
    await router.push(redirect)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <aside class="login-visual">
      <div class="login-visual__brand">
        <img class="login-visual__logo" src="/logo_nw.png" width="28" height="28" alt="" />
        {{ t('brand.title') }}
      </div>
      <LoginCreatures :curious="curious" :hiding="hiding" :layout-tick="layoutTick" />
    </aside>

    <section class="login-form-pane">
      <div class="login-pane-inner">
        <div class="login-head">
          <h1 class="login-title">{{ mode === 'register' ? t('login.welcomeRegister') : t('login.welcomeBack') }}</h1>
          <p class="login-subtitle">{{ mode === 'register' ? t('login.leadRegister') : t('login.leadLogin') }}</p>
        </div>

        <form
          class="login-fields"
          @submit.prevent="submit"
          @focusin="onFormFocus"
          @focusout="onFormBlur"
        >
          <label class="cloud-field">
            <span>{{ t('login.username') }}</span>
            <input
              v-model="username"
              class="cloud-input"
              name="username"
              type="text"
              autocomplete="username"
              :placeholder="t('login.usernamePlaceholder')"
              @blur="checkNeedCaptcha"
            />
          </label>
          <label v-if="mode === 'register'" class="cloud-field">
            <span>{{ t('login.displayName') }}</span>
            <input
              v-model="displayName"
              class="cloud-input"
              name="displayName"
              type="text"
              autocomplete="nickname"
              :placeholder="t('login.displayNamePlaceholder')"
            />
          </label>
          <label class="cloud-field">
            <span>{{ t('login.password') }}</span>
            <div class="login-input-wrap">
              <input
                v-model="password"
                class="cloud-input"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
                :placeholder="t('login.passwordPlaceholder')"
              />
              <button
                class="login-eye"
                type="button"
                :aria-label="showPassword ? t('login.hidePassword') : t('login.showPassword')"
                @click="showPassword = !showPassword"
              >
                <PhEyeSlash v-if="showPassword" :size="18" />
                <PhEye v-else :size="18" />
              </button>
            </div>
          </label>
          <label v-if="showCaptcha" class="cloud-field">
            <span>{{ t('login.captchaLabel') }}</span>
            <div class="login-captcha-row">
              <input
                v-model="captchaInput"
                class="cloud-input"
                name="captcha"
                type="text"
                :placeholder="t('login.captchaPlaceholder')"
              />
              <img
                v-if="captcha"
                class="captcha-image"
                :src="captcha.captchaImage"
                :alt="t('login.captchaAlt')"
                :title="t('login.captchaRefresh')"
                @click="loadCaptcha"
              />
            </div>
          </label>
          <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
          <AppButton class="login-submit" variant="ghost" type="submit" :disabled="pending">
            {{ pending ? t('common.processing') : (mode === 'login' ? t('login.login') : t('login.registerAndLogin')) }}
          </AppButton>
        </form>

        <p class="login-switch">
          {{ mode === 'register' ? t('login.hasAccount') : t('login.noAccount') }}
          <button type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
            {{ mode === 'register' ? t('login.login') : t('login.register') }}
          </button>
        </p>
      </div>
    </section>
    <LocaleToggle class="login-locale" />
  </div>
</template>
