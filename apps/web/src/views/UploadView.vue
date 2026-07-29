<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'
import DatasetUploadPanel from '@/components/DatasetUploadPanel.vue'
import TrainingJobPanel from '@/components/TrainingJobPanel.vue'

type UploadTab = 'dataset' | 'model'

const router = useRouter()
const projectStore = useProjectStore()
const { t } = useI18n()

const activeTab = ref<UploadTab>('dataset')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const statusMessage = ref('')
const pending = ref(false)

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('upload.loadProjectsFailed')
  }
})

function triggerUpload() {
  if (!projectStore.activeProject) {
    errorMessage.value = t('upload.openProjectFirst')
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
  errorMessage.value = ''
  statusMessage.value = t('upload.uploadingFile', { name: file.name })

  try {
    await uploadModel(projectStore.activeProjectId, file)
    statusMessage.value = t('upload.uploadedFile', { name: file.name })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('upload.uploadFailed')
    statusMessage.value = ''
  } finally {
    pending.value = false
  }
}

function goToProjects() {
  router.push('/app/projects')
}

function goToViewer() {
  if (!projectStore.activeProject) {
    errorMessage.value = t('upload.openProjectRequired')
    return
  }

  router.push('/app/layer')
}
</script>

<template>
  <div class="upload-page">
    <div class="cloud-page-inner">
    <header class="upload-header">
      <div>
        <p class="eyebrow">{{ t('upload.eyebrow') }}</p>
        <h2 class="upload-title">{{ t('upload.title') }}</h2>
      </div>
    </header>

    <div class="upload-tabs" role="tablist">
      <button
        class="upload-tab"
        :class="{ 'upload-tab--active': activeTab === 'dataset' }"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'dataset'"
        @click="activeTab = 'dataset'"
      >
        {{ t('upload.tabDataset') }}
      </button>
      <button
        class="upload-tab"
        :class="{ 'upload-tab--active': activeTab === 'model' }"
        type="button"
        role="tab"
        :aria-selected="activeTab === 'model'"
        @click="activeTab = 'model'"
      >
        {{ t('upload.tabModel') }}
      </button>
    </div>

    <template v-if="projectStore.activeProject">
      <section v-if="activeTab === 'dataset'" class="upload-tab-panel">
        <p class="upload-tab-hint">
          {{ t('upload.datasetHint', { name: projectStore.activeProject.name }) }}
        </p>
        <DatasetUploadPanel :project-id="projectStore.activeProjectId!" />
        <TrainingJobPanel :project-id="projectStore.activeProjectId" />
      </section>

      <section v-else class="upload-tab-panel">
        <section class="upload-current-card section-card">
          <p class="eyebrow">{{ t('upload.modelEyebrow') }}</p>
          <h3 class="upload-project-name">{{ projectStore.activeProject.name }}</h3>
          <p class="upload-project-description">
            {{ t('upload.modelHint') }}
          </p>

          <div class="upload-actions">
            <button class="side-button primary" type="button" :disabled="pending" @click="triggerUpload">
              {{ pending ? t('common.uploading') : t('upload.selectFileUpload') }}
            </button>
            <button class="side-button" type="button" @click="goToViewer">{{ t('upload.goToViewer') }}</button>
          </div>

          <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
          <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
        </section>
      </section>
    </template>

    <section v-else class="upload-empty-card section-card">
      <h3 class="section-title">{{ t('upload.noProjectOpen') }}</h3>
      <p class="upload-empty-text">{{ t('upload.noProjectOpenHint') }}</p>
      <button class="side-button primary" type="button" @click="goToProjects">{{ t('upload.goToProjects') }}</button>
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
