<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { useFormatDateTime } from '@/composables/useAppLocale'

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
    <div class="cloud-grain" aria-hidden="true" />
    <div class="home-page__orb home-page__orb--blue" aria-hidden="true" />
    <div class="home-page__orb home-page__orb--blue-left" aria-hidden="true" />
    <div class="home-page__orb home-page__orb--amber" aria-hidden="true" />
    <div class="home-edge-label" aria-hidden="true">XJI CLOUD</div>

    <div class="home-grid">
      <div class="home-hero-main">
        <p class="home-eyebrow">{{ t('brand.title') }}</p>
        <h1 class="home-display-title">{{ t('brand.subtitle') }}</h1>

        <div class="home-actions">
          <button class="home-primary-button" type="button" @click="openCreateDialog">
            {{ t('home.newProject') }}
          </button>
          <button class="home-secondary-button" type="button" @click="openProjectPicker">
            {{ t('home.openProject') }}
          </button>
        </div>

        <p v-if="errorMessage && !createDialogVisible && !openDialogVisible" class="home-error">{{ errorMessage }}</p>
      </div>

      <aside class="home-recent-section">
        <h2 class="home-recent-title">{{ t('home.recentProjects', { count: projectStore.recentProjects.length }) }}</h2>
        <p v-if="projectStore.loading" class="home-recent-empty">{{ t('common.loading') }}</p>
        <p v-else-if="projectStore.recentProjects.length === 0" class="home-recent-empty">{{ t('home.noRecentProjects') }}</p>
        <ul v-else class="home-recent-list">
          <li v-for="project in projectStore.recentProjects" :key="project.id">
            <button class="home-recent-card" type="button" @click="openRecentProject(project.id)">
              <p class="home-recent-card__name">{{ project.name }}</p>
              <p class="home-recent-card__desc">{{ project.description || t('common.noDescription') }}</p>
              <p class="home-recent-card__meta">
                {{ t('home.lastOpened', { date: formatDateTime(project.openedAt) }) }}
              </p>
            </button>
          </li>
        </ul>
      </aside>
    </div>

    <div v-if="createDialogVisible" class="app-modal-backdrop" @click.self="createDialogVisible = false">
      <div class="app-modal home-dialog">
        <div class="app-modal-header">
          <h2 class="app-modal-title">{{ t('home.createProjectTitle') }}</h2>
        </div>
        <div class="app-modal-body home-dialog-body">
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
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="createDialogVisible = false">{{ t('common.cancel') }}</button>
          <button class="side-button primary" type="button" :disabled="pending" @click="submitCreateProject">
            {{ pending ? t('home.creating') : t('home.createAndOpen') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="openDialogVisible" class="app-modal-backdrop" @click.self="openDialogVisible = false">
      <div class="app-modal home-dialog">
        <div class="app-modal-header">
          <h2 class="app-modal-title">{{ t('home.openProjectTitle') }}</h2>
        </div>
        <div class="app-modal-body home-dialog-body">
          <p v-if="projectStore.projects.length === 0" class="home-recent-empty">{{ t('home.noProjects') }}</p>
          <div v-else class="home-project-picker">
            <button
              v-for="project in projectStore.projects"
              :key="project.id"
              class="home-picker-item"
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
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="openDialogVisible = false">{{ t('common.cancel') }}</button>
          <button class="side-button primary" type="button" @click="confirmOpenProject">{{ t('common.open') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>
