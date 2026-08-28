<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { formatDomainError } from '@/presentation/errors'
import { useDatasetTraining } from '@/features/dataset-training/presentation/composables/useDatasetTraining'
import type { JobStatus, TrainingJob } from '@/features/dataset-training/domain/entities/training-job.entity'

const props = defineProps<{
  projectId: string | null
}>()

const training = useDatasetTraining()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const jobs = ref<TrainingJob[]>([])
const loading = ref(false)
const loadError = ref('')
const actionError = ref('')

const statusLabel = computed<Record<JobStatus, string>>(() => ({
  PENDING: t('training.status.pending'),
  UPLOADING: t('training.status.uploading'),
  QUEUED: t('training.status.queued'),
  RUNNING: t('training.status.running'),
  COMPLETED: t('training.status.completed'),
  FAILED: t('training.status.failed'),
  CANCELLED: t('training.status.cancelled'),
}))

function badgeClass(status: JobStatus) {
  switch (status) {
    case 'COMPLETED':
      return 'cloud-badge--success'
    case 'FAILED':
    case 'CANCELLED':
      return 'cloud-badge--danger'
    case 'RUNNING':
      return 'cloud-badge--warning'
    default:
      return 'cloud-badge--info'
  }
}

async function loadJobs() {
  if (!props.projectId) {
    jobs.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  const [error, data] = await training.list(props.projectId)
  loading.value = false
  if (error) {
    loadError.value = formatDomainError(t, error)
    return
  }
  training.watchHub.replaceAll(data ?? [])
  jobs.value = training.watchHub.jobs()
  for (const job of jobs.value) {
    if (training.isActiveJob(job.status)) {
      training.watchHub.watch(job.id)
    }
  }
}

const stopWatch = training.watchHub.subscribe(() => {
  jobs.value = training.watchHub.jobs()
})

watch(
  () => props.projectId,
  () => {
    training.watchHub.clear()
    void loadJobs()
  },
)

onMounted(() => {
  void loadJobs()
})

onBeforeUnmount(() => {
  stopWatch()
  training.watchHub.clear()
})

async function mutate(job: TrainingJob) {
  actionError.value = ''
  const confirmKey = training.isActiveJob(job.status) ? 'training.cancelConfirm' : 'training.deleteConfirm'
  if (!window.confirm(t(confirmKey))) {
    return
  }
  const [error] = await training.cancelOrDelete(job)
  if (error) {
    actionError.value = formatDomainError(t, error)
    return
  }
  await loadJobs()
}
</script>

<template>
  <section class="cloud-card training-job-panel">
    <div class="training-job-header">
      <h3 class="section-title">{{ t('training.title') }}</h3>
      <AppButton :disabled="!projectId" @click="loadJobs">{{ t('training.refresh') }}</AppButton>
    </div>
    <p v-if="!projectId" class="upload-empty-text">{{ t('training.openProjectFirst') }}</p>
    <p v-else-if="loading" class="upload-empty-text">{{ t('common.loading') }}</p>
    <p v-else-if="loadError" class="upload-error">{{ loadError }}</p>
    <p v-else-if="jobs.length === 0" class="upload-empty-text">{{ t('training.noJobs') }}</p>
    <div v-else class="training-job-list">
      <article v-for="job in jobs" :key="job.id" class="training-job-item">
        <div class="training-job-item__header">
          <div>
            <strong>{{ job.name }}</strong>
            <p class="training-job-meta">{{ formatDateTime(job.createdAt) }}</p>
          </div>
          <div class="training-job-item__header-actions">
            <span class="cloud-badge" :class="badgeClass(job.status)">{{ statusLabel[job.status] }}</span>
            <AppButton compact @click="mutate(job)">
              {{ training.isActiveJob(job.status) ? t('training.cancelRecord') : t('training.deleteRecord') }}
            </AppButton>
          </div>
        </div>
        <div class="cloud-progress">
          <div class="cloud-progress-bar">
            <div class="cloud-progress-bar__fill" :style="{ width: `${job.progress}%` }" />
          </div>
          <span>{{ job.progress }}%</span>
        </div>
        <p v-if="job.stage || job.message" class="training-job-message">
          {{ job.stage }}<span v-if="job.message"> · {{ job.message }}</span>
        </p>
        <p v-if="job.errorMessage" class="upload-error">{{ job.errorMessage }}</p>
        <div v-if="job.downloadUrl" class="training-job-actions">
          <a class="app-btn app-btn--primary" :href="job.downloadUrl" target="_blank" rel="noopener">{{ t('training.downloadModel') }}</a>
        </div>
      </article>
    </div>
    <p v-if="actionError" class="upload-error">{{ actionError }}</p>
  </section>
</template>
