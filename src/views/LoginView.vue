<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/client'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

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
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败，请稍后重试'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <p class="login-eyebrow">XJI Cloud</p>
      <h1 class="login-title">建模解决方案云平台</h1>
      <p class="login-subtitle">登录后管理工程项目与 3DGS 模型查看</p>

      <div class="login-mode-row">
        <button class="login-mode-button" :class="{ 'is-active': mode === 'login' }" type="button" @click="mode = 'login'">
          登录
        </button>
        <button class="login-mode-button" :class="{ 'is-active': mode === 'register' }" type="button" @click="mode = 'register'">
          注册
        </button>
      </div>

      <form class="login-form" @submit.prevent="submit">
        <label class="login-field">
          <span>用户名</span>
          <input v-model="username" class="text-control" type="text" autocomplete="username" required />
        </label>

        <label v-if="mode === 'register'" class="login-field">
          <span>显示名称</span>
          <input v-model="displayName" class="text-control" type="text" autocomplete="name" />
        </label>

        <label class="login-field">
          <span>密码</span>
          <input v-model="password" class="text-control" type="password" autocomplete="current-password" required />
        </label>

        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <button class="side-button primary login-submit" type="submit" :disabled="pending">
          {{ pending ? '处理中...' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
