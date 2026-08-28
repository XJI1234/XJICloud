<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ApiError } from '@/shared/infrastructure/http/client'
import { uploadModel } from '@/domains/model/api/models'
import SparkViewport from '@/domains/viewer/components/SparkViewport.vue'
import { CAMERA_STATUS_KEY, type CameraStatus } from '@/domains/viewer/constants/cameraStatus'
import {
  createCloudViewerStorage,
  rememberModelMeta,
  VIEWER_STORAGE_KEY,
  type ModelSummary,
} from '@/domains/viewer/infrastructure/viewerStorage'
import { useProjectStore } from '@/domains/project/stores/project'
import clockwiseRotateIcon from '@/assets/clockwise-rotate.svg'
import counterclockwiseRotateIcon from '@/assets/counterclockwise-rotate.svg'

type PainterMode = 'view' | 'paint' | 'erase' | 'undo'
type SidePanelMenu = 'info' | 'view' | 'edit'

interface ProjectInfoField {
  key: string
  label: string
  value: string
}

interface ProjectInfoConfig {
  projectName: string
  fields: ProjectInfoField[]
}

interface LoadedModelInfo {
  fileName: string
  splatCount: number
}

interface PainterExportResult {
  fileName: string
  splatCount: number
  clippedCount: number
  sphericalHarmonicsDegree: number
}

interface DirectoryPickerOptions {
  mode?: 'read' | 'readwrite'
}

type FileSystemWindow = Window & typeof globalThis & {
  showDirectoryPicker?: (options?: DirectoryPickerOptions) => Promise<FileSystemDirectoryHandle>
}

interface ModelCandidate {
  id?: string
  name: string
  format?: string
  version?: number
  updatedAt?: string
  sizeBytes?: number
  handle: FileSystemFileHandle | null
  path: string | null
}

const PROJECT_INFO_BUILTIN_KEYS = ['coordinates', 'buildingName', 'floorCount', 'height'] as const

const { t, locale } = useI18n()
const projectStore = useProjectStore()
const viewerStorage = createCloudViewerStorage()
provide(VIEWER_STORAGE_KEY, viewerStorage)

const cameraStatus = ref<CameraStatus>({
  longitude: '--',
  latitude: '--',
  elevation: '--',
  viewHeight: '--',
})
provide(CAMERA_STATUS_KEY, cameraStatus)

const currentModelId = ref<string | null>(null)
const currentFile = ref<File | null>(null)
const currentFileHandle = ref<FileSystemFileHandle | null>(null)
const currentDirectoryHandle = ref<FileSystemDirectoryHandle | null>(null)
const uploadInputRef = ref<HTMLInputElement | null>(null)
const panelCollapsed = ref(false)
const activeSidePanel = ref<SidePanelMenu>('info')
const painterMode = ref<PainterMode>('view')
const painterColor = ref('#BC1010')
const brushRadiusFactor = ref(0.005)
const brushDepthFactor = ref(0.2)
const exportToken = ref(0)
const restoreModelToken = ref(0)
const undoEditToken = ref(0)
const redoEditToken = ref(0)
const resetViewToken = ref(0)
const saveDefaultViewToken = ref(0)
const saveMarkersToken = ref(0)
const saveProjectInfoToken = ref(0)
const rotateClockwiseToken = ref(0)
const rotateCounterclockwiseToken = ref(0)
const annotationPlacementActive = ref(false)
const annotationEdgeColor = ref('#BC1010')
const cubePlacementActive = ref(false)
const cubeEdgeColor = ref('#46C7FF')
const modelSelectionVisible = ref(false)
const modelCandidates = ref<ModelCandidate[]>([])
const pendingDirectoryHandle = ref<FileSystemDirectoryHandle | null>(null)
const pendingDirectoryPath = ref<string | null>(null)
const exportPending = ref(false)
const modelInfo = ref<LoadedModelInfo | null>(null)
const actionError = ref('')
const statusState = ref<
  | { type: 'i18n'; key: string; params?: Record<string, unknown> }
  | { type: 'raw'; message: string }
>({ type: 'i18n', key: 'viewer.status.pickFile' })
const canUndoEdit = ref(false)
const canRedoEdit = ref(false)
const projectInfo = ref<ProjectInfoConfig>(createEmptyProjectInfo())
const projectInfoDraft = ref<ProjectInfoConfig>(createEmptyProjectInfo())
const projectInfoDialogVisible = ref(false)

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

  if (state.key === 'viewer.status.exported') {
    const params = state.params ?? {}
    const count = Number(params.count ?? 0)
    const clippedCount = Number(params.clippedCount ?? 0)
    const shDegree = Number(params.shDegree ?? 0)
    const sh = shDegree > 0 ? `，SH ${shDegree}` : ''
    const clipped = clippedCount > 0
      ? t('viewer.status.exportClipped', { count: clippedCount.toLocaleString(locale.value) })
      : ''
    return String(t(state.key, {
      fileName: params.fileName,
      count: count.toLocaleString(locale.value),
      sh,
      clipped,
    }))
  }

  return String(t(state.key, state.params as Record<string, unknown>))
})

const canEdit = computed(() => Boolean(modelInfo.value))
const canExport = computed(() => Boolean(modelInfo.value && !exportPending.value))
const activeProjectName = computed(() => projectStore.projects.find((project) => project.id === projectStore.activeProjectId)?.name ?? t('viewer.noProjectSelected'))
const brushRadiusLabel = computed(() => t('viewer.brushRadiusValue', { percent: (Number(brushRadiusFactor.value) * 100).toFixed(1) }))
const brushDepthLabel = computed(() => t('viewer.brushDepthValue', { value: Number(brushDepthFactor.value).toFixed(1) }))
const painterModeLabel = computed(() => t(`viewer.painterMode.${painterMode.value}`))

function getBuiltinProjectInfoFields() {
  return PROJECT_INFO_BUILTIN_KEYS.map((key) => ({
    key,
    label: t(`viewer.fields.${key}`),
  }))
}

function createEmptyProjectInfo(): ProjectInfoConfig {
  return {
    projectName: '',
    fields: getBuiltinProjectInfoFields().map(({ key, label }) => ({
      key,
      label,
      value: '',
    })),
  }
}

function normalizeProjectInfo(info: ProjectInfoConfig | null | undefined): ProjectInfoConfig {
  if (!info || typeof info !== 'object') {
    return createEmptyProjectInfo()
  }

  const rawFields = Array.isArray(info.fields) ? info.fields : []
  const builtinFields = new Map<string, ProjectInfoField>()
  const customFields: ProjectInfoField[] = []
  const seenCustomKeys = new Set<string>()
  const builtinDefinitions = getBuiltinProjectInfoFields()

  for (const rawField of rawFields) {
    if (!rawField || typeof rawField !== 'object') {
      continue
    }

    const key = typeof rawField.key === 'string' ? rawField.key.trim() : ''
    if (!key) {
      continue
    }

    const builtinDefinition = builtinDefinitions.find((field) => field.key === key)
    const label = typeof rawField.label === 'string' && rawField.label.trim()
      ? rawField.label.trim()
      : builtinDefinition?.label ?? t('viewer.customField')
    const value = typeof rawField.value === 'string' ? rawField.value : ''

    if (builtinDefinition) {
      if (!builtinFields.has(key)) {
        builtinFields.set(key, { key, label, value })
      }
      continue
    }

    if (seenCustomKeys.has(key)) {
      continue
    }

    seenCustomKeys.add(key)
    customFields.push({ key, label, value })
  }

  return {
    projectName: typeof info.projectName === 'string' ? info.projectName.trim() : '',
    fields: [
      ...builtinDefinitions.map(({ key, label }) => {
        const existingField = builtinFields.get(key)
        return {
          key,
          label: existingField?.label?.trim() || label,
          value: existingField?.value ?? '',
        }
      }),
      ...customFields,
    ],
  }
}

function createCustomProjectInfoField(): ProjectInfoField {
  return {
    key: `custom-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    label: t('viewer.customField'),
    value: '',
  }
}

function isBuiltinProjectInfoField(key: string) {
  return PROJECT_INFO_BUILTIN_KEYS.some((fieldKey) => fieldKey === key)
}

function resetProjectInfoState() {
  projectInfo.value = createEmptyProjectInfo()
  projectInfoDraft.value = createEmptyProjectInfo()
  projectInfoDialogVisible.value = false
}

function resetModelInteractionState() {
  annotationPlacementActive.value = false
  cubePlacementActive.value = false
  actionError.value = ''
  exportPending.value = false
  canUndoEdit.value = false
  canRedoEdit.value = false
  painterMode.value = 'view'
  activeSidePanel.value = 'info'
}

function setCurrentModelSource(file: File, modelId: string | null = null) {
  currentFile.value = file
  currentFileHandle.value = null
  currentDirectoryHandle.value = null
  currentModelId.value = modelId
  modelInfo.value = null
  resetProjectInfoState()
  resetModelInteractionState()
  setStatus('viewer.status.loadingFile', { name: file.name })
  closeModelSelectionDialog()
}

async function loadCloudModel(candidate: ModelCandidate) {
  if (!candidate.id || !projectStore.activeProjectId) {
    return
  }

  rememberModelMeta(candidate.id, {
    fileName: candidate.name,
    projectId: projectStore.activeProjectId,
  })

  try {
    setStatus('viewer.status.downloading', { name: candidate.name })
    const loaded = await viewerStorage.loadModelBytes(candidate.id, (loaded, total) => {
      if (total > 0) {
        setStatus('viewer.status.downloadingProgress', {
          name: candidate.name,
          percent: Math.round((loaded / total) * 100),
        })
      }
    })
    setCurrentModelSource(loaded.file, loaded.modelId)
  } catch (error) {
    if (error instanceof ApiError) {
      actionError.value = error.message
      setRawStatus(error.message)
    } else {
      actionError.value = 'loadModelFailed'
      setStatus('viewer.status.loadModelFailed')
    }
  }
}

function closeModelSelectionDialog() {
  modelSelectionVisible.value = false
  modelCandidates.value = []
  pendingDirectoryHandle.value = null
  pendingDirectoryPath.value = null
}

async function selectModelCandidate(candidate: ModelCandidate) {
  await loadCloudModel(candidate)
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

function toModelCandidates(models: ModelSummary[]) {
  return [...models]
    .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
    .map((model) => ({
      id: model.id,
      name: model.fileName,
      format: model.format,
      version: model.version,
      updatedAt: model.updatedAt,
      sizeBytes: model.sizeBytes,
      handle: null,
      path: null,
    }))
}

function presentModelSelection(models: ModelSummary[]) {
  modelCandidates.value = toModelCandidates(models)
  modelSelectionVisible.value = true
  if (models.length > 1) {
    setStatus('viewer.status.selectModelMulti', { count: models.length })
  } else {
    setStatus('viewer.status.selectModelPrompt')
  }
}

async function openFilePicker() {
  actionError.value = ''

  if (!projectStore.activeProjectId) {
    actionError.value = 'selectProjectFirst'
    setStatus('viewer.status.selectProjectFirst')
    return
  }

  try {
    const models = await viewerStorage.listModels(projectStore.activeProjectId)
    if (models.length === 0) {
      actionError.value = 'noModels'
      setStatus('viewer.status.noModels')
      return
    }

    presentModelSelection(models)
  } catch (error) {
    if (error instanceof ApiError) {
      actionError.value = error.message
      setRawStatus(error.message)
    } else {
      actionError.value = 'loadModelListFailed'
      setStatus('viewer.status.loadModelListFailed')
    }
  }
}

function triggerUpload() {
  if (!projectStore.activeProjectId) {
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

  if (!file || !projectStore.activeProjectId) {
    return
  }

  try {
    setStatus('viewer.status.uploading', { name: file.name })
    const model = await uploadModel(projectStore.activeProjectId, file)
    rememberModelMeta(model.id, {
      fileName: model.fileName,
      projectId: projectStore.activeProjectId,
    })
    setCurrentModelSource(file, model.id)
    setStatus('viewer.status.uploadDoneLoading', { name: file.name })
  } catch (error) {
    if (error instanceof ApiError) {
      actionError.value = error.message
      setRawStatus(error.message)
    } else {
      actionError.value = 'uploadModelFailed'
      setStatus('viewer.status.uploadModelFailed')
    }
  }
}

function setPainterMode(nextMode: PainterMode) {
  if (nextMode !== 'view' && !modelInfo.value) {
    return
  }

  if (nextMode !== 'view') {
    annotationPlacementActive.value = false
    cubePlacementActive.value = false
  }

  painterMode.value = nextMode
  actionError.value = ''
  if (nextMode !== 'view') {
    activeSidePanel.value = 'edit'
  }

  switch (nextMode) {
    case 'paint':
      setStatus('viewer.status.paintMode')
      break
    case 'erase':
      setStatus('viewer.status.eraseMode')
      break
    case 'undo':
      setStatus('viewer.status.eraserMode')
      break
    default:
      setStatus('viewer.status.viewMode')
      break
  }
}

function requestRotateView(direction: 'clockwise' | 'counterclockwise') {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  activeSidePanel.value = 'view'

  if (direction === 'clockwise') {
    setStatus('viewer.status.rotatingCw')
    rotateClockwiseToken.value += 1
    return
  }

  setStatus('viewer.status.rotatingCcw')
  rotateCounterclockwiseToken.value += 1
}

function toggleAnnotationPlacement() {
  if (!modelInfo.value) {
    return
  }

  painterMode.value = 'view'
  cubePlacementActive.value = false
  annotationPlacementActive.value = !annotationPlacementActive.value
  actionError.value = ''
  activeSidePanel.value = 'view'
  setStatus(annotationPlacementActive.value ? 'viewer.status.bubbleOn' : 'viewer.status.bubbleOff')
}

function toggleCubePlacement() {
  if (!modelInfo.value) {
    return
  }

  painterMode.value = 'view'
  annotationPlacementActive.value = false
  cubePlacementActive.value = !cubePlacementActive.value
  actionError.value = ''
  activeSidePanel.value = 'view'
  setStatus(cubePlacementActive.value ? 'viewer.status.cubeOn' : 'viewer.status.cubeOff')
}

function requestExport() {
  if (!canExport.value) {
    return
  }

  exportPending.value = true
  activeSidePanel.value = 'edit'
  actionError.value = ''
  setStatus('viewer.status.exporting')
  exportToken.value += 1
}

function requestRestoreModel() {
  if (!modelInfo.value) {
    return
  }

  painterMode.value = 'view'
  annotationPlacementActive.value = false
  cubePlacementActive.value = false
  exportPending.value = false
  actionError.value = ''
  setStatus('viewer.status.restoring')
  restoreModelToken.value += 1
}

function requestUndoLastEdit() {
  if (!modelInfo.value || !canUndoEdit.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.undoing')
  undoEditToken.value += 1
}

function requestRedoLastEdit() {
  if (!modelInfo.value || !canRedoEdit.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.redoing')
  redoEditToken.value += 1
}

function requestResetView() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.resettingCamera')
  resetViewToken.value += 1
}

function requestSaveDefaultView() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.savingDefaultView')
  saveDefaultViewToken.value += 1
}

function requestSaveMarkers() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  setStatus('viewer.status.savingMarkers')
  saveMarkersToken.value += 1
}

function openProjectInfoDialog() {
  if (!modelInfo.value) {
    return
  }

  projectInfoDraft.value = normalizeProjectInfo(projectInfo.value)
  actionError.value = ''
  projectInfoDialogVisible.value = true
}

function closeProjectInfoDialog() {
  projectInfoDialogVisible.value = false
  projectInfoDraft.value = normalizeProjectInfo(projectInfo.value)
}

function addCustomProjectInfoField() {
  projectInfoDraft.value.fields.push(createCustomProjectInfoField())
}

function removeCustomProjectInfoField(fieldKey: string) {
  if (isBuiltinProjectInfoField(fieldKey)) {
    return
  }

  projectInfoDraft.value.fields = projectInfoDraft.value.fields.filter((field) => field.key !== fieldKey)
}

function saveProjectInfoChanges() {
  if (!modelInfo.value) {
    return
  }

  const normalizedProjectInfo = normalizeProjectInfo(projectInfoDraft.value)
  projectInfo.value = normalizedProjectInfo
  projectInfoDraft.value = normalizeProjectInfo(normalizedProjectInfo)
  projectInfoDialogVisible.value = false
  activeSidePanel.value = 'info'
  actionError.value = ''
  setStatus('viewer.status.savingProjectInfo')
  saveProjectInfoToken.value += 1
}

function togglePanel() {
  panelCollapsed.value = !panelCollapsed.value
}

function setActiveSidePanel(nextPanel: SidePanelMenu) {
  if (nextPanel === 'edit' && !modelInfo.value) {
    return
  }

  activeSidePanel.value = nextPanel
}

function isEditableTarget(target: EventTarget | null) {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || (target instanceof HTMLElement && target.isContentEditable)
}

function onWindowKeydown(event: KeyboardEvent) {
  if (projectInfoDialogVisible.value || modelSelectionVisible.value || isEditableTarget(event.target)) {
    return
  }

  if ((event.ctrlKey || event.metaKey) && !event.altKey) {
    if (!event.shiftKey && event.code === 'KeyZ') {
      if (!modelInfo.value || !canUndoEdit.value) {
        return
      }

      event.preventDefault()
      requestUndoLastEdit()
      return
    }

    if (event.code === 'KeyY' || (event.shiftKey && event.code === 'KeyZ')) {
      if (!modelInfo.value || !canRedoEdit.value) {
        return
      }

      event.preventDefault()
      requestRedoLastEdit()
      return
    }
  }

  switch (event.code) {
    case 'KeyO':
      event.preventDefault()
      openFilePicker()
      break
    case 'Digit1':
    case 'Numpad1':
      event.preventDefault()
      setPainterMode('paint')
      break
    case 'Digit2':
    case 'Numpad2':
      event.preventDefault()
      setPainterMode('erase')
      break
    case 'Digit3':
    case 'Numpad3':
      event.preventDefault()
      setPainterMode('undo')
      break
    case 'Escape':
      event.preventDefault()
      setPainterMode('view')
      break
    default:
      break
  }
}

function handleLoaded(info: LoadedModelInfo) {
  modelInfo.value = info
  exportPending.value = false
  canUndoEdit.value = false
  canRedoEdit.value = false
  annotationPlacementActive.value = false
  cubePlacementActive.value = false
  actionError.value = ''
  activeSidePanel.value = 'info'
  setStatus('viewer.status.modelLoaded', { name: info.fileName })
}

function handleFailed(message: string) {
  resetProjectInfoState()
  canUndoEdit.value = false
  canRedoEdit.value = false
  actionError.value = message
  exportPending.value = false
  annotationPlacementActive.value = false
  cubePlacementActive.value = false
  setRawStatus(message)
}

function handleStatus(message: string) {
  if (!actionError.value) {
    setRawStatus(message)
  }
}

function handleExported(result: PainterExportResult) {
  exportPending.value = false
  actionError.value = ''
  setStatus('viewer.status.exported', {
    fileName: result.fileName,
    count: result.splatCount,
    shDegree: result.sphericalHarmonicsDegree,
    clippedCount: result.clippedCount,
  })
}

function handleExportFailed(message: string) {
  exportPending.value = false
  if (message === t('viewer.status.exportCancelled') || message === '已取消导出') {
    actionError.value = ''
    setStatus('viewer.status.exportCancelled')
    return
  }

  actionError.value = message
  setRawStatus(message)
}

function handleAnnotationPlacementChange(active: boolean) {
  annotationPlacementActive.value = active
  if (active) {
    cubePlacementActive.value = false
  }
}

function handleAnnotationSelectionColorChange(color: string | null) {
  if (!color) {
    return
  }

  annotationEdgeColor.value = color
}

function handleCubePlacementChange(active: boolean) {
  cubePlacementActive.value = active
  if (active) {
    annotationPlacementActive.value = false
  }
}

function handleCubeSelectionColorChange(color: string | null) {
  if (!color) {
    return
  }

  cubeEdgeColor.value = color
}

function handleProjectInfoLoaded(info: ProjectInfoConfig) {
  projectInfo.value = normalizeProjectInfo(info)
  if (!projectInfoDialogVisible.value) {
    projectInfoDraft.value = normalizeProjectInfo(info)
  }
}

function handleUndoAvailabilityChange(available: boolean) {
  canUndoEdit.value = available
}

function handleRedoAvailabilityChange(available: boolean) {
  canRedoEdit.value = available
}

function handleCameraChange(status: CameraStatus) {
  cameraStatus.value = status
}

onMounted(() => {
  window.addEventListener('keydown', onWindowKeydown)
  projectStore.fetchProjects().catch(() => {
    setStatus('viewer.status.projectsLoadFailed')
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
})
</script>

<template>
  <main class="app-shell layer-viewer-root">
    <section class="viewer-stage">
      <div class="viewport-frame">
        <SparkViewport
          :file="currentFile"
          :file-handle="currentFileHandle"
          :directory-handle="currentDirectoryHandle"
          :cloud-model-id="currentModelId"
          :painter-mode="painterMode"
          :painter-color="painterColor"
          :brush-radius-factor="brushRadiusFactor"
          :brush-depth-factor="brushDepthFactor"
          :export-token="exportToken"
          :restore-model-token="restoreModelToken"
          :undo-edit-token="undoEditToken"
          :redo-edit-token="redoEditToken"
          :reset-view-token="resetViewToken"
          :save-default-view-token="saveDefaultViewToken"
          :save-markers-token="saveMarkersToken"
          :rotate-clockwise-token="rotateClockwiseToken"
          :rotate-counterclockwise-token="rotateCounterclockwiseToken"
          :annotation-placement-active="annotationPlacementActive"
          :annotation-edge-color="annotationEdgeColor"
          :cube-placement-active="cubePlacementActive"
          :cube-edge-color="cubeEdgeColor"
          :project-info="projectInfo"
          :save-project-info-token="saveProjectInfoToken"
          :scene-interaction-locked="projectInfoDialogVisible"
          @loaded="handleLoaded"
          @failed="handleFailed"
          @status="handleStatus"
          @exported="handleExported"
          @export-failed="handleExportFailed"
          @annotation-placement-change="handleAnnotationPlacementChange"
          @annotation-selection-color-change="handleAnnotationSelectionColorChange"
          @cube-placement-change="handleCubePlacementChange"
          @cube-selection-color-change="handleCubeSelectionColorChange"
          @project-info-loaded="handleProjectInfoLoaded"
          @undo-availability-change="handleUndoAvailabilityChange"
          @redo-availability-change="handleRedoAvailabilityChange"
          @camera-change="handleCameraChange"
        />
      </div>
    </section>

    <button v-if="panelCollapsed" class="panel-toggle collapsed-toggle floating-collapsed-toggle" type="button" @click="togglePanel">
      {{ t('viewer.menu') }}
    </button>

    <aside class="side-panel" :class="{ collapsed: panelCollapsed }">
      <div v-if="!panelCollapsed" class="side-content">
        <button class="panel-toggle expanded-toggle" type="button" @click="togglePanel">
          {{ t('viewer.collapseMenu') }}
        </button>

        <section class="model-card">
          <h1 class="model-title">{{ modelInfo?.fileName || t('viewer.waitingModel') }}</h1>
        </section>

        <nav class="nav-strip" aria-label="Viewer navigation">
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'info' }"
            type="button"
            @click="setActiveSidePanel('info')"
          >
            {{ t('viewer.info') }}
          </button>
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'view' }"
            type="button"
            @click="setActiveSidePanel('view')"
          >
            {{ t('viewer.view') }}
          </button>
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'edit' }"
            type="button"
            :disabled="!canEdit"
            @click="setActiveSidePanel('edit')"
          >
            {{ t('viewer.edit') }}
          </button>
        </nav>

        <section v-if="activeSidePanel === 'info'" class="section-card info-card">
          <p class="info-project-context">{{ t('viewer.currentProject', { name: activeProjectName }) }}</p>
          <button class="side-button primary" type="button" @click="openFilePicker">
            {{ t('viewer.selectModel') }}
          </button>
          <button class="side-button" type="button" @click="triggerUpload">
            {{ t('viewer.uploadModel') }}
          </button>

          <template v-if="modelInfo">
            <div class="info-summary">
              <h3 class="info-project-title">{{ projectInfo.projectName || t('viewer.unnamedProject') }}</h3>
            </div>

            <div class="info-list">
              <article v-for="field in projectInfo.fields" :key="field.key" class="info-item">
                <span class="info-item-label">{{ field.label }}</span>
                <strong class="info-item-value" :class="{ 'is-empty': !field.value }">
                  {{ field.value || t('viewer.notFilled') }}
                </strong>
              </article>
            </div>

            <button class="side-button primary info-edit-button" type="button" :disabled="!modelInfo" @click="openProjectInfoDialog">
              {{ t('viewer.editInfo') }}
            </button>
          </template>

          <template v-else>
            <div class="info-empty-state">
              <p class="info-empty-title">{{ t('viewer.loadModelPrompt') }}</p>
            </div>
          </template>
        </section>

        <section v-else-if="activeSidePanel === 'view'" class="section-card">
          <h2 class="section-title">{{ t('viewer.view') }}</h2>

          <div class="action-stack">
            <button class="side-button" type="button" :disabled="!modelInfo" @click="requestResetView">
              {{ t('viewer.resetView') }}
            </button>
            <button class="side-button" type="button" :disabled="!modelInfo" @click="requestSaveDefaultView">
              {{ t('viewer.setDefaultView') }}
            </button>
            <button class="side-button" :class="{ 'is-active': annotationPlacementActive }" type="button" :disabled="!modelInfo" @click="toggleAnnotationPlacement">
              {{ annotationPlacementActive ? t('viewer.cancelBubbleAnnotation') : t('viewer.addBubbleAnnotation') }}
            </button>
            <button class="side-button" :class="{ 'is-active': cubePlacementActive }" type="button" :disabled="!modelInfo" @click="toggleCubePlacement">
              {{ cubePlacementActive ? t('viewer.cancelCubeMarker') : t('viewer.addCubeMarker') }}
            </button>
            <div class="rotation-row">
              <button class="side-button rotation-button" type="button" :disabled="!modelInfo" @click="requestRotateView('counterclockwise')">
                <img class="rotation-icon" :src="counterclockwiseRotateIcon" alt="" aria-hidden="true" />
                <span>{{ t('viewer.counterclockwise') }}</span>
              </button>
              <button class="side-button rotation-button" type="button" :disabled="!modelInfo" @click="requestRotateView('clockwise')">
                <img class="rotation-icon" :src="clockwiseRotateIcon" alt="" aria-hidden="true" />
                <span>{{ t('viewer.clockwise') }}</span>
              </button>
            </div>
            <button class="side-button primary" type="button" :disabled="!modelInfo" @click="requestSaveMarkers">
              {{ t('viewer.saveMarkerChanges') }}
            </button>
          </div>

          <div class="field-block">
            <label class="field-label" for="annotation-edge-color">{{ t('viewer.bubbleColor') }}</label>
            <div class="color-row">
              <input id="annotation-edge-color" v-model="annotationEdgeColor" class="color-swatch" type="color" :disabled="!modelInfo" />
              <span class="field-value">{{ annotationEdgeColor.toUpperCase() }}</span>
            </div>
          </div>

          <div class="field-block">
            <label class="field-label" for="cube-edge-color">{{ t('viewer.cubeEdgeColor') }}</label>
            <div class="color-row">
              <input id="cube-edge-color" v-model="cubeEdgeColor" class="color-swatch" type="color" :disabled="!modelInfo" />
              <span class="field-value">{{ cubeEdgeColor.toUpperCase() }}</span>
            </div>
          </div>
        </section>

        <section v-else class="section-card">
          <h2 class="section-title">{{ t('viewer.edit') }}</h2>

          <div class="chip-row">
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'paint' }" type="button" :disabled="!canEdit" @click="setPainterMode('paint')">
              {{ t('viewer.colorMark') }}
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'erase' }" type="button" :disabled="!canEdit" @click="setPainterMode('erase')">
              {{ t('viewer.modelErase') }}
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'undo' }" type="button" :disabled="!canEdit" @click="setPainterMode('undo')">
              {{ t('viewer.eraser') }}
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'view' }" type="button" @click="setPainterMode('view')">
              {{ t('viewer.viewMode') }}
            </button>
          </div>

          <div class="field-block">
            <label class="field-label" for="painter-color">{{ t('viewer.color') }}</label>
            <div class="color-row">
              <input id="painter-color" v-model="painterColor" class="color-swatch" type="color" :disabled="!canEdit" />
              <span class="field-value">{{ painterColor.toUpperCase() }}</span>
            </div>
          </div>

          <div class="field-block">
            <label class="field-label" for="brush-radius">{{ t('viewer.brushRadius') }}</label>
            <input id="brush-radius" v-model.number="brushRadiusFactor" class="range-control" type="range" min="0.005" max="0.12" step="0.001" :disabled="!canEdit" />
            <span class="field-value">{{ brushRadiusLabel }}</span>
          </div>

          <div class="field-block">
            <label class="field-label" for="brush-depth">{{ t('viewer.brushDepth') }}</label>
            <input id="brush-depth" v-model.number="brushDepthFactor" class="range-control" type="range" min="0.2" max="8" step="0.1" :disabled="!canEdit" />
            <span class="field-value">{{ brushDepthLabel }}</span>
          </div>

          <div class="action-stack compact">
            <button class="side-button" type="button" :disabled="!canEdit || !canUndoEdit" @click="requestUndoLastEdit">
              {{ t('viewer.undo') }}
            </button>
            <button class="side-button" type="button" :disabled="!canEdit || !canRedoEdit" @click="requestRedoLastEdit">
              {{ t('viewer.redo') }}
            </button>
            <button class="side-button" type="button" :disabled="!canEdit" @click="requestRestoreModel">
              {{ t('viewer.restoreOriginal') }}
            </button>
            <button class="side-button primary" type="button" :disabled="!canExport" @click="requestExport">
              {{ exportPending ? t('viewer.exporting') : t('viewer.exportSpz') }}
            </button>
          </div>
        </section>

        <section class="status-strip" :class="{ error: actionError }">
          {{ statusMessage }}
        </section>
      </div>
    </aside>

    <div v-if="projectInfoDialogVisible" class="app-modal-backdrop" @click.self="closeProjectInfoDialog">
      <div class="app-modal project-info-modal">
        <div class="app-modal-header">
          <div>
            <p class="eyebrow">{{ t('viewer.configInfo') }}</p>
            <h2 class="app-modal-title">{{ t('viewer.editProjectInfo') }}</h2>
          </div>
          <button class="annotation-dialog-button primary" type="button" @click="saveProjectInfoChanges">
            {{ t('common.save') }}
          </button>
        </div>

        <div class="app-modal-body project-info-form">
          <section class="project-info-block">
            <label class="field-label" for="project-name-input">{{ t('viewer.projectName') }}</label>
            <input
              id="project-name-input"
              v-model="projectInfoDraft.projectName"
              class="text-control project-name-control"
              type="text"
              :placeholder="t('viewer.projectNamePlaceholder')"
            />
          </section>

          <section class="project-info-block">
            <h3 class="section-title project-info-subtitle">{{ t('viewer.fieldContent') }}</h3>

            <div class="project-info-field-list">
              <article v-for="field in projectInfoDraft.fields" :key="field.key" class="project-info-field-card">
                <div class="project-info-field-grid">
                  <label class="field-label">{{ t('viewer.fieldName') }}</label>
                  <input v-model="field.label" class="text-control" type="text" :placeholder="t('viewer.fieldNamePlaceholder')" />
                  <label class="field-label">{{ t('viewer.fieldContent') }}</label>
                  <input v-model="field.value" class="text-control" type="text" :placeholder="t('viewer.fieldValuePlaceholder')" />
                </div>

                <div v-if="!isBuiltinProjectInfoField(field.key)" class="project-info-field-actions">
                  <button
                    class="annotation-dialog-button danger project-info-remove-button"
                    type="button"
                    @click="removeCustomProjectInfoField(field.key)"
                  >
                    {{ t('common.delete') }}
                  </button>
                </div>
              </article>
            </div>

            <button class="side-button project-info-add-button" type="button" @click="addCustomProjectInfoField">
              {{ t('viewer.addCustomField') }}
            </button>
          </section>
        </div>

        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="closeProjectInfoDialog">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>

    <input
      ref="uploadInputRef"
      class="visually-hidden"
      type="file"
      accept=".ply,.spz"
      @change="handleUpload"
    />

    <div v-if="modelSelectionVisible" class="app-modal-backdrop" @click.self="closeModelSelectionDialog">
      <div class="app-modal model-picker-modal">
        <div class="app-modal-header">
          <h2 class="app-modal-title">{{ t('viewer.selectModelTitle') }}</h2>
          <p class="model-picker-subtitle">
            {{ t('viewer.modelCountHint', { count: modelCandidates.length }) }}
          </p>
        </div>
        <div class="app-modal-body model-choice-list">
          <button
            v-for="candidate in modelCandidates"
            :key="candidate.id ?? candidate.name"
            class="model-choice-card"
            :class="{ 'is-current': candidate.id && candidate.id === currentModelId }"
            type="button"
            @click="selectModelCandidate(candidate)"
          >
            <span class="model-choice-name">{{ candidate.name }}</span>
            <span class="model-choice-meta">
              {{ candidate.format || t('viewer.unknownFormat') }}
              <template v-if="candidate.version"> · v{{ candidate.version }}</template>
              <template v-if="candidate.sizeBytes"> · {{ formatModelSize(candidate.sizeBytes) }}</template>
            </span>
            <span v-if="candidate.updatedAt" class="model-choice-updated">
              {{ t('viewer.updatedAt', { date: formatModelUpdatedAt(candidate.updatedAt) }) }}
            </span>
            <span v-if="candidate.id && candidate.id === currentModelId" class="model-choice-badge">{{ t('viewer.currentlyLoaded') }}</span>
          </button>
        </div>
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="closeModelSelectionDialog">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>
  </main>
</template>
