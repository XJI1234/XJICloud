<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ApiError } from '@/api/client'

const router = useRouter()
const projectStore = useProjectStore()

const errorMessage = ref('')
const createDialogVisible = ref(false)
const openDialogVisible = ref(false)
const newProjectName = ref('')
const newProjectDescription = ref('')
const selectedProjectId = ref<string | null>(null)
const pending = ref(false)

onMounted(async () => {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载项目失败'
  }
})

function openCreateDialog() {
  newProjectName.value = ''
  newProjectDescription.value = ''
  errorMessage.value = ''
  createDialogVisible.value = true
}

function openProjectPicker() {
  selectedProjectId.value = projectStore.activeProjectId
  errorMessage.value = ''
  openDialogVisible.value = true
}

async function submitCreateProject() {
  if (!newProjectName.value.trim()) {
    errorMessage.value = '请输入项目名称'
    return
  }

  pending.value = true
  try {
    await projectStore.createProject(newProjectName.value.trim(), newProjectDescription.value.trim())
    createDialogVisible.value = false
    await router.push('/app/projects')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '创建项目失败'
  } finally {
    pending.value = false
  }
}

function confirmOpenProject() {
  if (!selectedProjectId.value) {
    errorMessage.value = '请选择一个项目'
    return
  }

  projectStore.openProject(selectedProjectId.value)
  openDialogVisible.value = false
  router.push('/app/projects')
}

function openRecentProject(projectId: string) {
  projectStore.openProject(projectId)
  router.push('/app/projects')
}
</script>

<template>
  <div class="home-page">
    <div class="home-hero">
      <div class="home-actions">
        <button class="home-primary-button" type="button" @click="openCreateDialog">
          新建项目
        </button>
        <button class="home-secondary-button" type="button" @click="openProjectPicker">
          打开项目
        </button>
      </div>

      <section class="home-recent-section">
        <h2 class="home-recent-title">最近项目：</h2>
        <p v-if="projectStore.loading" class="home-recent-empty">加载中...</p>
        <p v-else-if="projectStore.recentProjects.length === 0" class="home-recent-empty">暂无最近打开的项目</p>
        <ul v-else class="home-recent-list">
          <li v-for="project in projectStore.recentProjects" :key="project.id">
            <button class="home-recent-link" type="button" @click="openRecentProject(project.id)">
              {{ project.name }}
            </button>
          </li>
        </ul>
      </section>

      <p v-if="errorMessage && !createDialogVisible && !openDialogVisible" class="home-error">{{ errorMessage }}</p>
    </div>

    <div v-if="createDialogVisible" class="app-modal-backdrop" @click.self="createDialogVisible = false">
      <div class="app-modal home-dialog">
        <div class="app-modal-header">
          <h2 class="app-modal-title">新建项目</h2>
        </div>
        <div class="app-modal-body home-dialog-body">
          <label class="field-label" for="home-project-name">项目名称</label>
          <input id="home-project-name" v-model="newProjectName" class="text-control" type="text" placeholder="输入项目名称" />
          <label class="field-label" for="home-project-desc">项目描述</label>
          <input id="home-project-desc" v-model="newProjectDescription" class="text-control" type="text" placeholder="可选" />
          <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
        </div>
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="createDialogVisible = false">取消</button>
          <button class="side-button primary" type="button" :disabled="pending" @click="submitCreateProject">
            {{ pending ? '创建中...' : '创建并打开' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="openDialogVisible" class="app-modal-backdrop" @click.self="openDialogVisible = false">
      <div class="app-modal home-dialog">
        <div class="app-modal-header">
          <h2 class="app-modal-title">打开项目</h2>
        </div>
        <div class="app-modal-body home-dialog-body">
          <p v-if="projectStore.projects.length === 0" class="home-recent-empty">暂无项目，请先新建项目。</p>
          <div v-else class="home-project-picker">
            <button
              v-for="project in projectStore.projects"
              :key="project.id"
              class="home-picker-item"
              :class="{ 'is-selected': selectedProjectId === project.id }"
              type="button"
              @click="selectedProjectId = project.id"
            >
              <strong>{{ project.name }}</strong>
              <span>{{ project.description || '暂无描述' }}</span>
            </button>
          </div>
          <p v-if="errorMessage" class="home-error">{{ errorMessage }}</p>
        </div>
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="openDialogVisible = false">取消</button>
          <button class="side-button primary" type="button" @click="confirmOpenProject">打开</button>
        </div>
      </div>
    </div>
  </div>
</template>
