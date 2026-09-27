<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RoutePreview } from '@/features/mission-ops/domain/entities/route-preview.entity'
import AppButton from '@/presentation/components/AppButton.vue'
import 'cesium/Build/Cesium/Widgets/widgets.css'

// Rendering is presentation-only: this viewport consumes the parsed domain entity,
// never a bridge DTO or KML. Imagery/options match the original Sky-Command map.
const props = defineProps<{ route: RoutePreview | null }>()
const { t } = useI18n()
const host = ref<HTMLElement | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const imageryUnavailable = ref(!navigator.onLine)
let api: typeof import('cesium') | undefined
let viewer: import('cesium').Viewer | undefined
let resizeObserver: ResizeObserver | undefined
let disposed = false
let initialization = 0
let removeImageryError: (() => void) | undefined
let removeRenderError: (() => void) | undefined
let positions: import('cesium').Cartesian3[] = []
let imageryErrors = 0
const IMAGERY_ERROR_THRESHOLD = 8

function locate() {
  if (!api || !viewer || viewer.isDestroyed() || positions.length < 2) return
  viewer.camera.flyToBoundingSphere(api.BoundingSphere.fromPoints(positions), {
    duration: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 0.8,
    offset: new api.HeadingPitchRange(0, -Math.PI / 2.8, 0),
  })
}

function drawRoute() {
  if (!api || !viewer || viewer.isDestroyed()) return
  const Cesium = api
  viewer.entities.removeAll()
  positions = (props.route?.points ?? []).map((point) =>
    Cesium.Cartesian3.fromDegrees(point.longitude, point.latitude, point.altitude ?? 0))
  if (positions.length < 2) { viewer.scene.requestRender(); return }
  viewer.entities.add({
    polyline: {
      positions, width: 5, clampToGround: false,
      material: new Cesium.PolylineGlowMaterialProperty({
        glowPower: 0.14, color: Cesium.Color.fromCssColorString('#d7f16a'),
      }),
    },
  })
  positions.forEach((position, index) => {
    const endpoint = index === 0 || index === positions.length - 1
    const color = index === 0 ? '#d7f16a' : index === positions.length - 1 ? '#ff9f43' : '#52d6ff'
    viewer!.entities.add({
      position,
      point: {
        pixelSize: endpoint ? 13 : 8, color: Cesium.Color.fromCssColorString(color),
        outlineColor: Cesium.Color.fromCssColorString('#17252d'), outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
      label: {
        text: endpoint ? t(index === 0 ? 'missionOps.mapStart' : 'missionOps.mapEnd') : String(index + 1),
        font: '12px system-ui', fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK, outlineWidth: 3,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        pixelOffset: new Cesium.Cartesian2(0, -16),
        distanceDisplayCondition: new Cesium.DistanceDisplayCondition(0, 15000),
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
    })
  })
  locate()
  viewer.scene.requestRender()
}

function releaseViewer() {
  resizeObserver?.disconnect()
  resizeObserver = undefined
  removeImageryError?.()
  removeRenderError?.()
  removeImageryError = undefined
  removeRenderError = undefined
  if (viewer && !viewer.isDestroyed()) viewer.destroy()
  viewer = undefined
}

async function initialize() {
  const attempt = ++initialization
  state.value = 'loading'
  imageryErrors = 0
  releaseViewer()
  try {
    const Cesium = await import('cesium')
    if (disposed || attempt !== initialization || !host.value) return
    api = Cesium
    viewer = new Cesium.Viewer(host.value, {
      animation: false, timeline: false, geocoder: false, homeButton: false,
      sceneModePicker: false, baseLayerPicker: false, navigationHelpButton: false,
      fullscreenButton: false, infoBox: false, selectionIndicator: false,
      scene3DOnly: true, skyBox: false, skyAtmosphere: false, baseLayer: false,
      requestRenderMode: true, maximumRenderTimeChange: Number.POSITIVE_INFINITY,
    })
    const imagery = new Cesium.UrlTemplateImageryProvider({
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
      credit: new Cesium.Credit('Tiles © Esri'),
      maximumLevel: 19,
    })
    removeImageryError = imagery.errorEvent.addEventListener(() => {
      imageryErrors += 1
      if (imageryErrors >= IMAGERY_ERROR_THRESHOLD) imageryUnavailable.value = true
    })
    removeRenderError = viewer.scene.renderError.addEventListener(() => {
      state.value = 'error'
      // Cesium stops its render loop on error; retry reconstructs the context.
    })
    viewer.imageryLayers.addImageryProvider(imagery)
    viewer.scene.backgroundColor = Cesium.Color.fromCssColorString('#1d2e36')
    viewer.scene.globe.baseColor = Cesium.Color.fromCssColorString('#1d2e36')
    viewer.scene.globe.depthTestAgainstTerrain = true
    viewer.scene.screenSpaceCameraController.minimumZoomDistance = 20
    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(120.1665, 30.3214, 2200),
      orientation: { heading: 0, pitch: -Math.PI / 2.8, roll: 0 },
    })
    resizeObserver = new ResizeObserver(() => {
      if (!viewer || viewer.isDestroyed()) return
      viewer.resize()
      viewer.scene.requestRender()
    })
    resizeObserver.observe(host.value)
    state.value = 'ready'
    drawRoute()
  } catch {
    if (disposed || attempt !== initialization) return
    releaseViewer()
    state.value = 'error'
  }
}

function retry() {
  imageryUnavailable.value = !navigator.onLine
  void initialize()
}
function offline() { imageryUnavailable.value = true }
function online() { retry() }
watch(() => props.route, drawRoute)
onMounted(() => {
  window.addEventListener('offline', offline)
  window.addEventListener('online', online)
  void initialize()
})
onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('offline', offline)
  window.removeEventListener('online', online)
  releaseViewer()
})
</script>

<template>
  <div class="route-map">
    <div ref="host" class="route-map__globe" :aria-label="t('missionOps.routeTitle')" />
    <div class="route-map__top">
      <div class="route-map__badge"><span /> SKY COMMAND <small>{{ t('missionOps.mapSatellite') }}</small></div>
      <AppButton variant="ghost" :disabled="state !== 'ready' || !route" @click="locate">{{ t('missionOps.mapLocate') }}</AppButton>
    </div>
    <div v-if="state !== 'ready'" class="route-map__status" role="status">
      <strong>{{ t(state === 'error' ? 'missionOps.mapError' : 'missionOps.mapLoading') }}</strong>
      <p>{{ t('missionOps.mapPreviewOnly') }}</p>
      <AppButton v-if="state === 'error'" variant="ghost" @click="retry">{{ t('missionOps.mapRetry') }}</AppButton>
    </div>
    <div v-else-if="imageryUnavailable" class="route-map__warning" role="status">
      {{ t('missionOps.mapOffline') }} <AppButton variant="ghost" @click="retry">{{ t('missionOps.mapRetry') }}</AppButton>
    </div>
    <div class="route-map__bottom">
      <div><strong>{{ route?.name || t('missionOps.routeNoPoints') }}</strong><small>{{ t('missionOps.mapPreviewOnly') }}</small></div>
      <div v-if="route" class="route-map__legend"><i /> {{ t('missionOps.routePoints', { count: route.points.length }) }}</div>
    </div>
  </div>
</template>

<style scoped>
.route-map { position: relative; height: clamp(360px, 48vw, 580px); margin-top: 16px; overflow: hidden; border: 1px solid #30434b; border-radius: 14px; background: #1d2e36; color: #f3f7f8; isolation: isolate; }
.route-map__globe { position: absolute; inset: 0; }
.route-map__top, .route-map__bottom { position: absolute; z-index: 1; left: 16px; right: 16px; display: flex; justify-content: space-between; align-items: center; gap: 12px; pointer-events: none; }
.route-map__top { top: 16px; }
.route-map__bottom { bottom: 34px; align-items: end; }
.route-map__top :deep(button), .route-map__warning :deep(button), .route-map__status :deep(button) { pointer-events: auto; background: #17252de6; color: #f3f7f8; border-color: #60747f; }
.route-map__badge, .route-map__bottom > div { padding: 10px 14px; border-radius: 8px; background: #13232de0; border: 1px solid #ffffff20; }
.route-map__badge { font-size: 11px; letter-spacing: .12em; }
.route-map__badge span { display: inline-block; width: 6px; height: 6px; margin-right: 8px; border-radius: 50%; background: #d7f16a; }
.route-map small { display: block; margin-top: 5px; color: #b6c7cf; font-size: 11px; letter-spacing: normal; }
.route-map__bottom strong { display: block; font-size: 13px; overflow-wrap: anywhere; }
.route-map__legend { white-space: nowrap; font-size: 12px; }
.route-map__legend i { display: inline-block; width: 22px; height: 3px; background: #d7f16a; vertical-align: middle; margin-right: 6px; }
.route-map__status { position: absolute; inset: 0; display: flex; flex-direction: column; justify-content: center; align-items: center; padding: 70px 20px; text-align: center; background: #13232df2; }
.route-map__status p { color: #b6c7cf; font-size: 12px; }
.route-map__warning { position: absolute; top: 85px; left: 16px; right: 16px; padding: 10px 14px; border-radius: 8px; background: #352d20ed; color: #ffdda0; font-size: 12px; }
@media (max-width: 600px) { .route-map__bottom { flex-wrap: wrap; } .route-map__badge { letter-spacing: .04em; } }
</style>
