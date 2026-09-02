<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import UploadProgressBar from '@/presentation/components/UploadProgressBar.vue'
import { formatDomainError } from '@/presentation/errors'
import { useTransferSpeed } from '@/presentation/composables/useTransferSpeed'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import type { Project } from '@/features/project/domain/entities/project.entity'
import DatasetUploadPanel from './DatasetUploadPanel.vue'
import TrainingJobPanel from './TrainingJobPanel.vue'

type UploadTab = 'dataset' | 'model'

const router = useRouter()
const { t } = useI18n()
const workspace = useProjectWorkspace()
const models = useModelAssets()

const projects = ref<Project[]>([])
const activeTab = ref<UploadTab>('dataset')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const statusMessage = ref('')
const pending = ref(false)
const uploadProgress = ref(0)
const { speedLabel, noteLoaded, resetSpeed } = useTransferSpeed()
let uploadAbort: AbortController | null = null
const activeProjectId = ref<string | null>(workspace.activeProjectId())
const activeProject = computed(() => projects.value.find((project) => project.id === activeProjectId.value) ?? null)

onMounted(async () => {
  const [error, data] = await workspace.load()
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  projects.value = data ?? []
  activeProjectId.value = workspace.activeProjectId()
})

function triggerUpload() {
  if (!activeProject.value) {
    errorMessage.value = t('upload.openProjectFirst')
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
  uploadAbort?.abort()
  const controller = new AbortController()
  uploadAbort = controller
  pending.value = true
  uploadProgress.value = 0
  resetSpeed()
  errorMessage.value = ''
  statusMessage.value = t('upload.uploadingFile', { name: file.name })
  const [error] = await models.upload({
    projectId: activeProjectId.value,
    file,
    signal: controller.signal,
    onProgress: ({ loaded, total }) => {
      const percent = total > 0 ? Math.round((loaded / total) * 100) : 0
      uploadProgress.value = percent
      noteLoaded(loaded)
      statusMessage.value = t('upload.uploadingProgress', { name: file.name, percent })
    },
  })
  pending.value = false
  if (controller.signal.aborted) {
    statusMessage.value = ''
    uploadProgress.value = 0
    resetSpeed()
    return
  }
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    statusMessage.value = ''
    uploadProgress.value = 0
    resetSpeed()
    return
  }
  uploadProgress.value = 100
  statusMessage.value = t('upload.uploadedFile', { name: file.name })
}

function cancelUpload() {
  uploadAbort?.abort()
}

function goToViewer() {
  if (!activeProject.value) {
    errorMessage.value = t('upload.openProjectRequired')
    return
  }
  void router.push('/app/layer')
}
</script>

<template>
  <div class="upload-page">
    <div class="cloud-page-inner">
      <h2 class="upload-title">{{ t('upload.title') }}</h2>
      <div class="upload-tabs">
        <button class="upload-tab" :class="{ 'upload-tab--active': activeTab === 'dataset' }" type="button" @click="activeTab = 'dataset'">
          {{ t('upload.tabDataset') }}
        </button>
        <button class="upload-tab" :class="{ 'upload-tab--active': activeTab === 'model' }" type="button" @click="activeTab = 'model'">
          {{ t('upload.tabModel') }}
        </button>
      </div>

      <section v-if="activeTab === 'dataset'" class="upload-tab-panel">
        <p class="upload-tab-hint">{{ t('upload.datasetHint', { name: activeProject?.name || t('upload.noProjectOpen') }) }}</p>
        <DatasetUploadPanel v-if="activeProjectId" :project-id="activeProjectId" />
        <p v-else class="upload-empty-text">{{ t('upload.openProjectFirst') }}</p>
        <TrainingJobPanel :project-id="activeProjectId" />
      </section>

      <section v-else class="upload-tab-panel">
        <p class="upload-tab-hint">{{ t('upload.modelHint') }}</p>
        <div class="upload-current-card">
          <h3 class="upload-project-name">{{ activeProject?.name || t('upload.noProjectOpen') }}</h3>
          <div class="upload-actions">
            <AppButton variant="primary" :disabled="pending" @click="triggerUpload">
              {{ pending ? t('common.uploading') : t('upload.selectFileUpload') }}
            </AppButton>
            <AppButton v-if="pending" @click="cancelUpload">{{ t('upload.cancelUpload') }}</AppButton>
            <AppButton @click="goToViewer">{{ t('upload.goToViewer') }}</AppButton>
            <AppButton @click="router.push('/app/projects')">{{ t('projects.backHome') }}</AppButton>
          </div>
        </div>
      </section>

      <UploadProgressBar
        v-if="activeTab === 'model' && (pending || uploadProgress > 0)"
        :percent="uploadProgress"
        :speed="speedLabel"
      />

      <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
      <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
      <input ref="uploadInputRef" class="visually-hidden" type="file" accept=".ply,.spz" @change="handleUpload" />
    </div>
  </div>
</template>
