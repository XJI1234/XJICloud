<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError } from '@/stores/auth'
import { fetchDashboard, fetchStats, type DashboardResponse } from '@/api/adminClient'

const dashboard = ref<DashboardResponse | null>(null)
const stats = ref<Record<string, number>>({})
const errorMessage = ref('')

onMounted(async () => {
  try {
    dashboard.value = await fetchDashboard()
    stats.value = await fetchStats()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载失败'
  }
})
</script>

<template>
  <div>
    <h2 class="admin-page-title">平台概览</h2>
    <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    <div v-if="dashboard" class="admin-grid">
      <div class="admin-card">
        <span>算力容器</span>
        <p class="admin-stat-value">{{ dashboard.onlineWorkers }} / {{ dashboard.workerCount }}</p>
      </div>
      <div class="admin-card">
        <span>队列深度</span>
        <p class="admin-stat-value">{{ dashboard.queueDepth }}</p>
      </div>
      <div class="admin-card">
        <span>运行中任务</span>
        <p class="admin-stat-value">{{ dashboard.runningJobs }}</p>
      </div>
      <div class="admin-card">
        <span>今日完成</span>
        <p class="admin-stat-value">{{ dashboard.completedJobsToday }}</p>
      </div>
      <div class="admin-card">
        <span>失败任务</span>
        <p class="admin-stat-value">{{ dashboard.failedJobs }}</p>
      </div>
      <div class="admin-card">
        <span>用户 / 项目</span>
        <p class="admin-stat-value">{{ stats.users ?? 0 }} / {{ stats.projects ?? 0 }}</p>
      </div>
    </div>
  </div>
</template>
