<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/stores/auth'
import { fetchWorkers, forceWorkerOffline, type WorkerResponse } from '@/api/adminClient'

const workers = ref<WorkerResponse[]>([])
const errorMessage = ref('')

async function load() {
  try {
    workers.value = await fetchWorkers()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载失败'
  }
}

async function offline(workerId: string) {
  await forceWorkerOffline(workerId)
  await load()
}

onMounted(load)

function badgeClass(status: WorkerResponse['status']) {
  if (status === 'IDLE') return 'badge--success'
  if (status === 'BUSY') return 'badge--warning'
  return 'badge--danger'
}
</script>

<template>
  <div>
    <div class="admin-actions" style="margin-bottom: 16px">
      <h2 class="admin-page-title" style="margin: 0">算力容器</h2>
      <button class="cloud-btn cloud-btn--ghost" type="button" @click="load">刷新</button>
    </div>
    <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    <table class="admin-table">
      <thead>
        <tr>
          <th>名称</th>
          <th>状态</th>
          <th>GPU</th>
          <th>当前任务</th>
          <th>最后心跳</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="worker in workers" :key="worker.id">
          <td>{{ worker.name }}</td>
          <td><span class="badge" :class="badgeClass(worker.status)">{{ worker.status }}</span></td>
          <td>{{ worker.gpuInfo || '-' }}</td>
          <td>{{ worker.currentJobId || '-' }}</td>
          <td>{{ new Date(worker.lastHeartbeat).toLocaleString('zh-CN') }}</td>
          <td>
            <button class="cloud-btn cloud-btn--danger" type="button" @click="offline(worker.id)">强制下线</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
