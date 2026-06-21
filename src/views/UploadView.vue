<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import FileUploadButton from '@/components/FileUploadButton.vue'
import DatasetUploadPanel from '@/components/DatasetUploadPanel.vue'
import TrainingJobPanel from '@/components/TrainingJobPanel.vue'

const router = useRouter()
const projectStore = useProjectStore()
const activeTab = ref<'model' | 'dataset'>('dataset')
const statusMessage = ref('')
const errorMessage = ref('')

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载项目失败'
  }
})

function goToProjects() {
  router.push('/app/projects')
}

function goToViewer() {
  if (!projectStore.activeProject) {
    errorMessage.value = '请先打开一个项目'
    return
  }
  router.push('/app/layer')
}
</script>

<template>
  <div class="upload-page">
    <header class="upload-header">
      <div>
        <p class="eyebrow">数据上传</p>
        <h2 class="upload-title">上传模型或图片数据集</h2>
      </div>
    </header>

    <div v-if="projectStore.activeProject" class="upload-tabs">
      <button
        class="cloud-btn"
        :class="{ 'cloud-btn--primary': activeTab === 'dataset' }"
        type="button"
        @click="activeTab = 'dataset'"
      >
        图片数据集
      </button>
      <button
        class="cloud-btn"
        :class="{ 'cloud-btn--primary': activeTab === 'model' }"
        type="button"
        @click="activeTab = 'model'"
      >
        模型文件
      </button>
    </div>

    <section v-if="projectStore.activeProject" class="upload-current-card cloud-card">
      <p class="eyebrow">当前工程</p>
      <h3 class="upload-project-name">{{ projectStore.activeProject.name }}</h3>
      <p class="upload-project-description">{{ projectStore.activeProject.description || '暂无描述' }}</p>

      <template v-if="activeTab === 'model'">
        <div class="upload-actions">
          <FileUploadButton
            :project-id="projectStore.activeProjectId"
            label="选择 PLY / SPZ 文件"
            @success="(name) => { statusMessage = `已上传 ${name}`; errorMessage = '' }"
            @error="(message) => { errorMessage = message; statusMessage = '' }"
          />
          <button class="cloud-btn cloud-btn--ghost" type="button" @click="goToViewer">前往模型查看</button>
        </div>
        <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
        <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
      </template>
    </section>

    <section v-else class="upload-empty-card cloud-card">
      <h3 class="section-title">尚未打开工程</h3>
      <p class="upload-empty-text">请先在主页新建或打开项目，再上传数据。</p>
      <button class="cloud-btn cloud-btn--primary" type="button" @click="goToProjects">前往工程项目</button>
    </section>

    <div v-if="projectStore.activeProject && activeTab === 'dataset'" class="upload-stack">
      <DatasetUploadPanel :project-id="projectStore.activeProjectId!" />
      <TrainingJobPanel :project-id="projectStore.activeProjectId" />
    </div>
  </div>
</template>

<style scoped>
.upload-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.upload-stack {
  display: grid;
  gap: 16px;
  max-width: 960px;
}
</style>
