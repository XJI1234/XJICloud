<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getCaptcha, needCaptcha, type CaptchaResponse } from '@/api/auth'
import { ApiError } from '@/api/client'

const authStore = useAuthStore()
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
const captcha = ref<CaptchaResponse | null>(null)
const captchaInput = ref('')

async function loadCaptcha() {
  try {
    captcha.value = await getCaptcha()
    captchaInput.value = ''
  } catch {
    captcha.value = null
  }
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
  const name = username.value.trim()
  if (!name) {
    return
  }
  try {
    const result = await needCaptcha(name)
    if (result.needCaptcha) {
      await ensureCaptchaShown()
    }
  } catch {
    // ignore probe failures; login will still enforce captcha server-side
  }
}

watch(mode, async (m) => {
  errorMessage.value = ''
  captchaInput.value = ''
  if (m === 'register') {
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
      const captchaPayload = captcha.value
      if (!captchaPayload) {
        errorMessage.value = t('login.captchaLoadFailed')
        await loadCaptcha()
        return
      }
      await authStore.register(
        username.value.trim(),
        password.value,
        displayName.value.trim() || undefined,
        captchaPayload.captchaKey,
        captchaInput.value.trim(),
      )
      authStore.logout()
      username.value = ''
      password.value = ''
      displayName.value = ''
      mode.value = 'login'
    } else {
      await authStore.login(
        username.value.trim(),
        password.value,
        showCaptcha.value ? captcha.value?.captchaKey : undefined,
        showCaptcha.value ? captchaInput.value.trim() : undefined,
      )
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/home'
      await router.push(redirect)
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('login.loginFailed')
    password.value = ''
    if (mode.value === 'login' || mode.value === 'register') {
      await ensureCaptchaShown()
      await loadCaptcha()
    }
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-page__atmosphere" aria-hidden="true" />
    <div class="cloud-grain" aria-hidden="true" />
    <div class="login-page__orb login-page__orb--blue" aria-hidden="true" />
    <div class="login-page__orb login-page__orb--amber" aria-hidden="true" />
    <div class="login-card">
      <p class="login-eyebrow">{{ t('brand.title') }}</p>
      <h1 class="login-title">{{ t('brand.subtitle') }}</h1>
      <p class="login-subtitle">{{ t('login.subtitle') }}</p>

      <div class="login-mode-row">
        <button class="login-mode-button cloud-pressable" :class="{ 'is-active': mode === 'login' }" type="button" @click="mode = 'login'">
          {{ t('login.login') }}
        </button>
        <button class="login-mode-button cloud-pressable" :class="{ 'is-active': mode === 'register' }" type="button" @click="mode = 'register'">
          {{ t('login.register') }}
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label class="login-field">
          <span>{{ t('login.username') }}</span>
          <input
            v-model="username"
            class="text-control"
            type="text"
            autocomplete="username"
            required
            @blur="checkNeedCaptcha"
          />
        </label>

        <label v-if="mode === 'register'" class="login-field">
          <span>{{ t('login.displayName') }}</span>
          <input v-model="displayName" class="text-control" type="text" autocomplete="name" />
        </label>

        <label class="login-field">
          <span>{{ t('login.password') }}</span>
          <input v-model="password" class="text-control" type="password" autocomplete="current-password" required />
        </label>

        <div v-if="showCaptcha" class="login-field">
          <span>{{ t('login.captchaLabel') }}</span>
          <div class="captcha-input-row">
            <input v-model="captchaInput" class="text-control captcha-code-input" type="text" maxlength="4" autocomplete="off" />
            <img
              v-if="captcha"
              class="captcha-thumb"
              :src="captcha.captchaImage"
              :alt="t('login.captchaAlt')"
              :title="t('login.captchaRefresh')"
              @click="loadCaptcha"
            />
            <span v-else class="captcha-loading">···</span>
          </div>
        </div>

        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <button class="side-button primary login-submit cloud-pressable" type="submit" :disabled="pending">
          {{ pending ? t('common.processing') : mode === 'login' ? t('login.login') : t('login.registerAndLogin') }}
        </button>
      </form>
    </div>
  </div>
</template>
