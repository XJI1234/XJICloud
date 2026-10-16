<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import { formatDomainError } from '@/presentation/errors'
import { useSkyCommandDesk } from '@/features/sky-command/presentation/composables/useSkyCommandDesk'
import type { SkyDeskAction } from '@/features/sky-command/domain/repositories/sky-command.port'

const { t } = useI18n()
const desk = useSkyCommandDesk()
const gatewayUrl = ref(desk.defaultUrl)
const deviceId = ref('')
const rtmpUrl = ref('')
const notice = ref('')
const busy = ref(false)
const confirmReturn = ref(false)

function selectedDevice(): string {
  return deviceId.value || desk.devices.value[0]?.deviceId || ''
}

async function connect() {
  busy.value = true
  notice.value = ''
  const [error] = await desk.connect(gatewayUrl.value)
  notice.value = error ? formatDomainError(t, error) : t('skyCommand.connected')
  const first = desk.devices.value[0]?.deviceId
  if (!deviceId.value && first) deviceId.value = first
  busy.value = false
}

async function run(action: SkyDeskAction) {
  const current = selectedDevice()
  if (!current) {
    notice.value = t('skyCommand.noDevice')
    return
  }
  if (action === 'flight.return-home' && !confirmReturn.value) {
    confirmReturn.value = true
    notice.value = t('skyCommand.confirmReturn')
    return
  }
  confirmReturn.value = false
  busy.value = true
  if (action === 'camera.photo.fetch') desk.progress.value = null
  const [error, outcome] = await desk.send(current, action, rtmpUrl.value)
  notice.value = error ? formatDomainError(t, error) : t('skyCommand.outcome', { status: outcome?.status ?? '', detail: outcome?.detail ?? '' })
  busy.value = false
}

async function onMission(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  const current = selectedDevice()
  if (!file || !current) {
    notice.value = t('skyCommand.noDevice')
    return
  }
  busy.value = true
  const [error, outcome] = await desk.upload(current, file.name, new Uint8Array(await file.arrayBuffer()))
  notice.value = error ? formatDomainError(t, error) : t('skyCommand.outcome', { status: outcome?.status ?? '', detail: outcome?.detail ?? '' })
  busy.value = false
}
</script>

<template>
  <div class="sky-page">
    <div class="cloud-page-inner">
      <h2 class="sky-title">{{ t('skyCommand.title') }}</h2>
      <p class="sky-lead">{{ t('skyCommand.lead') }}</p>
      <section class="cloud-card sky-card">
        <label class="sky-label" for="sky-gateway">{{ t('skyCommand.gateway') }}</label>
        <div class="sky-row">
          <input id="sky-gateway" v-model="gatewayUrl" class="sky-input" type="url" spellcheck="false" />
          <AppButton variant="primary" :disabled="busy" @click="connect">{{ t('skyCommand.connect') }}</AppButton>
          <AppButton :disabled="busy" @click="desk.disconnect()">{{ t('skyCommand.disconnect') }}</AppButton>
        </div>
        <p class="sky-status">{{ t('skyCommand.status') }}：{{ t(`skyCommand.link.${desk.status.value}`) }}</p>
        <label class="sky-label" for="sky-device">{{ t('skyCommand.device') }}</label>
        <select id="sky-device" v-model="deviceId" class="sky-input">
          <option value="">{{ t('skyCommand.pickDevice') }}</option>
          <option v-for="device in desk.devices.value" :key="device.sessionId" :value="device.deviceId">{{ device.deviceId }}</option>
        </select>
        <div class="sky-actions">
          <AppButton :disabled="busy" @click="run('telemetry.read')">{{ t('skyCommand.telemetry') }}</AppButton>
          <AppButton :disabled="busy" @click="run('pairing.status')">{{ t('skyCommand.pairing') }}</AppButton>
          <AppButton :disabled="busy" @click="run('camera.photo.capture')">{{ t('skyCommand.capture') }}</AppButton>
          <AppButton :disabled="busy" @click="run('camera.photo.fetch')">{{ t('skyCommand.fetchPhotos') }}</AppButton>
          <AppButton variant="destructive" :disabled="busy" @click="run('flight.return-home')">{{ t('skyCommand.returnHome') }}</AppButton>
        </div>
        <label class="sky-label" for="sky-rtmp">{{ t('skyCommand.rtmp') }}</label>
        <div class="sky-row">
          <input id="sky-rtmp" v-model="rtmpUrl" class="sky-input" type="text" spellcheck="false" placeholder="rtmp://127.0.0.1/live" />
          <AppButton :disabled="busy" @click="run('live-stream.start')">{{ t('skyCommand.startStream') }}</AppButton>
          <AppButton :disabled="busy" @click="run('live-stream.stop')">{{ t('skyCommand.stopStream') }}</AppButton>
        </div>
        <label class="sky-label" for="sky-mission">{{ t('skyCommand.mission') }}</label>
        <input id="sky-mission" class="sky-input" type="file" accept=".kmz,application/vnd.google-earth.kmz" @change="onMission" />
        <p v-if="desk.progress.value" class="sky-progress" role="status">
          {{ t('skyCommand.progress', desk.progress.value) }}
        </p>
        <p v-if="notice" class="sky-notice" role="status">{{ notice }}</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.sky-page { padding: 28px 8px 48px; }
.sky-title { margin: 0 0 8px; font-size: 28px; font-weight: 650; }
.sky-lead { margin: 0 0 20px; max-width: 68ch; color: var(--ink-muted); line-height: 1.5; }
.sky-card { display: grid; gap: 12px; max-width: 820px; }
.sky-label { font-size: 13px; color: var(--ink-muted); }
.sky-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.sky-input { flex: 1; min-width: 220px; border: 1px solid var(--line-strong); border-radius: var(--radius-ctrl); background: var(--elevated); color: var(--ink); padding: 10px 12px; font: inherit; }
.sky-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.sky-status, .sky-progress, .sky-notice { margin: 0; color: var(--ink); }
.sky-notice { color: var(--ink-muted); }
</style>
