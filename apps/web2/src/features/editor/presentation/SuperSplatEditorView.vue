<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { PhCloud, PhCloudArrowUp, PhFolderOpen, PhPlus, PhClockCounterClockwise } from '@phosphor-icons/vue'
import AppButton from '@/presentation/components/AppButton.vue'
import AppSheet from '@/presentation/components/AppSheet.vue'
import WaitOverlay from '@/presentation/components/WaitOverlay.vue'
import { useWaitSession } from '@/presentation/composables/useWaitSession'
import { createTransferRateTracker, formatTransferSpeed } from '@/shared/transfer-rate'
import { formatDomainError } from '@/presentation/errors'
import { DomainError } from '@/shared/domain-error'
import { mapCloudSaveBar } from '@/features/editor/domain/services/cloud-save-progress.service'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import { useEditorSession } from '@/features/editor/presentation/composables/useEditorSession'
import type { SpzVersion } from '@/features/editor/domain/entities/editor-session.entity'
import type { ModelAsset, ModelVersion } from '@/features/model-asset/domain/entities/model-asset.entity'
import { detectModelFormat, type ExportModelFormat } from '@/features/model-asset/domain/services/model-format.service'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { saveBlobAsFile } from '@/presentation/save-blob'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const { formatDateTime } = useFormatDateTime()
const workspace = useProjectWorkspace()
const modelsApi = useModelAssets()
const editor = useEditorSession()
const wait = useWaitSession()
const { waitView } = wait

const iframeRef = ref<HTMLIFrameElement | null>(null)
const localInputRef = ref<HTMLInputElement | null>(null)
const models = ref<ModelAsset[]>([])
// Identity belongs to the successfully imported scene, never the picker or URL.
const selectedModelId = ref<string | null>(null)
const sceneReady = ref(false)
const frameGeneration = ref(0)
let disposed = false
const lifetime = new AbortController()
const switching = ref(false)
const localFileName = ref<string | null>(null)
const pickerVisible = ref(false)
const versionsVisible = ref(false)
const exportVisible = ref(false)
const exportFormat = ref<ExportModelFormat>('ply')
const spzVersion = ref<SpzVersion>(3)
const exportMode = ref<'overwrite' | 'saveAs'>('saveAs')
const exportFileName = ref('')
const downloadingHistory = ref(false)
const versions = ref<ModelVersion[]>([])
const loadingEditor = ref(false)
const saving = ref(false)
const restoring = ref(false)
const statusMessage = ref('')
const downloadById = ref<Record<string, true>>({})
const errorMessage = ref('')
const activeProjectId = ref<string | null>(workspace.activeProjectId())

const editorLang = computed(() => (locale.value === 'en-US' ? 'en' : 'zh-CN'))
const selectedCloudModel = computed(() => models.value.find((model) => model.id === selectedModelId.value) ?? null)
const canSaveToCloud = computed(() => sceneReady.value && Boolean(activeProjectId.value) && !loadingEditor.value && !saving.value && !restoring.value && !switching.value)
const canManageVersions = computed(() => Boolean(selectedModelId.value) && !saving.value && !restoring.value && !loadingEditor.value && !switching.value)
const sessionLabel = computed(() => {
  if (selectedCloudModel.value) {
    return selectedCloudModel.value.fileName
  }
  if (localFileName.value) {
    return localFileName.value
  }
  return t('supersplat.blankSession')
})

function defaultExportFormat(): ExportModelFormat {
  const name = selectedCloudModel.value?.fileName ?? localFileName.value ?? ''
  return detectModelFormat(name) === 'SPZ' ? 'spz' : 'ply'
}

function openExportSheet() {
  if (!canSaveToCloud.value) {
    return
  }
  exportFormat.value = defaultExportFormat()
  exportMode.value = selectedModelId.value ? 'overwrite' : 'saveAs'
  exportFileName.value = selectedCloudModel.value?.fileName ?? localFileName.value ?? ''
  errorMessage.value = ''
  exportVisible.value = true
}

async function permitReplacement() {
  if (saving.value || loadingEditor.value || restoring.value || switching.value || disposed) return false
  if (!sceneReady.value) return true
  switching.value = true
  try {
    const frame = editorFrame()
    if (!frame) return false
    const [error, dirty] = await editor.isDirty(frame, lifetime.signal)
    if (disposed) return false
    if (error) {
      errorMessage.value = formatDomainError(t, error)
      return false
    }
    return !dirty || window.confirm(t('supersplat.leaveConfirm'))
  } finally {
    switching.value = false
  }
}

async function navigateEditor(src: string) {
  // The host has obtained discard permission. Replace the node so vendor beforeunload
  // cannot ask a second time; browser/tab close still uses the vendor's own guard.
  sceneReady.value = false
  selectedModelId.value = null
  localFileName.value = null
  frameGeneration.value++
  await nextTick()
  const iframe = iframeRef.value
  if (!iframe || disposed) return new DomainError('OPERATION_CANCELLED')
  return new Promise<DomainError | null>((resolve) => {
    const finish = (error: DomainError | null) => {
      clearTimeout(timer)
      iframe.removeEventListener('load', loaded)
      lifetime.signal.removeEventListener('abort', cancelled)
      resolve(error)
    }
    const loaded = () => finish(null)
    const cancelled = () => finish(new DomainError('OPERATION_CANCELLED'))
    const timer = setTimeout(() => finish(new DomainError('EDITOR_TIMEOUT')), 30_000)
    iframe.addEventListener('load', loaded, { once: true })
    lifetime.signal.addEventListener('abort', cancelled, { once: true })
    iframe.src = `${src}${src.includes('?') ? '&' : '?'}s=${Date.now()}`
  })
}

function editorFrame() {
  const iframe = iframeRef.value
  if (!iframe) {
    return null
  }
  return {
    get contentWindow() {
      return iframe.contentWindow
    },
    get src() {
      return iframe.src
    },
  }
}

function dismissWait() {
  wait.hide()
  saving.value = false
}

async function importScene(file?: File) {
  const [srcError, src] = editor.src(editor.blank(editorLang.value))
  if (srcError || !src) return srcError ?? new DomainError('EDITOR_NOT_READY')
  const navigationError = await navigateEditor(src)
  if (navigationError) return navigationError
  const frame = editorFrame()
  if (!frame?.contentWindow) return new DomainError('EDITOR_NOT_READY')
  const [readyError] = await editor.waitReady(frame, lifetime.signal)
  if (readyError || disposed) return readyError ?? new DomainError('OPERATION_CANCELLED')
  if (file) {
    const [importError] = await editor.importLocal(frame, file, lifetime.signal)
    if (importError || disposed) return importError ?? new DomainError('OPERATION_CANCELLED')
  }
  sceneReady.value = true
  return null
}

async function loadBlankEditor() {
  loadingEditor.value = true
  wait.showBusy(t('wait.loading'))
  try {
    const error = await importScene()
    if (error) errorMessage.value = formatDomainError(t, error)
    return !error
  } finally {
    loadingEditor.value = false
    wait.hide()
  }
}

async function loadCloudEditor(model: ModelAsset, syncRoute = true) {
  pickerVisible.value = false
  loadingEditor.value = true
  errorMessage.value = ''
  const tracker = createTransferRateTracker()
  downloadById.value = { ...downloadById.value, [model.id]: true }
  wait.showBusy(t('wait.downloading'))
  try {
    const [downloadError, buffer] = await modelsApi.downloadBytes(model.id, (loaded, total) => {
      if (disposed) return
      const rate = tracker.push(loaded, Date.now())
      wait.showProgress(t('wait.downloading'), total > 0 ? Math.min(99, Math.round(loaded / total * 100)) : 0,
        rate != null ? formatTransferSpeed(rate) : waitView.value.speed)
    }, { signal: lifetime.signal, cacheBust: `${model.version}-${model.updatedAt}`, revision: {
      updatedAt: model.updatedAt, sizeBytes: model.sizeBytes, fileName: model.fileName,
    } })
    if (disposed) return false
    if (downloadError || !buffer) {
      errorMessage.value = formatDomainError(t, downloadError)
      return false
    }
    const file = new File([buffer], model.fileName, { type: 'application/octet-stream' })
    const [prepareError] = editor.prepareLocal(file)
    const importError = prepareError ?? await importScene(file)
    if (importError) {
      errorMessage.value = formatDomainError(t, importError)
      return false
    }
    selectedModelId.value = model.id
    statusMessage.value = ''
    if (syncRoute) await router.replace({ path: route.path, query: { ...route.query, modelId: model.id } })
    return true
  } finally {
    const next = { ...downloadById.value }
    delete next[model.id]
    downloadById.value = next
    loadingEditor.value = false
    wait.hide()
  }
}

async function ensureProjects() {
  const [error] = await workspace.load()
  if (error) {
    return
  }
  activeProjectId.value = workspace.activeProjectId()
}

async function refreshModels() {
  if (!activeProjectId.value) {
    models.value = []
    selectedModelId.value = null
    return
  }
  const [error, data] = await modelsApi.list(activeProjectId.value)
  if (error) {
    models.value = []
    return
  }
  models.value = data ?? []
}

async function openBlankSession() {
  if (!await permitReplacement()) return
  if (await loadBlankEditor()) {
    statusMessage.value = ''
    await router.replace({ path: route.path, query: { ...route.query, modelId: undefined } })
  }
}

async function selectCloudModel(model: ModelAsset) {
  if (!await permitReplacement()) return
  await loadCloudEditor(model)
}

async function deletePickedModel(model: ModelAsset, event: Event) {
  event.stopPropagation()
  event.preventDefault()
  if (!window.confirm(t('supersplat.deleteConfirm'))) {
    return
  }
  const deletingScene = selectedModelId.value === model.id
  if (deletingScene && !await permitReplacement()) return
  const [error] = await modelsApi.remove(model.id)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  models.value = models.value.filter((item) => item.id !== model.id)
  if (deletingScene && await loadBlankEditor()) {
    await router.replace({ path: route.path, query: { ...route.query, modelId: undefined } })
  }
}

function triggerLocalOpen() {
  localInputRef.value?.click()
}

async function handleLocalFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const [prepareError] = editor.prepareLocal(file)
  if (prepareError) {
    errorMessage.value = formatDomainError(t, prepareError)
    return
  }
  if (!await permitReplacement()) return
  loadingEditor.value = true
  wait.showBusy(t('wait.loading'))
  try {
    const error = await importScene(file)
    if (error) {
      errorMessage.value = formatDomainError(t, error)
      return
    }
    localFileName.value = file.name
    await router.replace({ path: route.path, query: { ...route.query, modelId: undefined } })
  } finally {
    loadingEditor.value = false
    wait.hide()
  }
}

async function saveToCloud() {
  if (!canSaveToCloud.value) return
  const frame = editorFrame()
  if (!frame?.contentWindow) {
    errorMessage.value = formatDomainError(t, new DomainError('EDITOR_NOT_READY'))
    return
  }
  if (!activeProjectId.value) {
    errorMessage.value = formatDomainError(t, new DomainError('MODEL_PROJECT_REQUIRED'))
    return
  }
  if (exportMode.value === 'overwrite' && !selectedModelId.value) {
    errorMessage.value = formatDomainError(t, new DomainError('UNKNOWN'))
    return
  }
  if (exportMode.value === 'saveAs' && !exportFileName.value.trim()) {
    errorMessage.value = formatDomainError(t, new DomainError('MODEL_INVALID_FORMAT'))
    return
  }

  exportVisible.value = false
  saving.value = true
  errorMessage.value = ''
  statusMessage.value = t('supersplat.savingToCloud')
  wait.showBusy(t('wait.exporting'), true)
  const tracker = createTransferRateTracker()
  let progressPhase: 'export' | 'upload' | null = 'export'
  let lastSpeed = ''

  const onProgress = (progress: { phase: 'export' | 'upload'; loaded: number; total: number }) => {
    if (progressPhase !== progress.phase) {
      tracker.reset()
      progressPhase = progress.phase
    }
    const rate = tracker.push(progress.loaded, Date.now())
    if (rate != null) {
      lastSpeed = formatTransferSpeed(rate)
    }
    if (progress.phase === 'export') {
      wait.showBusy(t('wait.exporting'), true)
      return
    }
    wait.showProgress(
      t('wait.uploading'),
      mapCloudSaveBar(progress.phase, progress.loaded, progress.total),
      lastSpeed,
      true,
    )
  }

  if (exportMode.value === 'overwrite' && selectedModelId.value) {
    const [error, saved] = await editor.saveExport({
      modelId: selectedModelId.value,
      sceneModelId: selectedModelId.value,
      signal: lifetime.signal,
      frame,
      format: exportFormat.value,
      spzVersion: exportFormat.value === 'spz' ? spzVersion.value : undefined,
      fileName: selectedCloudModel.value?.fileName ?? exportFileName.value,
      onProgress,
    })
    if (disposed) return
    if (error || !saved) {
      errorMessage.value = formatDomainError(t, error ?? new DomainError('EDITOR_EXPORT_FAILED'))
      statusMessage.value = ''
      wait.hide()
      saving.value = false
      return
    }
    statusMessage.value = t('supersplat.savedToCloud')
    await refreshModels()
    wait.showDone(t('wait.done'), 100, lastSpeed)
    return
  }

  const [error, created] = await editor.saveAsNew({
    projectId: activeProjectId.value,
    signal: lifetime.signal,
    frame,
    format: exportFormat.value,
    spzVersion: exportFormat.value === 'spz' ? spzVersion.value : undefined,
    fileName: exportFileName.value,
    onProgress,
  })
  if (disposed) return
  if (error || !created) {
    errorMessage.value = formatDomainError(t, error ?? new DomainError('EDITOR_EXPORT_FAILED'))
    statusMessage.value = ''
    wait.hide()
    saving.value = false
    return
  }
  selectedModelId.value = created.id
  localFileName.value = null
  statusMessage.value = t('supersplat.savedAsNewModel', { name: created.fileName })
  await refreshModels()
  await router.replace({ path: route.path, query: { ...route.query, modelId: created.id } })
  wait.showDone(t('wait.done'), 100, lastSpeed)
}

async function openVersions() {
  if (!selectedModelId.value) {
    return
  }
  errorMessage.value = ''
  const modelId = selectedModelId.value
  const [error, data] = await modelsApi.listVersions(modelId)
  if (disposed || modelId !== selectedModelId.value) return
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  versions.value = data ?? []
  versionsVisible.value = true
}

async function importBufferIntoEditor(file: File) {
  const target = selectedModelId.value
  const error = await importScene(file)
  if (!error) selectedModelId.value = target
  return error
}

async function previewVersion(version: ModelVersion) {
  if (!selectedModelId.value || version.current) {
    return
  }
  if (!await permitReplacement()) return
  restoring.value = true
  versionsVisible.value = false
  errorMessage.value = ''
  statusMessage.value = t('supersplat.previewingVersion')
  wait.showBusy(t('wait.downloading'))
  const tracker = createTransferRateTracker()
  const [downloadError, buffer] = await modelsApi.downloadVersionBytes(
    selectedModelId.value,
    version.id,
    (loadedBytes, total) => {
      const percent = total > 0 ? Math.min(99, Math.round((loadedBytes / total) * 100)) : 0
      const rate = tracker.push(loadedBytes, Date.now())
      wait.showProgress(
        t('wait.downloading'),
        percent,
        rate != null ? formatTransferSpeed(rate) : waitView.value.speed,
      )
    },
    {
      updatedAt: version.createdAt,
      sizeBytes: version.sizeBytes,
      fileName: version.fileName,
    },
    lifetime.signal,
  )
  if (disposed) return
  if (downloadError || !buffer) {
    restoring.value = false
    errorMessage.value = formatDomainError(t, downloadError)
    statusMessage.value = ''
    wait.hide()
    return
  }
  wait.showBusy(t('wait.loading'))
  const file = new File([buffer], version.fileName || 'history.ply', { type: 'application/octet-stream' })
  const importError = await importBufferIntoEditor(file)
  restoring.value = false
  wait.hide()
  if (importError) {
    errorMessage.value = formatDomainError(t, importError)
    statusMessage.value = ''
    return
  }
  statusMessage.value = t('supersplat.previewedVersion', { name: version.fileName || version.createdAt })
}

async function restoreVersion(version: ModelVersion) {
  if (!selectedModelId.value || version.current) {
    return
  }
  if (!window.confirm(t('supersplat.restoreConfirm'))) {
    return
  }
  if (!await permitReplacement()) return
  restoring.value = true
  versionsVisible.value = false
  errorMessage.value = ''
  wait.showBusy(t('wait.restoring'))
  const [error, restored] = await modelsApi.restoreVersion(selectedModelId.value, version.id)
  if (error || !restored) {
    restoring.value = false
    errorMessage.value = formatDomainError(t, error ?? new DomainError('UNKNOWN'))
    wait.hide()
    return
  }
  statusMessage.value = t('supersplat.restoredVersion')
  await refreshModels()
  const model =
    models.value.find((item) => item.id === restored.id) ??
    ({
      ...restored,
    } satisfies ModelAsset)
  await loadCloudEditor(model)
  restoring.value = false
}

async function downloadHistoryVersion(version: ModelVersion) {
  if (!selectedModelId.value || version.current) {
    return
  }
  downloadingHistory.value = true
  versionsVisible.value = false
  errorMessage.value = ''
  wait.showBusy(t('wait.downloading'), true)
  const tracker = createTransferRateTracker()
  let lastSpeed = ''
  const [downloadError, buffer] = await modelsApi.downloadVersionBytes(
    selectedModelId.value,
    version.id,
    (loadedBytes, total) => {
      const percent = total > 0 ? Math.min(99, Math.round((loadedBytes / total) * 100)) : 0
      const rate = tracker.push(loadedBytes, Date.now())
      if (rate != null) {
        lastSpeed = formatTransferSpeed(rate)
      }
      wait.showProgress(t('wait.downloading'), percent, lastSpeed, true)
    },
    {
      updatedAt: version.createdAt,
      sizeBytes: version.sizeBytes,
      fileName: version.fileName,
    },
    lifetime.signal,
  )
  if (disposed) return
  downloadingHistory.value = false
  if (downloadError || !buffer) {
    errorMessage.value = formatDomainError(t, downloadError)
    wait.hide()
    return
  }
  saveBlobAsFile(new Blob([buffer]), version.fileName || 'history.ply')
  wait.showDone(t('wait.done'), 100, lastSpeed)
}

onBeforeRouteUpdate(async (to) => {
  const id = typeof to.query.modelId === 'string' ? to.query.modelId : null
  if (sceneReady.value && id === selectedModelId.value) return true
  if (!await permitReplacement()) return false
  if (!id) return loadBlankEditor()
  const model = models.value.find(item => item.id === id)
  return model ? loadCloudEditor(model, false) : false
})

onMounted(async () => {
  await nextTick()
  await ensureProjects()
  await refreshModels()
  if (disposed) return
  const model = models.value.find(item => item.id === route.query.modelId)
  if (model) await loadCloudEditor(model, false)
  else await loadBlankEditor()
})

onBeforeUnmount(() => {
  disposed = true
  lifetime.abort()
})

onBeforeRouteLeave(() => permitReplacement())

</script>

<template>
  <div class="supersplat-page">
    <header class="supersplat-chrome">
      <div class="supersplat-chrome__copy">
        <p class="supersplat-chrome__title">{{ sessionLabel }}</p>
        <p class="supersplat-chrome__hint">{{ t('supersplat.localHint') }}</p>
        <p v-if="statusMessage" class="supersplat-chrome__status">{{ statusMessage }}</p>
      </div>
      <div class="supersplat-chrome__actions">
        <AppButton compact @click="triggerLocalOpen">
          <PhFolderOpen :size="16" weight="regular" />
          {{ t('supersplat.openLocal') }}
        </AppButton>
        <AppButton compact @click="openBlankSession">
          <PhPlus :size="16" weight="regular" />
          {{ t('supersplat.blankEditor') }}
        </AppButton>
        <AppButton compact @click="pickerVisible = true">
          <PhCloud :size="16" weight="regular" />
          {{ t('supersplat.pickCloudModel') }}
        </AppButton>
        <AppButton compact :disabled="!canManageVersions" @click="openVersions">
          <PhClockCounterClockwise :size="16" weight="regular" />
          {{ t('supersplat.versions') }}
        </AppButton>
        <AppButton compact variant="primary" :disabled="!canSaveToCloud" @click="openExportSheet">
          <PhCloudArrowUp :size="16" weight="regular" />
          {{ t('supersplat.saveToCloud') }}
        </AppButton>
      </div>
    </header>

    <p v-if="errorMessage" class="supersplat-chrome__error">{{ errorMessage }}</p>

    <section class="supersplat-stage">
      <iframe :key="frameGeneration" ref="iframeRef" class="supersplat-iframe" :title="t('supersplat.title')" allow="fullscreen" />
    </section>

    <input
      ref="localInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleLocalFile"
    />

    <AppSheet :visible="pickerVisible" :title="t('supersplat.pickCloudModel')" @close="pickerVisible = false">
      <p v-if="!activeProjectId" class="supersplat-picker-hint">{{ t('supersplat.noProjectHint') }}</p>
      <p v-else-if="models.length === 0" class="supersplat-picker-hint">{{ t('supersplat.uploadHint') }}</p>
      <p v-else class="supersplat-picker-hint">{{ t('supersplat.modelCountHint', { count: models.length }) }}</p>
      <div v-if="models.length > 0" class="model-choice-list">
        <div v-for="model in models" :key="model.id" class="model-choice-row">
          <button
            class="model-choice-card"
            :class="{ 'is-current': model.id === selectedModelId }"
            type="button"
            @click="selectCloudModel(model)"
          >
            <span class="model-choice-name">{{ model.fileName }}</span>
            <span class="model-choice-meta">{{ model.format }} · {{ formatDateTime(model.updatedAt || model.createdAt) }}</span>
            <span v-if="downloadById[model.id]" class="model-choice-badge">{{ t('supersplat.downloading') }}</span>
          </button>
          <AppButton compact variant="destructive" @click="deletePickedModel(model, $event)">
            {{ t('supersplat.deleteModel') }}
          </AppButton>
        </div>
      </div>
      <template #footer>
        <AppButton @click="pickerVisible = false">{{ t('common.cancel') }}</AppButton>
        <AppButton v-if="models.length === 0" variant="primary" @click="router.push('/app/upload')">
          {{ t('supersplat.goToUpload') }}
        </AppButton>
      </template>
    </AppSheet>

    <AppSheet :visible="versionsVisible" :title="t('supersplat.versionsTitle')" @close="versionsVisible = false">
      <p class="supersplat-picker-hint">{{ t('supersplat.versionsHint') }}</p>
      <div v-if="versions.length > 0" class="model-choice-list">
        <div v-for="version in versions" :key="version.id" class="model-choice-row">
          <div class="model-choice-card" :class="{ 'is-current': version.current }">
            <span class="model-choice-name">{{ version.fileName }}</span>
            <span class="model-choice-meta">
              {{ version.current ? t('supersplat.currentLive') : t('supersplat.historySnapshot') }}
              · {{ formatDateTime(version.createdAt) }}
              · {{ Math.max(1, Math.round(version.sizeBytes / 1024)) }} KB
            </span>
          </div>
          <AppButton
            v-if="!version.current"
            compact
            :disabled="restoring || downloadingHistory"
            @click="previewVersion(version)"
          >
            {{ t('supersplat.previewVersion') }}
          </AppButton>
          <AppButton
            v-if="!version.current"
            compact
            :disabled="restoring || downloadingHistory"
            @click="downloadHistoryVersion(version)"
          >
            {{ t('upload.downloadModel') }}
          </AppButton>
          <AppButton
            v-if="!version.current"
            compact
            variant="primary"
            :disabled="restoring || downloadingHistory"
            @click="restoreVersion(version)"
          >
            {{ t('supersplat.restoreVersion') }}
          </AppButton>
        </div>
      </div>
      <template #footer>
        <AppButton @click="versionsVisible = false">{{ t('common.cancel') }}</AppButton>
      </template>
    </AppSheet>

    <AppSheet :visible="exportVisible" :title="t('supersplat.exportSheetTitle')" @close="exportVisible = false">
      <div class="export-sheet-fields">
        <p class="supersplat-picker-hint">{{ t('supersplat.exportFormat') }}</p>
        <div class="export-sheet-options">
          <label class="export-sheet-option">
            <input v-model="exportFormat" type="radio" value="ply" />
            {{ t('supersplat.formatPly') }}
          </label>
          <label class="export-sheet-option">
            <input v-model="exportFormat" type="radio" value="spz" />
            {{ t('supersplat.formatSpz') }}
          </label>
        </div>
        <div v-if="exportFormat === 'spz'" class="export-sheet-options">
          <label class="export-sheet-option">
            <input v-model="spzVersion" type="radio" name="spz-version" :value="3" />
            {{ t('supersplat.spzV3') }}
          </label>
          <label class="export-sheet-option">
            <input v-model="spzVersion" type="radio" name="spz-version" :value="4" />
            v4
          </label>
        </div>
        <p class="supersplat-picker-hint">{{ t('supersplat.exportMode') }}</p>
        <div class="export-sheet-options">
          <label class="export-sheet-option" :class="{ 'is-disabled': !selectedModelId }">
            <input v-model="exportMode" type="radio" value="overwrite" :disabled="!selectedModelId" />
            {{ t('supersplat.overwriteExisting') }}
          </label>
          <label class="export-sheet-option">
            <input v-model="exportMode" type="radio" value="saveAs" />
            {{ t('supersplat.saveAsNewModel') }}
          </label>
        </div>
        <label v-if="exportMode === 'saveAs'" class="export-sheet-name">
          <span>{{ t('supersplat.exportName') }}</span>
          <input v-model="exportFileName" class="cloud-input" type="text" :placeholder="t('supersplat.exportNamePlaceholder')" />
        </label>
      </div>
      <template #footer>
        <AppButton @click="exportVisible = false">{{ t('common.cancel') }}</AppButton>
        <AppButton variant="primary" :disabled="saving" @click="saveToCloud">{{ t('supersplat.confirmExport') }}</AppButton>
      </template>
    </AppSheet>

    <WaitOverlay
      :visible="waitView.visible"
      :phase="waitView.phase"
      :title="waitView.title"
      :percent="waitView.percent"
      :speed="waitView.speed"
      :complete="waitView.complete"
      :await-confirm="waitView.awaitConfirm"
      @dismiss="dismissWait"
    />
  </div>
</template>
