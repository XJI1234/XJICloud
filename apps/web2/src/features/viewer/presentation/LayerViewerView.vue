<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import Native3DGSViewer from '@/features/viewer/presentation/components/Native3DGSViewer.vue'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useViewerStorage } from '@/features/viewer/presentation/composables/useViewerStorage'
import type { StoredDefaultView, ViewerModelSummary } from '@/features/viewer/domain/entities/viewer-config.entity'
import { DomainError } from '@/shared/domain-error'
import { formatDomainError } from '@/presentation/errors'
import AppButton from '@/presentation/components/AppButton.vue'
import AppSheet from '@/presentation/components/AppSheet.vue'

const { t, locale } = useI18n()
const route = useRoute()
const workspace = useProjectWorkspace()
const viewer = useViewerStorage()
const currentFile = ref<File | null>(null)
const activeFile = ref<File | null>(null)
const activeModelId = ref<string | null>(null)
const defaultView = ref<StoredDefaultView | null>(null)
const error = ref<DomainError | null>(null)
const download = ref<{ name: string; loaded: number; total: number } | null>(null)
const pickerVisible = ref(false)
const pickerLoading = ref(false)
const candidates = ref<ViewerModelSummary[]>([])
const pickerMessage = ref('')
const sortedCandidates = computed(() => [...candidates.value].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt)))
let alive = true
let selection = 0
let pickerRequest = 0
let controller: AbortController | null = null
let candidateModel: { file: File; modelId: string } | null = null

function cancelLoad() {
  ++selection
  controller?.abort()
  controller = null
  download.value = null
  currentFile.value = activeFile.value
  candidateModel = null
  error.value = null
}
function openLocal(file: File) {
  cancelLoad()
  const [validationError, validated] = viewer.openLocal(file)
  if (validationError || !validated) { error.value = validationError; return }
  defaultView.value = null
  currentFile.value = validated
  closePicker()
}
function loaded(file: File) {
  if (file !== currentFile.value) return
  activeFile.value = file
  activeModelId.value = candidateModel?.file === file ? candidateModel.modelId : null
  candidateModel = null
}
function closed() {
  cancelLoad()
  currentFile.value = null
  activeFile.value = null
  activeModelId.value = null
  defaultView.value = null
}
function closePicker() { pickerVisible.value = false; ++pickerRequest; pickerLoading.value = false }

async function openCloudPicker() {
  const projectId = workspace.activeProjectId()
  pickerVisible.value = true
  candidates.value = []
  pickerMessage.value = ''
  const request = ++pickerRequest
  if (!projectId) { pickerMessage.value = t('nativeViewer.noProject'); return }
  pickerLoading.value = true
  const [listError, models] = await viewer.listModels(projectId)
  if (!alive || request !== pickerRequest) return
  pickerLoading.value = false
  if (listError) { pickerMessage.value = formatDomainError(t, listError); return }
  candidates.value = models ?? []
  if (!candidates.value.length) pickerMessage.value = t('nativeViewer.noCloudModels')
}

async function loadCloud(model: ViewerModelSummary) {
  cancelLoad()
  const request = selection
  const abort = new AbortController()
  controller = abort
  closePicker()
  defaultView.value = null
  viewer.rememberMeta(model.id, model.fileName)
  download.value = { name: model.fileName, loaded: 0, total: model.sizeBytes }
  try {
    // Old camera metadata is optional: never hold a downloaded model behind this request.
    void viewer.loadConfig(model.id, { signal: abort.signal }).then(([, config]) => {
      if (alive && request === selection && !abort.signal.aborted) defaultView.value = config?.defaultView ?? null
    })
    const [loadError, data] = await viewer.loadBytes(model.id, (bytes, total) => {
      if (alive && request === selection) download.value = { name: model.fileName, loaded: bytes, total }
    }, { signal: abort.signal })

    if (!alive || request !== selection || abort.signal.aborted) return
    if (loadError || !data) { error.value = loadError ?? new DomainError('VIEWER_LOAD_FAILED'); return }
    candidateModel = { file: data.file, modelId: data.modelId }
    currentFile.value = data.file
  } catch {
    if (alive && request === selection && !abort.signal.aborted) error.value = new DomainError('VIEWER_LOAD_FAILED')
  } finally {
    if (alive && request === selection) download.value = null
  }
}

function modelSize(bytes: number) {
  return bytes >= 1024 ** 3 ? `${(bytes / 1024 ** 3).toFixed(2)} GB`
    : bytes >= 1024 ** 2 ? `${(bytes / 1024 ** 2).toFixed(1)} MB`
      : bytes >= 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${bytes} B`
}
function updatedAt(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale.value)
}

watch(() => route.query.modelId, async (modelId) => {
  // A route change is a new selection, including removal of modelId.
  cancelLoad()
  const request = ++pickerRequest
  const routeSelection = selection
  if (typeof modelId !== 'string' || !modelId) return
  const projectId = workspace.activeProjectId()
  if (!projectId) { error.value = new DomainError('MODEL_PROJECT_REQUIRED'); return }
  const [listError, models] = await viewer.listModels(projectId)
  if (!alive || request !== pickerRequest || routeSelection !== selection) return
  if (listError) { error.value = listError; return }
  const model = models?.find((item) => item.id === modelId)
  if (model) await loadCloud(model)
  else error.value = new DomainError('VIEWER_LOAD_FAILED')
}, { immediate: true })
onBeforeUnmount(() => { alive = false; ++pickerRequest; cancelLoad() })
</script>

<template>
  <main class="app-shell layer-viewer-root">
    <Native3DGSViewer :file="currentFile" :default-view="defaultView" :download="download" :error="error" cloud
      @open-file="openLocal" @open-cloud="openCloudPicker" @cancel="cancelLoad" @closed="closed" @loaded="loaded" />
    <AppSheet :visible="pickerVisible" :title="t('viewer.selectModelTitle')" @close="closePicker">
      <p class="model-picker-subtitle">{{ t('nativeViewer.pickerHint') }}</p>
      <p v-if="pickerLoading" role="status">{{ t('viewer.downloading') }}</p>
      <p v-else-if="pickerMessage" role="status">{{ pickerMessage }}</p>
      <div v-else class="model-choice-list">
        <button v-for="model in sortedCandidates" :key="model.id" class="model-choice-card" :class="{ 'is-current': model.id === activeModelId }" type="button" @click="loadCloud(model)">
          <span class="model-choice-name">{{ model.fileName }}</span>
          <span class="model-choice-meta">{{ model.format }} · {{ modelSize(model.sizeBytes) }}</span>
          <span v-if="model.updatedAt" class="model-choice-updated">{{ t('viewer.updatedAt', { date: updatedAt(model.updatedAt) }) }}</span>
          <span v-if="model.id === activeModelId" class="model-choice-badge">{{ t('viewer.currentlyLoaded') }}</span>
        </button>
      </div>
      <template #footer><AppButton @click="closePicker">{{ t('common.close') }}</AppButton></template>
    </AppSheet>
  </main>
</template>
