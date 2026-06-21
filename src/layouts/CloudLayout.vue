<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CAMERA_STATUS_KEY, type CameraStatus } from '@/constants/cameraStatus'
import { useAuthStore } from '@/stores/auth'
import ToolIcon from '@/components/ToolIcon.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userMenuOpen = ref(false)
const userMenuRootRef = ref<HTMLElement | null>(null)

const cameraStatus = inject(CAMERA_STATUS_KEY, ref<CameraStatus>({
  longitude: '--',
  latitude: '--',
  elevation: '--',
  viewHeight: '--',
}))

const brandTitle = 'XJI Cloud'
const brandSubtitle = '建模解决方案云平台'

const navItems = [
  { label: '工程项目', route: '/app/projects' },
  { label: '搜索索引', route: null },
  { label: '双屏显示', route: null },
  { label: '用户空间', route: null },
  { label: '热力展示', route: null },
] as const

const toolItems = [
  { label: '航线规划', route: null, icon: 'route' },
  { label: '数据上传', route: '/app/upload', icon: 'upload' },
  { label: '模型查看', route: '/app/layer', icon: 'view' },
  { label: '高级编辑', route: '/app/supersplat', icon: 'edit' },
] as const

function isActiveNav(path: string | null) {
  return Boolean(path && route.path === path)
}

function navigate(path: string | null) {
  if (!path) {
    return
  }
  router.push(path)
}

function showComingSoon(label: string) {
  window.alert(`${label} 功能即将推出`)
}

function toggleUserMenu() {
  userMenuOpen.value = !userMenuOpen.value
}

function closeUserMenu() {
  userMenuOpen.value = false
}

function logout() {
  closeUserMenu()
  authStore.logout()
  router.push('/login')
}

function onDocumentClick(event: MouseEvent) {
  if (!userMenuOpen.value) {
    return
  }

  const target = event.target
  if (userMenuRootRef.value && target instanceof Node && !userMenuRootRef.value.contains(target)) {
    closeUserMenu()
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeUserMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})

const statusText = computed(() => cameraStatus.value)
const hideStatusBar = computed(() => route.name === 'supersplat')
</script>

<template>
  <div class="cloud-shell">
    <header class="cloud-header">
      <div class="cloud-brand">
        <img class="cloud-brand-logo" src="/logo.jpg" alt="XJI Cloud" />
        <div>
          <h1 class="cloud-brand-title">{{ brandTitle }}</h1>
          <p class="cloud-brand-subtitle">{{ brandSubtitle }}</p>
        </div>
      </div>

      <nav class="cloud-top-nav" aria-label="主导航">
        <button
          v-for="item in navItems"
          :key="item.label"
          class="cloud-nav-link"
          :class="{ 'is-active': isActiveNav(item.route) }"
          type="button"
          @click="item.route ? navigate(item.route) : showComingSoon(item.label)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="cloud-header-actions">
        <button class="cloud-icon-button" type="button" title="团队" @click="showComingSoon('团队')">团</button>
        <button class="cloud-icon-button" type="button" title="下载" @click="showComingSoon('下载')">下</button>
        <button class="cloud-icon-button" type="button" title="语言">中</button>
        <button class="cloud-icon-button" type="button" title="帮助" @click="showComingSoon('帮助')">?</button>

        <div ref="userMenuRootRef" class="cloud-user-menu-root">
          <button class="cloud-user-chip" type="button" @click.stop="toggleUserMenu">
            {{ authStore.displayName || authStore.username || '用户' }}
          </button>
          <div v-if="userMenuOpen" class="cloud-user-menu" role="menu">
            <div class="cloud-user-menu-info">
              <strong>{{ authStore.displayName || '未设置显示名' }}</strong>
              <span>@{{ authStore.username || 'unknown' }}</span>
            </div>
            <button class="cloud-user-menu-item" type="button" role="menuitem" @click="logout">
              退出登录
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="cloud-body">
      <aside class="cloud-tool-rail" aria-label="工具栏">
        <button
          v-for="item in toolItems"
          :key="item.label"
          class="cloud-tool-button"
          :class="{ 'is-active': isActiveNav(item.route) }"
          type="button"
          :title="item.label"
          @click="item.route ? navigate(item.route) : showComingSoon(item.label)"
        >
          <span class="cloud-tool-glyph"><ToolIcon :name="item.icon" /></span>
          <span class="cloud-tool-label">{{ item.label }}</span>
        </button>
      </aside>

      <main class="cloud-main">
        <RouterView />
      </main>
    </div>

    <footer v-if="!hideStatusBar" class="cloud-status-bar">
      <div class="cloud-status-metrics">
        <span>经度 {{ statusText.longitude }}</span>
        <span>纬度 {{ statusText.latitude }}</span>
        <span>高程 {{ statusText.elevation }}</span>
        <span>视角 {{ statusText.viewHeight }}</span>
      </div>
      <div class="cloud-status-hint">GIS 底图与双屏显示将在后续版本启用</div>
    </footer>
  </div>
</template>
