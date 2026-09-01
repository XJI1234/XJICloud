<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import AppSheet from '@/presentation/components/AppSheet.vue'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { formatDomainError } from '@/presentation/errors'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import type { Project } from '@/features/project/domain/entities/project.entity'

const router = useRouter()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()
const workspace = useProjectWorkspace()

const projects = ref<Project[]>([])
const loading = ref(true)
const errorMessage = ref('')
const loadError = ref('')
const createDialogVisible = ref(false)
const openDialogVisible = ref(false)
const newProjectName = ref('')
const newProjectDescription = ref('')
const selectedProjectId = ref<string | null>(null)
const pending = ref(false)

const recentProjects = computed(() => workspace.recent(projects.value))

onMounted(async () => {
  loading.value = true
  const [error, data] = await workspace.load()
  loading.value = false
  if (error) {
    loadError.value = t('home.loadProjectsFailed')
    return
  }
  loadError.value = ''
  projects.value = data ?? []
})

function openCreateDialog() {
  newProjectName.value = ''
  newProjectDescription.value = ''
  errorMessage.value = ''
  createDialogVisible.value = true
}

function openProjectPicker() {
  selectedProjectId.value = workspace.activeProjectId()
  errorMessage.value = ''
  openDialogVisible.value = true
}

async function submitCreateProject() {
  pending.value = true
  const [error, project] = await workspace.create({
    name: newProjectName.value.trim(),
    description: newProjectDescription.value.trim(),
  })
  pending.value = false
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  if (project) {
    projects.value = [project, ...projects.value.filter((item) => item.id !== project.id)]
  }
  createDialogVisible.value = false
  await router.push('/app/projects')
}

function confirmOpenProject() {
  if (!selectedProjectId.value) {
    errorMessage.value = t('home.selectProject')
    return
  }
  workspace.open(selectedProjectId.value)
  openDialogVisible.value = false
  void router.push('/app/projects')
}

function openRecentProject(projectId: string) {
  workspace.open(projectId)
  void router.push('/app/projects')
}
</script>

<template>
  <div class="home-page">
    <div class="home-grid">
      <div class="home-hero">
        <h1 class="home-display-title">{{ t('brand.subtitle') }}</h1>
        <p class="home-hero-subtitle">{{ t('home.heroSubtitle') }}</p>
        <div class="home-actions">
          <AppButton variant="primary" @click="openCreateDialog">{{ t('home.newProject') }}</AppButton>
          <AppButton @click="openProjectPicker">{{ t('home.openProject') }}</AppButton>
        </div>
      </div>
      <aside class="home-recent-rail">
        <h2 class="section-title">{{ t('home.recentProjects', { count: recentProjects.length }) }}</h2>
        <p v-if="loading" class="home-recent-empty">{{ t('common.loading') }}</p>
        <p v-else-if="loadError" class="home-recent-empty">{{ loadError }}</p>
        <p v-else-if="recentProjects.length === 0" class="home-recent-empty">{{ t('home.noRecentProjects') }}</p>
        <ul v-else class="home-recent-list">
          <li v-for="project in recentProjects" :key="project.id">
            <button class="home-recent-card" type="button" @click="openRecentProject(project.id)">
              <p class="home-recent-card__name">{{ project.name }}</p>
              <p class="home-recent-card__desc">{{ project.description || t('common.noDescription') }}</p>
              <p class="home-recent-card__meta">{{ t('home.lastOpened', { date: formatDateTime(project.openedAt) }) }}</p>
            </button>
          </li>
        </ul>
      </aside>
    </div>
    <img class="home-mark" src="/logo_nw.png" alt="" aria-hidden="true" />

    <AppSheet :visible="createDialogVisible" :title="t('home.createProjectTitle')" @close="createDialogVisible = false">
      <div class="login-fields">
        <label class="cloud-field">
          <span>{{ t('home.projectName') }}</span>
          <input v-model="newProjectName" class="cloud-input" type="text" :placeholder="t('home.projectNamePlaceholder')" />
        </label>
        <label class="cloud-field">
          <span>{{ t('home.projectDesc') }}</span>
          <input v-model="newProjectDescription" class="cloud-input" type="text" :placeholder="t('home.projectDescPlaceholder')" />
        </label>
        <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
      </div>
      <template #footer>
        <AppButton @click="createDialogVisible = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" :disabled="pending" @click="submitCreateProject">
          {{ pending ? t('home.creating') : t('home.createAndOpen') }}
        </AppButton>
      </template>
    </AppSheet>

    <AppSheet :visible="openDialogVisible" :title="t('home.openProjectTitle')" @close="openDialogVisible = false">
      <p v-if="projects.length === 0">{{ t('home.noProjects') }}</p>
      <div v-else class="header-modal-options">
        <button
          v-for="project in projects"
          :key="project.id"
          class="header-modal-option"
          :class="{ 'is-active': selectedProjectId === project.id }"
          type="button"
          @click="selectedProjectId = project.id"
        >
          {{ project.name }}
        </button>
      </div>
      <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
      <template #footer>
        <AppButton @click="openDialogVisible = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" @click="confirmOpenProject">{{ t('common.open') }}</AppButton>
      </template>
    </AppSheet>
  </div>
</template>
