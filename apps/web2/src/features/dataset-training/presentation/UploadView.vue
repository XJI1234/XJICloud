<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import type { Project } from '@/features/project/domain/entities/project.entity'
import DatasetUploadPanel from './DatasetUploadPanel.vue'
import TrainingJobPanel from './TrainingJobPanel.vue'
import ModelUploadPanel from '@/features/model-asset/presentation/ModelUploadPanel.vue'

type UploadTab = 'dataset' | 'model'

const router = useRouter()
const { t } = useI18n()
const workspace = useProjectWorkspace()

const projects = ref<Project[]>([])
const activeTab = ref<UploadTab>('dataset')
const errorMessage = ref('')
const activeProjectId = ref<string | null>(workspace.activeProjectId())
const activeProject = computed(() => projects.value.find((project) => project.id === activeProjectId.value) ?? null)

onMounted(async () => {
  const [error, data] = await workspace.load()
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  projects.value = data ?? []
  activeProjectId.value = workspace.activeProjectId()
})
</script>

<template>
  <div class="upload-page">
    <div class="cloud-page-inner">
      <h2 class="upload-title">{{ t('upload.title') }}</h2>
      <div class="upload-tabs">
        <button class="upload-tab" :class="{ 'upload-tab--active': activeTab === 'dataset' }" type="button" @click="activeTab = 'dataset'">
          {{ t('upload.tabDataset') }}
        </button>
        <button class="upload-tab" :class="{ 'upload-tab--active': activeTab === 'model' }" type="button" @click="activeTab = 'model'">
          {{ t('upload.tabModel') }}
        </button>
      </div>

      <section v-if="activeTab === 'dataset'" class="upload-tab-panel">
        <p class="upload-tab-hint">{{ t('upload.datasetHint', { name: activeProject?.name || t('upload.noProjectOpen') }) }}</p>
        <DatasetUploadPanel v-if="activeProjectId" :project-id="activeProjectId" />
        <p v-else class="upload-empty-text">{{ t('upload.openProjectFirst') }}</p>
        <TrainingJobPanel :project-id="activeProjectId" />
      </section>

      <section v-else class="upload-tab-panel">
        <ModelUploadPanel
          :project-id="activeProjectId"
          :project-name="activeProject?.name || t('upload.noProjectOpen')"
        />
      </section>

      <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
      <p v-if="activeTab === 'dataset'" class="upload-actions upload-page-footer">
        <AppButton @click="router.push('/app/projects')">{{ t('projects.backHome') }}</AppButton>
      </p>
    </div>
  </div>
</template>
