<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import UploadProgressBar from '@/presentation/components/UploadProgressBar.vue'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { useTransferSpeed } from '@/presentation/composables/useTransferSpeed'
import { formatDomainError } from '@/presentation/errors'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import type { Project } from '@/features/project/domain/entities/project.entity'

const router = useRouter()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()
const workspace = useProjectWorkspace()
const models = useModelAssets()

const projects = ref<Project[]>([])
const loading = ref(true)
const errorMessage = ref('')
const newProjectName = ref('')
const newProjectDescription = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const pending = ref(false)
const uploadProgress = ref(0)
const { speedLabel, noteLoaded, resetSpeed } = useTransferSpeed()
const editingProjectId = ref<string | null>(null)
const editName = ref('')
const editDescription = ref('')
const activeProjectId = ref<string | null>(workspace.activeProjectId())

const activeProject = computed(() => projects.value.find((project) => project.id === activeProjectId.value) ?? null)

onMounted(async () => {
  await refresh()
})

async function refresh() {
  loading.value = true
  const [error, data] = await workspace.load()
  loading.value = false
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  projects.value = data ?? []
  activeProjectId.value = workspace.activeProjectId()
}

async function createProject() {
  const [error, project] = await workspace.create({
    name: newProjectName.value.trim(),
    description: newProjectDescription.value.trim(),
  })
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  newProjectName.value = ''
  newProjectDescription.value = ''
  errorMessage.value = ''
  if (project) {
    projects.value = [project, ...projects.value.filter((item) => item.id !== project.id)]
    activeProjectId.value = project.id
  }
}

function selectProject(projectId: string) {
  workspace.open(projectId)
  activeProjectId.value = projectId
  errorMessage.value = ''
}

function triggerUpload() {
  if (!activeProject.value) {
    errorMessage.value = t('projects.selectProjectFirst')
    return
  }
  uploadInputRef.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !activeProjectId.value) {
    return
  }
  pending.value = true
  uploadProgress.value = 0
  resetSpeed()
  const [error] = await models.upload({
    projectId: activeProjectId.value,
    file,
    onProgress: ({ loaded, total }) => {
      uploadProgress.value = total > 0 ? Math.round((loaded / total) * 100) : 0
      noteLoaded(loaded)
    },
  })
  pending.value = false
  if (!error) {
    uploadProgress.value = 100
  } else {
    resetSpeed()
  }
  errorMessage.value = error ? formatDomainError(t, error) : ''
}

async function handleDeleteProject(projectId: string) {
  if (!window.confirm(t('projects.deleteConfirm'))) {
    return
  }
  const [error] = await workspace.remove(projectId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  projects.value = projects.value.filter((project) => project.id !== projectId)
  activeProjectId.value = workspace.activeProjectId()
  errorMessage.value = ''
}

function startEdit(projectId: string, name: string, description: string) {
  editingProjectId.value = projectId
  editName.value = name
  editDescription.value = description
}

async function saveEdit(projectId: string) {
  const [error, project] = await workspace.update({
    id: projectId,
    name: editName.value.trim(),
    description: editDescription.value.trim(),
  })
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  if (project) {
    projects.value = projects.value.map((item) => (item.id === project.id ? project : item))
  }
  editingProjectId.value = null
  errorMessage.value = ''
}
</script>

<template>
  <div class="projects-page">
    <div class="cloud-page-inner">
      <section class="projects-current-card section-card">
        <div class="projects-current-header">
          <div>
            <template v-if="activeProject">
              <h2 class="projects-current-title">{{ activeProject.name }}</h2>
              <p class="projects-current-description">{{ activeProject.description || t('common.noDescription') }}</p>
              <p class="projects-current-meta">{{ t('projects.createdAt', { date: formatDateTime(activeProject.createdAt) }) }}</p>
            </template>
            <template v-else>
              <h2 class="projects-current-title">{{ t('projects.noProjectOpen') }}</h2>
              <p class="projects-current-description">{{ t('projects.noProjectOpenHint') }}</p>
            </template>
          </div>
          <AppButton @click="router.push('/app/home')">{{ t('projects.backHome') }}</AppButton>
        </div>
        <div v-if="activeProject" class="projects-current-actions">
          <AppButton variant="primary" :disabled="pending" @click="triggerUpload">
            {{ pending ? t('common.uploading') : t('projects.uploadModel') }}
          </AppButton>
        </div>
        <UploadProgressBar v-if="pending || uploadProgress > 0" :percent="uploadProgress" :speed="speedLabel" />
      </section>

      <section class="projects-create-card section-card">
        <h3 class="section-title">{{ t('projects.createProject') }}</h3>
        <div class="projects-create-grid">
          <input v-model="newProjectName" class="text-control" type="text" :placeholder="t('projects.projectNamePlaceholder')" />
          <input v-model="newProjectDescription" class="text-control" type="text" :placeholder="t('projects.projectDescPlaceholder')" />
          <AppButton variant="primary" @click="createProject">{{ t('projects.createProjectBtn') }}</AppButton>
        </div>
      </section>

      <p v-if="errorMessage" class="projects-error">{{ errorMessage }}</p>

      <section class="projects-list section-card">
        <h3 class="section-title">{{ t('projects.projectList') }}</h3>
        <p v-if="loading">{{ t('common.loading') }}</p>
        <p v-else-if="projects.length === 0">{{ t('projects.noProjects') }}</p>
        <div
          v-for="project in projects"
          :key="project.id"
          class="project-list-row"
          :class="{ 'is-active': activeProjectId === project.id }"
        >
          <template v-if="editingProjectId === project.id">
            <div class="project-list-edit">
              <input v-model="editName" class="text-control" type="text" />
              <input v-model="editDescription" class="text-control" type="text" />
              <div class="project-list-edit__actions">
                <AppButton variant="primary" @click="saveEdit(project.id)">{{ t('common.save') }}</AppButton>
                <AppButton @click="editingProjectId = null">{{ t('common.cancel') }}</AppButton>
              </div>
            </div>
          </template>
          <template v-else>
            <button class="project-list-row__select" type="button" @click="selectProject(project.id)">
              <h4 class="project-card-title">{{ project.name }}</h4>
              <p class="projects-current-description">{{ project.description || t('common.noDescription') }}</p>
            </button>
            <div class="project-list-row__actions">
              <AppButton compact @click="startEdit(project.id, project.name, project.description)">{{ t('projects.editProject') }}</AppButton>
              <AppButton compact variant="destructive" @click="handleDeleteProject(project.id)">{{ t('common.delete') }}</AppButton>
            </div>
          </template>
        </div>
      </section>

      <input ref="uploadInputRef" class="visually-hidden" type="file" accept=".ply,.spz" @change="handleUpload" />
    </div>
  </div>
</template>
