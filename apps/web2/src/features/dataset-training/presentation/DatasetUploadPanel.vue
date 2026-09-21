<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import WaitOverlay from '@/presentation/components/WaitOverlay.vue'
import { useWaitSession } from '@/presentation/composables/useWaitSession'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { useTransferSpeed } from '@/presentation/composables/useTransferSpeed'
import { formatBytes } from '@/presentation/format'
import { formatDomainError } from '@/presentation/errors'
import { totalArchiveBytes } from '@/features/dataset-training/domain/services/dataset-archive.service'
import { useDatasetTraining } from '@/features/dataset-training/presentation/composables/useDatasetTraining'
import type { DatasetArchive } from '@/features/dataset-training/domain/entities/training-job.entity'

const props = defineProps<{
  projectId: string
}>()

const training = useDatasetTraining()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const folderInputRef = ref<HTMLInputElement | null>(null)
const datasetName = ref('')
const archive = ref<DatasetArchive | null>(null)
const pending = ref(false)
const uploadProgress = ref(0)
const { speedLabel, noteLoaded, resetSpeed } = useTransferSpeed()
const wait = useWaitSession()
const { waitView } = wait
const statusMessage = ref('')
const errorMessage = ref('')

const totalUploadBytes = computed(() => (archive.value ? totalArchiveBytes(archive.value) : 0))

function defaultDatasetName() {
  return t('dataset.defaultName', { time: formatDateTime(new Date()) })
}

function handleFolderSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  errorMessage.value = ''
  const [error, data] = training.archiveFolder(files)
  if (error || !data) {
    archive.value = null
    errorMessage.value = formatDomainError(t, error)
    return
  }
  archive.value = data
  if (!datasetName.value.trim()) {
    datasetName.value = defaultDatasetName()
  }
  statusMessage.value = t('dataset.archived', {
    count: data.files.length,
    size: formatBytes(totalUploadBytes.value),
  })
}

async function uploadArchive() {
  if (!archive.value) {
    errorMessage.value = t('dataset.selectFolderFirst')
    return
  }
  pending.value = true
  uploadProgress.value = 0
  resetSpeed()
  errorMessage.value = ''
  statusMessage.value = t('dataset.requestingUrls')
  wait.showBusy(t('wait.uploading'), true)
  const [error, job] = await training.submit({
    projectId: props.projectId,
    name: datasetName.value.trim() || defaultDatasetName(),
    archive: archive.value,
    onProgress: (progress: { percent: number; loaded: number }) => {
      uploadProgress.value = progress.percent
      noteLoaded(progress.loaded)
      statusMessage.value = t('dataset.uploadingOss')
      wait.showProgress(t('wait.uploading'), progress.percent, speedLabel.value, true)
    },
  })
  pending.value = false
  if (error || !job) {
    errorMessage.value = formatDomainError(t, error)
    statusMessage.value = ''
    resetSpeed()
    wait.hide()
    return
  }
  training.watchHub.upsert(job)
  training.watchHub.watch(job.id)
  uploadProgress.value = 100
  statusMessage.value = t('dataset.jobSubmitted')
  archive.value = null
  wait.showDone(t('wait.done'), 100, speedLabel.value)
}
</script>

<template>
  <section class="cloud-card dataset-upload-panel">
    <div class="dataset-upload-header">
      <div>
        <h3 class="section-title">{{ t('dataset.title') }}</h3>
      </div>
      <span class="cloud-badge cloud-badge--info">JPG / PNG / WebP</span>
    </div>
    <label class="cloud-field">
      <span>{{ t('dataset.nameLabel') }}</span>
      <input v-model="datasetName" class="cloud-input" type="text" :placeholder="t('dataset.namePlaceholder')" />
    </label>
    <div class="dataset-upload-actions">
      <AppButton :disabled="pending" @click="folderInputRef?.click()">{{ t('dataset.selectFolder') }}</AppButton>
      <AppButton variant="primary" :disabled="pending || !archive" @click="uploadArchive">
        {{ pending ? t('common.uploading') : t('dataset.startUpload') }}
      </AppButton>
    </div>
    <input ref="folderInputRef" class="visually-hidden" type="file" webkitdirectory directory multiple @change="handleFolderSelect" />
    <div v-if="archive" class="dataset-archive-summary">
      <p>{{ t('dataset.selectedSummary', { count: archive.files.length, size: formatBytes(totalUploadBytes) }) }}</p>
    </div>
    <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
    <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
    <WaitOverlay
      :visible="waitView.visible"
      :phase="waitView.phase"
      :title="waitView.title"
      :percent="waitView.percent"
      :speed="waitView.speed"
      :complete="waitView.complete"
      :await-confirm="waitView.awaitConfirm"
      @dismiss="wait.hide()"
    />
  </section>
</template>
