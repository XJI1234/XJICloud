<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { PhArrowArcLeft, PhArrowArcRight, PhArrowCounterClockwise, PhArrowsDownUp, PhCloud, PhFolderOpen, PhList, PhUploadSimple, PhX } from '@phosphor-icons/vue'
import NativeSplatViewport from '@/features/viewer/presentation/components/NativeSplatViewport.vue'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import { useViewerStorage } from '@/features/viewer/presentation/composables/useViewerStorage'
import { formatDomainError } from '@/presentation/errors'
import AppButton from '@/presentation/components/AppButton.vue'
import AppSheet from '@/presentation/components/AppSheet.vue'
import type { StoredDefaultView, ViewerModelSummary } from '@/features/viewer/domain/entities/viewer-config.entity'
import type { Project } from '@/features/project/domain/entities/project.entity'

const { t, locale } = useI18n()
const workspace = useProjectWorkspace()
const modelsApi = useModelAssets()
const viewer = useViewerStorage()

const projectList = ref<Project[]>([])
const activeProjectId = ref<string | null>(workspace.activeProjectId())

const currentModelId = ref<string | null>(null)
const currentFile = ref<File | null>(null)
const defaultView = ref<StoredDefaultView | null>(null)
const uploadInputRef = ref<HTMLInputElement | null>(null)
const localInputRef = ref<HTMLInputElement | null>(null)
const resetViewToken = ref(0)
const rotateClockwiseToken = ref(0)
const rotateCounterclockwiseToken = ref(0)
const flipYToken = ref(0)
const menuOpen = ref(true)
const modelSelectionVisible = ref(false)
const modelCandidates = ref<ViewerModelSummary[]>([])
const modelInfo = ref<{ fileName: string; splatCount: number } | null>(null)
const actionError = ref('')
const statusState = ref<
  | { type: 'i18n'; key: string; params?: Record<string, unknown> }
  | { type: 'raw'; message: string }
>({ type: 'i18n', key: 'viewer.status.pickFile' })

function setStatus(key: string, params?: Record<string, unknown>) {
  statusState.value = params ? { type: 'i18n', key, params } : { type: 'i18n', key }
}

function setRawStatus(message: string) {
  statusState.value = { type: 'raw', message }
}

const statusMessage = computed(() => {
  const state = statusState.value
  if (state.type === 'raw') {
    return state.message
  }
  return String(t(state.key, state.params as Record<string, unknown>))
})

const activeProjectName = computed(
  () => projectList.value.find((project) => project.id === activeProjectId.value)?.name ?? t('viewer.noProjectSelected'),
)

const sortedCandidates = computed(() =>
  [...modelCandidates.value].sort(
    (left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime(),
  ),
)

async function loadSavedDefaultView(modelId: string) {
  const [error, config] = await viewer.loadConfig(modelId)
  if (error || !config) {
    defaultView.value = null
    return
  }
  defaultView.value = config.defaultView
}

function setCurrentModelSource(file: File, modelId: string | null = null) {
  currentFile.value = file
  currentModelId.value = modelId
  modelInfo.value = null
  defaultView.value = null
  actionError.value = ''
  setStatus('viewer.status.loadingFile', { name: file.name })
  closeModelSelectionDialog()
  if (modelId) {
    void loadSavedDefaultView(modelId)
  }
}

async function loadCloudModel(model: ViewerModelSummary) {
  if (!activeProjectId.value) {
    return
  }

  viewer.rememberMeta(model.id, model.fileName)

  try {
    setStatus('viewer.status.downloading', { name: model.fileName })
    const [error, loaded] = await viewer.loadBytes(model.id, (loadedBytes, total) => {
      if (total > 0) {
        setStatus('viewer.status.downloadingProgress', {
          name: model.fileName,
          percent: Math.round((loadedBytes / total) * 100),
        })
      }
    })
    if (error || !loaded) {
      actionError.value = formatDomainError(t, error)
      setRawStatus(formatDomainError(t, error))
      return
    }
    setCurrentModelSource(loaded.file, loaded.modelId)
  } catch (error) {
    actionError.value = formatDomainError(t, error)
    setRawStatus(formatDomainError(t, error))
  }
}

function closeModelSelectionDialog() {
  modelSelectionVisible.value = false
  modelCandidates.value = []
}

function formatModelSize(sizeBytes: number) {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`
  }
  if (sizeBytes < 1024 * 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`
  }
  return `${(sizeBytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function formatModelUpdatedAt(updatedAt: string) {
  const date = new Date(updatedAt)
  if (Number.isNaN(date.getTime())) {
    return updatedAt
  }
  return date.toLocaleString(locale.value)
}

async function openFilePicker() {
  actionError.value = ''

  if (!activeProjectId.value) {
    actionError.value = 'selectProjectFirst'
    setStatus('viewer.status.selectProjectFirst')
    return
  }

  try {
    const [error, models] = await viewer.listModels(activeProjectId.value)
    if (error) {
      actionError.value = formatDomainError(t, error)
      setRawStatus(formatDomainError(t, error))
      return
    }
    if (!models || models.length === 0) {
      actionError.value = 'noModels'
      setStatus('viewer.status.noModels')
      return
    }

    modelCandidates.value = models
    modelSelectionVisible.value = true
    if (models.length > 1) {
      setStatus('viewer.status.selectModelMulti', { count: models.length })
    } else {
      setStatus('viewer.status.selectModelPrompt')
    }
  } catch (error) {
    actionError.value = formatDomainError(t, error)
    setRawStatus(formatDomainError(t, error))
  }
}

function triggerLocalOpen() {
  actionError.value = ''
  localInputRef.value?.click()
}

function handleLocalFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }

  const [error, localFile] = viewer.openLocal(file)
  if (error || !localFile) {
    actionError.value = formatDomainError(t, error)
    setRawStatus(formatDomainError(t, error))
    return
  }

  setCurrentModelSource(localFile, null)
}

function triggerUpload() {
  if (!activeProjectId.value) {
    actionError.value = 'selectProjectFirst'
    setStatus('viewer.status.selectProjectFirst')
    return
  }

  uploadInputRef.value?.click()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file || !activeProjectId.value) {
    return
  }

  try {
    setStatus('viewer.status.uploading', { name: file.name })
    const [error, model] = await modelsApi.upload({ projectId: activeProjectId.value, file })
    if (error || !model) {
      actionError.value = formatDomainError(t, error)
      setRawStatus(formatDomainError(t, error))
      return
    }
    viewer.rememberMeta(model.id, model.fileName)
    setCurrentModelSource(file, model.id)
    setStatus('viewer.status.uploadDoneLoading', { name: file.name })
  } catch (error) {
    actionError.value = formatDomainError(t, error)
    setRawStatus(formatDomainError(t, error))
  }
}

function requestResetView() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.resettingCamera')
  resetViewToken.value += 1
}

function requestRotateView(direction: 'clockwise' | 'counterclockwise') {
  if (!modelInfo.value) {
    return
  }
  actionError.value = ''
  setStatus(direction === 'clockwise' ? 'viewer.status.rotatingCw' : 'viewer.status.rotatingCcw')
  if (direction === 'clockwise') {
    rotateClockwiseToken.value += 1
    return
  }
  rotateCounterclockwiseToken.value += 1
}

function requestFlipY() {
  if (!modelInfo.value) {
    return
  }
  actionError.value = ''
  setStatus('viewer.status.flippingY')
  flipYToken.value += 1
}

function handleLoaded(info: { fileName: string; splatCount: number; view: 'default' | 'framed' }) {
  modelInfo.value = { fileName: info.fileName, splatCount: info.splatCount }
  actionError.value = ''
  if (info.view === 'default') {
    setStatus('viewer.status.appliedDefaultView')
    return
  }
  setStatus('viewer.status.modelLoaded', { name: info.fileName })
}

function handleFailed(message: string) {
  modelInfo.value = null
  actionError.value = message === 'load-failed' ? String(t('viewer.status.loadModelFailed')) : message
  setRawStatus(actionError.value)
}

function handleStatus(code: string) {
  if (actionError.value) {
    return
  }
  if (code === 'applied-default') {
    setStatus('viewer.status.appliedDefaultView')
    return
  }
  if (code === 'reset-default') {
    setStatus('viewer.status.resetToDefault')
    return
  }
  if (code === 'reset-framed') {
    setStatus('viewer.status.resetToFramed')
    return
  }
  if (code === 'rotation-cw-done') {
    setStatus('viewer.status.rotationCwDone')
    return
  }
  if (code === 'rotation-ccw-done') {
    setStatus('viewer.status.rotationCcwDone')
    return
  }
  if (code === 'flip-y') {
    setStatus('viewer.status.flippedY')
    return
  }
  if (code === 'loading') {
    if (currentFile.value) {
      setStatus('viewer.status.loadingFile', { name: currentFile.value.name })
    }
  }
}

onMounted(() => {
  workspace.load().then(([error, data]) => {
    if (error) {
      setStatus('viewer.status.projectsLoadFailed')
      return
    }
    projectList.value = data ?? []
    activeProjectId.value = workspace.activeProjectId()
  })
})
</script>

<template>
  <main class="app-shell layer-viewer-root">
    <section class="viewer-stage">
      <div class="viewport-frame">
        <NativeSplatViewport
          :file="currentFile"
          :default-view="defaultView"
          :reset-view-token="resetViewToken"
          :rotate-clockwise-token="rotateClockwiseToken"
          :rotate-counterclockwise-token="rotateCounterclockwiseToken"
          :flip-y-token="flipYToken"
          @loaded="handleLoaded"
          @failed="handleFailed"
          @status="handleStatus"
        />
      </div>
    </section>

    <button
      v-if="!menuOpen"
      class="viewer-menu-fab"
      type="button"
      :aria-label="t('viewer.menu')"
      @click="menuOpen = true"
    >
      <PhList :size="20" weight="regular" />
    </button>

    <aside v-else class="viewer-inspector">
      <div class="viewer-inspector__top">
        <h1 class="model-title">{{ modelInfo?.fileName || t('viewer.waitingModel') }}</h1>
        <button class="viewer-inspector__collapse" type="button" :aria-label="t('viewer.collapseMenu')" @click="menuOpen = false">
          <PhX :size="16" weight="regular" />
        </button>
      </div>
      <p class="info-project-context">{{ t('viewer.currentProject', { name: activeProjectName }) }}</p>
      <p v-if="modelInfo" class="viewer-meta">{{ t('viewer.splatCount', { count: modelInfo.splatCount.toLocaleString(locale) }) }}</p>
      <p v-else class="viewer-hint">{{ t('viewer.emptyHint') }}</p>

      <div class="viewer-actions">
        <AppButton variant="primary" compact @click="triggerLocalOpen">
          <PhFolderOpen :size="16" weight="regular" />
          {{ t('viewer.openLocal') }}
        </AppButton>
        <AppButton compact @click="openFilePicker">
          <PhCloud :size="16" weight="regular" />
          {{ t('viewer.selectModel') }}
        </AppButton>
        <AppButton compact @click="triggerUpload">
          <PhUploadSimple :size="16" weight="regular" />
          {{ t('viewer.uploadModel') }}
        </AppButton>
        <AppButton compact :disabled="!modelInfo" @click="requestResetView">
          <PhArrowCounterClockwise :size="16" weight="regular" />
          {{ t('viewer.resetView') }}
        </AppButton>
        <div class="viewer-orient-row">
          <AppButton compact :disabled="!modelInfo" @click="requestRotateView('counterclockwise')">
            <PhArrowArcLeft :size="16" weight="regular" />
            {{ t('viewer.counterclockwise') }}
          </AppButton>
          <AppButton compact :disabled="!modelInfo" @click="requestRotateView('clockwise')">
            <PhArrowArcRight :size="16" weight="regular" />
            {{ t('viewer.clockwise') }}
          </AppButton>
        </div>
        <AppButton compact :disabled="!modelInfo" @click="requestFlipY">
          <PhArrowsDownUp :size="16" weight="regular" />
          {{ t('viewer.flipY') }}
        </AppButton>
      </div>

      <p class="viewer-status" :class="{ error: Boolean(actionError) }">{{ statusMessage }}</p>
    </aside>

    <input
      ref="localInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleLocalFile"
    />
    <input
      ref="uploadInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleUpload"
    />

    <AppSheet
      :visible="modelSelectionVisible"
      :title="t('viewer.selectModelTitle')"
      @close="closeModelSelectionDialog"
    >
      <p class="model-picker-subtitle">{{ t('viewer.modelCountHint', { count: modelCandidates.length }) }}</p>
      <div class="model-choice-list">
        <button
          v-for="candidate in sortedCandidates"
          :key="candidate.id"
          class="model-choice-card"
          :class="{ 'is-current': candidate.id === currentModelId }"
          type="button"
          @click="loadCloudModel(candidate)"
        >
          <span class="model-choice-name">{{ candidate.fileName }}</span>
          <span class="model-choice-meta">
            {{ candidate.format || t('viewer.unknownFormat') }}
            <template v-if="candidate.version"> · v{{ candidate.version }}</template>
            <template v-if="candidate.sizeBytes"> · {{ formatModelSize(candidate.sizeBytes) }}</template>
          </span>
          <span v-if="candidate.updatedAt" class="model-choice-updated">
            {{ t('viewer.updatedAt', { date: formatModelUpdatedAt(candidate.updatedAt) }) }}
          </span>
          <span v-if="candidate.id === currentModelId" class="model-choice-badge">{{ t('viewer.currentlyLoaded') }}</span>
        </button>
      </div>
      <template #footer>
        <AppButton @click="closeModelSelectionDialog">{{ t('common.cancel') }}</AppButton>
      </template>
    </AppSheet>
  </main>
</template>
