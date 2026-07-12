<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CAMERA_STATUS_KEY, type CameraStatus } from '@/constants/cameraStatus'
import ToolIcon from '@/components/ToolIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { clearUserSession } from '@/utils/session'
import { showComingSoon } from '@/utils/comingSoon'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userMenuOpen = ref(false)
const langMenuOpen = ref(false)
const userMenuRootRef = ref<HTMLElement | null>(null)
const langMenuRootRef = ref<HTMLElement | null>(null)
const locale = ref<'zh' | 'en'>('zh')

const cameraStatus = inject(CAMERA_STATUS_KEY, ref<CameraStatus>({
  longitude: '--',
  latitude: '--',
  elevation: '--',
  viewHeight: '--',
}))

const brandTitle = 'XJI Cloud'
const brandSubtitle = '建模解决方案云平台'

const navItems = [
  { label: '首页', route: '/app/home' },
  { label: '工程项目', route: '/app/projects' },
  { label: '搜索索引', route: null },
  { label: '双屏显示', route: null },
  { label: '用户空间', route: null },
  { label: '热力展示', route: null },
] as const

const toolItems = [
  { label: '航线规划', route: null, icon: 'route' as const },
  { label: '数据上传', route: '/app/upload', icon: 'upload' as const },
  { label: '模型查看', route: '/app/layer', icon: 'view' as const },
  { label: '高级编辑', route: '/app/supersplat', icon: 'edit' as const },
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

function goHome() {
  if (route.path !== '/app/home') {
    router.push('/app/home')
  }
}

function toggleUserMenu() {
  langMenuOpen.value = false
  userMenuOpen.value = !userMenuOpen.value
}

function closeUserMenu() {
  userMenuOpen.value = false
}

function toggleLangMenu() {
  userMenuOpen.value = false
  langMenuOpen.value = !langMenuOpen.value
}

function closeLangMenu() {
  langMenuOpen.value = false
}

function selectLocale(next: 'zh' | 'en') {
  if (next === 'en') {
    showComingSoon('English 语言包')
    closeLangMenu()
    return
  }

  locale.value = 'zh'
  localStorage.setItem('xjicloud_locale', 'zh')
  closeLangMenu()
}

async function logout() {
  closeUserMenu()
  clearUserSession()
  await router.replace({ name: 'login' })
}

function onDocumentClick(event: MouseEvent) {
  const target = event.target

  if (userMenuOpen.value) {
    if (userMenuRootRef.value && target instanceof Node && !userMenuRootRef.value.contains(target)) {
      closeUserMenu()
    }
  }

  if (langMenuOpen.value) {
    if (langMenuRootRef.value && target instanceof Node && !langMenuRootRef.value.contains(target)) {
      closeLangMenu()
    }
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeUserMenu()
    closeLangMenu()
  }
}

onMounted(() => {
  const savedLocale = localStorage.getItem('xjicloud_locale')
  if (savedLocale === 'zh') {
    locale.value = 'zh'
  }

  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})

const statusText = computed(() => cameraStatus.value)
const hideStatusBar = computed(() => route.name === 'supersplat')
const isImmersiveRoute = computed(() => route.name === 'home')
</script>

<template>
  <div class="cloud-shell" :class="{ 'cloud-shell--immersive': isImmersiveRoute }">
    <div class="cloud-shell__backdrop" aria-hidden="true" />
    <div class="cloud-edge-accent" aria-hidden="true" />
    <header class="cloud-header">
      <div class="cloud-brand">
        <button class="cloud-brand-button" type="button" title="返回首页" @click="goHome">
          <img class="cloud-brand-logo" src="/logo.jpg" alt="XJI Cloud" />
          <div>
            <h1 class="cloud-brand-title">{{ brandTitle }}</h1>
            <p class="cloud-brand-subtitle">{{ brandSubtitle }}</p>
          </div>
        </button>
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
        <button class="cloud-header-tool-button" type="button" @click="showComingSoon('团队')">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="team" />
          </span>
          <span class="cloud-header-tool-button__label">团队</span>
        </button>

        <div ref="langMenuRootRef" class="cloud-user-menu-root">
          <button class="cloud-header-tool-button" type="button" @click.stop="toggleLangMenu">
            <span class="cloud-header-tool-button__icon">
              <ToolIcon name="language" />
            </span>
            <span class="cloud-header-tool-button__label">语言</span>
            <span class="cloud-header-tool-button__caret" aria-hidden="true">▾</span>
          </button>
          <div v-if="langMenuOpen" class="cloud-user-menu cloud-lang-menu" role="menu">
            <button
              class="cloud-user-menu-item"
              :class="{ 'is-active': locale === 'zh' }"
              type="button"
              role="menuitem"
              @click="selectLocale('zh')"
            >
              中文
            </button>
            <button
              class="cloud-user-menu-item"
              :class="{ 'is-active': locale === 'en' }"
              type="button"
              role="menuitem"
              @click="selectLocale('en')"
            >
              English
            </button>
          </div>
        </div>

        <button class="cloud-header-tool-button" type="button" @click="showComingSoon('帮助')">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="help" />
          </span>
          <span class="cloud-header-tool-button__label">帮助</span>
        </button>

        <div ref="userMenuRootRef" class="cloud-user-menu-root">
          <button class="cloud-user-chip" type="button" @click.stop="toggleUserMenu">
            我的
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
          <span class="cloud-tool-glyph">
            <ToolIcon :name="item.icon" />
          </span>
          <span class="cloud-tool-label">{{ item.label }}</span>
        </button>
      </aside>

      <main class="cloud-main">
        <RouterView v-slot="{ Component, route: childRoute }">
          <Transition :name="(childRoute.meta.transition as string) ?? 'cloud-page'" mode="out-in">
            <component :is="Component" :key="childRoute.path" />
          </Transition>
        </RouterView>
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
