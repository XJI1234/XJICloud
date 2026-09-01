<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAdminAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAdminAuthStore()

const navItems = [
  { label: '概览', name: 'dashboard' },
  { label: 'OSS 配置', name: 'oss' },
  { label: '算力容器', name: 'workers' },
  { label: '训练任务', name: 'jobs' },
]

const activeName = computed(() => route.name)

function navigate(name: string) {
  router.push({ name })
}

function logout() {
  authStore.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <h1>XJI Cloud Admin</h1>
        <p>{{ authStore.username || '管理员' }}</p>
      </div>
      <nav class="admin-nav">
        <button
          v-for="item in navItems"
          :key="item.name"
          class="admin-nav-link"
          :class="{ 'is-active': activeName === item.name }"
          type="button"
          @click="navigate(item.name)"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="admin-actions" style="margin-top: 24px">
        <button class="cloud-btn cloud-btn--ghost" type="button" @click="logout">退出登录</button>
      </div>
    </aside>
    <main class="admin-main">
      <RouterView />
    </main>
  </div>
</template>
