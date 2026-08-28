<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useAuthSession } from '@/features/identity/presentation/composables/useAuthSession'
import type { CaptchaChallenge } from '@/features/identity/domain/entities/captcha.entity'

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
    <section class="login-form-pane">
      <h1 class="login-title">{{ t('brand.subtitle') }}</h1>
      <p class="login-subtitle">{{ t('login.subtitle') }}</p>

      <div class="login-mode-row">
        <button class="login-mode-button" :class="{ 'is-active': mode === 'login' }" type="button" @click="mode = 'login'">
          {{ t('login.login') }}
        </button>
        <button class="login-mode-button" :class="{ 'is-active': mode === 'register' }" type="button" @click="mode = 'register'">
          {{ t('login.register') }}
        </button>
      </div>

      <form class="login-fields" @submit.prevent="submit">
        <label class="cloud-field">
          <span>{{ t('login.username') }}</span>
          <input v-model="username" class="cloud-input" type="text" autocomplete="username" @blur="checkNeedCaptcha" />
        </label>
        <label v-if="mode === 'register'" class="cloud-field">
          <span>{{ t('login.displayName') }}</span>
          <input v-model="displayName" class="cloud-input" type="text" autocomplete="nickname" />
        </label>
        <label class="cloud-field">
          <span>{{ t('login.password') }}</span>
          <input v-model="password" class="cloud-input" type="password" autocomplete="current-password" />
        </label>
        <label v-if="showCaptcha" class="cloud-field">
          <span>{{ t('login.captchaLabel') }}</span>
          <div class="captcha-row">
            <input v-model="captchaInput" class="cloud-input" type="text" />
            <img
              v-if="captcha"
              class="captcha-image"
              :src="captcha.captchaImage"
              alt=""
              @click="loadCaptcha"
            />
          </div>
        </label>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
        <AppButton variant="primary" type="submit" :disabled="pending">
          {{ pending ? t('common.processing') : (mode === 'login' ? t('login.login') : t('login.registerAndLogin')) }}
        </AppButton>
      </form>
    </section>
    <aside class="login-visual" aria-hidden="true">
      <div class="login-visual__mist" />
    </aside>
  </div>
</template>
