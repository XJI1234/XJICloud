<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'
import SparkViewport from '@/modules/viewer/components/SparkViewport.vue'
import { CAMERA_STATUS_KEY, type CameraStatus } from '@/constants/cameraStatus'
import {
  createCloudViewerStorage,
  rememberModelMeta,
  VIEWER_STORAGE_KEY,
  type ModelSummary,
} from '@/modules/viewer/viewerStorage'
import { useProjectStore } from '@/stores/project'
import clockwiseRotateIcon from '../../../顺时针旋转.svg'
import counterclockwiseRotateIcon from '../../../逆时针旋转.svg'

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

const PROJECT_INFO_BUILTIN_FIELDS = [
  { key: 'coordinates', label: '经纬度' },
  { key: 'buildingName', label: '建筑名称' },
  { key: 'floorCount', label: '楼层数' },
  { key: 'height', label: '高度' },
] as const

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
const currentFilePath = ref<string | null>(null)
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
const statusMessage = ref('选择一个 PLY 或 SPZ 文件开始查看')
const canUndoEdit = ref(false)
const canRedoEdit = ref(false)
const projectInfo = ref<ProjectInfoConfig>(createEmptyProjectInfo())
const projectInfoDraft = ref<ProjectInfoConfig>(createEmptyProjectInfo())
const projectInfoDialogVisible = ref(false)

const canEdit = computed(() => Boolean(modelInfo.value))
const canExport = computed(() => Boolean(modelInfo.value && !exportPending.value))
const activeProjectName = computed(() => projectStore.projects.find((project) => project.id === projectStore.activeProjectId)?.name ?? '未选择项目')
const brushRadiusLabel = computed(() => `${(Number(brushRadiusFactor.value) * 100).toFixed(1)}% 模型半径`)
const brushDepthLabel = computed(() => `${Number(brushDepthFactor.value).toFixed(1)} 倍模型半径`)
const painterModeLabel = computed(() => {
  switch (painterMode.value) {
    case 'paint':
      return '颜色标记模式'
    case 'erase':
      return '模型擦除模式'
    case 'undo':
      return '橡皮擦模式'
    default:
      return '查看模式'
  }
})

function createEmptyProjectInfo(): ProjectInfoConfig {
  return {
    projectName: '',
    fields: PROJECT_INFO_BUILTIN_FIELDS.map(({ key, label }) => ({
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

  for (const rawField of rawFields) {
    if (!rawField || typeof rawField !== 'object') {
      continue
    }

    const key = typeof rawField.key === 'string' ? rawField.key.trim() : ''
    if (!key) {
      continue
    }

    const builtinDefinition = PROJECT_INFO_BUILTIN_FIELDS.find((field) => field.key === key)
    const label = typeof rawField.label === 'string' && rawField.label.trim()
      ? rawField.label.trim()
      : builtinDefinition?.label ?? '自定义字段'
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
      ...PROJECT_INFO_BUILTIN_FIELDS.map(({ key, label }) => {
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
    label: '自定义字段',
    value: '',
  }
}

function isBuiltinProjectInfoField(key: string) {
  return PROJECT_INFO_BUILTIN_FIELDS.some((field) => field.key === key)
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
  currentFilePath.value = null
  currentModelId.value = modelId
  modelInfo.value = null
  resetProjectInfoState()
  resetModelInteractionState()
  statusMessage.value = `正在载入 ${file.name}`
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
    statusMessage.value = `正在下载 ${candidate.name}...`
    const loaded = await viewerStorage.loadModelBytes(candidate.id, (loaded, total) => {
      if (total > 0) {
        statusMessage.value = `正在下载 ${candidate.name} (${Math.round((loaded / total) * 100)}%)`
      }
    })
    setCurrentModelSource(loaded.file, loaded.modelId)
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '加载模型失败'
    statusMessage.value = actionError.value
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
  return date.toLocaleString()
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
  statusMessage.value = models.length > 1
    ? `当前工程共有 ${models.length} 个模型，请选择要加载的文件`
    : '请选择要加载的模型文件'
}

async function openFilePicker() {
  actionError.value = ''

  if (!projectStore.activeProjectId) {
    actionError.value = '请先在工程项目页选择或创建项目'
    statusMessage.value = actionError.value
    return
  }

  try {
    const models = await viewerStorage.listModels(projectStore.activeProjectId)
    if (models.length === 0) {
      actionError.value = '当前项目还没有模型，请先上传 PLY 或 SPZ 文件'
      statusMessage.value = actionError.value
      return
    }

    presentModelSelection(models)
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '加载模型列表失败'
    statusMessage.value = actionError.value
  }
}

function triggerUpload() {
  if (!projectStore.activeProjectId) {
    actionError.value = '请先在工程项目页选择或创建项目'
    statusMessage.value = actionError.value
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
    statusMessage.value = `正在上传 ${file.name}...`
    const model = await uploadModel(projectStore.activeProjectId, file)
    rememberModelMeta(model.id, {
      fileName: model.fileName,
      projectId: projectStore.activeProjectId,
    })
    setCurrentModelSource(file, model.id)
    statusMessage.value = `上传完成，正在载入 ${file.name}`
  } catch (error) {
    actionError.value = error instanceof ApiError ? error.message : '上传模型失败'
    statusMessage.value = actionError.value
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
      statusMessage.value = '左键进行颜色标记，右键仍可拖动画面'
      break
    case 'erase':
      statusMessage.value = '左键执行模型擦除，右键仍可拖动画面'
      break
    case 'undo':
      statusMessage.value = '左键使用橡皮擦恢复原始颜色与透明度'
      break
    default:
      statusMessage.value = '查看模式已启用，左键旋转，右键拖动，滚轮缩放'
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
    statusMessage.value = '正在顺时针旋转屏幕平面视角'
    rotateClockwiseToken.value += 1
    return
  }

  statusMessage.value = '正在逆时针旋转屏幕平面视角'
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
  statusMessage.value = annotationPlacementActive.value ? '气泡标注模式已启用，请单击模型表面添加文字' : '已取消气泡标注模式'
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
  statusMessage.value = cubePlacementActive.value ? '立方体标记模式已启用，请按住左键拖拽绘制立方体' : '已取消立方体标记模式'
}

function requestExport() {
  if (!canExport.value) {
    return
  }

  exportPending.value = true
  activeSidePanel.value = 'edit'
  actionError.value = ''
  statusMessage.value = '正在导出已编辑模型为 SPZ'
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
  statusMessage.value = '正在恢复原始模型'
  restoreModelToken.value += 1
}

function requestUndoLastEdit() {
  if (!modelInfo.value || !canUndoEdit.value) {
    return
  }

  actionError.value = ''
  statusMessage.value = '正在撤回上一次更改'
  undoEditToken.value += 1
}

function requestRedoLastEdit() {
  if (!modelInfo.value || !canRedoEdit.value) {
    return
  }

  actionError.value = ''
  statusMessage.value = '正在重做上一次撤回的更改'
  redoEditToken.value += 1
}

function requestResetView() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  statusMessage.value = '正在重置相机视角'
  resetViewToken.value += 1
}

function requestSaveDefaultView() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  statusMessage.value = '正在保存当前视角为该模型的默认视角'
  saveDefaultViewToken.value += 1
}

function requestSaveMarkers() {
  if (!modelInfo.value) {
    return
  }

  actionError.value = ''
  statusMessage.value = '正在保存当前标记配置'
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
  statusMessage.value = '正在保存项目信息'
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
  statusMessage.value = `${info.fileName} 已载入，左键旋转，右键拖动，滚轮缩放`
}

function handleFailed(message: string) {
  resetProjectInfoState()
  canUndoEdit.value = false
  canRedoEdit.value = false
  actionError.value = message
  exportPending.value = false
  annotationPlacementActive.value = false
  cubePlacementActive.value = false
  statusMessage.value = message
}

function handleStatus(message: string) {
  if (!actionError.value) {
    statusMessage.value = message
  }
}

function handleExported(result: PainterExportResult) {
  exportPending.value = false
  actionError.value = ''
  const shSuffix = result.sphericalHarmonicsDegree > 0 ? `，SH ${result.sphericalHarmonicsDegree}` : ''
  const clippedSuffix = result.clippedCount > 0 ? `，裁剪 ${result.clippedCount.toLocaleString('zh-CN')} 个 splat` : ''
  statusMessage.value = `已导出 ${result.fileName}，包含 ${result.splatCount.toLocaleString('zh-CN')} 个 splat${shSuffix}${clippedSuffix}`
}

function handleExportFailed(message: string) {
  exportPending.value = false
  if (message === '已取消导出') {
    actionError.value = ''
    statusMessage.value = message
      return
    }

  actionError.value = message
  statusMessage.value = message
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
    statusMessage.value = '项目列表加载失败，请检查登录状态'
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
          :file-path="currentFilePath"
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
      菜单
    </button>

    <aside class="side-panel" :class="{ collapsed: panelCollapsed }">
      <div v-if="!panelCollapsed" class="side-content">
        <button class="panel-toggle expanded-toggle" type="button" @click="togglePanel">
          收起菜单
        </button>

        <section class="model-card">
          <h1 class="model-title">{{ modelInfo?.fileName || '等待模型载入' }}</h1>
        </section>

        <nav class="nav-strip" aria-label="Viewer navigation">
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'info' }"
            type="button"
            @click="setActiveSidePanel('info')"
          >
            信息
          </button>
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'view' }"
            type="button"
            @click="setActiveSidePanel('view')"
          >
            查看
          </button>
          <button
            class="nav-button"
            :class="{ 'is-active': activeSidePanel === 'edit' }"
            type="button"
            :disabled="!canEdit"
            @click="setActiveSidePanel('edit')"
          >
            编辑
          </button>
        </nav>

        <section v-if="activeSidePanel === 'info'" class="section-card info-card">
          <p class="info-project-context">当前项目：{{ activeProjectName }}</p>
          <button class="side-button primary" type="button" @click="openFilePicker">
            选择模型
          </button>
          <button class="side-button" type="button" @click="triggerUpload">
            上传模型
          </button>

          <template v-if="modelInfo">
            <div class="info-summary">
              <h3 class="info-project-title">{{ projectInfo.projectName || '未命名项目' }}</h3>
            </div>

            <div class="info-list">
              <article v-for="field in projectInfo.fields" :key="field.key" class="info-item">
                <span class="info-item-label">{{ field.label }}</span>
                <strong class="info-item-value" :class="{ 'is-empty': !field.value }">
                  {{ field.value || '未填写' }}
                </strong>
              </article>
            </div>

            <button class="side-button primary info-edit-button" type="button" :disabled="!modelInfo" @click="openProjectInfoDialog">
              编辑信息
            </button>
          </template>

          <template v-else>
            <div class="info-empty-state">
              <p class="info-empty-title">请加载模型</p>
            </div>
          </template>
        </section>

        <section v-else-if="activeSidePanel === 'view'" class="section-card">
          <h2 class="section-title">查看</h2>

          <div class="action-stack">
            <button class="side-button" type="button" :disabled="!modelInfo" @click="requestResetView">
              重置视角
            </button>
            <button class="side-button" type="button" :disabled="!modelInfo" @click="requestSaveDefaultView">
              设为默认视角
            </button>
            <button class="side-button" :class="{ 'is-active': annotationPlacementActive }" type="button" :disabled="!modelInfo" @click="toggleAnnotationPlacement">
              {{ annotationPlacementActive ? '取消气泡标注' : '添加气泡标注' }}
            </button>
            <button class="side-button" :class="{ 'is-active': cubePlacementActive }" type="button" :disabled="!modelInfo" @click="toggleCubePlacement">
              {{ cubePlacementActive ? '取消立方体标记' : '添加立方体标记' }}
            </button>
            <div class="rotation-row">
              <button class="side-button rotation-button" type="button" :disabled="!modelInfo" @click="requestRotateView('counterclockwise')">
                <img class="rotation-icon" :src="counterclockwiseRotateIcon" alt="" aria-hidden="true" />
                <span>逆时针</span>
              </button>
              <button class="side-button rotation-button" type="button" :disabled="!modelInfo" @click="requestRotateView('clockwise')">
                <img class="rotation-icon" :src="clockwiseRotateIcon" alt="" aria-hidden="true" />
                <span>顺时针</span>
              </button>
            </div>
            <button class="side-button primary" type="button" :disabled="!modelInfo" @click="requestSaveMarkers">
              保存标记更改
            </button>
          </div>

          <div class="field-block">
            <label class="field-label" for="annotation-edge-color">气泡标记颜色</label>
            <div class="color-row">
              <input id="annotation-edge-color" v-model="annotationEdgeColor" class="color-swatch" type="color" :disabled="!modelInfo" />
              <span class="field-value">{{ annotationEdgeColor.toUpperCase() }}</span>
            </div>
          </div>

          <div class="field-block">
            <label class="field-label" for="cube-edge-color">立方体边线颜色</label>
            <div class="color-row">
              <input id="cube-edge-color" v-model="cubeEdgeColor" class="color-swatch" type="color" :disabled="!modelInfo" />
              <span class="field-value">{{ cubeEdgeColor.toUpperCase() }}</span>
            </div>
          </div>
        </section>

        <section v-else class="section-card">
          <h2 class="section-title">编辑</h2>

          <div class="chip-row">
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'paint' }" type="button" :disabled="!canEdit" @click="setPainterMode('paint')">
              颜色标记
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'erase' }" type="button" :disabled="!canEdit" @click="setPainterMode('erase')">
              模型擦除
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'undo' }" type="button" :disabled="!canEdit" @click="setPainterMode('undo')">
              橡皮擦
            </button>
            <button class="mode-chip" :class="{ 'is-active': painterMode === 'view' }" type="button" @click="setPainterMode('view')">
              查看
            </button>
          </div>

          <div class="field-block">
            <label class="field-label" for="painter-color">颜色</label>
            <div class="color-row">
              <input id="painter-color" v-model="painterColor" class="color-swatch" type="color" :disabled="!canEdit" />
              <span class="field-value">{{ painterColor.toUpperCase() }}</span>
            </div>
          </div>

          <div class="field-block">
            <label class="field-label" for="brush-radius">画笔半径</label>
            <input id="brush-radius" v-model.number="brushRadiusFactor" class="range-control" type="range" min="0.005" max="0.12" step="0.001" :disabled="!canEdit" />
            <span class="field-value">{{ brushRadiusLabel }}</span>
          </div>

          <div class="field-block">
            <label class="field-label" for="brush-depth">画笔深度</label>
            <input id="brush-depth" v-model.number="brushDepthFactor" class="range-control" type="range" min="0.2" max="8" step="0.1" :disabled="!canEdit" />
            <span class="field-value">{{ brushDepthLabel }}</span>
          </div>

          <div class="action-stack compact">
            <button class="side-button" type="button" :disabled="!canEdit || !canUndoEdit" @click="requestUndoLastEdit">
              撤回
            </button>
            <button class="side-button" type="button" :disabled="!canEdit || !canRedoEdit" @click="requestRedoLastEdit">
              重做
            </button>
            <button class="side-button" type="button" :disabled="!canEdit" @click="requestRestoreModel">
              恢复原始模型
            </button>
            <button class="side-button primary" type="button" :disabled="!canExport" @click="requestExport">
              {{ exportPending ? '导出中...' : '导出 SPZ' }}
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
            <p class="eyebrow">配置文件信息</p>
            <h2 class="app-modal-title">编辑项目信息</h2>
          </div>
          <button class="annotation-dialog-button primary" type="button" @click="saveProjectInfoChanges">
            保存
          </button>
        </div>

        <div class="app-modal-body project-info-form">
          <section class="project-info-block">
            <label class="field-label" for="project-name-input">项目名称</label>
            <input
              id="project-name-input"
              v-model="projectInfoDraft.projectName"
              class="text-control project-name-control"
              type="text"
              placeholder="输入项目名称"
            />
          </section>

          <section class="project-info-block">
            <h3 class="section-title project-info-subtitle">字段内容</h3>

            <div class="project-info-field-list">
              <article v-for="field in projectInfoDraft.fields" :key="field.key" class="project-info-field-card">
                <div class="project-info-field-grid">
                  <label class="field-label">字段名称</label>
                  <input v-model="field.label" class="text-control" type="text" placeholder="输入字段名称" />
                  <label class="field-label">字段内容</label>
                  <input v-model="field.value" class="text-control" type="text" placeholder="输入字段内容" />
                </div>

                <div v-if="!isBuiltinProjectInfoField(field.key)" class="project-info-field-actions">
                  <button
                    class="annotation-dialog-button danger project-info-remove-button"
                    type="button"
                    @click="removeCustomProjectInfoField(field.key)"
                  >
                    删除
                  </button>
                </div>
              </article>
            </div>

            <button class="side-button project-info-add-button" type="button" @click="addCustomProjectInfoField">
              新增自定义字段
            </button>
          </section>
        </div>

        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="closeProjectInfoDialog">取消</button>
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
          <h2 class="app-modal-title">选择模型</h2>
          <p class="model-picker-subtitle">
            共 {{ modelCandidates.length }} 个模型；高级编辑保存后会更新版本，请重新选择以加载最新文件。
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
              {{ candidate.format || '未知格式' }}
              <template v-if="candidate.version"> · v{{ candidate.version }}</template>
              <template v-if="candidate.sizeBytes"> · {{ formatModelSize(candidate.sizeBytes) }}</template>
            </span>
            <span v-if="candidate.updatedAt" class="model-choice-updated">
              更新于 {{ formatModelUpdatedAt(candidate.updatedAt) }}
            </span>
            <span v-if="candidate.id && candidate.id === currentModelId" class="model-choice-badge">当前加载</span>
          </button>
        </div>
        <div class="app-modal-footer">
          <button class="side-button" type="button" @click="closeModelSelectionDialog">取消</button>
        </div>
      </div>
    </div>
  </main>
</template>
