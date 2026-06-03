<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'

const router = useRouter()
const projectStore = useProjectStore()
const newProjectName = ref('')
const newProjectDescription = ref('')
const errorMessage = ref('')
const uploadInputRef = ref<HTMLInputElement | null>(null)
const pendingUploadProjectId = ref<string | null>(null)

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载项目失败'
  }
})

async function createProject() {
  if (!newProjectName.value.trim()) {
    errorMessage.value = '请输入项目名称'
    return
  }

  try {
    await projectStore.createProject(newProjectName.value.trim(), newProjectDescription.value.trim())
    newProjectName.value = ''
    newProjectDescription.value = ''
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '创建项目失败'
  }
}

function openProject(projectId: string) {
  projectStore.setActiveProject(projectId)
  router.push('/app/layer')
}

function triggerUpload(projectId: string) {
  pendingUploadProjectId.value = projectId
  uploadInputRef.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const projectId = pendingUploadProjectId.value
  input.value = ''

  if (!file || !projectId) {
    return
  }

  try {
    projectStore.setActiveProject(projectId)
    await uploadModel(projectId, file)
    errorMessage.value = ''
    await router.push('/app/layer')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '上传模型失败'
  }
}
</script>

<template>
  <div class="projects-page">
    <header class="projects-header">
      <div>
        <p class="eyebrow">工程项目</p>
        <h2 class="projects-title">管理云端项目与模型</h2>
      </div>
      <button class="side-button primary" type="button" @click="router.push('/app/layer')">
        进入模型查看
      </button>
    </header>

    <section class="projects-create-card section-card">
      <h3 class="section-title">新建项目</h3>
      <div class="projects-create-grid">
        <input v-model="newProjectName" class="text-control" type="text" placeholder="项目名称" />
        <input v-model="newProjectDescription" class="text-control" type="text" placeholder="项目描述（可选）" />
        <button class="side-button primary" type="button" @click="createProject">创建项目</button>
      </div>
    </section>

    <p v-if="errorMessage" class="projects-error">{{ errorMessage }}</p>

    <section class="projects-list section-card">
      <h3 class="section-title">项目列表</h3>
      <p v-if="projectStore.loading" class="projects-empty">加载中...</p>
      <p v-else-if="projectStore.projects.length === 0" class="projects-empty">暂无项目，请先创建。</p>

      <article v-for="project in projectStore.projects" :key="project.id" class="project-card">
        <div>
          <h4 class="project-card-title">{{ project.name }}</h4>
          <p class="project-card-description">{{ project.description || '暂无描述' }}</p>
        </div>
        <div class="project-card-actions">
          <button class="side-button primary" type="button" @click="openProject(project.id)">打开查看</button>
          <button class="side-button" type="button" @click="triggerUpload(project.id)">上传模型</button>
        </div>
      </article>
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
