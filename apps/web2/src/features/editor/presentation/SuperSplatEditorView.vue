<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { PhCloud, PhCloudArrowUp, PhFolderOpen, PhPlus, PhClockCounterClockwise } from '@phosphor-icons/vue'
import AppButton from '@/presentation/components/AppButton.vue'
import AppSheet from '@/presentation/components/AppSheet.vue'
import UploadProgressBar from '@/presentation/components/UploadProgressBar.vue'
import { createTransferRateTracker, formatTransferSpeed } from '@/shared/transfer-rate'
import { formatDomainError } from '@/presentation/errors'
import { DomainError } from '@/shared/domain-error'
import { useProjectWorkspace } from '@/features/project/presentation/composables/useProjectWorkspace'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import { useEditorSession } from '@/features/editor/presentation/composables/useEditorSession'
import { CLOUD_SAVE_DONE, CLOUD_SAVE_ERROR, CLOUD_SAVE_REQUEST, isTrustedIframeMessage } from '@/features/editor/infrastructure/supersplat-protocol'
import type { ModelAsset, ModelVersion } from '@/features/model-asset/domain/entities/model-asset.entity'
import { CONTAINER_KEY } from '@/shared/di'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const workspace = useProjectWorkspace()
const modelsApi = useModelAssets()
const editor = useEditorSession()
const container = inject(CONTAINER_KEY)!

const iframeRef = ref<HTMLIFrameElement | null>(null)
const localInputRef = ref<HTMLInputElement | null>(null)
const models = ref<ModelAsset[]>([])
const selectedModelId = ref<string | null>(null)
const localFileName = ref<string | null>(null)
const pickerVisible = ref(false)
const versionsVisible = ref(false)
const versions = ref<ModelVersion[]>([])
const loadingEditor = ref(false)
const saving = ref(false)
const restoring = ref(false)
const statusMessage = ref('')
const downloadById = ref<Record<string, { percent: number; speed: string }>>({})
const uploadProgress = ref<{ percent: number; speed: string } | null>(null)
const errorMessage = ref('')
const activeProjectId = ref<string | null>(workspace.activeProjectId())
let localFile: File | null = null

const editorLang = computed(() => (locale.value === 'en-US' ? 'en' : 'zh-CN'))
const selectedCloudModel = computed(() => models.value.find((model) => model.id === selectedModelId.value) ?? null)
const canSaveToCloud = computed(() => Boolean(activeProjectId.value) && !loadingEditor.value && !saving.value)
const canManageVersions = computed(() => Boolean(selectedModelId.value) && !saving.value && !restoring.value)
const sessionLabel = computed(() => {
  if (selectedCloudModel.value) {
    return `${selectedCloudModel.value.fileName} · v${selectedCloudModel.value.version}`
  }
  if (localFileName.value) {
    return localFileName.value
  }
  return t('supersplat.blankSession')
})

async function navigateEditor(src: string) {
  const iframe = iframeRef.value
  if (!iframe) {
    return
  }
  const loaded = new Promise<void>((resolve) => {
    iframe.addEventListener('load', () => resolve(), { once: true })
  })
  iframe.src = `${src}${src.includes('?') ? '&' : '?'}s=${Date.now()}`
  await loaded
}

function editorFrame() {
  const iframe = iframeRef.value
  if (!iframe) {
    return null
  }
  return { contentWindow: iframe.contentWindow, src: iframe.src }
}

async function loadBlankEditor() {
  loadingEditor.value = true
  errorMessage.value = ''
  await nextTick()
  const [srcError, src] = editor.src(editor.blank(editorLang.value))
  if (srcError || !src) {
    errorMessage.value = formatDomainError(t, srcError)
    loadingEditor.value = false
    return
  }
  await navigateEditor(src)
  loadingEditor.value = false
}

async function loadCloudEditor(model: ModelAsset) {
  loadingEditor.value = true
  errorMessage.value = ''
  localFile = null
  localFileName.value = null
  const tracker = createTransferRateTracker()
  downloadById.value = {
    ...downloadById.value,
    [model.id]: { percent: 0, speed: '' },
  }
  try {
    const [downloadError, buffer] = await modelsApi.downloadBytes(
    model.id,
    (loadedBytes, total) => {
      const percent = total > 0 ? Math.min(99, Math.round((loadedBytes / total) * 100)) : 0
      const rate = tracker.push(loadedBytes, Date.now())
      downloadById.value = {
        ...downloadById.value,
        [model.id]: {
          percent,
          speed: rate != null ? formatTransferSpeed(rate) : downloadById.value[model.id]?.speed ?? '',
        },
      }
    },
    { cacheBust: `${model.version}-${model.updatedAt || Date.now()}` },
  )
    if (downloadError || !buffer) {
      errorMessage.value = formatDomainError(t, downloadError)
      return
    }
    const file = new File([buffer], model.fileName, { type: 'application/octet-stream' })
    const [prepareError] = editor.prepareLocal(file)
    if (prepareError) {
      errorMessage.value = formatDomainError(t, prepareError)
      return
    }
    const [srcError, src] = editor.src(editor.blank(editorLang.value))
    if (srcError || !src) {
      errorMessage.value = formatDomainError(t, srcError)
      return
    }
    await navigateEditor(src)
    const frame = editorFrame()
    if (!frame?.contentWindow) {
      errorMessage.value = formatDomainError(t, new DomainError('EDITOR_NOT_READY'))
      return
    }
    statusMessage.value = t('supersplat.loadingEditor')
    const [readyError] = await editor.waitReady(frame)
    if (readyError) {
      errorMessage.value = formatDomainError(t, readyError)
      statusMessage.value = ''
      return
    }
    const [importError] = await editor.importLocal(frame, file)
    if (importError) {
      errorMessage.value = formatDomainError(t, importError)
      statusMessage.value = ''
      return
    }
    statusMessage.value = ''
    pickerVisible.value = false
    await router.replace({ path: route.path, query: { ...route.query, modelId: model.id } })
  } finally {
    const next = { ...downloadById.value }
    delete next[model.id]
    downloadById.value = next
    loadingEditor.value = false
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
  const queryModelId = typeof route.query.modelId === 'string' ? route.query.modelId : null
  if (queryModelId && models.value.some((model) => model.id === queryModelId)) {
    selectedModelId.value = queryModelId
  } else if (selectedModelId.value && !models.value.some((model) => model.id === selectedModelId.value)) {
    selectedModelId.value = null
  }
}

async function openBlankSession() {
  selectedModelId.value = null
  localFile = null
  localFileName.value = null
  statusMessage.value = ''
  await router.replace({ path: route.path, query: { ...route.query, modelId: undefined } })
  await loadBlankEditor()
}

async function selectCloudModel(model: ModelAsset) {
  if (downloadById.value[model.id]) {
    return
  }
  selectedModelId.value = model.id
  statusMessage.value = ''
  await loadCloudEditor(model)
}

async function deletePickedModel(model: ModelAsset, event: Event) {
  event.stopPropagation()
  event.preventDefault()
  if (!window.confirm(t('supersplat.deleteConfirm'))) {
    return
  }
  const [error] = await modelsApi.remove(model.id)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  models.value = models.value.filter((item) => item.id !== model.id)
  if (selectedModelId.value === model.id) {
    await openBlankSession()
  }
}

function triggerLocalOpen() {
  localInputRef.value?.click()
}

async function loadLocalEditor(file: File) {
  loadingEditor.value = true
  errorMessage.value = ''
  const [srcError, src] = editor.src(editor.blank(editorLang.value))
  if (srcError || !src) {
    errorMessage.value = formatDomainError(t, srcError)
    loadingEditor.value = false
    return
  }
  await navigateEditor(src)
  const frame = editorFrame()
  if (!frame?.contentWindow) {
    errorMessage.value = formatDomainError(t, new DomainError('EDITOR_NOT_READY'))
    loadingEditor.value = false
    return
  }
  const [readyError] = await editor.waitReady(frame)
  if (readyError) {
    errorMessage.value = formatDomainError(t, readyError)
    loadingEditor.value = false
    return
  }
  const [importError] = await editor.importLocal(frame, file)
  if (importError) {
    errorMessage.value = formatDomainError(t, importError)
  }
  loadingEditor.value = false
}

async function handleLocalFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }

  const [prepareError, prepared] = editor.prepareLocal(file)
  if (prepareError || !prepared) {
    errorMessage.value = formatDomainError(t, prepareError)
    return
  }

  selectedModelId.value = null
  localFile = file
  localFileName.value = prepared.fileName
  statusMessage.value = ''
  await loadLocalEditor(file)
  await router.replace({ path: route.path, query: { ...route.query, modelId: undefined } })
}

async function saveToCloud() {
  const frame = editorFrame()
  if (!frame?.contentWindow) {
    errorMessage.value = formatDomainError(t, new DomainError('EDITOR_NOT_READY'))
    return
  }
  if (!activeProjectId.value) {
    errorMessage.value = formatDomainError(t, new DomainError('MODEL_PROJECT_REQUIRED'))
    return
  }

  saving.value = true
  errorMessage.value = ''
  statusMessage.value = t('supersplat.savingToCloud')
  uploadProgress.value = null

  if (selectedModelId.value) {
    const [error, saved] = await editor.saveExport({
      modelId: selectedModelId.value,
      frame,
      fileName: selectedCloudModel.value?.fileName ?? localFileName.value ?? undefined,
    })
    saving.value = false
    if (error || !saved) {
      errorMessage.value = formatDomainError(t, error ?? new DomainError('EDITOR_EXPORT_FAILED'))
      statusMessage.value = ''
      return
    }
    statusMessage.value = t('supersplat.savedToCloud', { version: saved.version })
    await refreshModels()
    return
  }

  const tracker = createTransferRateTracker()
  const [error, created] = await editor.saveAsNew({
    projectId: activeProjectId.value,
    frame,
    fileName: localFileName.value ?? undefined,
    onProgress: (progress) => {
      const percent = progress.total > 0 ? Math.min(99, Math.round((progress.loaded / progress.total) * 100)) : 0
      const rate = tracker.push(progress.loaded, Date.now())
      uploadProgress.value = {
        percent,
        speed: rate != null ? formatTransferSpeed(rate) : uploadProgress.value?.speed ?? '',
      }
    },
  })
  saving.value = false
  uploadProgress.value = null
  if (error || !created) {
    errorMessage.value = formatDomainError(t, error ?? new DomainError('EDITOR_EXPORT_FAILED'))
    statusMessage.value = ''
    return
  }
  selectedModelId.value = created.id
  localFile = null
  localFileName.value = null
  statusMessage.value = t('supersplat.savedAsNewModel', { name: created.fileName })
  await refreshModels()
  await router.replace({ path: route.path, query: { ...route.query, modelId: created.id } })
}

async function openVersions() {
  if (!selectedModelId.value) {
    return
  }
  errorMessage.value = ''
  const [error, data] = await modelsApi.listVersions(selectedModelId.value)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  versions.value = data ?? []
  versionsVisible.value = true
}

async function importBufferIntoEditor(file: File) {
  const [prepareError] = editor.prepareLocal(file)
  if (prepareError) {
    return prepareError
  }
  const [srcError, src] = editor.src(editor.blank(editorLang.value))
  if (srcError || !src) {
    return srcError ?? new DomainError('EDITOR_NOT_READY')
  }
  await navigateEditor(src)
  const frame = editorFrame()
  if (!frame?.contentWindow) {
    return new DomainError('EDITOR_NOT_READY')
  }
  const [readyError] = await editor.waitReady(frame)
  if (readyError) {
    return readyError
  }
  const [importError] = await editor.importLocal(frame, file)
  return importError
}

async function previewVersion(version: ModelVersion) {
  if (!selectedModelId.value || version.current) {
    return
  }
  restoring.value = true
  errorMessage.value = ''
  statusMessage.value = t('supersplat.previewingVersion')
  const [downloadError, buffer] = await modelsApi.downloadVersionBytes(selectedModelId.value, version.id)
  if (downloadError || !buffer) {
    restoring.value = false
    errorMessage.value = formatDomainError(t, downloadError)
    statusMessage.value = ''
    return
  }
  const file = new File([buffer], version.fileName || 'history.ply', { type: 'application/octet-stream' })
  const importError = await importBufferIntoEditor(file)
  restoring.value = false
  if (importError) {
    errorMessage.value = formatDomainError(t, importError)
    statusMessage.value = ''
    return
  }
  versionsVisible.value = false
  statusMessage.value = t('supersplat.previewedVersion', { name: version.fileName || version.createdAt })
}

async function restoreVersion(version: ModelVersion) {
  if (!selectedModelId.value || version.current) {
    return
  }
  if (!window.confirm(t('supersplat.restoreConfirm'))) {
    return
  }
  restoring.value = true
  errorMessage.value = ''
  const [error, restored] = await modelsApi.restoreVersion(selectedModelId.value, version.id)
  if (error || !restored) {
    restoring.value = false
    errorMessage.value = formatDomainError(t, error ?? new DomainError('UNKNOWN'))
    return
  }
  versionsVisible.value = false
  statusMessage.value = t('supersplat.restoredVersion', { version: restored.version })
  await refreshModels()
  const model =
    models.value.find((item) => item.id === restored.id) ??
    ({
      ...restored,
    } satisfies ModelAsset)
  await loadCloudEditor(model)
  restoring.value = false
}

async function handleCloudSaveRequest(event: MessageEvent) {
  const data = event.data as { type?: string; modelId?: string; fileName?: string; buffer?: ArrayBuffer }
  if (data?.type !== CLOUD_SAVE_REQUEST || !data.modelId || !data.fileName || !data.buffer) {
    return
  }
  const iframe = iframeRef.value
  if (!iframe || !isTrustedIframeMessage(event, iframe, window.location.origin)) {
    return
  }
  if (!selectedModelId.value || data.modelId !== selectedModelId.value) {
    return
  }
  const source = event.source as Window | null
  if (!source) {
    return
  }
  const blob = new Blob([data.buffer], { type: 'application/octet-stream' })
  const [error] = await container.models.uploadExport(data.modelId, blob, data.fileName)
  if (error) {
    source.postMessage({ type: CLOUD_SAVE_ERROR, message: formatDomainError(t, error) }, event.origin)
    return
  }
  source.postMessage({ type: CLOUD_SAVE_DONE }, event.origin)
  statusMessage.value = t('supersplat.savedToCloudShort')
  await refreshModels()
}

watch(activeProjectId, async () => {
  await refreshModels()
})

watch(locale, async () => {
  if (!iframeRef.value) {
    return
  }
  if (selectedCloudModel.value) {
    await loadCloudEditor(selectedCloudModel.value)
    return
  }
  if (localFile) {
    await loadLocalEditor(localFile)
    return
  }
  await loadBlankEditor()
})

onMounted(async () => {
  window.addEventListener('message', handleCloudSaveRequest)
  await nextTick()
  await loadBlankEditor()
  await ensureProjects()
  await refreshModels()
  if (selectedModelId.value) {
    const model = models.value.find((item) => item.id === selectedModelId.value)
    if (model) {
      await loadCloudEditor(model)
    }
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleCloudSaveRequest)
  localFile = null
})

onBeforeRouteLeave(async (_to, _from, next) => {
  const iframe = iframeRef.value
  if (!iframe?.contentWindow) {
    next()
    return
  }
  const [error, dirty] = await editor.isDirty(iframe)
  if (!error && dirty && !window.confirm(t('supersplat.leaveConfirm'))) {
    next(false)
    return
  }
  next()
})
</script>

<template>
  <div class="supersplat-page">
    <header class="supersplat-chrome">
      <div class="supersplat-chrome__copy">
        <p class="supersplat-chrome__title">{{ sessionLabel }}</p>
        <p class="supersplat-chrome__hint">{{ t('supersplat.localHint') }}</p>
        <p v-if="statusMessage" class="supersplat-chrome__status">{{ statusMessage }}</p>
        <UploadProgressBar
          v-if="uploadProgress"
          :percent="uploadProgress.percent"
          :speed="uploadProgress.speed"
        />
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
        <AppButton compact variant="primary" :disabled="!canSaveToCloud" @click="saveToCloud">
          <PhCloudArrowUp :size="16" weight="regular" />
          {{ selectedModelId ? t('supersplat.saveToCloud') : t('supersplat.saveAsNew') }}
        </AppButton>
      </div>
    </header>

    <p v-if="errorMessage" class="supersplat-chrome__error">{{ errorMessage }}</p>

    <section class="supersplat-stage">
      <iframe ref="iframeRef" class="supersplat-iframe" :title="t('supersplat.title')" allow="fullscreen" />
      <p v-if="loadingEditor" class="supersplat-loading">{{ t('supersplat.loadingEditor') }}</p>
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
            <span class="model-choice-meta">{{ model.format }} · v{{ model.version }}</span>
            <span v-if="downloadById[model.id]" class="model-choice-badge">{{ t('supersplat.downloading') }}</span>
            <UploadProgressBar
              v-if="downloadById[model.id]"
              :percent="downloadById[model.id].percent"
              :speed="downloadById[model.id].speed"
            />
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
            <span class="model-choice-name">{{ version.fileName }} v{{ version.version }}</span>
            <span class="model-choice-meta">
              {{ version.current ? t('supersplat.currentVersion') : version.createdAt }}
              · {{ Math.max(1, Math.round(version.sizeBytes / 1024)) }} KB
            </span>
          </div>
          <AppButton
            v-if="!version.current"
            compact
            :disabled="restoring"
            @click="previewVersion(version)"
          >
            {{ t('supersplat.previewVersion') }}
          </AppButton>
          <AppButton
            v-if="!version.current"
            compact
            variant="primary"
            :disabled="restoring"
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
  </div>
</template>
