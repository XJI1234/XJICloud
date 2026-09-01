<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { SparkControls, SparkRenderer, SplatMesh } from '@xjicloud/spark'
import type { StoredDefaultView } from '@/features/viewer/domain/entities/viewer-config.entity'

const MODEL_VERTICAL_FLIP_RADIANS = Math.PI
const CAMERA_FIT_PADDING = 0.92
const VIEW_ROLL_STEP_RADIANS = THREE.MathUtils.degToRad(15)
const VIEW_ROLL_SPEED_RADIANS_PER_SECOND = THREE.MathUtils.degToRad(40)

const props = defineProps<{
  file: File | null
  defaultView?: StoredDefaultView | null
  resetViewToken?: number
  rotateClockwiseToken?: number
  rotateCounterclockwiseToken?: number
  flipYToken?: number
}>()

const emit = defineEmits<{
  loaded: [info: { fileName: string; splatCount: number; view: 'default' | 'framed' }]
  failed: [message: string]
  status: [message: string]
}>()

const frameRef = ref<HTMLElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let spark: SparkRenderer | null = null
let controls: SparkControls | null = null
let currentMesh: SplatMesh | null = null
let currentModelRoot: THREE.Group | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let loadGeneration = 0
let defaultViewApplied = false
let queuedViewRollRadians = 0
let pendingRotationStatus = ''
let lastTickMs = 0
let extraFlipXRadians = 0
const scratchBoundsSize = new THREE.Vector3()
const scratchWorldCenter = new THREE.Vector3()
const scratchViewDirection = new THREE.Vector3()
const scratchRollQuaternion = new THREE.Quaternion()

function resetControlInertia() {
  controls?.pointerControls.moveVelocity.set(0, 0, 0)
  controls?.pointerControls.rotateVelocity.set(0, 0, 0)
  controls?.pointerControls.scroll.set(0, 0, 0)
}

function resizeRenderer() {
  if (!renderer || !camera || !frameRef.value) {
    return
  }

  const width = Math.max(1, Math.floor(frameRef.value.clientWidth))
  const height = Math.max(1, Math.floor(frameRef.value.clientHeight))
  const canvas = canvasRef.value
  if (canvas && (canvas.width !== width || canvas.height !== height)) {
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  }
}

function applyStoredView(view: StoredDefaultView | null | undefined) {
  if (!camera || !view) {
    return false
  }

  camera.position.set(...view.position)
  camera.quaternion.set(...view.quaternion).normalize()
  camera.updateProjectionMatrix()
  resetControlInertia()
  return true
}

function frameCurrentMesh() {
  if (!currentMesh || !camera) {
    return
  }

  const box = currentMesh.getBoundingBox(true)
  const size = box.getSize(scratchBoundsSize)
  const maxDimension = Math.max(size.x, size.y, size.z, 0.5)
  const halfFovRadians = THREE.MathUtils.degToRad(camera.fov * 0.5)
  const halfHorizontalFovRadians = Math.atan(Math.tan(halfFovRadians) * camera.aspect)
  const limitingHalfFovRadians = Math.min(halfFovRadians, halfHorizontalFovRadians)
  const worldCenter = currentModelRoot
    ? scratchWorldCenter.copy(currentModelRoot.position)
    : box.getCenter(scratchWorldCenter)

  const modelRadius = Math.max(maxDimension * 0.5, 0.25)
  const fitDistance = (modelRadius / Math.tan(limitingHalfFovRadians)) * CAMERA_FIT_PADDING
  camera.position.copy(worldCenter).add(new THREE.Vector3(0, 0, Math.max(fitDistance, modelRadius * 1.05)))
  camera.near = Math.max(modelRadius / 500, 0.01)
  camera.far = Math.max(modelRadius * 40, 1000)
  camera.lookAt(worldCenter)
  camera.updateProjectionMatrix()
  resetControlInertia()
}

function applyPreferredView() {
  queuedViewRollRadians = 0
  pendingRotationStatus = ''
  frameCurrentMesh()
  if (applyStoredView(props.defaultView)) {
    return 'default' as const
  }
  return 'framed' as const
}

function applyModelOrientation() {
  if (!currentModelRoot) {
    return
  }
  currentModelRoot.rotation.x = MODEL_VERTICAL_FLIP_RADIANS + extraFlipXRadians
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
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
  if (!currentMesh) {
    return false
  }
  const step = direction === 'clockwise' ? VIEW_ROLL_STEP_RADIANS : -VIEW_ROLL_STEP_RADIANS
  pendingRotationStatus = direction === 'clockwise' ? 'rotation-cw-done' : 'rotation-ccw-done'
  if (prefersReducedMotion()) {
    applyViewRollStep(step)
    emit('status', pendingRotationStatus)
    pendingRotationStatus = ''
    queuedViewRollRadians = 0
    return true
  }
  queuedViewRollRadians += step
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
    if (pendingRotationStatus) {
      emit('status', pendingRotationStatus)
      pendingRotationStatus = ''
    }
  }
}

function toggleYFlip() {
  if (!currentModelRoot) {
    return false
  }
  extraFlipXRadians = extraFlipXRadians === 0 ? Math.PI : 0
  applyModelOrientation()
  emit('status', 'flip-y')
  return true
}

function disposeCurrentMesh() {
  if (currentMesh) {
    currentModelRoot?.remove(currentMesh)
    currentMesh.dispose()
    currentMesh = null
  }
  if (currentModelRoot && scene) {
    scene.remove(currentModelRoot)
    currentModelRoot = null
  }
  defaultViewApplied = false
  queuedViewRollRadians = 0
  pendingRotationStatus = ''
  extraFlipXRadians = 0
}

function tick(now = performance.now()) {
  if (!renderer || !scene || !camera) {
    return
  }

  const deltaSeconds = lastTickMs ? Math.min((now - lastTickMs) / 1000, 0.05) : 0
  lastTickMs = now
  updateQueuedViewRotation(deltaSeconds)
  resizeRenderer()
  controls?.update(camera)
  renderer.render(scene, camera)
  animationFrame = requestAnimationFrame(tick)
}

async function loadFile(file: File | null) {
  const generation = ++loadGeneration
  disposeCurrentMesh()

  if (!file || !scene) {
    return
  }

  emit('status', 'loading')

  try {
    const fileBytes = new Uint8Array(await file.arrayBuffer())
    if (generation !== loadGeneration) {
      return
    }

    const nextMesh = new SplatMesh({
      fileBytes,
      fileName: file.name,
    })
    await nextMesh.initialized
    if (generation !== loadGeneration || !nextMesh.packedSplats) {
      nextMesh.dispose()
      return
    }

    const nextModelRoot = new THREE.Group()
    extraFlipXRadians = 0
    nextModelRoot.rotation.x = MODEL_VERTICAL_FLIP_RADIANS
    const modelCenter = nextMesh.getBoundingBox(true).getCenter(new THREE.Vector3())
    nextMesh.position.copy(modelCenter).multiplyScalar(-1)
    nextModelRoot.position.copy(modelCenter)
    nextModelRoot.add(nextMesh)
    scene.add(nextModelRoot)

    currentMesh = nextMesh
    currentModelRoot = nextModelRoot
    const view = applyPreferredView()
    defaultViewApplied = view === 'default'
    emit('loaded', {
      fileName: file.name,
      splatCount: nextMesh.packedSplats.numSplats,
      view,
    })
  } catch (error) {
    if (generation !== loadGeneration) {
      return
    }
    disposeCurrentMesh()
    emit('failed', error instanceof Error ? error.message : 'load-failed')
  }
}

onMounted(() => {
  if (!canvasRef.value || !frameRef.value) {
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
  canvasRef.value.addEventListener('contextmenu', (event) => event.preventDefault())

  resizeObserver = new ResizeObserver(() => resizeRenderer())
  resizeObserver.observe(frameRef.value)
  resizeRenderer()
  animationFrame = requestAnimationFrame(tick)

  if (props.file) {
    void loadFile(props.file)
  }
})

watch(
  () => props.file,
  (file) => {
    if (!renderer) {
      return
    }
    void loadFile(file)
  },
)

watch(
  () => props.defaultView,
  (view) => {
    if (!currentMesh || !view || defaultViewApplied) {
      return
    }
    if (applyStoredView(view)) {
      defaultViewApplied = true
      emit('status', 'applied-default')
    }
  },
)

watch(
  () => props.resetViewToken,
  (value, previousValue) => {
    if (!value || value === previousValue || !currentMesh) {
      return
    }
    const view = applyPreferredView()
    emit('status', view === 'default' ? 'reset-default' : 'reset-framed')
  },
)

watch(
  () => props.rotateClockwiseToken,
  (value, previousValue) => {
    if (value && value !== previousValue) {
      queueViewRotation('clockwise')
    }
  },
)

watch(
  () => props.rotateCounterclockwiseToken,
  (value, previousValue) => {
    if (value && value !== previousValue) {
      queueViewRotation('counterclockwise')
    }
  },
)

watch(
  () => props.flipYToken,
  (value, previousValue) => {
    if (value && value !== previousValue) {
      toggleYFlip()
    }
  },
)

onBeforeUnmount(() => {
  loadGeneration += 1
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  resizeObserver = null
  disposeCurrentMesh()
  renderer?.dispose()
  spark = null
  scene = null
  camera = null
  controls = null
  renderer = null
})
</script>

<template>
  <div ref="frameRef" class="native-splat-frame">
    <canvas ref="canvasRef" class="spark-canvas" />
  </div>
</template>
