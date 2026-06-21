<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useTrainingJobStore } from '@/stores/trainingJob'
import type { JobStatus } from '@/api/datasets'

const props = defineProps<{
  projectId: string | null
}>()

const trainingJobStore = useTrainingJobStore()

const statusLabel: Record<JobStatus, string> = {
  PENDING: '待处理',
  UPLOADING: '上传中',
  QUEUED: '排队中',
  RUNNING: '训练中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

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

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<template>
  <section class="cloud-card training-job-panel">
    <div class="training-job-header">
      <div>
        <p class="eyebrow">训练任务</p>
        <h3 class="section-title">实时进度</h3>
      </div>
      <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="!projectId" @click="loadJobs">
        刷新
      </button>
    </div>

    <p v-if="!projectId" class="upload-empty-text">请先打开项目以查看训练任务。</p>
    <p v-else-if="trainingJobStore.loading" class="upload-empty-text">加载中...</p>
    <p v-else-if="trainingJobStore.errorMessage" class="upload-error">{{ trainingJobStore.errorMessage }}</p>
    <p v-else-if="trainingJobStore.jobs.length === 0" class="upload-empty-text">暂无训练任务。</p>

    <div v-else class="training-job-list">
      <article v-for="job in trainingJobStore.jobs" :key="job.id" class="training-job-item">
        <div class="training-job-item__header">
          <div>
            <strong>{{ job.name }}</strong>
            <p class="training-job-meta">{{ formatTime(job.createdAt) }}</p>
          </div>
          <span class="cloud-badge" :class="badgeClass(job.status)">{{ statusLabel[job.status] }}</span>
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
            下载模型
          </a>
        </div>
      </article>
    </div>
  </section>
</template>
