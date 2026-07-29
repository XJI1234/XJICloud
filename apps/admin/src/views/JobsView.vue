<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/stores/auth'
import { cancelJob, fetchJobs, retryJob, type JobResponse } from '@/api/adminClient'

const jobs = ref<JobResponse[]>([])
const errorMessage = ref('')

async function load() {
  try {
    jobs.value = await fetchJobs()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载失败'
  }
}

async function retry(jobId: string) {
  await retryJob(jobId)
  await load()
}

async function cancel(jobId: string) {
  await cancelJob(jobId)
  await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="admin-actions" style="margin-bottom: 16px">
      <h2 class="admin-page-title" style="margin: 0">训练任务</h2>
      <button class="cloud-btn cloud-btn--ghost" type="button" @click="load">刷新</button>
    </div>
    <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    <table class="admin-table">
      <thead>
        <tr>
          <th>名称</th>
          <th>状态</th>
          <th>进度</th>
          <th>阶段</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.id">
          <td>{{ job.name }}</td>
          <td>{{ job.status }}</td>
          <td>{{ job.progress }}%</td>
          <td>{{ job.stage }} {{ job.message }}</td>
          <td>{{ new Date(job.createdAt).toLocaleString('zh-CN') }}</td>
          <td class="admin-actions">
            <button
              v-if="job.status === 'FAILED' || job.status === 'CANCELLED'"
              class="cloud-btn cloud-btn--primary"
              type="button"
              @click="retry(job.id)"
            >
              重试
            </button>
            <button
              v-if="job.status !== 'COMPLETED' && job.status !== 'CANCELLED'"
              class="cloud-btn cloud-btn--danger"
              type="button"
              @click="cancel(job.id)"
            >
              取消
            </button>
            <a v-if="job.downloadUrl" class="cloud-btn cloud-btn--ghost" :href="job.downloadUrl" target="_blank">下载</a>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
