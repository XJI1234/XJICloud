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
const pending = ref(false)

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

function selectProject(projectId: string) {
  projectStore.openProject(projectId)
  errorMessage.value = ''
}

function triggerUpload() {
  if (!projectStore.activeProject) {
    errorMessage.value = '请先在下方列表选择一个工程'
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
  try {
    await uploadModel(projectStore.activeProjectId, file)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '上传模型失败'
  } finally {
    pending.value = false
  }
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function goHome() {
  router.push('/app/home')
}
</script>

<template>
  <div class="projects-page">
    <section class="projects-current-card section-card">
      <div class="projects-current-header">
        <div>
          <p class="eyebrow">当前工程</p>
          <template v-if="projectStore.activeProject">
            <h2 class="projects-current-title">{{ projectStore.activeProject.name }}</h2>
            <p class="projects-current-description">
              {{ projectStore.activeProject.description || '暂无描述' }}
            </p>
            <p class="projects-current-meta">创建于 {{ formatDate(projectStore.activeProject.createdAt) }}</p>
          </template>
          <template v-else>
            <h2 class="projects-current-title">尚未打开工程</h2>
            <p class="projects-current-description">
              请从下方列表选择一个工程，或返回主页新建项目。
            </p>
          </template>
        </div>
        <button class="side-button" type="button" @click="goHome">返回主页</button>
      </div>

      <div v-if="projectStore.activeProject" class="projects-current-actions">
        <button class="side-button primary" type="button" :disabled="pending" @click="triggerUpload">
          {{ pending ? '上传中...' : '上传模型' }}
        </button>
      </div>
    </section>

    <section class="projects-create-card section-card">
      <h3 class="section-title">新建工程</h3>
      <div class="projects-create-grid">
        <input v-model="newProjectName" class="text-control" type="text" placeholder="工程名称" />
        <input v-model="newProjectDescription" class="text-control" type="text" placeholder="工程描述（可选）" />
        <button class="side-button primary" type="button" @click="createProject">创建工程</button>
      </div>
    </section>

    <p v-if="errorMessage" class="projects-error">{{ errorMessage }}</p>

    <section class="projects-list section-card">
      <h3 class="section-title">工程列表</h3>
      <p v-if="projectStore.loading" class="projects-empty">加载中...</p>
      <p v-else-if="projectStore.projects.length === 0" class="projects-empty">暂无工程，请先新建。</p>

      <button
        v-for="project in projectStore.projects"
        :key="project.id"
        class="project-list-row"
        :class="{ 'is-active': projectStore.activeProjectId === project.id }"
        type="button"
        @click="selectProject(project.id)"
      >
        <div class="project-list-copy">
          <h4 class="project-card-title">{{ project.name }}</h4>
          <p class="project-card-description">{{ project.description || '暂无描述' }}</p>
          <p class="project-card-meta">{{ formatDate(project.createdAt) }}</p>
        </div>
        <span v-if="projectStore.activeProjectId === project.id" class="project-list-badge">当前工程</span>
      </button>
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
