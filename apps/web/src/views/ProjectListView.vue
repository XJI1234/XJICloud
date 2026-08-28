<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'
import { useFormatDateTime } from '@/composables/useAppLocale'

const router = useRouter()
const projectStore = useProjectStore()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const newProjectName = ref('')
const newProjectDescription = ref('')
const errorMessage = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const pending = ref(false)

// inline edit state
const editingProjectId = ref<string | null>(null)
const editName = ref('')
const editDescription = ref('')

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('projects.loadProjectsFailed')
  }
})

async function createProject() {
  if (!newProjectName.value.trim()) {
    errorMessage.value = t('projects.enterProjectName')
    return
  }

  try {
    await projectStore.createProject(newProjectName.value.trim(), newProjectDescription.value.trim())
    newProjectName.value = ''
    newProjectDescription.value = ''
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('projects.createProjectFailed')
  }
}

function selectProject(projectId: string) {
  projectStore.openProject(projectId)
  errorMessage.value = ''
}

function triggerUpload() {
  if (!projectStore.activeProject) {
    errorMessage.value = t('projects.selectProjectFirst')
    return
  }

  uploadInputRef.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file || !projectStore.activeProjectId) {
    return
  }

  pending.value = true
  try {
    await uploadModel(projectStore.activeProjectId, file)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('projects.uploadModelFailed')
  } finally {
    pending.value = false
  }
}

function goHome() {
  router.push('/app/home')
}

async function handleDeleteProject(projectId: string) {
  if (!window.confirm(t('projects.deleteConfirm'))) {
    return
  }
  try {
    await projectStore.deleteProject(projectId)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('projects.deleteProjectFailed')
  }
}

function startEdit(projectId: string, name: string, description: string) {
  editingProjectId.value = projectId
  editName.value = name
  editDescription.value = description
  errorMessage.value = ''
}

function cancelEdit() {
  editingProjectId.value = null
  editName.value = ''
  editDescription.value = ''
}

async function saveEdit(projectId: string) {
  if (!editName.value.trim()) {
    errorMessage.value = t('projects.enterProjectName')
    return
  }
  try {
    await projectStore.updateProject(projectId, editName.value.trim(), editDescription.value.trim())
    cancelEdit()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('projects.updateProjectFailed')
  }
}
</script>

<template>
  <div class="projects-page">
    <div class="cloud-page-inner">
    <section class="projects-current-card section-card">
      <div class="projects-current-header">
        <div>
          <p class="eyebrow">{{ t('projects.currentProject') }}</p>
          <template v-if="projectStore.activeProject">
            <h2 class="projects-current-title">{{ projectStore.activeProject.name }}</h2>
            <p class="projects-current-description">
              {{ projectStore.activeProject.description || t('common.noDescription') }}
            </p>
            <p class="projects-current-meta">
              {{ t('projects.createdAt', { date: formatDateTime(projectStore.activeProject.createdAt) }) }}
            </p>
          </template>
          <template v-else>
            <h2 class="projects-current-title">{{ t('projects.noProjectOpen') }}</h2>
            <p class="projects-current-description">
              {{ t('projects.noProjectOpenHint') }}
            </p>
          </template>
        </div>
        <button class="side-button side-button--inline cloud-pressable" type="button" @click="goHome">{{ t('projects.backHome') }}</button>
      </div>

      <div v-if="projectStore.activeProject" class="projects-current-actions">
        <button class="side-button primary cloud-pressable" type="button" :disabled="pending" @click="triggerUpload">
          {{ pending ? t('common.uploading') : t('projects.uploadModel') }}
        </button>
      </div>
    </section>

    <section class="projects-create-card section-card">
      <h3 class="section-title">{{ t('projects.createProject') }}</h3>
      <div class="projects-create-grid">
        <input v-model="newProjectName" class="text-control" type="text" :placeholder="t('projects.projectNamePlaceholder')" />
        <input v-model="newProjectDescription" class="text-control" type="text" :placeholder="t('projects.projectDescPlaceholder')" />
        <button class="side-button primary cloud-pressable" type="button" @click="createProject">{{ t('projects.createProjectBtn') }}</button>
      </div>
    </section>

    <p v-if="errorMessage" class="projects-error">{{ errorMessage }}</p>

    <section class="projects-list section-card">
      <h3 class="section-title">{{ t('projects.projectList') }}</h3>
      <p v-if="projectStore.loading" class="projects-empty">{{ t('common.loading') }}</p>
      <p v-else-if="projectStore.projects.length === 0" class="projects-empty">{{ t('projects.noProjects') }}</p>

      <div
        v-for="project in projectStore.projects"
        :key="project.id"
        class="project-list-row cloud-pressable"
        :class="{ 'is-active': projectStore.activeProjectId === project.id }"
      >
        <template v-if="editingProjectId === project.id">
          <div class="project-list-edit">
            <input v-model="editName" class="text-control" type="text" :placeholder="t('projects.projectNamePlaceholder')" />
            <input v-model="editDescription" class="text-control" type="text" :placeholder="t('projects.projectDescPlaceholder')" />
            <div class="project-list-edit__actions">
              <button class="side-button primary" type="button" @click="saveEdit(project.id)">{{ t('common.save') }}</button>
              <button class="side-button" type="button" @click="cancelEdit">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </template>
        <template v-else>
          <button
            class="project-list-row__select cloud-pressable"
            type="button"
            @click="selectProject(project.id)"
          >
            <div class="project-list-copy">
              <h4 class="project-card-title">{{ project.name }}</h4>
              <p class="project-card-description">{{ project.description || t('common.noDescription') }}</p>
              <p class="project-card-meta">{{ formatDateTime(project.createdAt) }}</p>
            </div>
          </button>
          <div class="project-list-row__actions">
            <span v-if="projectStore.activeProjectId === project.id" class="project-list-badge">{{ t('projects.currentBadge') }}</span>
            <button
              class="side-button side-button--inline"
              type="button"
              @click.stop="startEdit(project.id, project.name, project.description)"
            >
              {{ t('projects.editProject') }}
            </button>
            <button
              class="side-button side-button--inline side-button--danger"
              type="button"
              @click.stop="handleDeleteProject(project.id)"
            >
              {{ t('projects.deleteProject') }}
            </button>
          </div>
        </template>
      </div>
    </section>

    <input
      ref="uploadInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleUpload"
    />
    </div>
  </div>
</template>
