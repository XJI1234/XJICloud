<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { CAMERA_STATUS_KEY, type CameraStatus } from '@/constants/cameraStatus'
import CloudSheet from '@/components/CloudSheet.vue'
import ToolIcon from '@/components/ToolIcon.vue'
import { useAppLocale } from '@/composables/useAppLocale'
import { useAuthStore } from '@/stores/auth'
import { clearUserSession } from '@/utils/session'
import { showComingSoon } from '@/utils/comingSoon'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const { currentLocale, setLocale } = useAppLocale()

const userModalVisible = ref(false)
const langModalVisible = ref(false)
const userTriggerRef = ref<HTMLButtonElement | null>(null)
const langTriggerRef = ref<HTMLButtonElement | null>(null)

const cameraStatus = inject(CAMERA_STATUS_KEY, ref<CameraStatus>({
  longitude: '--',
  latitude: '--',
  elevation: '--',
  viewHeight: '--',
}))

const brandTitle = computed(() => t('brand.title'))
const brandSubtitle = computed(() => t('brand.subtitle'))

const navItems = computed(() => [
  { labelKey: 'nav.home', route: '/app/home' },
  { labelKey: 'nav.projects', route: '/app/projects' },
  { labelKey: 'nav.searchIndex', route: null },
  { labelKey: 'nav.dualScreen', route: null },
  { labelKey: 'nav.userSpace', route: null },
] as const)

const toolItems = computed(() => [
  { labelKey: 'tools.routePlanning', route: null, icon: 'route' as const },
  { labelKey: 'tools.dataUpload', route: '/app/upload', icon: 'upload' as const },
  { labelKey: 'tools.modelView', route: '/app/layer', icon: 'view' as const },
  { labelKey: 'tools.advancedEdit', route: '/app/supersplat', icon: 'edit' as const },
] as const)

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
  setLocale(next)
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
    <header class="cloud-header cloud-scroll-edge--bottom">
      <div class="cloud-brand">
        <button class="cloud-brand-button" type="button" :title="t('nav.home')" @click="goHome">
          <img class="cloud-brand-logo" src="/logo.jpg" alt="XJI Cloud" />
          <div>
            <h1 class="cloud-brand-title">{{ brandTitle }}</h1>
            <p class="cloud-brand-subtitle">{{ brandSubtitle }}</p>
          </div>
        </button>
      </div>

      <nav class="cloud-top-nav" aria-label="Main navigation">
        <button
          v-for="item in navItems"
          :key="item.labelKey"
          class="cloud-nav-link"
          :class="{ 'is-active': isActiveNav(item.route) }"
          type="button"
          @click="item.route ? navigate(item.route) : showComingSoon(item.labelKey)"
        >
          {{ t(item.labelKey) }}
        </button>
      </nav>

      <div class="cloud-header-actions">
        <button class="cloud-header-tool-button" type="button" @click="showComingSoon('header.team')">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="team" />
          </span>
          <span class="cloud-header-tool-button__label">{{ t('header.team') }}</span>
        </button>

        <button
          id="lang-trigger"
          ref="langTriggerRef"
          class="cloud-header-tool-button"
          type="button"
          :aria-expanded="langModalVisible"
          aria-haspopup="dialog"
          @click="openLangModal"
        >
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="language" />
          </span>
          <span class="cloud-header-tool-button__label">{{ t('header.language') }}</span>
        </button>

        <button class="cloud-header-tool-button" type="button" @click="showComingSoon('header.help')">
          <span class="cloud-header-tool-button__icon">
            <ToolIcon name="help" />
          </span>
          <span class="cloud-header-tool-button__label">{{ t('header.help') }}</span>
        </button>

        <button
          id="user-trigger"
          ref="userTriggerRef"
          class="cloud-user-chip"
          type="button"
          :aria-expanded="userModalVisible"
          aria-haspopup="dialog"
          @click="openUserModal"
        >
          {{ t('header.mine') }}
        </button>
      </div>
    </header>

    <div class="cloud-body">
      <aside class="cloud-tool-rail" aria-label="Toolbar">
        <button
          v-for="item in toolItems"
          :key="item.labelKey"
          class="cloud-tool-button"
          :class="{ 'is-active': isActiveNav(item.route) }"
          type="button"
          :title="t(item.labelKey)"
          @click="item.route ? navigate(item.route) : showComingSoon(item.labelKey)"
        >
          <span class="cloud-tool-glyph">
            <ToolIcon :name="item.icon" />
          </span>
          <span class="cloud-tool-label">{{ t(item.labelKey) }}</span>
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

    <footer v-if="!hideStatusBar" class="cloud-status-bar cloud-scroll-edge--top">
      <div class="cloud-status-metrics">
        <span>{{ t('status.longitude') }} {{ statusText.longitude }}</span>
        <span>{{ t('status.latitude') }} {{ statusText.latitude }}</span>
        <span>{{ t('status.elevation') }} {{ statusText.elevation }}</span>
        <span>{{ t('status.viewHeight') }} {{ statusText.viewHeight }}</span>
      </div>
      <div class="cloud-status-hint">{{ t('status.gisHint') }}</div>
    </footer>

    <CloudSheet
      :visible="userModalVisible"
      mode="popover"
      :anchor-el="userTriggerRef"
      :title="t('header.mine')"
      @close="closeUserModal"
    >
      <div class="cloud-user-menu-info header-modal-user-info">
        <strong>{{ authStore.displayName || t('common.notSet') }}</strong>
        <span>@{{ authStore.username || 'unknown' }}</span>
      </div>
      <template #footer>
        <button class="side-button" type="button" @click="closeUserModal">{{ t('common.close') }}</button>
        <button class="side-button primary" type="button" @click="logout">{{ t('header.logout') }}</button>
      </template>
    </CloudSheet>

    <CloudSheet
      :visible="langModalVisible"
      mode="popover"
      :anchor-el="langTriggerRef"
      :title="t('header.language')"
      @close="closeLangModal"
    >
      <div class="header-modal-options">
        <button
          class="header-modal-option cloud-user-menu-item cloud-pressable"
          :class="{ 'is-active': currentLocale === 'zh' }"
          type="button"
          @click="selectLocale('zh')"
        >
          {{ t('header.langZh') }}
        </button>
        <button
          class="header-modal-option cloud-user-menu-item cloud-pressable"
          :class="{ 'is-active': currentLocale === 'en' }"
          type="button"
          @click="selectLocale('en')"
        >
          {{ t('header.langEn') }}
        </button>
      </div>
      <template #footer>
        <button class="side-button" type="button" @click="closeLangModal">{{ t('common.close') }}</button>
      </template>
    </CloudSheet>
  </div>
</template>
