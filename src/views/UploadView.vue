<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'

const router = useRouter()
const projectStore = useProjectStore()
const uploadInputRef = ref<HTMLInputElement | null>(null)
const errorMessage = ref('')
const statusMessage = ref('')
const pending = ref(false)

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载项目失败'
  }
})

function triggerUpload() {
  if (!projectStore.activeProject) {
    errorMessage.value = '请先在主页或工程页打开项目'
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
  statusMessage.value = `正在上传 ${file.name}...`

  try {
    await uploadModel(projectStore.activeProjectId, file)
    statusMessage.value = `已上传 ${file.name}，可通过左侧栏「模型查看」加载模型`
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '上传失败'
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
        <h2 class="upload-title">上传 PLY / SPZ 模型</h2>
      </div>
    </header>

    <section v-if="projectStore.activeProject" class="upload-current-card section-card">
      <p class="eyebrow">当前工程</p>
      <h3 class="upload-project-name">{{ projectStore.activeProject.name }}</h3>
      <p class="upload-project-description">{{ projectStore.activeProject.description || '暂无描述' }}</p>

      <div class="upload-actions">
        <button class="side-button primary" type="button" :disabled="pending" @click="triggerUpload">
          {{ pending ? '上传中...' : '选择文件上传' }}
        </button>
        <button class="side-button" type="button" @click="goToViewer">前往模型查看</button>
      </div>

      <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
      <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
    </section>

    <section v-else class="upload-empty-card section-card">
      <h3 class="section-title">尚未打开工程</h3>
      <p class="upload-empty-text">请先在主页新建或打开项目，再上传模型数据。</p>
      <button class="side-button primary" type="button" @click="goToProjects">前往工程项目</button>
    </section>

    <input
      ref="uploadInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleUpload"
    />
  </div>
</template>
