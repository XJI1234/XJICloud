<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { classifyBridgeReadiness, shouldPollSnapshot } from '@/features/mission-ops/domain/services/bridge-readiness.service'
import { LOCAL_INSTALLER_PATH } from '@/features/mission-ops/domain/repositories/installer-asset.port'
import {
  emptyMissionSnapshot,
  offlineBridgeHealth,
  type LinkSegmentState,
  type MissionOpsSnapshot,
} from '@/features/mission-ops/domain/entities/bridge-status.entity'
import { LOCAL_BRIDGE_DEFAULT_BASE_URL } from '@/features/mission-ops/domain/repositories/local-bridge.port'
import { useMissionOps } from '@/features/mission-ops/presentation/composables/useMissionOps'
import type { MissionCommandName } from '@/features/mission-ops/domain/entities/mission-command.entity'
import type { RoutePreview } from '@/features/mission-ops/domain/entities/route-preview.entity'
import RouteMapViewport from '@/features/mission-ops/presentation/RouteMapViewport.vue'

const { t } = useI18n()
const missionOps = useMissionOps()

const snapshot = ref<MissionOpsSnapshot>(emptyMissionSnapshot(offlineBridgeHealth()))
const waking = ref(false)
const busy = ref(false)
const notice = ref('')
const errorMessage = ref('')
const route = ref<RoutePreview | null>(null)
const routeError = ref('')
const routeInput = ref<HTMLInputElement | null>(null)
const installerReady = ref<boolean | null>(null)
const videoElement = ref<HTMLVideoElement | null>(null)
let player: { destroy: () => void } | null = null
let playingUrl: string | null = null
async function syncVideo(url: string | null) {
  if (url === playingUrl && (url === null || player !== null)) return
  player?.destroy()
  player = null
  playingUrl = url
  if (!url || !videoElement.value) return
  let flvjs: typeof import('flv.js')
  try { flvjs = await import('flv.js') } catch { errorMessage.value = t('missionOps.videoHint'); return }
  if (url !== playingUrl || !videoElement.value || !flvjs.default.isSupported()) {
    if (url === playingUrl) errorMessage.value = t('missionOps.videoHint')
    return
  }
  const instance = flvjs.default.createPlayer({ type: 'flv', isLive: true, url }, { enableStashBuffer: false })
  instance.attachMediaElement(videoElement.value)
  instance.load()
  player = instance
  try { await instance.play() } catch { errorMessage.value = t('missionOps.videoHint') }
}
let routeSelection = 0

async function stageMission() {
  if (!snapshot.value.deviceId || !snapshot.value.routeId || busy.value) return
  busy.value = true
  const [error] = await missionOps.stage(snapshot.value.deviceId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    busy.value = false
    return
  }
  notice.value = t('missionOps.commandSent')
  await refreshSnapshot({ preserveStatus: true })
}

async function chooseRoute(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const selection = ++routeSelection
  routeError.value = ''
  if (/\.kmz$/i.test(file.name)) {
    route.value = null
  } else {
    const [error, preview] = await missionOps.previewRoute(file)
    if (selection !== routeSelection) return
    if (error || !preview) {
      route.value = null
      routeError.value = error ? formatDomainError(t, error) : t('errors.ROUTE_PREVIEW_INVALID')
      return
    }
    route.value = preview
  }
  if (selection !== routeSelection || !snapshot.value.bridge.online) return
  busy.value = true
  try {
    const [importError] = await missionOps.importRoute(file)
    if (selection !== routeSelection) return
    if (importError) {
      routeError.value = formatDomainError(t, importError)
      return
    }
    routeError.value = ''
    await refreshSnapshot({ preserveStatus: true })
  } finally {
    if (selection === routeSelection) busy.value = false
  }
}

async function returnHome() {
  const deviceId = snapshot.value.deviceId
  if (!deviceId || busy.value) return
  busy.value = true
  const [requestError, confirmationId] = await missionOps.requestFlight(deviceId, 'return-home')
  if (requestError || !confirmationId) {
    busy.value = false
    errorMessage.value = requestError ? formatDomainError(t, requestError) : t('errors.BRIDGE_COMMAND_REJECTED')
    return
  }
  const [error] = window.confirm(t('missionOps.returnHomeConfirm'))
    ? await missionOps.confirmFlight(deviceId, confirmationId)
    : await missionOps.cancelFlight(deviceId, confirmationId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    busy.value = false
    return
  }
  notice.value = t('missionOps.commandSent')
  await refreshSnapshot({ preserveStatus: true })
}

let pollTimer: ReturnType<typeof setInterval> | undefined
let snapshotGeneration = 0
let pollInFlight = false

const readiness = computed(() => classifyBridgeReadiness(snapshot.value.bridge, waking.value))
const bridgeOnline = computed(() => snapshot.value.bridge.online)
const controlsEnabled = computed(() => bridgeOnline.value && snapshot.value.bridge.relayListening && !!snapshot.value.deviceId)
const hasRoute = computed(() => !!snapshot.value.routeId)

function segmentLabel(state: LinkSegmentState) {
  return t(`missionOps.link.${state}`)
}

function segmentClass(state: LinkSegmentState) {
  return {
    'is-up': state === 'up',
    'is-down': state === 'down',
    'is-unknown': state === 'unknown',
  }
}

async function refreshSnapshot(options?: { wakeIfOffline?: boolean; background?: boolean; preserveStatus?: boolean }) {
  const background = options?.background === true
  if (background) {
    if (!shouldPollSnapshot({ busy: busy.value, pollInFlight })) return
    pollInFlight = true
  } else {
    busy.value = true
    if (!options?.preserveStatus) {
      errorMessage.value = ''
      notice.value = ''
    }
  }
  const generation = ++snapshotGeneration
  try {
    if (options?.wakeIfOffline) {
      waking.value = true
      const [readyError, health] = await missionOps.ensureReady({ wakeIfOffline: true, settleMs: 800 })
      if (generation !== snapshotGeneration) return
      waking.value = false
      if (readyError) {
        snapshot.value = emptyMissionSnapshot(offlineBridgeHealth())
        void syncVideo(null)
        errorMessage.value = formatDomainError(t, readyError)
        const [probeError, probed] = await missionOps.probe()
        if (generation !== snapshotGeneration) return
        if (!probeError && probed) snapshot.value = emptyMissionSnapshot(probed)
        return
      }
      if (health) notice.value = t('missionOps.bridgeReady')
    }

    const [error, data] = await missionOps.loadSnapshot()
    if (generation !== snapshotGeneration) return
    if (error) {
      if (!background) {
        errorMessage.value = formatDomainError(t, error)
        snapshot.value = emptyMissionSnapshot(offlineBridgeHealth())
        void syncVideo(null)
      }
      return
    }
    if (data) {
      snapshot.value = data
      void syncVideo(data.videoUrl)
    }
  } finally {
    if (background) pollInFlight = false
    else if (generation === snapshotGeneration) {
      busy.value = false
      waking.value = false
    }
  }
}

async function assignRoute(routeId: string) {
  if (!snapshot.value.deviceId || busy.value) return
  busy.value = true
  const [error] = await missionOps.assign(snapshot.value.deviceId, routeId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    busy.value = false
    return
  }
  notice.value = t('missionOps.commandSent')
  await refreshSnapshot({ preserveStatus: true })
}

async function startStream() {
  if (!snapshot.value.deviceId || busy.value) return
  busy.value = true
  const [error] = await missionOps.startStream(snapshot.value.deviceId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    busy.value = false
    return
  }
  notice.value = t('missionOps.commandSent')
  await refreshSnapshot({ preserveStatus: true })
}

async function wakeOnly() {
  waking.value = true
  errorMessage.value = ''
  notice.value = t('missionOps.wakingHint')
  const [error] = await missionOps.wake()
  waking.value = false
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  await refreshSnapshot()
}

async function runCommand(command: MissionCommandName) {
  if (!controlsEnabled.value || (command === 'mission.upload' && !hasRoute.value) || busy.value) {
    return
  }
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  const deviceId = snapshot.value.deviceId
  if (!deviceId) { busy.value = false; return }
  const [error] = await missionOps.dispatch(command, deviceId)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    busy.value = false
    return
  }
  notice.value = t('missionOps.commandSent')
  await refreshSnapshot({ preserveStatus: true })
}

onMounted(() => {
  void missionOps.installerAvailable().then((available) => {
    installerReady.value = available
  })
  void refreshSnapshot({ wakeIfOffline: true })
  pollTimer = setInterval(() => {
    void refreshSnapshot({ background: true })
  }, 4000)
})

onBeforeUnmount(() => {
  playingUrl = null
  player?.destroy()
  player = null
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})
</script>

<template>
  <section class="mission-ops" aria-label="Mission operations">
    <header class="mission-ops__head">
      <div>
        <p class="mission-ops__eyebrow">{{ t('missionOps.eyebrow') }}</p>
        <h1>{{ t('missionOps.title') }}</h1>
        <p class="mission-ops__sub">{{ t('missionOps.subtitle') }}</p>
      </div>
      <div class="mission-ops__actions">
        <AppButton variant="ghost" :disabled="busy" @click="refreshSnapshot()">
          {{ t('common.refresh') }}
        </AppButton>
      </div>
    </header>

    <div
      class="mission-ops__bridge"
      :class="{
        'is-ready': readiness === 'ready',
        'is-waking': readiness === 'waking',
        'is-offline': readiness === 'offline',
      }"
      role="status"
    >
      <div>
        <strong>{{ t(`missionOps.readiness.${readiness}`) }}</strong>
        <p>
          {{
            bridgeOnline
              ? t('missionOps.bridgeOnlineDetail', {
                  version: snapshot.bridge.version || t('common.notSet'),
                  relay: snapshot.bridge.relayListening
                    ? t('missionOps.relayOn')
                    : t('missionOps.relayOff'),
                })
              : t('missionOps.bridgeOfflineDetail', {
                  baseUrl: LOCAL_BRIDGE_DEFAULT_BASE_URL,
                })
          }}
        </p>
      </div>
      <span class="mission-ops__pill">{{ readiness }}</span>
    </div>

    <p v-if="!bridgeOnline" class="mission-ops__notice">{{ t('missionOps.integrationPending') }} <AppButton variant="ghost" @click="wakeOnly">{{ t('missionOps.wakeAgent') }}</AppButton></p>
    <p v-if="notice" class="mission-ops__notice">{{ notice }}</p>
    <p v-if="errorMessage" class="mission-ops__error">{{ errorMessage }}</p>

    <div class="mission-ops__grid">
      <aside class="mission-ops__panel">
        <header class="mission-ops__panel-head">
          <h2>{{ t('missionOps.linkTitle') }}</h2>
          <span>{{ snapshot.deviceId || t('missionOps.noDevice') }}</span>
        </header>
        <div class="mission-ops__link-chain">
          <div class="mission-ops__node" :class="segmentClass(snapshot.link.desktop)">
            <strong>{{ t('missionOps.segments.desktop') }}</strong>
            <small>{{ segmentLabel(snapshot.link.desktop) }}</small>
          </div>
          <div class="mission-ops__connector" />
          <div class="mission-ops__node" :class="segmentClass(snapshot.link.remoteController)">
            <strong>{{ t('missionOps.segments.remoteController') }}</strong>
            <small>{{ segmentLabel(snapshot.link.remoteController) }}</small>
          </div>
          <div class="mission-ops__connector" />
          <div class="mission-ops__node" :class="segmentClass(snapshot.link.aircraft)">
            <strong>{{ t('missionOps.segments.aircraft') }}</strong>
            <small>{{ segmentLabel(snapshot.link.aircraft) }}</small>
          </div>
        </div>
        <dl class="mission-ops__kv">
          <div>
            <dt>{{ t('missionOps.battery') }}</dt>
            <dd>
              {{
                snapshot.batteryPercent == null
                  ? t('missionOps.unknownValue')
                  : `${snapshot.batteryPercent}%`
              }}
            </dd>
          </div>
          <div>
            <dt>{{ t('missionOps.missionPhase') }}</dt>
            <dd>{{ snapshot.missionPhase || t('missionOps.unknownValue') }}</dd>
          </div>
          <div>
            <dt>{{ t('missionOps.liveStream') }}</dt>
            <dd>
              {{
                snapshot.liveStreamActive ? t('missionOps.liveOn') : t('missionOps.liveOff')
              }}
            </dd>
          </div>
        </dl>
      </aside>

      <section class="mission-ops__canvas" aria-label="Flight canvas">
        <div class="mission-ops__video">
          <video ref="videoElement" v-show="snapshot.videoUrl" class="mission-ops__video-player" muted autoplay playsinline />
          <div v-if="!snapshot.videoUrl" class="mission-ops__video-copy">
            <strong>{{ t('missionOps.videoTitle') }}</strong>
            <p>{{ t('missionOps.videoHint') }}</p>
          </div>
        </div>
        <div class="mission-ops__telemetry">
          <div>
            <label>{{ t('missionOps.telemetry.alt') }}</label>
            <strong>{{ snapshot.altitudeMeters ?? t('missionOps.unknownValue') }}</strong>
          </div>
          <div>
            <label>{{ t('missionOps.telemetry.sats') }}</label>
            <strong>{{ snapshot.satelliteCount ?? t('missionOps.unknownValue') }}</strong>
          </div>
          <div>
            <label>{{ t('missionOps.telemetry.mode') }}</label>
            <strong>{{ snapshot.flightMode ?? t('missionOps.unknownValue') }}</strong>
          </div>
        </div>
      </section>

      <aside class="mission-ops__panel mission-ops__panel--controls">
        <header class="mission-ops__panel-head">
          <h2>{{ t('missionOps.controlsTitle') }}</h2>
          <span>{{ controlsEnabled ? t('missionOps.controlsArmed') : t('missionOps.controlsLocked') }}</span>
        </header>
        <div class="mission-ops__controls">
          <AppButton :disabled="!controlsEnabled || busy" @click="startStream">{{ t('missionOps.actions.startStream') }}</AppButton>
          <AppButton :disabled="!controlsEnabled || busy || !snapshot.routeId" @click="stageMission">{{ t('missionOps.actions.stage') }}</AppButton>
          <AppButton class="mission-ops__wide" variant="primary" :disabled="!controlsEnabled || !hasRoute || busy" @click="runCommand('mission.upload')">
            {{ t('missionOps.actions.uploadMission') }}
          </AppButton>
          <AppButton :disabled="!controlsEnabled || busy" @click="runCommand('mission.start')">{{ t('missionOps.actions.start') }}</AppButton>
          <AppButton :disabled="!controlsEnabled || busy" @click="runCommand('mission.pause')">{{ t('missionOps.actions.pause') }}</AppButton>
          <AppButton :disabled="!controlsEnabled || busy" @click="runCommand('mission.resume')">{{ t('missionOps.actions.resume') }}</AppButton>
          <AppButton :disabled="!controlsEnabled || busy" @click="runCommand('mission.stop')">{{ t('missionOps.actions.stop') }}</AppButton>
          <AppButton class="mission-ops__wide" variant="destructive" :disabled="!controlsEnabled || busy" @click="returnHome">
            {{ t('missionOps.actions.returnHome') }}
          </AppButton>
        </div>
        <p class="mission-ops__footnote">{{ t('missionOps.controlsFootnote') }}</p>
      </aside>
    </div>

    <section class="mission-ops__install" aria-label="Route preview">
      <h2>{{ t('missionOps.routeTitle') }}</h2>
      <p>{{ t('missionOps.routeHint') }}</p>
      <input ref="routeInput" class="mission-ops__file" type="file" accept=".kml,.kmz" :aria-label="t('missionOps.routePick')" @change="chooseRoute" />
      <AppButton variant="ghost" @click="routeInput?.click()">{{ t('missionOps.routePick') }}</AppButton>
      <p v-if="routeError" class="mission-ops__error" role="alert">{{ routeError }}</p>
      <RouteMapViewport :route="route" />
      <div v-for="entry in snapshot.routes" :key="entry.id" class="mission-ops__route-entry">
        <span>{{ entry.name }}</span>
        <AppButton variant="ghost" :disabled="!controlsEnabled || busy" @click="assignRoute(entry.id)">{{ t('missionOps.actions.assign') }}</AppButton>
      </div>
    </section>

    <section class="mission-ops__install" aria-label="Local agent install">
      <h2>{{ t('missionOps.installTitle') }}</h2>
      <a v-if="installerReady" class="app-btn app-btn--primary mission-ops__download" :href="LOCAL_INSTALLER_PATH" download>
        {{ t('missionOps.installDownload') }}
      </a>
      <p v-else-if="installerReady === false" class="mission-ops__notice">{{ t('missionOps.installUnavailable') }}</p>
      <ol>
        <li>{{ t('missionOps.installStep1') }}</li>
        <li>{{ t('missionOps.installStep2') }}</li>
        <li>{{ t('missionOps.installStep3') }}</li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.mission-ops {
  display: grid;
  gap: 16px;
  padding: 20px 24px 28px;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  align-content: start;
  background: var(--surface);
}

.mission-ops__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.mission-ops__eyebrow {
  margin: 0;
  color: var(--accent);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.mission-ops__head h1 {
  margin: 4px 0 0;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.mission-ops__sub {
  margin: 6px 0 0;
  color: var(--ink-muted);
  max-width: 56ch;
}

.mission-ops__actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.mission-ops__bridge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sheet);
  background: var(--elevated);
}

.mission-ops__bridge strong {
  display: block;
  margin-bottom: 4px;
}

.mission-ops__bridge p {
  margin: 0;
  color: var(--ink-muted);
  font-size: 13px;
}

.mission-ops__bridge.is-ready {
  border-color: color-mix(in srgb, var(--ok) 40%, var(--line-strong));
  background: color-mix(in srgb, var(--ok) 8%, var(--elevated));
}

.mission-ops__bridge.is-waking {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--line-strong));
  background: var(--accent-soft);
}

.mission-ops__bridge.is-offline {
  border-color: color-mix(in srgb, var(--danger) 35%, var(--line-strong));
  background: var(--danger-soft);
}

.mission-ops__pill {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--line-strong);
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ink-muted);
}

.mission-ops__notice,
.mission-ops__error {
  margin: 0;
  font-size: 13px;
}

.mission-ops__error {
  color: var(--danger);
}

.mission-ops__grid {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr) minmax(220px, 260px);
  gap: 12px;
  min-height: 480px;
}

.mission-ops__panel,
.mission-ops__canvas {
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sheet);
  background: var(--elevated);
  overflow: hidden;
}

.mission-ops__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--line);
}

.mission-ops__panel-head h2 {
  margin: 0;
  font-size: 13px;
}

.mission-ops__panel-head span {
  color: var(--ink-faint);
  font-size: 11px;
}

.mission-ops__link-chain {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.mission-ops__node {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.mission-ops__node strong {
  display: block;
  margin-bottom: 4px;
}

.mission-ops__node small {
  color: var(--ink-muted);
}

.mission-ops__node.is-up {
  border-color: color-mix(in srgb, var(--ok) 45%, var(--line));
}

.mission-ops__node.is-down {
  border-color: color-mix(in srgb, var(--danger) 40%, var(--line));
}

.mission-ops__connector {
  height: 12px;
  width: 2px;
  margin: 0 auto;
  background: var(--line-strong);
}

.mission-ops__kv {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0 14px 14px;
}

.mission-ops__kv div {
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface);
}

.mission-ops__kv dt {
  color: var(--ink-muted);
  font-size: 11px;
}

.mission-ops__kv dd {
  margin: 4px 0 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.mission-ops__canvas {
  display: grid;
  grid-template-rows: minmax(280px, 1fr) auto;
  background: #12161a;
  color: #e8eef2;
}

.mission-ops__video {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 280px;
  background:
    linear-gradient(160deg, rgba(61, 107, 138, 0.18), transparent 45%),
    radial-gradient(circle at 30% 40%, rgba(255, 255, 255, 0.05), transparent 35%),
    #0b0f12;
}

.mission-ops__video-player {
  width: 100%;
  height: 100%;
  max-height: 580px;
  object-fit: contain;
}

.mission-ops__route-entry {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.mission-ops__video-copy {
  text-align: center;
  color: rgba(232, 238, 242, 0.72);
  padding: 20px;
}

.mission-ops__video-copy strong {
  display: block;
  margin-bottom: 6px;
  color: #f5f8fa;
  font-size: 16px;
}

.mission-ops__telemetry {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: rgba(255, 255, 255, 0.08);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.mission-ops__telemetry div {
  padding: 12px;
  background: #171c21;
}

.mission-ops__telemetry label {
  display: block;
  color: rgba(232, 238, 242, 0.55);
  font-size: 10px;
}

.mission-ops__telemetry strong {
  display: block;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 16px;
}

.mission-ops__controls {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 14px;
}

.mission-ops__wide {
  grid-column: 1 / -1;
}

.mission-ops__footnote {
  margin: 0;
  padding: 0 14px 14px;
  color: var(--ink-muted);
  font-size: 12px;
}

.mission-ops__install {
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sheet);
  background: var(--elevated);
}

.mission-ops__install h2 {
  margin: 0 0 8px;
  font-size: 14px;
}

.mission-ops__download {
  margin: 0 0 12px;
  width: fit-content;
  text-decoration: none;
}

.mission-ops__install ol {
  margin: 0;
  padding-left: 18px;
  color: var(--ink-muted);
}

.mission-ops__install li + li {
  margin-top: 4px;
}

.mission-ops__file {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

@media (max-width: 1100px) {
  .mission-ops__grid {
    grid-template-columns: 1fr;
  }
}
</style>
