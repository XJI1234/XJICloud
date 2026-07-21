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
const userModalVisible = ref(false)
const langModalVisible = ref(false)
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

function openUserModal() {
  langModalVisible.value = false
  userModalVisible.value = true
}

function closeUserModal() {
  userModalVisible.value = false
}

function openLangModal() {
  userModalVisible.value = false
  langModalVisible.value = true
}

function closeLangModal() {
  langModalVisible.value = false
}

function closeAllModals() {
  closeUserModal()
  closeLangModal()
}

function selectLocale(next: 'zh' | 'en') {
  if (next === 'en') {
    closeLangModal()
    showComingSoon('English 语言包')
    return
  }

  locale.value = 'zh'
  localStorage.setItem('xjicloud_locale', 'zh')
  closeLangModal()
}

async function logout() {
  closeUserModal()
  clearUserSession()
  await router.replace({ name: 'login' })
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeAllModals()
  }
}

onMounted(() => {
  const savedLocale = localStorage.getItem('xjicloud_locale')
  if (savedLocale === 'zh') {
    locale.value = 'zh'
  }

  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
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

        <button class="cloud-header-tool-button" type="button" @click="openLangModal">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="language" />
          </span>
          <span class="cloud-header-tool-button__label">语言</span>
        </button>

        <button class="cloud-header-tool-button" type="button" @click="showComingSoon('帮助')">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="help" />
          </span>
          <span class="cloud-header-tool-button__label">帮助</span>
        </button>

        <button class="cloud-user-chip" type="button" @click="openUserModal">
          我的
        </button>
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

    <Teleport to="body">
      <div
        v-if="userModalVisible"
        class="header-modal-backdrop app-modal-backdrop"
        @click.self="closeUserModal"
      >
        <div class="app-modal header-modal" role="dialog" aria-labelledby="user-modal-title">
          <div class="app-modal-header">
            <h2 id="user-modal-title" class="app-modal-title">我的</h2>
          </div>
          <div class="app-modal-body">
            <div class="cloud-user-menu-info header-modal-user-info">
              <strong>{{ authStore.displayName || '未设置显示名' }}</strong>
              <span>@{{ authStore.username || 'unknown' }}</span>
            </div>
          </div>
          <div class="app-modal-footer header-modal-footer">
            <button class="side-button" type="button" @click="closeUserModal">关闭</button>
            <button class="side-button primary" type="button" @click="logout">退出登录</button>
          </div>
        </div>
      </div>

      <div
        v-if="langModalVisible"
        class="header-modal-backdrop app-modal-backdrop"
        @click.self="closeLangModal"
      >
        <div class="app-modal header-modal" role="dialog" aria-labelledby="lang-modal-title">
          <div class="app-modal-header">
            <h2 id="lang-modal-title" class="app-modal-title">语言</h2>
          </div>
          <div class="app-modal-body header-modal-options">
            <button
              class="header-modal-option cloud-user-menu-item"
              :class="{ 'is-active': locale === 'zh' }"
              type="button"
              @click="selectLocale('zh')"
            >
              中文
            </button>
            <button
              class="header-modal-option cloud-user-menu-item"
              :class="{ 'is-active': locale === 'en' }"
              type="button"
              @click="selectLocale('en')"
            >
              English
            </button>
          </div>
          <div class="app-modal-footer header-modal-footer">
            <button class="side-button" type="button" @click="closeLangModal">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
