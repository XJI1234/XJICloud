<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import CloudSheet from '@/components/CloudSheet.vue'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { useFormatDateTime } from '@/composables/useAppLocale'
import { showComingSoon } from '@/utils/comingSoon'

const router = useRouter()
const projectStore = useProjectStore()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const errorMessage = ref('')
const createDialogVisible = ref(false)
const openDialogVisible = ref(false)
const newProjectName = ref('')
const newProjectDescription = ref('')
const selectedProjectId = ref<string | null>(null)
const pending = ref(false)

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('home.loadProjectsFailed')
  }
})

function openCreateDialog() {
  newProjectName.value = ''
  newProjectDescription.value = ''
  errorMessage.value = ''
  createDialogVisible.value = true
}

function openProjectPicker() {
  selectedProjectId.value = projectStore.activeProjectId
  errorMessage.value = ''
  openDialogVisible.value = true
}

async function submitCreateProject() {
  if (!newProjectName.value.trim()) {
    errorMessage.value = t('home.enterProjectName')
    return
  }

  pending.value = true
  try {
    await projectStore.createProject(newProjectName.value.trim(), newProjectDescription.value.trim())
    createDialogVisible.value = false
    await router.push('/app/projects')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('home.createProjectFailed')
  } finally {
    pending.value = false
  }
}

function confirmOpenProject() {
  if (!selectedProjectId.value) {
    errorMessage.value = t('home.selectProject')
    return
  }

  projectStore.openProject(selectedProjectId.value)
  openDialogVisible.value = false
  router.push('/app/projects')
}

function openRecentProject(projectId: string) {
  projectStore.openProject(projectId)
  router.push('/app/projects')
}
</script>

<template>
  <div class="home-page">
    <div class="home-page__atmosphere" aria-hidden="true" />
    <div class="home-page__grid" aria-hidden="true" />
    <div class="cloud-grain" aria-hidden="true" />
    <div class="home-page__orb home-page__orb--blue" aria-hidden="true" />
    <div class="home-page__orb home-page__orb--amber" aria-hidden="true" />

    <div class="home-grid">
      <div class="home-hero-main">
        <p class="home-eyebrow">{{ t('brand.title') }}</p>
        <h1 class="home-display-title">{{ t('brand.subtitle') }}</h1>
        <p class="home-hero-subtitle">{{ t('home.heroSubtitle') }}</p>

        <div class="home-actions">
          <button class="home-primary-button cloud-pressable" type="button" @click="openCreateDialog">
            {{ t('home.newProject') }}
          </button>
          <button class="home-secondary-button cloud-pressable" type="button" @click="openProjectPicker">
            {{ t('home.openProject') }}
          </button>
        </div>

        <p v-if="errorMessage && !createDialogVisible && !openDialogVisible" class="home-error">{{ errorMessage }}</p>
      </div>

      <aside class="home-visual" :aria-label="t('home.featureTitle')">
        <article class="home-feature-card">
          <div class="home-feature-card__copy">
            <p class="home-feature-eyebrow">{{ t('home.featureEyebrow') }}</p>
            <h2 class="home-feature-title">{{ t('home.featureTitle') }}</h2>
            <p class="home-feature-desc">{{ t('home.featureDesc') }}</p>
            <button
              class="home-primary-button home-feature-cta cloud-pressable"
              type="button"
              @click="showComingSoon('tools.routePlanning')"
            >
              {{ t('home.featureCta') }}
            </button>
          </div>
          <div class="home-feature-card__scene" aria-hidden="true">
            <div class="home-feature-scene">
              <div class="home-feature-scene__platform" />
              <div class="home-feature-scene__buildings">
                <span class="home-feature-scene__building home-feature-scene__building--a" />
                <span class="home-feature-scene__building home-feature-scene__building--b" />
                <span class="home-feature-scene__building home-feature-scene__building--c" />
              </div>
              <svg class="home-feature-scene__orbit" viewBox="0 0 240 140" fill="none" xmlns="http://www.w3.org/2000/svg">
                <ellipse
                  class="home-feature-scene__orbit-path"
                  cx="120"
                  cy="78"
                  rx="98"
                  ry="42"
                  stroke="currentColor"
                  stroke-width="1.5"
                  stroke-dasharray="6 5"
                />
                <circle class="home-feature-scene__drone" cx="218" cy="78" r="4" fill="currentColor" />
              </svg>
            </div>
            <p class="home-feature-scene__caption">{{ t('home.featureSceneCaption') }}</p>
          </div>
        </article>
      </aside>
    </div>

    <section class="home-recent-section" :aria-label="t('home.recentProjects', { count: projectStore.recentProjects.length })">
      <h2 class="home-recent-title">{{ t('home.recentProjects', { count: projectStore.recentProjects.length }) }}</h2>
      <p v-if="projectStore.loading" class="home-recent-empty">{{ t('common.loading') }}</p>
      <p v-else-if="projectStore.recentProjects.length === 0" class="home-recent-empty">{{ t('home.noRecentProjects') }}</p>
      <ul v-else class="home-recent-list">
        <li v-for="project in projectStore.recentProjects" :key="project.id">
          <button class="home-recent-card cloud-pressable" type="button" @click="openRecentProject(project.id)">
            <p class="home-recent-card__name">{{ project.name }}</p>
            <p class="home-recent-card__desc">{{ project.description || t('common.noDescription') }}</p>
            <p class="home-recent-card__meta">
              {{ t('home.lastOpened', { date: formatDateTime(project.openedAt) }) }}
            </p>
          </button>
        </li>
      </ul>
    </section>

    <CloudSheet
      :visible="createDialogVisible"
      mode="center"
      :title="t('home.createProjectTitle')"
      @close="createDialogVisible = false"
    >
      <div class="home-dialog-body">
        <label class="field-label" for="home-project-name">{{ t('home.projectName') }}</label>
        <input
          id="home-project-name"
          v-model="newProjectName"
          class="text-control"
          type="text"
          :placeholder="t('home.projectNamePlaceholder')"
        />
        <label class="field-label" for="home-project-desc">{{ t('home.projectDesc') }}</label>
        <input
          id="home-project-desc"
          v-model="newProjectDescription"
          class="text-control"
          type="text"
          :placeholder="t('home.projectDescPlaceholder')"
        />
        <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
      </div>
      <template #footer>
        <button class="side-button" type="button" @click="createDialogVisible = false">{{ t('common.cancel') }}</button>
        <button class="side-button primary" type="button" :disabled="pending" @click="submitCreateProject">
          {{ pending ? t('home.creating') : t('home.createAndOpen') }}
        </button>
      </template>
    </CloudSheet>

    <CloudSheet
      :visible="openDialogVisible"
      mode="center"
      :title="t('home.openProjectTitle')"
      @close="openDialogVisible = false"
    >
      <div class="home-dialog-body">
        <p v-if="projectStore.projects.length === 0" class="home-recent-empty">{{ t('home.noProjects') }}</p>
        <div v-else class="home-project-picker">
          <button
            v-for="project in projectStore.projects"
            :key="project.id"
            class="home-picker-item cloud-pressable"
            :class="{ 'is-selected': selectedProjectId === project.id }"
            type="button"
            @click="selectedProjectId = project.id"
          >
            <strong>{{ project.name }}</strong>
            <span>{{ project.description || t('common.noDescription') }}</span>
          </button>
        </div>
        <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
      </div>
      <template #footer>
        <button class="side-button" type="button" @click="openDialogVisible = false">{{ t('common.cancel') }}</button>
        <button class="side-button primary" type="button" @click="confirmOpenProject">{{ t('common.open') }}</button>
      </template>
    </CloudSheet>
  </div>
</template>
