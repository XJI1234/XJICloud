<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/api/client'
import {
  completeDataset,
  createDataset,
  putToOss,
  type CreateDatasetResponse,
} from '@/api/datasets'
import { useTrainingJobStore } from '@/stores/trainingJob'
import { buildDatasetArchive, formatBytes, type DatasetArchive } from '@/utils/datasetArchive'
import { useFormatDateTime } from '@/composables/useAppLocale'

const props = defineProps<{
  projectId: string
}>()

const trainingJobStore = useTrainingJobStore()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const folderInputRef = ref<HTMLInputElement | null>(null)
const datasetName = ref('')
const archive = ref<DatasetArchive | null>(null)
const pending = ref(false)
const uploadProgress = ref(0)
const statusMessage = ref('')
const errorMessage = ref('')

const totalUploadBytes = computed(() => {
  if (!archive.value) {
    return 0
  }
  return archive.value.files.reduce((sum, file) => sum + file.sizeBytes, archive.value.manifestBlob.size)
})

function defaultDatasetName() {
  return t('dataset.defaultName', { time: formatDateTime(new Date()) })
}

function triggerFolderSelect() {
  folderInputRef.value?.click()
}

function handleFolderSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''

  errorMessage.value = ''
  statusMessage.value = ''

  try {
    archive.value = buildDatasetArchive(files)
    if (!datasetName.value.trim()) {
      datasetName.value = defaultDatasetName()
    }
    statusMessage.value = t('dataset.archived', {
      count: archive.value.files.length,
      size: formatBytes(totalUploadBytes.value),
    })
  } catch (error) {
    archive.value = null
    errorMessage.value = error instanceof Error ? error.message : t('dataset.folderParseFailed')
  }
}

async function uploadArchive() {
  if (!archive.value) {
    errorMessage.value = t('dataset.selectFolderFirst')
    return
  }

  pending.value = true
  uploadProgress.value = 0
  errorMessage.value = ''
  statusMessage.value = t('dataset.requestingUrls')

  try {
    const response: CreateDatasetResponse = await createDataset(props.projectId, {
      name: datasetName.value.trim() || defaultDatasetName(),
      files: archive.value.files.map(({ archivedName, originalName, contentType, sizeBytes }) => ({
        archivedName,
        originalName,
        contentType,
        sizeBytes,
      })),
    })

    let completedBytes = 0
    statusMessage.value = t('dataset.uploadingOss')

    for (const fileItem of archive.value.files) {
      const uploadItem = response.uploads.find((item) => item.archivedName === fileItem.archivedName)
      if (!uploadItem) {
        throw new Error(t('dataset.missingUploadUrl', { name: fileItem.archivedName }))
      }

      await putToOss(uploadItem.uploadUrl, fileItem.file, fileItem.contentType, (loaded) => {
        uploadProgress.value = Math.min(
          100,
          Math.round(((completedBytes + loaded) / totalUploadBytes.value) * 100),
        )
      })
      completedBytes += fileItem.sizeBytes
      uploadProgress.value = Math.min(100, Math.round((completedBytes / totalUploadBytes.value) * 100))
    }

    const manifestUpload = response.uploads.find((item) => item.archivedName === 'manifest.json')
    if (manifestUpload) {
      const manifestSize = archive.value.manifestBlob.size
      await putToOss(
        manifestUpload.uploadUrl,
        archive.value.manifestBlob,
        'application/json',
        (loaded) => {
          uploadProgress.value = Math.min(
            100,
            Math.round(((completedBytes + loaded) / totalUploadBytes.value) * 100),
          )
        },
      )
      completedBytes += manifestSize
    }

    uploadProgress.value = 100
    statusMessage.value = t('dataset.uploadComplete')

    const job = await completeDataset(props.projectId, response.jobId)
    trainingJobStore.upsertJob(job)
    trainingJobStore.watchJob(job.id)
    statusMessage.value = t('dataset.jobSubmitted')
    archive.value = null
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : (error instanceof Error ? error.message : t('dataset.uploadFailed'))
    statusMessage.value = ''
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="cloud-card dataset-upload-panel">
    <div class="dataset-upload-header">
      <div>
        <p class="eyebrow">{{ t('dataset.eyebrow') }}</p>
        <h3 class="section-title">{{ t('dataset.title') }}</h3>
      </div>
      <span class="cloud-badge cloud-badge--info">JPG / PNG / WebP</span>
    </div>

    <label class="cloud-field">
      <span>{{ t('dataset.nameLabel') }}</span>
      <input v-model="datasetName" class="cloud-input" type="text" :placeholder="t('dataset.namePlaceholder')" />
    </label>

    <div class="dataset-upload-actions">
      <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="pending" @click="triggerFolderSelect">
        {{ t('dataset.selectFolder') }}
      </button>
      <button
        class="cloud-btn cloud-btn--primary"
        type="button"
        :disabled="pending || !archive"
        @click="uploadArchive"
      >
        {{ pending ? t('common.uploading') : t('dataset.startUpload') }}
      </button>
    </div>

    <input
      ref="folderInputRef"
      class="visually-hidden"
      type="file"
      webkitdirectory
      directory
      multiple
      @change="handleFolderSelect"
    />

    <div v-if="archive" class="dataset-archive-summary">
      <p>{{ t('dataset.selectedSummary', { count: archive.files.length, size: formatBytes(totalUploadBytes) }) }}</p>
      <ul class="dataset-file-list">
        <li v-for="file in archive.files.slice(0, 8)" :key="file.archivedName">
          {{ file.archivedName }} ← {{ file.originalName }}
        </li>
        <li v-if="archive.files.length > 8">{{ t('dataset.moreFiles', { count: archive.files.length - 8 }) }}</li>
      </ul>
    </div>

    <div v-if="pending || uploadProgress > 0" class="cloud-progress">
      <div class="cloud-progress-bar">
        <div class="cloud-progress-bar__fill" :style="{ width: `${uploadProgress}%` }" />
      </div>
      <span class="cloud-progress-label">{{ uploadProgress }}%</span>
    </div>

    <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
    <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
  </section>
</template>
