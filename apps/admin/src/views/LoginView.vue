<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useAdminAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()
const username = ref('admin')
const password = ref('')
const errorMessage = ref('')
const pending = ref(false)

async function submit() {
  pending.value = true
  errorMessage.value = ''
  try {
    await authStore.login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '登录失败'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="login-card admin-form" @submit.prevent="submit">
      <h1 class="admin-page-title">管理控制面板</h1>
      <p>默认账号：admin / admin123（首次启动自动创建，生产环境请修改）</p>
      <label class="admin-field">
        <span>用户名</span>
        <input v-model="username" class="admin-input" type="text" autocomplete="username" />
      </label>
      <label class="admin-field">
        <span>密码</span>
        <input v-model="password" class="admin-input" type="password" autocomplete="current-password" />
      </label>
      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
      <button class="cloud-btn cloud-btn--primary" type="submit" :disabled="pending">
        {{ pending ? '登录中...' : '登录' }}
      </button>
    </form>
  </div>
</template>
