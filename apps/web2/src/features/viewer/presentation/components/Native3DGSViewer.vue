<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNative3DGS } from '@native3dgs/web/vue'
import { PhArrowsOut, PhArrowCounterClockwise, PhCamera, PhCloud, PhFolderOpen, PhMinus, PhPlus, PhWarningCircle, PhX } from '@phosphor-icons/vue'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import type { DomainError } from '@/shared/domain-error'
import type { StoredDefaultView } from '@/features/viewer/domain/entities/viewer-config.entity'
import { ViewerSession, type ViewerSessionState } from '@/features/viewer/presentation/viewer-session'
import { mapViewerEngineError } from '@/features/viewer/presentation/viewer-engine-error'
import '@/presentation/styles/viewer-canvas.css'

const props = withDefaults(defineProps<{
  file: File | null
  defaultView?: StoredDefaultView | null
  cloud?: boolean
  download?: { name: string; loaded: number; total: number } | null
  error?: DomainError | null
}>(), { defaultView: null, cloud: false, download: null, error: null })
const emit = defineEmits<{
  'open-file': [file: File]
  'open-cloud': []
  cancel: []
  closed: []
  loaded: [file: File]
}>()
const { t } = useI18n()
const host = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
const engineGeneration = ref(0)
let dragDepth = 0
const base = new URL(`${import.meta.env.BASE_URL}gs-assets/`, location.origin)
const { engine, snapshot, initializationError } = useNative3DGS(host, {
  assets: { baseUrl: base, workerUrl: new URL('decoder.worker.js', base) },
  decoder: { mode: 'auto', threads: 4 }, sorting: { mode: 'adaptive' },
  pixelRatio: Math.min(window.devicePixelRatio, 2),
})
const session = shallowRef<ViewerSession | null>(null)
const ui = shallowRef<ViewerSessionState | null>(null)
// Historical configs do not identify their coordinate system; use SDK auto-framing.
const legacyViewWarning = computed(() => props.defaultView !== null && props.file === ui.value?.activeFile)
let alive = true

watch(engine, (instance, _previous, onCleanup) => {
  session.value = null
  ui.value = null
  if (!instance) return
  const next = new ViewerSession(instance)
  session.value = next
  ui.value = next.getSnapshot()
  const unsubscribe = next.subscribe(() => { ui.value = next.getSnapshot() })
  onCleanup(() => { unsubscribe(); next.dispose() })
}, { immediate: true, flush: 'sync' })

watch([session, () => props.file], ([current, file], _old, onCleanup) => {
  if (!current || !file || file === current.getSnapshot().activeFile) return
  let currentRequest = true
  void current.openFile(file).then(([error, loaded]) => {
    if (!alive || !currentRequest || error || !loaded) return
    emit('loaded', loaded)
  })
  onCleanup(() => { currentRequest = false; current.cancel() })
}, { immediate: true })

const phase = computed(() => snapshot.value?.phase ?? 'Idle')
const ready = computed(() => !!session.value && !ui.value?.closing && !['Faulted', 'Recovering', 'Stopping', 'Stopped'].includes(phase.value))
const scene = computed(() => !!ui.value?.activeFile)
const busy = computed(() => !!props.download || !!ui.value?.opening)
const failure = computed(() => {
  if (initializationError.value) return mapViewerEngineError(initializationError.value)
  if (props.error) return props.error
  if (busy.value) return null
  if (ui.value?.error) return ui.value.error
  const error = snapshot.value?.error
  return error && error.code !== 'Cancelled' ? mapViewerEngineError(error) : null
})
const progress = computed(() => {
  if (props.download) return { key: 'nativeViewer.downloading', ...props.download }
  const load = snapshot.value?.progress
  return ui.value?.opening && load ? { key: 'nativeViewer.preparing', name: ui.value.candidate?.name ?? '', loaded: load.done, total: load.total ?? 0 } : null
})
const percent = computed(() => progress.value && progress.value.total > 0 ? Math.min(100, Math.round(progress.value.loaded / progress.value.total * 100)) : null)
const status = computed(() => {
  if (failure.value) return formatDomainError(t, failure.value)
  if (phase.value === 'Recovering') return t('nativeViewer.recovering')
  if (ui.value?.closing) return t('nativeViewer.closing')
  if (!session.value) return t('nativeViewer.initializing')
  if (progress.value) return t(progress.value.key, { name: progress.value.name })
  if (busy.value) return t('nativeViewer.preparing', { name: ui.value?.candidate?.name ?? '' })
  if (legacyViewWarning.value) return t('nativeViewer.legacyView')
  return scene.value ? t('nativeViewer.ready') : t('nativeViewer.localOnly')
})

function select(event: Event) {
  const element = event.target as HTMLInputElement
  const file = element.files?.[0]
  element.value = ''
  if (file) emit('open-file', file)
}
function drop(event: DragEvent) {
  dragDepth = 0
  dragging.value = false
  const file = event.dataTransfer?.files[0]
  if (file && ready.value) emit('open-file', file)
}
function dragEnter(event: DragEvent) {
  if (!event.dataTransfer?.types.includes('Files')) return
  dragDepth += 1
  dragging.value = true
}
function dragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) dragging.value = false
}
function restart() {
  session.value?.cancel()
  engineGeneration.value += 1
}
function cancel() { session.value?.cancel(); emit('cancel') }
async function close() {
  emit('cancel')
  if (await session.value?.close()) emit('closed')
}
onBeforeUnmount(() => { alive = false })
</script>

<template>
  <section class="native-viewer" :aria-label="t('nativeViewer.title')">
    <header class="native-viewer__toolbar" :aria-label="t('nativeViewer.tools')">
      <div class="native-viewer__files">
        <AppButton variant="primary" compact :disabled="!ready" @click="input?.click()"><PhFolderOpen :size="17" />{{ t('nativeViewer.open') }}</AppButton>
        <AppButton v-if="cloud" compact :disabled="!ready" @click="emit('open-cloud')"><PhCloud :size="17" />{{ t('nativeViewer.cloud') }}</AppButton>
        <AppButton v-if="busy" compact @click="cancel">{{ t('nativeViewer.cancel') }}</AppButton>
        <AppButton v-else compact :disabled="!ready || !scene" @click="close"><PhX :size="16" />{{ t('nativeViewer.close') }}</AppButton>
      </div>
      <div v-if="scene" class="native-viewer__navigation">
        <div class="native-viewer__mode" role="group" :aria-label="t('nativeViewer.mode')">
          <button type="button" :disabled="!ready" :aria-pressed="ui?.mode === 'orbit'" @click="session?.setMode('orbit')">{{ t('nativeViewer.orbit') }}</button>
          <button type="button" :disabled="!ready" :aria-pressed="ui?.mode === 'fly'" @click="session?.setMode('fly')">{{ t('nativeViewer.fly') }}</button>
        </div>
        <AppButton compact :disabled="!ready || !scene" @click="session?.fit()"><PhArrowsOut :size="17" />{{ t('nativeViewer.fit') }}</AppButton>
        <AppButton compact :disabled="!ready || !scene" @click="session?.reset()"><PhArrowCounterClockwise :size="17" />{{ t('nativeViewer.reset') }}</AppButton>
        <AppButton compact :disabled="!ready || !scene" :aria-pressed="ui?.flipY" @click="session?.flip()">{{ t('nativeViewer.flip') }}</AppButton>
        <div class="native-viewer__zoom" role="group" :aria-label="t('nativeViewer.zoom')">
          <AppButton compact :disabled="!ready || !scene" :aria-label="t('nativeViewer.zoomIn')" :title="t('nativeViewer.zoomIn')" @click="session?.zoom(-1)"><PhPlus :size="17" /></AppButton>
          <AppButton compact :disabled="!ready || !scene" :aria-label="t('nativeViewer.zoomOut')" :title="t('nativeViewer.zoomOut')" @click="session?.zoom(1)"><PhMinus :size="17" /></AppButton>
        </div>
        <AppButton compact :disabled="!ready || !scene || busy || ui?.capturing" @click="session?.capture()"><PhCamera :size="17" />{{ t(ui?.capturing ? 'nativeViewer.capturing' : 'nativeViewer.capture') }}</AppButton>
      </div>
    </header>
    <div class="native-viewer__stage" :class="{ 'is-dragging': dragging }" @dragenter.prevent="dragEnter" @dragover.prevent @dragleave.prevent="dragLeave" @drop.prevent="drop">
      <div :key="engineGeneration" ref="host" class="native-viewer__host" />
      <div v-if="initializationError" class="native-viewer__empty native-viewer__empty--error" role="alert">
        <PhWarningCircle :size="40" weight="light" />
        <h1>{{ t('nativeViewer.unavailableTitle') }}</h1>
        <p>{{ status }}</p>
        <AppButton @click="restart">{{ t('nativeViewer.restart') }}</AppButton>
      </div>
      <div v-else-if="!scene && !busy" class="native-viewer__empty">
        <PhFolderOpen :size="40" weight="light" />
        <h1>{{ t(failure ? 'nativeViewer.failedTitle' : 'nativeViewer.emptyTitle') }}</h1>
        <p :role="failure ? 'alert' : undefined">{{ failure ? status : t('nativeViewer.emptyHint') }}</p>
        <AppButton variant="primary" :disabled="!ready" @click="input?.click()">{{ t('nativeViewer.chooseFile') }}</AppButton>
      </div>
      <div v-if="busy" class="native-viewer__loading" role="status" aria-live="polite">
        <span>{{ status }}</span>
        <progress :value="percent ?? undefined" max="100" :aria-label="t('nativeViewer.progress')" />
        <span v-if="percent !== null">{{ percent }}%</span>
        <AppButton compact @click="cancel">{{ t('nativeViewer.cancel') }}</AppButton>
      </div>
      <div v-if="scene" class="native-viewer__hint">{{ t(ui?.mode === 'fly' ? 'nativeViewer.flyHint' : 'nativeViewer.orbitHint') }}</div>
      <div v-if="dragging" class="native-viewer__drop" aria-hidden="true"><PhFolderOpen :size="32" />{{ t('nativeViewer.dropHint') }}</div>
    </div>
    <footer class="native-viewer__footer">
      <strong :title="ui?.activeFile?.name">{{ ui?.activeFile?.name || t('nativeViewer.noModel') }}</strong>
      <span :class="{ 'is-error': failure }" :role="failure ? 'alert' : 'status'" aria-live="polite">{{ status }}</span>
      <AppButton v-if="phase === 'Faulted'" compact @click="session?.recover()">{{ t('nativeViewer.recover') }}</AppButton>
    </footer>
    <input ref="input" class="visually-hidden" type="file" accept=".ply,.spz" :aria-label="t('nativeViewer.chooseFile')" @change="select" />
  </section>
</template>
