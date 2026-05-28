<script setup lang="ts">
import { markRaw, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js'
import { LineSegments2 } from 'three/examples/jsm/lines/LineSegments2.js'
import { LineSegmentsGeometry } from 'three/examples/jsm/lines/LineSegmentsGeometry.js'
import { pickDesktopExportPath, readDesktopModelFile, readDesktopTextFile, writeDesktopBinaryFile, writeDesktopTextFile } from '@/utils/desktopRuntime'
import { dyno, PackedSplats, readRgbaArray, RgbaArray, SparkControls, SparkRenderer, SplatMesh, SpzWriter, unpackSplat } from '@/lib/spark'

type PainterMode = 'view' | 'paint' | 'erase' | 'undo'

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

interface StoredDefaultView {
  position: [number, number, number]
  quaternion: [number, number, number, number]
}

interface ProjectInfoField {
  key: string
  label: string
  value: string
}

interface ProjectInfoConfig {
  projectName: string
  fields: ProjectInfoField[]
}

interface ViewerConfigFile {
  version: number
  defaultView: StoredDefaultView | null
  pointAnnotations: StoredTextAnnotation[]
  cubeMarkers: StoredCubeMarker[]
  projectInfo: ProjectInfoConfig
}

interface StoredTextAnnotation {
  id: string
  text: string
  position: [number, number, number]
  edgeColor: string
}

interface TextAnnotation extends StoredTextAnnotation {
  screenX: number
  screenY: number
  visible: boolean
}

interface StoredCubeMarker {
  id: string
  center: [number, number, number]
  size: number
  edgeColor: string
  annotationText?: string
}

interface CubeMarkerVisual {
  mesh: THREE.Mesh<THREE.BoxGeometry, THREE.MeshBasicMaterial>
  edges: LineSegments2
}

interface CubeMarker extends StoredCubeMarker, CubeMarkerVisual {
  labelScreenX: number
  labelScreenY: number
  labelVisible: boolean
}

type AnnotationDialogTarget =
  | { kind: 'create-point'; position: [number, number, number] }
  | { kind: 'edit-point'; id: string }
  | { kind: 'cube-label'; cubeId: string }

const props = defineProps<{
  file: File | null
  filePath: string | null
  fileHandle: FileSystemFileHandle | null
  directoryHandle: FileSystemDirectoryHandle | null
  painterMode: PainterMode
  painterColor: string
  brushRadiusFactor: number
  brushDepthFactor: number
  exportToken: number
  restoreModelToken: number
  undoEditToken: number
  redoEditToken: number
  resetViewToken: number
  saveDefaultViewToken: number
  saveMarkersToken: number
  rotateClockwiseToken: number
  rotateCounterclockwiseToken: number
  annotationPlacementActive: boolean
  annotationEdgeColor: string
  cubePlacementActive: boolean
  cubeEdgeColor: string
  projectInfo: ProjectInfoConfig | null
  saveProjectInfoToken: number
  sceneInteractionLocked: boolean
}>()

const emit = defineEmits<{
  loaded: [info: LoadedModelInfo]
  failed: [message: string]
  status: [message: string]
  exported: [result: PainterExportResult]
  'export-failed': [message: string]
  'annotation-placement-change': [active: boolean]
  'annotation-selection-color-change': [color: string | null]
  'cube-placement-change': [active: boolean]
  'cube-selection-color-change': [color: string | null]
  'project-info-loaded': [info: ProjectInfoConfig]
  'undo-availability-change': [available: boolean]
  'redo-availability-change': [available: boolean]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const annotationDialogInputRef = ref<HTMLTextAreaElement | null>(null)
const pointAnnotations = ref<TextAnnotation[]>([])
const cubeMarkers = ref<CubeMarker[]>([])
const annotationDialogVisible = ref(false)
const annotationDialogTitle = ref('')
const annotationDialogText = ref('')
const annotationDialogTarget = ref<AnnotationDialogTarget | null>(null)
const activeAnnotationDragId = ref<string | null>(null)
const activeCubeDragId = ref<string | null>(null)
const selectedPointId = ref<string | null>(null)
const selectedCubeId = ref<string | null>(null)

const brushParameters = {
  eraseEnabled: dyno.dynoBool(false),
  brushEnabled: dyno.dynoBool(false),
  undoEnabled: dyno.dynoBool(false),
  brushDepth: dyno.dynoFloat(1.0),
  brushRadius: dyno.dynoFloat(0.05),
  brushOrigin: dyno.dynoVec3(new THREE.Vector3(0.0, 0.0, 0.0)),
  brushDirection: dyno.dynoVec3(new THREE.Vector3(0.0, 0.0, -1.0)),
  brushColor: dyno.dynoVec3(new THREE.Vector3(0.0627, 0.7255, 0.5059)),
}

const revealParameters = {
  progress: dyno.dynoFloat(8.5),
}

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let spark: SparkRenderer | null = null
let controls: SparkControls | null = null
let raycaster: THREE.Raycaster | null = null
let currentModelRoot: THREE.Group | null = null
let currentMesh: SplatMesh | null = null
let currentFile: File | null = null
let currentFileBytes: Uint8Array | null = null
let currentOriginalRgba: RgbaArray | null = null
let undoHistory: RgbaArray[] = []
let redoHistory: RgbaArray[] = []
let currentFileBaseName = 'painted-splat'
let modelRadius = 1
let activePaintPointerId: number | null = null
let revealActive = false
let revealStartTime = 0
let queuedViewRollRadians = 0
let pendingRotationCompletionMessage = ''
let lastAnimationTime = 0
let activeAnnotationDragPointerId: number | null = null
let activeCubeDrawPointerId: number | null = null
let activeCubeDragPointerId: number | null = null
let cubeDrawAnchorPoint: THREE.Vector3 | null = null
let previewCube: CubeMarkerVisual | null = null
let pointDragDidMove = false
let cubeDragDidMove = false
let cubeDragPlaneAnchor: THREE.Vector3 | null = null
let currentConfigHandle: FileSystemFileHandle | null = null
let currentConfigPath: string | null = null
let currentConfigFileName = ''
let currentDefaultView: StoredDefaultView | null = null
let configDirty = false

const cubeDragOffset = new THREE.Vector3()

const MODEL_VERTICAL_FLIP_RADIANS = Math.PI
const CAMERA_FIT_PADDING = 0.92
const REVEAL_EFFECT_END = 8.5
const REVEAL_DURATION_SECONDS = 1.85
const REVEAL_MIN_SCALE = 0.002
const VIEWER_CONFIG_SUFFIX = '.viewer.json'
const VIEWER_CONFIG_VERSION = 2
const VIEW_ROLL_STEP_RADIANS = THREE.MathUtils.degToRad(15)
const VIEW_ROLL_SPEED_RADIANS_PER_SECOND = THREE.MathUtils.degToRad(40)
const CUBE_FILL_OPACITY = 0
const CUBE_PREVIEW_OPACITY = 0
const CUBE_DRAG_WHEEL_DEPTH_FACTOR = 0.001
const MIN_CUBE_SIZE_FALLBACK = 0.03
const CUBE_LABEL_OFFSET_FACTOR = 0.65
const EDIT_HISTORY_LIMIT = 24
const PROJECT_INFO_BUILTIN_FIELDS = [
  { key: 'coordinates', label: '经纬度' },
  { key: 'buildingName', label: '建筑名称' },
  { key: 'floorCount', label: '楼层数' },
  { key: 'height', label: '高度' },
] as const
const currentProjectInfo = ref<ProjectInfoConfig>(createEmptyProjectInfo())

const scratchBoundsCenter = new THREE.Vector3()
const scratchBoundsSize = new THREE.Vector3()
const scratchWorldCenter = new THREE.Vector3()
const scratchPointerNdc = new THREE.Vector2()
const scratchViewDirection = new THREE.Vector3()
const scratchRollQuaternion = new THREE.Quaternion()
const scratchProjectionPoint = new THREE.Vector3()
const scratchRayPlanePoint = new THREE.Vector3()
const scratchDragPlane = new THREE.Plane()
const scratchCubePoint = new THREE.Vector3()
const scratchCubeOffset = new THREE.Vector3()

function brushDyno(originalRgba: any) {
  const flatColor = dyno.dynoVec3(new THREE.Vector3(1.0, 1.0, 1.0))
  const luminanceThreshold = dyno.dynoFloat(0.1)

  return dyno.dynoBlock({ gsplat: dyno.Gsplat }, { gsplat: dyno.Gsplat }, ({ gsplat }) => {
    let nextGsplat = gsplat as any
    const { center, rgb, opacity, index } = dyno.splitGsplat(nextGsplat).outputs
    const projectionAmplitude = dyno.dot(brushParameters.brushDirection, dyno.sub(center, brushParameters.brushOrigin))
    const projectedCenter = dyno.add(brushParameters.brushOrigin, dyno.mul(brushParameters.brushDirection, projectionAmplitude))
    const distance = dyno.length(dyno.sub(projectedCenter, center))
    const isInside = dyno.and(
      dyno.lessThan(distance, brushParameters.brushRadius),
      dyno.and(dyno.greaterThan(projectionAmplitude, dyno.dynoFloat(0.0)), dyno.lessThan(projectionAmplitude, brushParameters.brushDepth)),
    )
    const luminanceOld = dyno.div(dyno.dot(rgb, flatColor), dyno.dynoFloat(3.0))
    const luminanceNew = dyno.div(dyno.dot(brushParameters.brushColor, flatColor), dyno.dynoFloat(3.0))
    const weightedRgb = dyno.mul(brushParameters.brushColor, dyno.div(luminanceOld, luminanceNew))
    const isLuminanceAboveThreshold = dyno.greaterThan(luminanceOld, luminanceThreshold)
    let newRgb = dyno.select(
      dyno.and(dyno.and(brushParameters.brushEnabled, isInside), isLuminanceAboveThreshold),
      weightedRgb,
      rgb,
    )
    let newOpacity = dyno.select(
      brushParameters.eraseEnabled,
      dyno.select(isInside, dyno.dynoFloat(0.0), opacity),
      opacity,
    )

    const originalRgbaValue = readRgbaArray(originalRgba as any, index as any)
    const originalRgbVec = dyno.vec3(originalRgbaValue as any)
    const originalOpacityVal = dyno.swizzle(originalRgbaValue as any, 'w' as any) as any
    newRgb = dyno.select(dyno.and(brushParameters.undoEnabled, isInside), originalRgbVec, newRgb)
    newOpacity = dyno.select(dyno.and(brushParameters.undoEnabled, isInside), originalOpacityVal, newOpacity) as any

    nextGsplat = dyno.combineGsplat({ gsplat: nextGsplat, rgb: newRgb, opacity: newOpacity })
    return { gsplat: nextGsplat }
  })
}

function updateBrushColor(colorHex: string) {
  const color = new THREE.Color(colorHex).convertLinearToSRGB()
  brushParameters.brushColor.value.set(color.r, color.g, color.b)
}

function syncBrushMode() {
  brushParameters.brushEnabled.value = props.painterMode === 'paint'
  brushParameters.eraseEnabled.value = props.painterMode === 'erase'
  brushParameters.undoEnabled.value = props.painterMode === 'undo'
}

function syncBrushMetrics() {
  brushParameters.brushRadius.value = Math.max(modelRadius * props.brushRadiusFactor, 0.01)
  brushParameters.brushDepth.value = Math.max(modelRadius * props.brushDepthFactor, 0.1)
}

function syncBrushParameters() {
  syncBrushMode()
  syncBrushMetrics()
  updateBrushColor(props.painterColor)
}

function getViewerConfigFileName(file = currentFile) {
  if (!file) {
    return null
  }

  const baseName = file.name.replace(/\.[^.]+$/, '') || 'viewer-model'
  return `${baseName}${VIEWER_CONFIG_SUFFIX}`
}

function getViewerConfigPath(filePath = props.filePath) {
  if (!filePath) {
    return null
  }

  return `${filePath.replace(/\.[^.]+$/, '')}${VIEWER_CONFIG_SUFFIX}`
}

function getFileNameFromPath(filePath: string) {
  const segments = filePath.split(/[/\\]/)
  return segments[segments.length - 1] || filePath
}

function markConfigDirty() {
  configDirty = true
}

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

function cloneProjectInfo(projectInfo: ProjectInfoConfig): ProjectInfoConfig {
  return {
    projectName: projectInfo.projectName,
    fields: projectInfo.fields.map((field) => ({
      key: field.key,
      label: field.label,
      value: field.value,
    })),
  }
}

function coerceProjectInfoConfig(rawValue: unknown): ProjectInfoConfig {
  const emptyProjectInfo = createEmptyProjectInfo()
  if (!rawValue || typeof rawValue !== 'object') {
    return emptyProjectInfo
  }

  try {
    const parsedValue = rawValue as Partial<ProjectInfoConfig>
    const rawFields = Array.isArray(parsedValue.fields) ? parsedValue.fields : []
    const builtinFields = new Map<string, ProjectInfoField>()
    const customFields: ProjectInfoField[] = []
    const seenCustomKeys = new Set<string>()

    for (const rawField of rawFields) {
      if (!rawField || typeof rawField !== 'object') {
        continue
      }

      const fieldRecord = rawField as Partial<ProjectInfoField>
      const key = typeof fieldRecord.key === 'string' ? fieldRecord.key.trim() : ''
      if (!key) {
        continue
      }

      const builtinDefinition = PROJECT_INFO_BUILTIN_FIELDS.find((field) => field.key === key)
      const label = typeof fieldRecord.label === 'string' && fieldRecord.label.trim()
        ? fieldRecord.label.trim()
        : builtinDefinition?.label ?? '自定义字段'
      const value = typeof fieldRecord.value === 'string' ? fieldRecord.value : ''

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
      projectName: typeof parsedValue.projectName === 'string' ? parsedValue.projectName.trim() : '',
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
  } catch {
    return emptyProjectInfo
  }
}

function createEmptyViewerConfig(): ViewerConfigFile {
  return {
    version: VIEWER_CONFIG_VERSION,
    defaultView: null,
    pointAnnotations: [],
    cubeMarkers: [],
    projectInfo: createEmptyProjectInfo(),
  }
}

function createViewerConfigSnapshot(defaultView = currentDefaultView): ViewerConfigFile {
  return {
    version: VIEWER_CONFIG_VERSION,
    defaultView,
    pointAnnotations: pointAnnotations.value.map(({ id, text, position, edgeColor }) => ({ id, text, position, edgeColor })),
    cubeMarkers: cubeMarkers.value.map(({ id, center, size, edgeColor, annotationText }) => ({
      id,
      center,
      size,
      edgeColor,
      annotationText: annotationText?.trim() || '',
    })),
    projectInfo: cloneProjectInfo(currentProjectInfo.value),
  }
}

function serializeViewerConfig(config: ViewerConfigFile) {
  return JSON.stringify(config, null, 2)
}

function getMinimumCubeSize() {
  return Math.max(modelRadius * 0.015, MIN_CUBE_SIZE_FALLBACK)
}

function setAnnotationPlacementActive(active: boolean) {
  emit('annotation-placement-change', active)
}

function setCubePlacementActive(active: boolean) {
  emit('cube-placement-change', active)
}

function captureCurrentView(): StoredDefaultView | null {
  if (!camera) {
    return null
  }

  return {
    position: [camera.position.x, camera.position.y, camera.position.z],
    quaternion: [camera.quaternion.x, camera.quaternion.y, camera.quaternion.z, camera.quaternion.w],
  }
}

function resetControlInertia() {
  controls?.pointerControls.moveVelocity.set(0, 0, 0)
  controls?.pointerControls.rotateVelocity.set(0, 0, 0)
  controls?.pointerControls.scroll.set(0, 0, 0)
}

function clearFpsInputState() {
  if (!controls) {
    return
  }

  controls.fpsMovement.keydown = {}
  controls.fpsMovement.keycode = {}
}

function setSceneInteractionEnabled(enabled: boolean) {
  if (controls) {
    const shouldEnable = enabled && !annotationDialogVisible.value && !props.sceneInteractionLocked
    controls.pointerControls.enable = shouldEnable
    controls.fpsMovement.enable = shouldEnable
    clearFpsInputState()
    resetControlInertia()
  }
}

function setUndoAvailability(available: boolean) {
  emit('undo-availability-change', available)
}

function setRedoAvailability(available: boolean) {
  emit('redo-availability-change', available)
}

function syncEditHistoryAvailability() {
  setUndoAvailability(undoHistory.length > 0)
  setRedoAvailability(redoHistory.length > 0)
}

function disposeHistoryStack(stack: RgbaArray[]) {
  for (const snapshot of stack) {
    snapshot.dispose()
  }
  stack.length = 0
}

function clearRedoHistory() {
  disposeHistoryStack(redoHistory)
  syncEditHistoryAvailability()
}

function clearEditHistory() {
  disposeHistoryStack(undoHistory)
  disposeHistoryStack(redoHistory)
  syncEditHistoryAvailability()
}

function pushHistorySnapshot(stack: RgbaArray[], snapshot: RgbaArray) {
  stack.push(snapshot)
  if (stack.length > EDIT_HISTORY_LIMIT) {
    stack.shift()?.dispose()
  }
  syncEditHistoryAvailability()
}

function applyStoredView(view: StoredDefaultView | null) {
  if (!camera || !view) {
    return false
  }

  camera.position.set(...view.position)
  camera.quaternion.set(...view.quaternion).normalize()
  camera.updateProjectionMatrix()
  resetControlInertia()
  return true
}

function coerceStoredDefaultViewValue(rawValue: unknown) {
  if (!rawValue || typeof rawValue !== 'object') {
    return null
  }

  try {
    const parsedValue = rawValue as Partial<StoredDefaultView>
    if (
      !Array.isArray(parsedValue.position) ||
      parsedValue.position.length !== 3 ||
      !Array.isArray(parsedValue.quaternion) ||
      parsedValue.quaternion.length !== 4
    ) {
      return null
    }

    const position = parsedValue.position.map((value) => Number(value)) as [number, number, number]
    const quaternion = parsedValue.quaternion.map((value) => Number(value)) as [number, number, number, number]
    if ([...position, ...quaternion].some((value) => !Number.isFinite(value))) {
      return null
    }

    return { position, quaternion }
  } catch {
    return null
  }
}

function coerceViewerConfig(rawValue: string | null): ViewerConfigFile {
  if (!rawValue?.trim()) {
    return createEmptyViewerConfig()
  }

  try {
    const parsedValue = JSON.parse(rawValue) as Partial<ViewerConfigFile>
    return {
      version: VIEWER_CONFIG_VERSION,
      defaultView: coerceStoredDefaultViewValue(parsedValue.defaultView),
      pointAnnotations: coerceStoredTextAnnotations(parsedValue.pointAnnotations),
      cubeMarkers: coerceStoredCubeMarkers(parsedValue.cubeMarkers),
      projectInfo: coerceProjectInfoConfig(parsedValue.projectInfo),
    }
  } catch {
    return createEmptyViewerConfig()
  }
}

async function writeViewerConfigFile(configHandle: FileSystemFileHandle, config: ViewerConfigFile) {
  const writable = await configHandle.createWritable()
  try {
    await writable.write(serializeViewerConfig(config))
  } finally {
    await writable.close()
  }
  configDirty = false
}

async function writeActiveViewerConfigFile(config: ViewerConfigFile) {
  if (currentConfigHandle) {
    await writeViewerConfigFile(currentConfigHandle, config)
    return
  }

  if (!currentConfigPath) {
    throw new Error('当前没有可写入的配置文件路径')
  }

  await writeDesktopTextFile(currentConfigPath, serializeViewerConfig(config))
  configDirty = false
}

async function ensureViewerConfigFile(file = currentFile, directoryHandle = props.directoryHandle, filePath = props.filePath) {
  if (filePath) {
    const configPath = getViewerConfigPath(filePath)
    if (!configPath) {
      return createEmptyViewerConfig()
    }

    currentConfigHandle = null
    currentConfigPath = configPath
    currentConfigFileName = getFileNameFromPath(configPath)

    const rawText = await readDesktopTextFile(configPath)
    const nextConfig = coerceViewerConfig(rawText)
    currentDefaultView = nextConfig.defaultView

    const normalizedText = serializeViewerConfig(nextConfig)
    if (!rawText?.trim() || rawText.trim() !== normalizedText.trim()) {
      await writeDesktopTextFile(configPath, normalizedText)
      configDirty = false
    } else {
      configDirty = false
    }

    return nextConfig
  }

  if (!file || !directoryHandle) {
    currentConfigHandle = null
    currentConfigPath = null
    currentConfigFileName = ''
    currentDefaultView = null
    configDirty = false
    return createEmptyViewerConfig()
  }

  const configFileName = getViewerConfigFileName(file)
  if (!configFileName) {
    return createEmptyViewerConfig()
  }

  const configHandle = await directoryHandle.getFileHandle(configFileName, { create: true })
  currentConfigHandle = configHandle
  currentConfigPath = null
  currentConfigFileName = configFileName

  const configFile = await configHandle.getFile()
  const rawText = await configFile.text()
  const nextConfig = coerceViewerConfig(rawText)
  currentDefaultView = nextConfig.defaultView

  const normalizedText = serializeViewerConfig(nextConfig)
  if (!rawText.trim() || rawText.trim() !== normalizedText.trim()) {
    await writeViewerConfigFile(configHandle, nextConfig)
  } else {
    configDirty = false
  }

  return nextConfig
}

async function saveViewerConfig(successMessage: string, failureMessage: string) {
  if (!currentConfigHandle && !currentConfigPath) {
    emit('status', failureMessage)
    return false
  }

  try {
    await writeActiveViewerConfigFile(createViewerConfigSnapshot())
    emit('status', successMessage)
    return true
  } catch {
    emit('status', failureMessage)
    return false
  }
}

async function saveCurrentViewAsDefault() {
  const currentView = captureCurrentView()
  if (!currentView || !currentMesh) {
    emit('status', '当前没有可保存的默认视角')
    return
  }

  currentDefaultView = currentView
  markConfigDirty()
  await saveViewerConfig('已将当前视角保存为该模型的默认视角', '默认视角保存失败')
}

async function saveProjectInfo() {
  currentProjectInfo.value = coerceProjectInfoConfig(props.projectInfo)
  markConfigDirty()
  await saveViewerConfig('已保存项目信息', '项目信息保存失败')
}

function applyPreferredView() {
  queuedViewRollRadians = 0
  pendingRotationCompletionMessage = ''
  frameCurrentMesh()
  if (applyStoredView(currentDefaultView)) {
    return 'default'
  }

  return 'framed'
}

function applyViewRollStep(angle: number) {
  if (!camera) {
    return false
  }

  camera.getWorldDirection(scratchViewDirection)
  scratchRollQuaternion.setFromAxisAngle(scratchViewDirection.normalize(), angle)
  camera.quaternion.premultiply(scratchRollQuaternion).normalize()
  camera.updateProjectionMatrix()
  resetControlInertia()
  return true
}

function queueViewRotation(direction: 'clockwise' | 'counterclockwise') {
  queuedViewRollRadians += direction === 'clockwise' ? VIEW_ROLL_STEP_RADIANS : -VIEW_ROLL_STEP_RADIANS
  pendingRotationCompletionMessage = direction === 'clockwise' ? '视角已完成顺时针旋转' : '视角已完成逆时针旋转'
  return true
}

function updateQueuedViewRotation(deltaSeconds: number) {
  if (!camera || Math.abs(queuedViewRollRadians) < 0.00001) {
    return
  }

  const maxStep = VIEW_ROLL_SPEED_RADIANS_PER_SECOND * Math.max(deltaSeconds, 0)
  if (maxStep <= 0) {
    return
  }

  const step = Math.sign(queuedViewRollRadians) * Math.min(Math.abs(queuedViewRollRadians), maxStep)
  if (!applyViewRollStep(step)) {
    return
  }

  queuedViewRollRadians -= step
  if (Math.abs(queuedViewRollRadians) < 0.00001) {
    queuedViewRollRadians = 0
    if (pendingRotationCompletionMessage) {
      emit('status', pendingRotationCompletionMessage)
      pendingRotationCompletionMessage = ''
    }
  }
}

function setRayFromClientPosition(clientX: number, clientY: number) {
  if (!camera || !raycaster || !canvasRef.value) {
    return false
  }

  const rect = canvasRef.value.getBoundingClientRect()
  if (rect.width === 0 || rect.height === 0) {
    return false
  }

  scratchPointerNdc.set(
    ((clientX - rect.left) / rect.width) * 2 - 1,
    -(((clientY - rect.top) / rect.height) * 2 - 1),
  )
  raycaster.setFromCamera(scratchPointerNdc, camera)
  return true
}

function pickModelPointFromClientPosition(clientX: number, clientY: number) {
  if (!currentMesh || !setRayFromClientPosition(clientX, clientY) || !raycaster) {
    return null
  }

  const hit = raycaster.intersectObject(currentMesh, false)[0]
  return hit?.point?.clone() ?? null
}

function pickPlanePointFromClientPosition(clientX: number, clientY: number, planePoint: THREE.Vector3) {
  if (!setRayFromClientPosition(clientX, clientY) || !raycaster || !camera) {
    return null
  }

  camera.getWorldDirection(scratchViewDirection)
  scratchDragPlane.setFromNormalAndCoplanarPoint(scratchViewDirection.normalize(), planePoint)
  const hit = raycaster.ray.intersectPlane(scratchDragPlane, scratchRayPlanePoint)
  return hit ? hit.clone() : null
}

function pickDragPointFromClientPosition(clientX: number, clientY: number, fallbackPoint: THREE.Vector3) {
  return pickModelPointFromClientPosition(clientX, clientY) ?? pickPlanePointFromClientPosition(clientX, clientY, fallbackPoint)
}

function updateBrushRay(event: PointerEvent) {
  if (!setRayFromClientPosition(event.clientX, event.clientY) || !raycaster) {
    return
  }

  brushParameters.brushDirection.value.copy(raycaster.ray.direction).normalize()
  brushParameters.brushOrigin.value.copy(raycaster.ray.origin)
}

function coerceStoredTextAnnotations(rawValue: unknown) {
  if (!Array.isArray(rawValue)) {
    return [] as StoredTextAnnotation[]
  }

  try {
    return rawValue.flatMap((entry, index) => {
      if (!entry || typeof entry !== 'object') {
        return [] as StoredTextAnnotation[]
      }

      const record = entry as Partial<StoredTextAnnotation>
      if (!Array.isArray(record.position) || record.position.length !== 3) {
        return [] as StoredTextAnnotation[]
      }

      const position = record.position.map((value) => Number(value)) as [number, number, number]
      if (position.some((value) => !Number.isFinite(value))) {
        return [] as StoredTextAnnotation[]
      }

      const text = typeof record.text === 'string' ? record.text.trim() : ''
      const edgeColor = typeof record.edgeColor === 'string' && record.edgeColor.trim() ? record.edgeColor : props.annotationEdgeColor
      if (!text) {
        return [] as StoredTextAnnotation[]
      }

      return [{
        id: typeof record.id === 'string' && record.id.trim() ? record.id : `point-annotation-${index + 1}`,
        text,
        position,
        edgeColor,
      }]
    })
  } catch {
    return [] as StoredTextAnnotation[]
  }
}

function createRuntimePointAnnotation(annotation: StoredTextAnnotation): TextAnnotation {
  return {
    ...annotation,
    screenX: 0,
    screenY: 0,
    visible: false,
  }
}

function createRuntimePointAnnotations(storedAnnotations: StoredTextAnnotation[]) {
  return storedAnnotations.map(createRuntimePointAnnotation)
}

function persistPointAnnotations() {
  markConfigDirty()
}

function findPointAnnotation(annotationId: string) {
  return pointAnnotations.value.find((annotation) => annotation.id === annotationId) ?? null
}

function createPointAnnotation(text: string, position: [number, number, number]) {
  const annotationId = `point-annotation-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  pointAnnotations.value.push({
    id: annotationId,
    text,
    position,
    edgeColor: props.annotationEdgeColor,
    screenX: 0,
    screenY: 0,
    visible: true,
  })
  selectPointAnnotation(annotationId)
  updatePointAnnotationScreenPositions()
  persistPointAnnotations()
}

function updatePointAnnotationText(annotationId: string, text: string) {
  const annotation = findPointAnnotation(annotationId)
  if (!annotation) {
    return false
  }

  annotation.text = text
  persistPointAnnotations()
  return true
}

function updatePointAnnotationPosition(annotationId: string, point: THREE.Vector3) {
  const annotation = findPointAnnotation(annotationId)
  if (!annotation) {
    return false
  }

  annotation.position = [point.x, point.y, point.z]
  updatePointAnnotationScreenPositions()
  return true
}

function updatePointAnnotationColor(annotationId: string, nextColor: string) {
  const annotation = findPointAnnotation(annotationId)
  if (!annotation) {
    return false
  }

  annotation.edgeColor = nextColor
  persistPointAnnotations()
  emit('annotation-selection-color-change', nextColor)
  return true
}

function removePointAnnotation(annotationId: string) {
  const nextAnnotations = pointAnnotations.value.filter((annotation) => annotation.id !== annotationId)
  if (nextAnnotations.length === pointAnnotations.value.length) {
    return false
  }

  pointAnnotations.value = nextAnnotations
  if (selectedPointId.value === annotationId) {
    selectedPointId.value = null
    emit('annotation-selection-color-change', null)
  }
  persistPointAnnotations()
  return true
}

function syncSelectionColorState() {
  emit('annotation-selection-color-change', selectedPointId.value ? findPointAnnotation(selectedPointId.value)?.edgeColor ?? null : null)
  emit('cube-selection-color-change', selectedCubeId.value ? findCubeMarker(selectedCubeId.value)?.edgeColor ?? null : null)
}

function selectPointAnnotation(annotationId: string | null) {
  selectedPointId.value = annotationId
  if (annotationId) {
    selectedCubeId.value = null
    for (const cube of cubeMarkers.value) {
      syncCubeMarkerVisual(cube)
    }
  }
  syncSelectionColorState()
}

function updatePointAnnotationScreenPositions() {
  if (!camera || !canvasRef.value) {
    return
  }

  const width = canvasRef.value.clientWidth || window.innerWidth
  const height = canvasRef.value.clientHeight || window.innerHeight
  for (const annotation of pointAnnotations.value) {
    scratchProjectionPoint.set(...annotation.position).project(camera)
    annotation.visible =
      scratchProjectionPoint.z >= -1 &&
      scratchProjectionPoint.z <= 1 &&
      Math.abs(scratchProjectionPoint.x) <= 1.15 &&
      Math.abs(scratchProjectionPoint.y) <= 1.15
    annotation.screenX = (scratchProjectionPoint.x * 0.5 + 0.5) * width
    annotation.screenY = (-scratchProjectionPoint.y * 0.5 + 0.5) * height
  }
}

function beginPointAnnotationDrag(annotationId: string, event: PointerEvent) {
  if (event.button !== 0 || event.detail > 1 || selectedPointId.value !== annotationId) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  activeAnnotationDragId.value = annotationId
  activeAnnotationDragPointerId = event.pointerId
  pointDragDidMove = false
  setSceneInteractionEnabled(false)
}

function updatePointAnnotationDrag(event: PointerEvent) {
  if (!activeAnnotationDragId.value || activeAnnotationDragPointerId !== event.pointerId) {
    return
  }

  const point = pickModelPointFromClientPosition(event.clientX, event.clientY)
  if (!point) {
    return
  }

  event.preventDefault()
  pointDragDidMove = true
  updatePointAnnotationPosition(activeAnnotationDragId.value, point)
}

function stopPointAnnotationDrag(event?: PointerEvent) {
  if (event && activeAnnotationDragPointerId !== event.pointerId) {
    return
  }

  if (!activeAnnotationDragId.value) {
    return
  }

  activeAnnotationDragId.value = null
  activeAnnotationDragPointerId = null
  setSceneInteractionEnabled(true)
  if (pointDragDidMove) {
    persistPointAnnotations()
    emit('status', '标注位置已更新')
  }
  pointDragDidMove = false
}

function cancelPointAnnotationDrag() {
  activeAnnotationDragId.value = null
  activeAnnotationDragPointerId = null
  pointDragDidMove = false
  setSceneInteractionEnabled(true)
}

function updateLineMaterialResolution(material: LineMaterial) {
  const width = canvasRef.value?.clientWidth || window.innerWidth
  const height = canvasRef.value?.clientHeight || window.innerHeight
  material.resolution.set(width, height)
}

function updateCubeLineResolutions() {
  if (previewCube) {
    updateLineMaterialResolution(previewCube.edges.material)
  }

  for (const cube of cubeMarkers.value) {
    updateLineMaterialResolution(cube.edges.material)
  }
}

function createCubeVisual(edgeColor: string, fillOpacity: number) {
  const geometry = new THREE.BoxGeometry(1, 1, 1)
  const fillMaterial = new THREE.MeshBasicMaterial({
    color: new THREE.Color(edgeColor),
    transparent: true,
    opacity: fillOpacity,
    depthWrite: false,
    depthTest: true,
    side: THREE.DoubleSide,
    toneMapped: false,
  })
  const mesh = new THREE.Mesh(geometry, fillMaterial)
  mesh.renderOrder = 10

  const edgesGeometry = new THREE.EdgesGeometry(geometry)
  const edgeMaterial = new LineMaterial({
    color: new THREE.Color(edgeColor),
    linewidth: 3.4,
    transparent: true,
    opacity: 0.96,
    depthTest: true,
    toneMapped: false,
  })
  updateLineMaterialResolution(edgeMaterial)
  const edgeLineGeometry = new LineSegmentsGeometry()
  edgeLineGeometry.setPositions(Array.from(edgesGeometry.getAttribute('position').array as ArrayLike<number>))
  edgesGeometry.dispose()
  const edges = new LineSegments2(edgeLineGeometry, edgeMaterial)
  edges.computeLineDistances()
  edges.renderOrder = 11

  return {
    mesh,
    edges,
  }
}

function disposeCubeVisual(visual: CubeMarkerVisual) {
  visual.mesh.geometry.dispose()
  visual.mesh.material.dispose()
  visual.edges.geometry.dispose()
  visual.edges.material.dispose()
}

function coerceStoredCubeMarkers(rawValue: unknown) {
  if (!Array.isArray(rawValue)) {
    return [] as StoredCubeMarker[]
  }

  try {
    return rawValue.flatMap((entry, index) => {
      if (!entry || typeof entry !== 'object') {
        return [] as StoredCubeMarker[]
      }

      const record = entry as Partial<StoredCubeMarker>
      if (!Array.isArray(record.center) || record.center.length !== 3) {
        return [] as StoredCubeMarker[]
      }

      const center = record.center.map((value) => Number(value)) as [number, number, number]
      const size = Number(record.size)
      const edgeColor = typeof record.edgeColor === 'string' && record.edgeColor.trim() ? record.edgeColor : props.cubeEdgeColor
      if (center.some((value) => !Number.isFinite(value)) || !Number.isFinite(size) || size <= 0) {
        return [] as StoredCubeMarker[]
      }

      return [{
        id: typeof record.id === 'string' && record.id.trim() ? record.id : `cube-marker-${index + 1}`,
        center,
        size,
        edgeColor,
        annotationText: typeof record.annotationText === 'string' ? record.annotationText.trim() : '',
      }]
    })
  } catch {
    return [] as StoredCubeMarker[]
  }
}

function persistCubeMarkers() {
  markConfigDirty()
}

function findCubeMarker(cubeId: string) {
  return cubeMarkers.value.find((cube) => cube.id === cubeId) ?? null
}

function syncCubeMarkerVisual(cube: CubeMarker) {
  const isSelected = selectedCubeId.value === cube.id
  cube.mesh.position.set(...cube.center)
  cube.edges.position.set(...cube.center)
  cube.mesh.scale.setScalar(cube.size)
  cube.edges.scale.setScalar(cube.size)
  cube.mesh.material.color.set(cube.edgeColor)
  cube.mesh.material.opacity = CUBE_FILL_OPACITY
  cube.edges.material.color.set(isSelected ? '#FFFFFF' : cube.edgeColor)
  cube.edges.material.opacity = isSelected ? 1 : 0.96
}

function selectCube(cubeId: string | null) {
  selectedCubeId.value = cubeId
  if (cubeId) {
    selectedPointId.value = null
  }
  for (const cube of cubeMarkers.value) {
    syncCubeMarkerVisual(cube)
  }
  syncSelectionColorState()
}

function createRuntimeCubeMarker(stored: StoredCubeMarker) {
  if (!scene) {
    return null
  }

  const visual = createCubeVisual(stored.edgeColor, CUBE_FILL_OPACITY)
  visual.mesh.userData.cubeMarkerId = stored.id
  visual.edges.userData.cubeMarkerId = stored.id
  scene.add(visual.mesh)
  scene.add(visual.edges)

  const cube: CubeMarker = {
    ...stored,
    mesh: markRaw(visual.mesh),
    edges: markRaw(visual.edges),
    labelScreenX: 0,
    labelScreenY: 0,
    labelVisible: false,
  }
  syncCubeMarkerVisual(cube)
  return cube
}

function disposeAllCubeMarkers() {
  for (const cube of cubeMarkers.value) {
    cube.mesh.removeFromParent()
    cube.edges.removeFromParent()
    disposeCubeVisual(cube)
  }
  cubeMarkers.value = []
  selectCube(null)
}

function hydrateCubeMarkers(storedMarkers: StoredCubeMarker[]) {
  disposeAllCubeMarkers()
  cubeMarkers.value = storedMarkers.map((stored) => createRuntimeCubeMarker(stored)).filter((cube): cube is CubeMarker => Boolean(cube))
  updateCubeMarkerScreenPositions()
}

function createCubeFromPoints(startPoint: THREE.Vector3, endPoint: THREE.Vector3, edgeColor: string) {
  const size = Math.max(
    Math.abs(endPoint.x - startPoint.x),
    Math.abs(endPoint.y - startPoint.y),
    Math.abs(endPoint.z - startPoint.z),
    getMinimumCubeSize(),
  )

  scratchCubeOffset.addVectors(startPoint, endPoint).multiplyScalar(0.5)
  return {
    id: `cube-marker-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    center: [scratchCubeOffset.x, scratchCubeOffset.y, scratchCubeOffset.z] as [number, number, number],
    size,
    edgeColor,
    annotationText: '',
  }
}

function updateCubeMarkerScreenPositions() {
  if (!camera || !canvasRef.value) {
    return
  }

  const width = canvasRef.value.clientWidth || window.innerWidth
  const height = canvasRef.value.clientHeight || window.innerHeight
  for (const cube of cubeMarkers.value) {
    scratchProjectionPoint
      .set(cube.center[0], cube.center[1] + cube.size * CUBE_LABEL_OFFSET_FACTOR, cube.center[2])
      .project(camera)

    cube.labelVisible =
      Boolean(cube.annotationText?.trim()) &&
      scratchProjectionPoint.z >= -1 &&
      scratchProjectionPoint.z <= 1 &&
      Math.abs(scratchProjectionPoint.x) <= 1.15 &&
      Math.abs(scratchProjectionPoint.y) <= 1.15

    cube.labelScreenX = (scratchProjectionPoint.x * 0.5 + 0.5) * width
    cube.labelScreenY = (-scratchProjectionPoint.y * 0.5 + 0.5) * height
  }
}

function ensurePreviewCube() {
  if (previewCube || !scene) {
    return previewCube
  }

  previewCube = createCubeVisual(props.cubeEdgeColor, CUBE_PREVIEW_OPACITY)
  previewCube.mesh.visible = false
  previewCube.edges.visible = false
  scene.add(previewCube.mesh)
  scene.add(previewCube.edges)
  return previewCube
}

function hidePreviewCube() {
  if (!previewCube) {
    return
  }

  previewCube.mesh.visible = false
  previewCube.edges.visible = false
}

function disposePreviewCube() {
  if (!previewCube) {
    return
  }

  previewCube.mesh.removeFromParent()
  previewCube.edges.removeFromParent()
  disposeCubeVisual(previewCube)
  previewCube = null
}

function updatePreviewCube(startPoint: THREE.Vector3, endPoint: THREE.Vector3) {
  const preview = ensurePreviewCube()
  if (!preview) {
    return
  }

  const stored = createCubeFromPoints(startPoint, endPoint, props.cubeEdgeColor)
  preview.mesh.visible = true
  preview.edges.visible = true
  preview.mesh.position.set(...stored.center)
  preview.edges.position.set(...stored.center)
  preview.mesh.scale.setScalar(stored.size)
  preview.edges.scale.setScalar(stored.size)
  preview.mesh.material.color.set(stored.edgeColor)
  preview.edges.material.color.set(stored.edgeColor)
}

function updateSelectedCubeColor(nextColor: string) {
  const selectedCube = selectedCubeId.value ? findCubeMarker(selectedCubeId.value) : null
  if (!selectedCube) {
    if (previewCube) {
      previewCube.mesh.material.color.set(nextColor)
      previewCube.edges.material.color.set(nextColor)
    }
    return
  }

  selectedCube.edgeColor = nextColor
  syncCubeMarkerVisual(selectedCube)
  persistCubeMarkers()
}

function removeCubeMarker(cubeId: string) {
  const cube = findCubeMarker(cubeId)
  if (!cube) {
    return false
  }

  cube.mesh.removeFromParent()
  cube.edges.removeFromParent()
  disposeCubeVisual(cube)
  cubeMarkers.value = cubeMarkers.value.filter((item) => item.id !== cubeId)
  if (selectedCubeId.value === cubeId) {
    selectedCubeId.value = null
    syncSelectionColorState()
  }
  persistCubeMarkers()
  return true
}

function clearCubeAnnotationText(cubeId: string) {
  const cube = findCubeMarker(cubeId)
  if (!cube) {
    return false
  }

  cube.annotationText = ''
  updateCubeMarkerScreenPositions()
  persistCubeMarkers()
  return true
}

function updateCubeAnnotationText(cubeId: string, text: string) {
  const cube = findCubeMarker(cubeId)
  if (!cube) {
    return false
  }

  cube.annotationText = text
  updateCubeMarkerScreenPositions()
  persistCubeMarkers()
  return true
}

function openAnnotationDialog(target: AnnotationDialogTarget, title: string, initialText: string) {
  annotationDialogTarget.value = target
  annotationDialogTitle.value = title
  annotationDialogText.value = initialText
  annotationDialogVisible.value = true
  setSceneInteractionEnabled(false)
  void nextTick(() => {
    annotationDialogInputRef.value?.focus()
    annotationDialogInputRef.value?.select()
  })
}

function closeAnnotationDialog() {
  annotationDialogVisible.value = false
  annotationDialogTarget.value = null
  annotationDialogTitle.value = ''
  annotationDialogText.value = ''
  setSceneInteractionEnabled(true)
}

function canDeleteAnnotationDialogTarget() {
  const target = annotationDialogTarget.value
  if (!target) {
    return false
  }

  if (target.kind === 'edit-point') {
    return true
  }

  if (target.kind === 'cube-label') {
    return Boolean(findCubeMarker(target.cubeId)?.annotationText?.trim())
  }

  return false
}

function openPointAnnotationCreateDialog(point: THREE.Vector3) {
  setAnnotationPlacementActive(false)
  openAnnotationDialog(
    { kind: 'create-point', position: [point.x, point.y, point.z] },
    '新增气泡标注',
    `标注 ${pointAnnotations.value.length + 1}`,
  )
  emit('status', '请填写气泡标注内容')
}

function openPointAnnotationEditDialog(annotationId: string) {
  const annotation = findPointAnnotation(annotationId)
  if (!annotation) {
    return
  }

  selectPointAnnotation(annotationId)
  openAnnotationDialog({ kind: 'edit-point', id: annotationId }, '编辑气泡标注', annotation.text)
}

function openCubeAnnotationDialog(cubeId: string) {
  const cube = findCubeMarker(cubeId)
  if (!cube) {
    return
  }

  selectCube(cubeId)
  openAnnotationDialog({ kind: 'cube-label', cubeId }, '编辑立方体标注', cube.annotationText || '')
}

function submitAnnotationDialog() {
  const target = annotationDialogTarget.value
  if (!target) {
    return
  }

  const text = annotationDialogText.value.trim()
  if (!text) {
    emit('status', '标注内容不能为空')
    return
  }

  if (target.kind === 'create-point') {
    createPointAnnotation(text, target.position)
    emit('status', '已创建气泡标注')
    closeAnnotationDialog()
    return
  }

  if (target.kind === 'edit-point') {
    if (updatePointAnnotationText(target.id, text)) {
      emit('status', '已更新气泡标注文本')
    }
    closeAnnotationDialog()
    return
  }

  if (updateCubeAnnotationText(target.cubeId, text)) {
    emit('status', '已更新立方体标注文本')
  }
  closeAnnotationDialog()
}

function deleteAnnotationDialogTarget() {
  const target = annotationDialogTarget.value
  if (!target) {
    return
  }

  if (target.kind === 'edit-point') {
    if (removePointAnnotation(target.id)) {
      emit('status', '已删除气泡标注')
    }
    closeAnnotationDialog()
    return
  }

  if (target.kind === 'cube-label') {
    if (clearCubeAnnotationText(target.cubeId)) {
      emit('status', '已删除立方体标注文本')
    }
    closeAnnotationDialog()
  }
}

function handlePointAnnotationEdit(annotationId: string, event?: Event) {
  event?.preventDefault()
  event?.stopPropagation()
  openPointAnnotationEditDialog(annotationId)
}

function handlePointAnnotationAnchorClick(annotationId: string, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (event.detail === 2) {
    selectPointAnnotation(annotationId)
  }
}

function handlePointAnnotationBubbleClick(annotationId: string, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()

  if (event.detail === 2) {
    selectPointAnnotation(annotationId)
    return
  }

  if (event.detail === 3) {
    openPointAnnotationEditDialog(annotationId)
  }
}

function handlePointAnnotationSelect(annotationId: string, event?: Event) {
  event?.preventDefault()
  event?.stopPropagation()
  selectPointAnnotation(annotationId)
}

function handlePointAnnotationDelete(annotationId: string, event?: Event) {
  event?.preventDefault()
  event?.stopPropagation()
  if (removePointAnnotation(annotationId)) {
    emit('status', '已删除气泡标注')
  }
}

function handleCubeAnnotationEdit(cubeId: string, event?: Event) {
  event?.preventDefault()
  event?.stopPropagation()
  openCubeAnnotationDialog(cubeId)
}

function handleCubeLabelDoubleClick(cubeId: string, event?: MouseEvent) {
  event?.preventDefault()
  event?.stopPropagation()

  if (selectedCubeId.value === cubeId) {
    openCubeAnnotationDialog(cubeId)
    return
  }

  selectCube(cubeId)
}

function handleCubeAnnotationDelete(cubeId: string, event?: Event) {
  event?.preventDefault()
  event?.stopPropagation()
  if (clearCubeAnnotationText(cubeId)) {
    emit('status', '已删除立方体标注文本')
  }
}

function pickCubeFromClientPosition(clientX: number, clientY: number) {
  if (!setRayFromClientPosition(clientX, clientY) || !raycaster || cubeMarkers.value.length === 0) {
    return null
  }

  const intersections = raycaster.intersectObjects(cubeMarkers.value.map((cube) => cube.mesh), false)
  const hit = intersections[0]
  if (!hit) {
    return null
  }

  const cubeId = hit.object.userData.cubeMarkerId as string | undefined
  if (!cubeId) {
    return null
  }

  return {
    cube: findCubeMarker(cubeId),
    point: hit.point.clone(),
  }
}

function beginCubeDrawing(event: PointerEvent, anchorPoint: THREE.Vector3) {
  activeCubeDrawPointerId = event.pointerId
  cubeDrawAnchorPoint = anchorPoint.clone()
  cubeDragDidMove = false
  setSceneInteractionEnabled(false)
  updatePreviewCube(anchorPoint, anchorPoint)
}

function updateCubeDrawing(event: PointerEvent) {
  if (!cubeDrawAnchorPoint || activeCubeDrawPointerId !== event.pointerId) {
    return
  }

  const dragPoint = pickPlanePointFromClientPosition(event.clientX, event.clientY, cubeDrawAnchorPoint)
  if (!dragPoint) {
    return
  }

  event.preventDefault()
  cubeDragDidMove = true
  updatePreviewCube(cubeDrawAnchorPoint, dragPoint)
}

function finishCubeDrawing(event?: PointerEvent) {
  if (event && activeCubeDrawPointerId !== event.pointerId) {
    return
  }

  if (!cubeDrawAnchorPoint || activeCubeDrawPointerId === null) {
    return
  }

  let finalPoint = cubeDrawAnchorPoint.clone()
  if (event) {
    finalPoint = pickPlanePointFromClientPosition(event.clientX, event.clientY, cubeDrawAnchorPoint) ?? finalPoint
  }

  const stored = createCubeFromPoints(cubeDrawAnchorPoint, finalPoint, props.cubeEdgeColor)
  if (scene) {
    const cube = createRuntimeCubeMarker(stored)
    if (cube) {
      cubeMarkers.value.push(cube)
      selectCube(cube.id)
      persistCubeMarkers()
      updateCubeMarkerScreenPositions()
      emit('status', '已创建立方体标记，可拖拽重新放置')
    }
  }

  cubeDrawAnchorPoint = null
  activeCubeDrawPointerId = null
  hidePreviewCube()
  setSceneInteractionEnabled(true)
  setCubePlacementActive(false)
}

function cancelCubeDrawing() {
  cubeDrawAnchorPoint = null
  activeCubeDrawPointerId = null
  cubeDragDidMove = false
  hidePreviewCube()
  setSceneInteractionEnabled(true)
}

function beginCubeDrag(cubeId: string, event: PointerEvent) {
  if (event.button !== 0 || event.detail > 1 || selectedCubeId.value !== cubeId) {
    return
  }

  const cube = findCubeMarker(cubeId)
  if (!cube) {
    return
  }

  const dragPoint = pickPlanePointFromClientPosition(
    event.clientX,
    event.clientY,
    scratchCubePoint.set(cube.center[0], cube.center[1], cube.center[2]),
  )
  if (!dragPoint) {
    return
  }

  event.preventDefault()
  event.stopPropagation()
  activeCubeDragId.value = cubeId
  activeCubeDragPointerId = event.pointerId
  cubeDragDidMove = false
  cubeDragPlaneAnchor = new THREE.Vector3(cube.center[0], cube.center[1], cube.center[2])
  cubeDragOffset.set(cube.center[0], cube.center[1], cube.center[2]).sub(dragPoint)
  setSceneInteractionEnabled(false)
}

function updateCubeDrag(event: PointerEvent) {
  if (!activeCubeDragId.value || activeCubeDragPointerId !== event.pointerId) {
    return
  }

  const cube = findCubeMarker(activeCubeDragId.value)
  if (!cube) {
    return
  }

  const currentCenter = scratchCubePoint.set(cube.center[0], cube.center[1], cube.center[2])
  const dragPoint = pickPlanePointFromClientPosition(event.clientX, event.clientY, cubeDragPlaneAnchor ?? currentCenter)
  if (!dragPoint) {
    return
  }

  event.preventDefault()
  cubeDragDidMove = true
  dragPoint.add(cubeDragOffset)
  cube.center = [dragPoint.x, dragPoint.y, dragPoint.z]
  syncCubeMarkerVisual(cube)
  updateCubeMarkerScreenPositions()
}

function stopCubeDrag(event?: PointerEvent) {
  if (event && activeCubeDragPointerId !== event.pointerId) {
    return
  }

  if (!activeCubeDragId.value) {
    return
  }

  activeCubeDragId.value = null
  activeCubeDragPointerId = null
  cubeDragPlaneAnchor = null
  setSceneInteractionEnabled(true)
  if (cubeDragDidMove) {
    persistCubeMarkers()
    emit('status', '立方体位置已更新')
  }
  cubeDragDidMove = false
}

function cancelCubeDrag() {
  activeCubeDragId.value = null
  activeCubeDragPointerId = null
  cubeDragDidMove = false
  cubeDragPlaneAnchor = null
  setSceneInteractionEnabled(true)
}

function nudgeActiveCubeDragDepth(deltaY: number) {
  if (!activeCubeDragId.value || !cubeDragPlaneAnchor || !camera) {
    return
  }

  const cube = findCubeMarker(activeCubeDragId.value)
  if (!cube) {
    return
  }

  const deltaDistance = deltaY * Math.max(modelRadius, 0.25) * CUBE_DRAG_WHEEL_DEPTH_FACTOR
  camera.getWorldDirection(scratchViewDirection)
  scratchViewDirection.normalize().multiplyScalar(deltaDistance)
  cubeDragPlaneAnchor.add(scratchViewDirection)
  cube.center = [
    cube.center[0] + scratchViewDirection.x,
    cube.center[1] + scratchViewDirection.y,
    cube.center[2] + scratchViewDirection.z,
  ]
  cubeDragDidMove = true
  syncCubeMarkerVisual(cube)
  updateCubeMarkerScreenPositions()
}

function deleteSelectedOverlay() {
  if (selectedPointId.value && removePointAnnotation(selectedPointId.value)) {
    emit('status', '已删除气泡标注')
    return
  }

  if (selectedCubeId.value && removeCubeMarker(selectedCubeId.value)) {
    emit('status', '已删除立方体标记')
  }
}

function isEditableTarget(target: EventTarget | null) {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || (target instanceof HTMLElement && target.isContentEditable)
}

function stopAllOverlayInteractions(event?: PointerEvent) {
  stopPointAnnotationDrag(event)
  finishCubeDrawing(event)
  stopCubeDrag(event)
}

function cancelAllOverlayInteractions() {
  cancelPointAnnotationDrag()
  cancelCubeDrawing()
  cancelCubeDrag()
}

function revealDyno() {
  return dyno.dynoBlock({ gsplat: dyno.Gsplat }, { gsplat: dyno.Gsplat }, ({ gsplat }) => {
    const reveal = new dyno.Dyno({
      inTypes: { gsplat: dyno.Gsplat, t: 'float' },
      outTypes: { gsplat: dyno.Gsplat },
      statements: ({ inputs, outputs }) => dyno.unindentLines(`
        ${outputs.gsplat} = ${inputs.gsplat};
        float t = ${inputs.t};
        vec3 localPos = ${inputs.gsplat}.center;
        vec3 scales = ${inputs.gsplat}.scales;
        float radialDistance = length(localPos.xz);
        float tt = t * t * 0.4 + 0.5;
        float innerReveal = clamp(tt - 1.0 - radialDistance * 2.0, 0.0, 1.0);
        float outerReveal = clamp(tt - 7.0 - radialDistance * 2.5, 0.0, 1.0);
        float reveal = max(innerReveal * 0.2, outerReveal);
        ${outputs.gsplat}.scales = mix(vec3(${REVEAL_MIN_SCALE.toFixed(4)}), scales, reveal);
      `),
    })

    return {
      gsplat: reveal.apply({ gsplat, t: revealParameters.progress }).gsplat,
    }
  })
}

function startRevealAnimation() {
  revealParameters.progress.value = 0
  revealStartTime = performance.now()
  revealActive = true
  currentMesh?.updateVersion()
}

function updateRevealAnimation() {
  if (!currentMesh || !revealActive) {
    return
  }

  const elapsedSeconds = (performance.now() - revealStartTime) / 1000
  const normalized = Math.min(elapsedSeconds / REVEAL_DURATION_SECONDS, 1)
  const eased = 1 - (1 - normalized) ** 3
  const nextProgress = eased * REVEAL_EFFECT_END

  if (Math.abs(revealParameters.progress.value - nextProgress) > 0.0001) {
    revealParameters.progress.value = nextProgress
    currentMesh.updateVersion()
  }

  if (normalized >= 1) {
    revealActive = false
    if (revealParameters.progress.value !== REVEAL_EFFECT_END) {
      revealParameters.progress.value = REVEAL_EFFECT_END
      currentMesh.updateVersion()
    }
  }
}

function resizeRenderer() {
  if (!renderer || !camera || !canvasRef.value) {
    return
  }

  const width = canvasRef.value.clientWidth || window.innerWidth
  const height = canvasRef.value.clientHeight || window.innerHeight
  if (canvasRef.value.width !== width || canvasRef.value.height !== height) {
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }

  updateCubeLineResolutions()
}

function frameCurrentMesh() {
  if (!currentMesh || !camera) {
    return
  }

  const box = currentMesh.getBoundingBox(true)
  const center = box.getCenter(scratchBoundsCenter)
  const size = box.getSize(scratchBoundsSize)
  const maxDimension = Math.max(size.x, size.y, size.z, 0.5)
  const halfFovRadians = THREE.MathUtils.degToRad(camera.fov * 0.5)
  const halfHorizontalFovRadians = Math.atan(Math.tan(halfFovRadians) * camera.aspect)
  const limitingHalfFovRadians = Math.min(halfFovRadians, halfHorizontalFovRadians)
  const worldCenter = currentModelRoot ? scratchWorldCenter.copy(currentModelRoot.position) : center

  modelRadius = Math.max(maxDimension * 0.5, 0.25)
  syncBrushMetrics()

  const fitDistance = (modelRadius / Math.tan(limitingHalfFovRadians)) * CAMERA_FIT_PADDING
  camera.position.copy(worldCenter).add(new THREE.Vector3(0, 0, Math.max(fitDistance, modelRadius * 1.05)))
  camera.near = Math.max(modelRadius / 500, 0.01)
  camera.far = Math.max(modelRadius * 40, 1000)
  camera.lookAt(worldCenter)
  camera.updateProjectionMatrix()
  resetControlInertia()
}

function disposeCurrentMesh() {
  revealActive = false
  revealParameters.progress.value = REVEAL_EFFECT_END
  queuedViewRollRadians = 0
  pendingRotationCompletionMessage = ''
  closeAnnotationDialog()
  cancelAllOverlayInteractions()
  hidePreviewCube()
  disposeAllCubeMarkers()

  if (currentModelRoot && scene) {
    scene.remove(currentModelRoot)
    currentModelRoot.clear()
  } else if (currentMesh && scene) {
    scene.remove(currentMesh)
  }

  if (currentMesh) {
    currentMesh.dispose()
  }

  currentModelRoot = null
  currentMesh = null
  currentOriginalRgba = null
  clearEditHistory()
  activePaintPointerId = null
  currentConfigHandle = null
  currentConfigPath = null
  currentConfigFileName = ''
  currentDefaultView = null
  configDirty = false
  currentProjectInfo.value = createEmptyProjectInfo()
  pointAnnotations.value = []
  selectPointAnnotation(null)
}

function renderCurrentEditableRgba() {
  if (!renderer || !currentMesh || !currentMesh.packedSplats) {
    return null
  }

  const generator = currentMesh.generator as any
  const rgba = new RgbaArray()
  rgba.render({
    renderer,
    count: currentMesh.packedSplats.numSplats,
    reader: dyno.dynoBlock({ index: 'int' }, { rgba8: 'vec4' }, ({ index }) => {
      const { gsplat } = generator.apply({ index })
      const { rgba } = dyno.splitGsplat(gsplat).outputs
      return { rgba8: rgba }
    }),
  })
  return rgba
}

function captureUndoSnapshot() {
  const snapshot = renderCurrentEditableRgba()
  if (!snapshot) {
    return false
  }

  clearRedoHistory()
  pushHistorySnapshot(undoHistory, snapshot)
  return true
}

function undoLastEdit() {
  if (!currentMesh || undoHistory.length === 0) {
    emit('status', '当前没有可撤回的更改')
    return false
  }

  const currentSnapshot = renderCurrentEditableRgba()
  const previousSnapshot = undoHistory.pop()
  if (!currentSnapshot || !previousSnapshot) {
    currentSnapshot?.dispose()
    syncEditHistoryAvailability()
    emit('status', '撤回失败')
    return false
  }

  pushHistorySnapshot(redoHistory, currentSnapshot)

  currentMesh.splatRgba?.dispose()
  currentMesh.splatRgba = previousSnapshot
  currentMesh.updateGenerator()
  syncEditHistoryAvailability()
  emit('status', '已撤回上一次更改')
  return true
}

function redoLastEdit() {
  if (!currentMesh || redoHistory.length === 0) {
    emit('status', '当前没有可重做的更改')
    return false
  }

  const currentSnapshot = renderCurrentEditableRgba()
  const nextSnapshot = redoHistory.pop()
  if (!currentSnapshot || !nextSnapshot) {
    currentSnapshot?.dispose()
    syncEditHistoryAvailability()
    emit('status', '重做失败')
    return false
  }

  pushHistorySnapshot(undoHistory, currentSnapshot)

  currentMesh.splatRgba?.dispose()
  currentMesh.splatRgba = nextSnapshot
  currentMesh.updateGenerator()
  syncEditHistoryAvailability()
  emit('status', '已重做上一次撤回的更改')
  return true
}

function bakeCurrentBrushStroke() {
  if (!currentMesh) {
    return
  }

  const newRgba = renderCurrentEditableRgba()
  if (!newRgba) {
    return
  }

  currentMesh.splatRgba?.dispose()
  currentMesh.splatRgba = newRgba
  currentMesh.updateGenerator()
}

function createPaintableMesh(fileBytes: Uint8Array, fileName: string) {
  currentOriginalRgba = new RgbaArray()

  return new SplatMesh({
    fileBytes,
    fileName,
    onFrame: ({ mesh }) => {
      mesh.needsUpdate = true
    },
    onLoad: (mesh) => {
      if (!renderer || !mesh.packedSplats) {
        return
      }

      mesh.worldModifier = brushDyno(currentOriginalRgba!.dyno)
      mesh.splatRgba = new RgbaArray().fromPackedSplats({
        packedSplats: mesh.packedSplats,
        base: 0,
        count: mesh.packedSplats.numSplats,
        renderer,
      })
      mesh.updateGenerator()
    },
  })
}

async function loadFile(file: File) {
  if (!scene || !renderer) {
    return
  }

  emit('status', `正在解析 ${file.name}`)
  disposeCurrentMesh()

  currentFile = file
  currentFileBaseName = file.name.replace(/\.[^.]+$/, '') || 'painted-splat'
  currentFileBytes = new Uint8Array(await file.arrayBuffer())
  const nextMesh = createPaintableMesh(currentFileBytes, file.name)
  const nextModelRoot = new THREE.Group()
  nextModelRoot.rotation.x = MODEL_VERTICAL_FLIP_RADIANS
  nextModelRoot.add(nextMesh)
  currentModelRoot = nextModelRoot
  currentMesh = nextMesh
  scene.add(nextModelRoot)

  try {
    await nextMesh.initialized
    if (!renderer || !nextMesh.packedSplats) {
      throw new Error('模型未能正确初始化')
    }

    currentOriginalRgba = new RgbaArray()
    currentOriginalRgba.fromPackedSplats({
      packedSplats: nextMesh.packedSplats,
      base: 0,
      count: nextMesh.packedSplats.numSplats,
      renderer,
    })
    const modelCenter = nextMesh.getBoundingBox(true).getCenter(new THREE.Vector3())
    nextMesh.position.copy(modelCenter).multiplyScalar(-1)
    nextModelRoot.position.copy(modelCenter)
    nextMesh.objectModifier = revealDyno()
    nextMesh.worldModifier = brushDyno(currentOriginalRgba.dyno)
    nextMesh.updateGenerator()

    const viewerConfig = await ensureViewerConfigFile(file)
    currentProjectInfo.value = coerceProjectInfoConfig(viewerConfig.projectInfo)
    pointAnnotations.value = createRuntimePointAnnotations(viewerConfig.pointAnnotations)
    hydrateCubeMarkers(viewerConfig.cubeMarkers)
    const preferredViewState = applyPreferredView()
    updatePointAnnotationScreenPositions()
    updateCubeMarkerScreenPositions()
    startRevealAnimation()
    emit('project-info-loaded', cloneProjectInfo(currentProjectInfo.value))
    emit('loaded', { fileName: file.name, splatCount: nextMesh.packedSplats.numSplats })
    if (preferredViewState === 'default') {
      emit('status', '已应用该模型保存的默认视角')
    }
  } catch (error) {
    disposeCurrentMesh()
    emit('failed', error instanceof Error ? error.message : '模型载入失败')
  }
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function getExportConfigFileName(exportModelFileName: string) {
  return exportModelFileName.replace(/\.[^.]+$/, VIEWER_CONFIG_SUFFIX)
}

function getExportConfigFilePath(exportModelPath: string) {
  return exportModelPath.replace(/\.[^.]+$/, VIEWER_CONFIG_SUFFIX)
}

async function exportCurrentMesh() {
  if (!renderer || !currentMesh || !currentMesh.packedSplats) {
    emit('export-failed', '当前没有可导出的模型')
    return
  }

  try {
    const generator = currentMesh.generator as any
    const rgba = new RgbaArray()
    rgba.render({
      renderer,
      count: currentMesh.packedSplats.numSplats,
      reader: dyno.dynoBlock({ index: 'int' }, { rgba8: 'vec4' }, ({ index }) => {
        const { gsplat } = generator.apply({ index })
        const { rgba } = dyno.splitGsplat(gsplat).outputs
        return { rgba8: rgba }
      }),
    })

    const rgbaBytes = await rgba.read()
    const sourceSplats = currentMesh.packedSplats
    const sourcePackedArray = sourceSplats.packedArray
    if (!sourcePackedArray) {
      throw new Error('当前模型缺少 packed splat 缓冲区')
    }

    let visibleCount = 0
    for (let index = 0; index < sourceSplats.numSplats; index += 1) {
      if (rgbaBytes[index * 4 + 3] > 0) {
        visibleCount += 1
      }
    }

    const bakedSplats = new PackedSplats({
      maxSplats: visibleCount,
      splatEncoding: sourceSplats.splatEncoding,
    })

    for (let index = 0; index < sourceSplats.numSplats; index += 1) {
      const rgbaOffset = index * 4
      const opacity = rgbaBytes[rgbaOffset + 3] / 255
      if (opacity === 0) {
        continue
      }

      const unpacked = unpackSplat(sourcePackedArray, index, sourceSplats.splatEncoding)
      unpacked.color.r = rgbaBytes[rgbaOffset + 0] / 255
      unpacked.color.g = rgbaBytes[rgbaOffset + 1] / 255
      unpacked.color.b = rgbaBytes[rgbaOffset + 2] / 255
      unpacked.opacity = opacity

      bakedSplats.pushSplat(unpacked.center, unpacked.scales, unpacked.quaternion, unpacked.opacity, unpacked.color)
    }

    const writer = new SpzWriter({
      numSplats: visibleCount,
      shDegree: 0,
      fractionalBits: 12,
      flagAntiAlias: true,
    })

    const bakedPackedArray = bakedSplats.packedArray
    if (!bakedPackedArray) {
      throw new Error('导出过程中未能生成 packed splat 缓冲区')
    }

    for (let index = 0; index < visibleCount; index += 1) {
      const unpacked = unpackSplat(bakedPackedArray, index, bakedSplats.splatEncoding)
      writer.setCenter(index, unpacked.center.x, unpacked.center.y, unpacked.center.z)
      writer.setScale(index, unpacked.scales.x, unpacked.scales.y, unpacked.scales.z)
      writer.setQuat(index, unpacked.quaternion.x, unpacked.quaternion.y, unpacked.quaternion.z, unpacked.quaternion.w)
      writer.setAlpha(index, unpacked.opacity)
      writer.setRgb(index, unpacked.color.r, unpacked.color.g, unpacked.color.b)
    }

    const spzBytes = await writer.finalize()
    const exportBytes = new Uint8Array(spzBytes)
    const exportName = `${currentFileBaseName}-painted.spz`
    const configContents = serializeViewerConfig(createViewerConfigSnapshot())

    if (props.filePath) {
      const exportPath = await pickDesktopExportPath(exportName)
      if (!exportPath) {
        emit('export-failed', '已取消导出')
        return
      }

      await writeDesktopBinaryFile(exportPath, exportBytes)
      await writeDesktopTextFile(getExportConfigFilePath(exportPath), configContents)

      emit('exported', {
        fileName: getFileNameFromPath(exportPath),
        splatCount: visibleCount,
        clippedCount: writer.clippedCount,
        sphericalHarmonicsDegree: 0,
      })
      return
    }

    const configName = getExportConfigFileName(exportName)
    downloadBlob(new Blob([exportBytes], { type: 'application/octet-stream' }), exportName)
    downloadBlob(new Blob([configContents], { type: 'application/json' }), configName)

    emit('exported', {
      fileName: exportName,
      splatCount: visibleCount,
      clippedCount: writer.clippedCount,
      sphericalHarmonicsDegree: 0,
    })
  } catch (error) {
    emit('export-failed', error instanceof Error ? error.message : '导出 SPZ 失败')
  }
}

function onPointerDownCapture(event: PointerEvent) {
  if (annotationDialogVisible.value || props.sceneInteractionLocked) {
    return
  }

  if (props.cubePlacementActive && event.button === 0 && currentMesh) {
    event.preventDefault()
    event.stopImmediatePropagation()
    const anchorPoint = pickModelPointFromClientPosition(event.clientX, event.clientY)
    if (!anchorPoint) {
      emit('status', '请在模型表面拖拽以绘制立方体')
      return
    }
    beginCubeDrawing(event, anchorPoint)
    return
  }

  if (props.annotationPlacementActive && event.button === 0 && currentMesh) {
    event.preventDefault()
    event.stopImmediatePropagation()
    const point = pickModelPointFromClientPosition(event.clientX, event.clientY)
    if (!point) {
      emit('status', '请直接点击模型表面以添加气泡标注')
      return
    }
    openPointAnnotationCreateDialog(point)
    return
  }

  if (props.painterMode === 'view' && event.button === 0) {
    const cubeHit = pickCubeFromClientPosition(event.clientX, event.clientY)
    if (cubeHit?.cube && selectedCubeId.value === cubeHit.cube.id) {
      event.preventDefault()
      event.stopImmediatePropagation()
      beginCubeDrag(cubeHit.cube.id, event)
      return
    }

    selectPointAnnotation(null)
    selectCube(null)
  }

  if (props.painterMode === 'view' || event.button !== 0 || !currentMesh || !canvasRef.value) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  if (!captureUndoSnapshot()) {
    emit('status', '无法为当前编辑创建撤回快照')
    return
  }
  activePaintPointerId = event.pointerId
  canvasRef.value.setPointerCapture(event.pointerId)
  updateBrushRay(event)
  bakeCurrentBrushStroke()
}

function onPointerMoveCapture(event: PointerEvent) {
  if (props.painterMode === 'view' || activePaintPointerId !== event.pointerId) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  updateBrushRay(event)
  bakeCurrentBrushStroke()
}

function stopPaintCapture(event: PointerEvent) {
  if (activePaintPointerId !== event.pointerId || !canvasRef.value) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()

  if (canvasRef.value.hasPointerCapture(event.pointerId)) {
    canvasRef.value.releasePointerCapture(event.pointerId)
  }

  activePaintPointerId = null
}

function onCanvasDoubleClick(event: MouseEvent) {
  if (annotationDialogVisible.value || props.sceneInteractionLocked || props.cubePlacementActive || props.annotationPlacementActive) {
    return
  }

  const cubeHit = pickCubeFromClientPosition(event.clientX, event.clientY)
  if (!cubeHit?.cube) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  if (selectedCubeId.value === cubeHit.cube.id) {
    openCubeAnnotationDialog(cubeHit.cube.id)
    return
  }

  selectCube(cubeHit.cube.id)
}

function onCanvasWheelCapture(event: WheelEvent) {
  if (!activeCubeDragId.value) {
    return
  }

  event.preventDefault()
  event.stopImmediatePropagation()
  nudgeActiveCubeDragDepth(event.deltaY)
}

function onWindowKeydown(event: KeyboardEvent) {
  if (annotationDialogVisible.value || props.sceneInteractionLocked || isEditableTarget(event.target)) {
    return
  }

  if (event.key !== 'Delete') {
    return
  }

  if (!selectedPointId.value && !selectedCubeId.value) {
    return
  }

  event.preventDefault()
  deleteSelectedOverlay()
}

function onResize() {
  resizeRenderer()
  updatePointAnnotationScreenPositions()
  updateCubeMarkerScreenPositions()
}

function onWindowPointerMove(event: PointerEvent) {
  updatePointAnnotationDrag(event)
  updateCubeDrawing(event)
  updateCubeDrag(event)
}

function onWindowPointerUp(event: PointerEvent) {
  stopAllOverlayInteractions(event)
}

onMounted(() => {
  if (!canvasRef.value) {
    return
  }

  renderer = new THREE.WebGLRenderer({ canvas: canvasRef.value, antialias: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setClearColor(new THREE.Color('#111111'), 1)

  scene = new THREE.Scene()
  spark = new SparkRenderer({ renderer })
  scene.add(spark)

  camera = new THREE.PerspectiveCamera(60, 1, 0.01, 1000)
  camera.position.set(0, 0, 2)
  camera.lookAt(0, 0, 0)

  controls = new SparkControls({ canvas: canvasRef.value })
  controls.pointerControls.scrollSpeed *= 2
  raycaster = new THREE.Raycaster()

  canvasRef.value.addEventListener('contextmenu', (event) => event.preventDefault())
  canvasRef.value.addEventListener('pointerdown', onPointerDownCapture, true)
  canvasRef.value.addEventListener('pointermove', onPointerMoveCapture, true)
  canvasRef.value.addEventListener('pointerup', stopPaintCapture, true)
  canvasRef.value.addEventListener('pointerleave', stopPaintCapture, true)
  canvasRef.value.addEventListener('dblclick', onCanvasDoubleClick, true)
  canvasRef.value.addEventListener('wheel', onCanvasWheelCapture, { capture: true, passive: false })
  window.addEventListener('resize', onResize)
  window.addEventListener('keydown', onWindowKeydown)
  window.addEventListener('pointermove', onWindowPointerMove, true)
  window.addEventListener('pointerup', onWindowPointerUp, true)

  syncBrushParameters()
  resizeRenderer()
  lastAnimationTime = 0

  renderer.setAnimationLoop((time) => {
    if (!renderer || !scene || !camera) {
      return
    }

    const deltaSeconds = lastAnimationTime > 0 ? (time - lastAnimationTime) / 1000 : 0
    lastAnimationTime = time

    resizeRenderer()
    updateRevealAnimation()
    controls?.update(camera)
    updateQueuedViewRotation(deltaSeconds)
    updatePointAnnotationScreenPositions()
    updateCubeMarkerScreenPositions()

    renderer.render(scene, camera)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('keydown', onWindowKeydown)
  window.removeEventListener('pointermove', onWindowPointerMove, true)
  window.removeEventListener('pointerup', onWindowPointerUp, true)

  if (canvasRef.value) {
    canvasRef.value.removeEventListener('pointerdown', onPointerDownCapture, true)
    canvasRef.value.removeEventListener('pointermove', onPointerMoveCapture, true)
    canvasRef.value.removeEventListener('pointerup', stopPaintCapture, true)
    canvasRef.value.removeEventListener('pointerleave', stopPaintCapture, true)
    canvasRef.value.removeEventListener('dblclick', onCanvasDoubleClick, true)
    canvasRef.value.removeEventListener('wheel', onCanvasWheelCapture, true)
  }

  renderer?.setAnimationLoop(null)
  disposePreviewCube()
  disposeCurrentMesh()
  renderer?.dispose()
  raycaster = null
  spark = null
  scene = null
  camera = null
  controls = null
  renderer = null
})

watch(
  () => props.file,
  async (file) => {
    if (!file) {
      currentProjectInfo.value = createEmptyProjectInfo()
      emit('project-info-loaded', cloneProjectInfo(currentProjectInfo.value))
      pointAnnotations.value = []
      selectPointAnnotation(null)
      disposeAllCubeMarkers()
      return
    }

    setAnnotationPlacementActive(false)
    setCubePlacementActive(false)
    await loadFile(file)
  },
)

watch(
  () => props.projectInfo,
  (value) => {
    currentProjectInfo.value = coerceProjectInfoConfig(value)
  },
  { immediate: true },
)

watch(
  () => props.sceneInteractionLocked,
  (locked) => {
    setSceneInteractionEnabled(!locked)
  },
  { immediate: true },
)

watch(
  () => [props.painterMode, props.painterColor, props.brushRadiusFactor, props.brushDepthFactor],
  () => {
    syncBrushParameters()
  },
  { immediate: true },
)

watch(
  () => props.exportToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      void exportCurrentMesh()
    }
  },
)

watch(
  () => props.restoreModelToken,
  async (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      if (props.fileHandle) {
        const latestFile = await props.fileHandle.getFile()
        currentFile = latestFile
        await loadFile(latestFile)
        return
      }

      if (props.filePath) {
        try {
          const latestModel = await readDesktopModelFile(props.filePath)
          currentFile = latestModel.file
          await loadFile(latestModel.file)
        } catch (error) {
          emit('failed', error instanceof Error ? error.message : '重新载入模型失败')
        }
        return
      }

      if (currentFile) {
        await loadFile(currentFile)
      }
    }
  },
)

watch(
  () => props.undoEditToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      undoLastEdit()
    }
  },
)

watch(
  () => props.redoEditToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      redoLastEdit()
    }
  },
)

watch(
  () => props.resetViewToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      const preferredViewState = applyPreferredView()
      emit('status', preferredViewState === 'default' ? '相机已重置到该模型的默认视角' : '相机已重置到模型默认取景')
    }
  },
)

watch(
  () => props.saveDefaultViewToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      void saveCurrentViewAsDefault()
    }
  },
)

watch(
  () => props.saveProjectInfoToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      void saveProjectInfo()
    }
  },
)

watch(
  () => props.saveMarkersToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue) {
      void saveViewerConfig('已保存当前标记配置', '标记配置保存失败')
    }
  },
)

watch(
  () => props.rotateClockwiseToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue && queueViewRotation('clockwise')) {
      emit('status', '视角开始顺时针线性旋转')
    }
  },
)

watch(
  () => props.rotateCounterclockwiseToken,
  (value, previousValue) => {
    if (value > 0 && value !== previousValue && queueViewRotation('counterclockwise')) {
      emit('status', '视角开始逆时针线性旋转')
    }
  },
)

watch(
  () => props.annotationEdgeColor,
  (value, previousValue) => {
    if (value === previousValue || !selectedPointId.value) {
      return
    }

    updatePointAnnotationColor(selectedPointId.value, value)
  },
)

watch(
  () => props.cubeEdgeColor,
  (value, previousValue) => {
    if (value === previousValue) {
      return
    }

    updateSelectedCubeColor(value)
    if (previewCube) {
      previewCube.mesh.material.color.set(value)
      previewCube.edges.material.color.set(value)
    }
  },
)
</script>

<template>
  <div class="spark-viewport">
    <canvas
      ref="canvasRef"
      class="spark-canvas"
      :class="{ 'annotation-placement-active': annotationPlacementActive || cubePlacementActive }"
      tabindex="0"
    ></canvas>

    <div class="annotation-layer" aria-live="polite">
      <div
        v-for="annotation in pointAnnotations"
        :key="annotation.id"
        class="annotation-bubble-wrap"
        :class="{ hidden: !annotation.visible, dragging: activeAnnotationDragId === annotation.id, selected: selectedPointId === annotation.id }"
        :style="{
          transform: `translate(${annotation.screenX}px, ${annotation.screenY}px)`,
          '--annotation-border-color': selectedPointId === annotation.id ? '#FFFFFF' : 'rgba(255, 255, 255, 0.08)',
          '--annotation-anchor-fill': annotation.edgeColor,
        }"
      >
        <button
          class="annotation-anchor"
          type="button"
          @pointerdown="beginPointAnnotationDrag(annotation.id, $event)"
          @click.stop="handlePointAnnotationAnchorClick(annotation.id, $event)"
        ></button>
        <div class="annotation-bubble" @pointerdown="beginPointAnnotationDrag(annotation.id, $event)" @click.stop="handlePointAnnotationBubbleClick(annotation.id, $event)">
          <div class="annotation-bubble-text">{{ annotation.text }}</div>
        </div>
      </div>

      <div
        v-for="cube in cubeMarkers"
        :key="cube.id"
        class="annotation-bubble-wrap cube-label-wrap"
        :class="{ hidden: !cube.labelVisible, dragging: activeCubeDragId === cube.id, selected: selectedCubeId === cube.id }"
        :style="{
          transform: `translate(${cube.labelScreenX}px, ${cube.labelScreenY}px)`,
          '--annotation-border-color': selectedCubeId === cube.id ? '#FFFFFF' : 'rgba(255, 255, 255, 0.08)',
        }"
      >
        <div class="annotation-bubble cube-label-bubble" @dblclick.stop="handleCubeLabelDoubleClick(cube.id, $event)">
          <div class="annotation-bubble-text">{{ cube.annotationText }}</div>
        </div>
      </div>
    </div>

    <div v-if="annotationDialogVisible" class="annotation-dialog-backdrop" @click.self="closeAnnotationDialog">
      <form class="annotation-dialog" @submit.prevent="submitAnnotationDialog" @keydown.esc.prevent.stop="closeAnnotationDialog">
        <header class="annotation-dialog-header">
          <h3 class="annotation-dialog-title">{{ annotationDialogTitle }}</h3>
        </header>

        <textarea
          ref="annotationDialogInputRef"
          v-model="annotationDialogText"
          class="annotation-dialog-input"
          rows="4"
          maxlength="240"
          placeholder="请输入标注内容"
        ></textarea>

        <footer class="annotation-dialog-footer">
          <button class="annotation-dialog-button" type="button" @click="closeAnnotationDialog">取消</button>
          <button
            v-if="canDeleteAnnotationDialogTarget()"
            class="annotation-dialog-button danger"
            type="button"
            @click="deleteAnnotationDialogTarget"
          >
            删除
          </button>
          <button class="annotation-dialog-button primary" type="submit">保存</button>
        </footer>
      </form>
    </div>
  </div>
</template>