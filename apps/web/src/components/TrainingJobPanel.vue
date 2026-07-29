<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTrainingJobStore } from '@/stores/trainingJob'
import type { JobStatus } from '@/api/datasets'
import { showComingSoon } from '@/utils/comingSoon'
import { useFormatDateTime } from '@/composables/useAppLocale'

const props = defineProps<{
  projectId: string | null
}>()

const trainingJobStore = useTrainingJobStore()
const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()

const statusLabel = computed<Record<JobStatus, string>>(() => ({
  PENDING: t('training.status.pending'),
  UPLOADING: t('training.status.uploading'),
  QUEUED: t('training.status.queued'),
  RUNNING: t('training.status.running'),
  COMPLETED: t('training.status.completed'),
  FAILED: t('training.status.failed'),
  CANCELLED: t('training.status.cancelled'),
}))

const badgeClass = computed(() => (status: JobStatus) => {
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
})

async function loadJobs() {
  if (!props.projectId) {
    return
  }
  await trainingJobStore.fetchJobs(props.projectId)
  for (const job of trainingJobStore.jobs) {
    if (job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'UPLOADING') {
      trainingJobStore.watchJob(job.id)
    }
  }
}

watch(
  () => props.projectId,
  () => {
    trainingJobStore.clearSubscriptions()
    void loadJobs()
  },
)

onMounted(() => {
  void loadJobs()
})

onBeforeUnmount(() => {
  trainingJobStore.clearSubscriptions()
})

function handleDeleteRecord() {
  showComingSoon('training.deleteRecordFeature')
}
</script>

<template>
  <section class="cloud-card training-job-panel">
    <div class="training-job-header">
      <div>
        <p class="eyebrow">{{ t('training.eyebrow') }}</p>
        <h3 class="section-title">{{ t('training.title') }}</h3>
      </div>
      <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="!projectId" @click="loadJobs">
        {{ t('training.refresh') }}
      </button>
    </div>

    <p v-if="!projectId" class="upload-empty-text">{{ t('training.openProjectFirst') }}</p>
    <p v-else-if="trainingJobStore.loading" class="upload-empty-text">{{ t('common.loading') }}</p>
    <p v-else-if="trainingJobStore.errorMessage" class="upload-error">{{ trainingJobStore.errorMessage }}</p>
    <p v-else-if="trainingJobStore.jobs.length === 0" class="upload-empty-text">{{ t('training.noJobs') }}</p>

    <div v-else class="training-job-list">
      <article v-for="job in trainingJobStore.jobs" :key="job.id" class="training-job-item">
        <div class="training-job-item__header">
          <div>
            <strong>{{ job.name }}</strong>
            <p class="training-job-meta">{{ formatDateTime(job.createdAt) }}</p>
          </div>
          <div class="training-job-item__header-actions">
            <span class="cloud-badge" :class="badgeClass(job.status)">{{ statusLabel[job.status] }}</span>
            <button
              class="cloud-btn cloud-btn--ghost cloud-btn--compact"
              type="button"
              @click="handleDeleteRecord"
            >
              {{ t('training.deleteRecord') }}
            </button>
          </div>
        </div>

        <div class="cloud-progress">
          <div class="cloud-progress-bar">
            <div class="cloud-progress-bar__fill" :style="{ width: `${job.progress}%` }" />
          </div>
          <span class="cloud-progress-label">{{ job.progress }}%</span>
        </div>

        <p v-if="job.stage || job.message" class="training-job-message">
          {{ job.stage }}<span v-if="job.message"> · {{ job.message }}</span>
        </p>
        <p v-if="job.errorMessage" class="upload-error">{{ job.errorMessage }}</p>

        <div v-if="job.downloadUrl" class="training-job-actions">
          <a class="cloud-btn cloud-btn--primary" :href="job.downloadUrl" target="_blank" rel="noopener">
            {{ t('training.downloadModel') }}
          </a>
        </div>
      </article>
    </div>
  </section>
</template>
