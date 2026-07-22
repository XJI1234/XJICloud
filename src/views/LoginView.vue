<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
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

async function submit() {
  errorMessage.value = ''
  pending.value = true
  try {
    if (mode.value === 'register') {
      await authStore.register(username.value.trim(), password.value, displayName.value.trim())
    } else {
      await authStore.login(username.value.trim(), password.value)
    }

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/home'
    await router.push(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('login.loginFailed')
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
        <button class="login-mode-button" :class="{ 'is-active': mode === 'login' }" type="button" @click="mode = 'login'">
          {{ t('login.login') }}
        </button>
        <button class="login-mode-button" :class="{ 'is-active': mode === 'register' }" type="button" @click="mode = 'register'">
          {{ t('login.register') }}
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label class="login-field">
          <span>{{ t('login.username') }}</span>
          <input v-model="username" class="text-control" type="text" autocomplete="username" required />
        </label>

        <label v-if="mode === 'register'" class="login-field">
          <span>{{ t('login.displayName') }}</span>
          <input v-model="displayName" class="text-control" type="text" autocomplete="name" />
        </label>

        <label class="login-field">
          <span>{{ t('login.password') }}</span>
          <input v-model="password" class="text-control" type="password" autocomplete="current-password" required />
        </label>

        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <button class="side-button primary login-submit" type="submit" :disabled="pending">
          {{ pending ? t('common.processing') : mode === 'login' ? t('login.login') : t('login.registerAndLogin') }}
        </button>
      </form>
    </div>
  </div>
</template>
