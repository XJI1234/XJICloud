<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import AppSheet from '@/presentation/components/AppSheet.vue'
import AppButton from '@/presentation/components/AppButton.vue'
import ToolIcon from '@/presentation/components/ToolIcon.vue'
import { useAppLocale } from '@/presentation/composables/useAppLocale'
import { useAuthSession } from '@/features/identity/presentation/composables/useAuthSession'
import { showComingSoon } from '@/presentation/coming-soon'
import { CONTAINER_KEY } from '@/shared/di'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { currentLocale, setLocale } = useAppLocale()
const auth = useAuthSession()
const container = inject(CONTAINER_KEY)!
const activeProjectId = ref(container.workspace.getActiveProjectId())

const userModalVisible = ref(false)
const langModalVisible = ref(false)
const userTriggerRef = ref<HTMLButtonElement | null>(null)
const langTriggerRef = ref<HTMLButtonElement | null>(null)
const session = ref(auth.session())

const navItems = computed(() => [
  { labelKey: 'nav.home', route: '/app/home' },
  { labelKey: 'nav.projects', route: '/app/projects' },
  { labelKey: 'nav.searchIndex', route: null },
  { labelKey: 'nav.dualScreen', route: null },
  { labelKey: 'nav.userSpace', route: null },
] as const)

const toolItems = computed(() => [
  { labelKey: 'tools.routePlanning', route: '/app/wayline', icon: 'route' as const },
  { labelKey: 'tools.dataUpload', route: '/app/upload', icon: 'upload' as const },
  { labelKey: 'tools.modelView', route: '/app/layer', icon: 'view' as const },
  { labelKey: 'tools.advancedEdit', route: '/app/supersplat', icon: 'edit' as const },
] as const)

function isActiveNav(path: string | null) {
  return Boolean(path && (route.path === path || route.path.startsWith(`${path}/`)))
}

function navigate(path: string | null) {
  if (!path) {
    return
  }
  void router.push(path)
}

function announceComingSoon(labelKey: string) {
  showComingSoon(t('common.comingSoon', { feature: t(labelKey) }))
}

function goHome() {
  if (route.path !== '/app/home') {
    void router.push('/app/home')
  }
}

async function logout() {
  userModalVisible.value = false
  auth.logout()
  await router.replace({ name: 'login' })
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    userModalVisible.value = false
    langModalVisible.value = false
  }
}

let unsubscribeWorkspace: (() => void) | undefined

onMounted(() => {
  session.value = auth.session()
  activeProjectId.value = container.workspace.getActiveProjectId()
  unsubscribeWorkspace = container.workspace.subscribe(() => {
    activeProjectId.value = container.workspace.getActiveProjectId()
  })
  document.addEventListener('keydown', onDocumentKeydown)
})

onBeforeUnmount(() => {
  unsubscribeWorkspace?.()
  document.removeEventListener('keydown', onDocumentKeydown)
})

const fillMain = computed(
  () =>
    route.name === 'layer' ||
    route.name === 'supersplat' ||
    route.name === 'wayline' ||
    route.name === 'wayline-editor',
)
const isImmersive = computed(
  () => route.name === 'home' || route.name === 'wayline-editor' || !activeProjectId.value,
)
</script>

<template>
  <div class="cloud-shell" :class="{ 'cloud-shell--immersive': isImmersive }">
    <header class="cloud-header">
      <div class="cloud-brand">
        <button class="cloud-brand-button" type="button" :title="t('nav.home')" @click="goHome">
          <img class="cloud-brand-logo" src="/logo.jpg" alt="XJI Cloud" />
          <div>
            <h1 class="cloud-brand-title">{{ t('brand.title') }}</h1>
            <p class="cloud-brand-subtitle">{{ t('brand.subtitle') }}</p>
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
          @click="item.route ? navigate(item.route) : announceComingSoon(item.labelKey)"
        >
          {{ t(item.labelKey) }}
        </button>
      </nav>

      <div class="cloud-header-actions">
        <button class="cloud-header-tool-button" type="button" @click="announceComingSoon('header.team')">
          <ToolIcon name="team" />
          <span>{{ t('header.team') }}</span>
        </button>
        <button
          ref="langTriggerRef"
          class="cloud-header-tool-button"
          type="button"
          :aria-expanded="langModalVisible"
          aria-haspopup="dialog"
          @click="langModalVisible = true; userModalVisible = false"
        >
          <ToolIcon name="language" />
          <span>{{ t('header.language') }}</span>
        </button>
        <button class="cloud-header-tool-button" type="button" @click="navigate('/app/help')">
          <ToolIcon name="help" />
          <span>{{ t('header.help') }}</span>
        </button>
        <button
          ref="userTriggerRef"
          class="cloud-user-chip"
          type="button"
          :aria-expanded="userModalVisible"
          aria-haspopup="dialog"
          @click="userModalVisible = true; langModalVisible = false"
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
          @click="navigate(item.route)"
        >
          <ToolIcon :name="item.icon" />
          <span class="cloud-tool-label">{{ t(item.labelKey) }}</span>
        </button>
      </aside>

      <main class="cloud-main" :class="{ 'cloud-main--fill': fillMain }">
        <RouterView v-slot="{ Component, route: childRoute }">
          <Transition :name="(childRoute.meta.transition as string) ?? 'app-page'" mode="out-in">
            <component :is="Component" :key="childRoute.path" />
          </Transition>
        </RouterView>
      </main>
    </div>

    <AppSheet
      :visible="userModalVisible"
      mode="popover"
      :anchor-el="userTriggerRef"
      :title="t('header.mine')"
      @close="userModalVisible = false"
    >
      <div class="cloud-user-menu-info">
        <strong>{{ session?.displayName || t('common.notSet') }}</strong>
        <span>@{{ session?.username || 'unknown' }}</span>
      </div>
      <template #footer>
        <AppButton @click="userModalVisible = false">{{ t('common.close') }}</AppButton>
        <AppButton variant="primary" @click="logout">{{ t('header.logout') }}</AppButton>
      </template>
    </AppSheet>

    <AppSheet
      :visible="langModalVisible"
      mode="popover"
      :anchor-el="langTriggerRef"
      :title="t('header.language')"
      @close="langModalVisible = false"
    >
      <div class="header-modal-options">
        <button class="header-modal-option" :class="{ 'is-active': currentLocale === 'zh' }" type="button" @click="setLocale('zh'); langModalVisible = false">
          {{ t('header.langZh') }}
        </button>
        <button class="header-modal-option" :class="{ 'is-active': currentLocale === 'en' }" type="button" @click="setLocale('en'); langModalVisible = false">
          {{ t('header.langEn') }}
        </button>
      </div>
    </AppSheet>
  </div>
</template>
