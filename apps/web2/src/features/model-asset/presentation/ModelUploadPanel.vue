<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import WaitOverlay from '@/presentation/components/WaitOverlay.vue'
import { useWaitSession } from '@/presentation/composables/useWaitSession'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { formatBytes } from '@/presentation/format'
import { formatDomainError } from '@/presentation/errors'
import { createTransferRateTracker, formatTransferSpeed } from '@/shared/transfer-rate'
import { saveBlobAsFile } from '@/presentation/save-blob'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import type { ModelAsset } from '@/features/model-asset/domain/entities/model-asset.entity'
import { sortModelsByUpdatedAtDesc } from '@/features/model-asset/domain/services/model-list.service'

type InFlightUpload = {
  localId: string
  fileName: string
  sizeBytes: number
  percent: number
  speed: string
  error: string
  abort: AbortController
  tracker: ReturnType<typeof createTransferRateTracker>
}

const props = defineProps<{
  projectId: string | null
  projectName: string
}>()

const { t } = useI18n()
const router = useRouter()
const modelsApi = useModelAssets()
const { formatDateTime } = useFormatDateTime()
const wait = useWaitSession()
const { waitView } = wait

const uploadInputRef = ref<HTMLInputElement | null>(null)
const models = ref<ModelAsset[]>([])
const inflight = ref<InFlightUpload[]>([])
const downloadById = ref<Record<string, true>>({})
const loading = ref(false)
const listError = ref('')
const actionError = ref('')

const sortedModels = computed(() => sortModelsByUpdatedAtDesc(models.value))
const hasRows = computed(() => inflight.value.length > 0 || sortedModels.value.length > 0)

async function loadModels() {
  if (!props.projectId) {
    models.value = []
    return
  }
  loading.value = true
  listError.value = ''
  const [error, data] = await modelsApi.list(props.projectId)
  loading.value = false
  if (error) {
    listError.value = formatDomainError(t, error)
    return
  }
  models.value = data ?? []
}

function triggerUpload() {
  actionError.value = ''
  if (!props.projectId) {
    actionError.value = t('upload.openProjectFirst')
    return
  }
  uploadInputRef.value?.click()
}

function patchInflight(localId: string, patch: Partial<InFlightUpload>) {
  inflight.value = inflight.value.map((item) => (item.localId === localId ? { ...item, ...patch } : item))
}

function dismissWait() {
  wait.hide()
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !props.projectId) {
    return
  }
  actionError.value = ''
  const localId = `upload-${Date.now()}-${file.name}`
  const row: InFlightUpload = {
    localId,
    fileName: file.name,
    sizeBytes: file.size,
    percent: 0,
    speed: '',
    error: '',
    abort: new AbortController(),
    tracker: createTransferRateTracker(),
  }
  inflight.value = [row, ...inflight.value]
  wait.showBusy(t('wait.uploading'), true)

  const [error, uploaded] = await modelsApi.upload({
    projectId: props.projectId,
    file,
    signal: row.abort.signal,
    onProgress: ({ loaded, total }) => {
      const percent = total > 0 ? Math.min(99, Math.round((loaded / total) * 100)) : 0
      const rate = row.tracker.push(loaded, Date.now())
      const speed = rate != null ? formatTransferSpeed(rate) : row.speed
      patchInflight(localId, {
        percent,
        speed,
      })
      wait.showProgress(t('wait.uploading'), percent, speed, true)
    },
  })

  if (row.abort.signal.aborted) {
    inflight.value = inflight.value.filter((item) => item.localId !== localId)
    wait.hide()
    return
  }
  if (error || !uploaded) {
    wait.hide()
    patchInflight(localId, {
      error: formatDomainError(t, error),
      speed: '',
    })
    return
  }
  inflight.value = inflight.value.filter((item) => item.localId !== localId)
  models.value = [uploaded, ...models.value.filter((item) => item.id !== uploaded.id)]
  wait.showDone(t('wait.done'), 100, row.speed)
}

function cancelUpload(row: InFlightUpload) {
  row.abort.abort()
  wait.hide()
}

function dismissFailed(localId: string) {
  inflight.value = inflight.value.filter((item) => item.localId !== localId)
}

function openModel(model: ModelAsset) {
  void router.push({ path: '/app/layer', query: { modelId: model.id } })
}

async function downloadModel(model: ModelAsset) {
  if (downloadById.value[model.id]) {
    return
  }
  actionError.value = ''
  const tracker = createTransferRateTracker()
  downloadById.value = {
    ...downloadById.value,
    [model.id]: true,
  }
  wait.showBusy(t('wait.downloading'), true)
  const [error, file] = await modelsApi.downloadToDisk(model, (loaded, total) => {
    const percent = total > 0 ? Math.min(99, Math.round((loaded / total) * 100)) : 0
    const rate = tracker.push(loaded, Date.now())
    wait.showProgress(
      t('wait.downloading'),
      percent,
      rate != null ? formatTransferSpeed(rate) : waitView.value.speed,
      true,
    )
  })
  const next = { ...downloadById.value }
  delete next[model.id]
  downloadById.value = next
  if (error || !file) {
    actionError.value = formatDomainError(t, error)
    wait.hide()
    return
  }
  saveBlobAsFile(file.blob, file.fileName)
  wait.showDone(t('wait.done'), 100, waitView.value.speed)
}

async function deleteModel(model: ModelAsset) {
  if (!window.confirm(t('upload.deleteModelConfirm'))) {
    return
  }
  actionError.value = ''
  const [error] = await modelsApi.remove(model.id)
  if (error) {
    actionError.value = formatDomainError(t, error)
    return
  }
  models.value = models.value.filter((item) => item.id !== model.id)
}

watch(
  () => props.projectId,
  () => {
    inflight.value.forEach((item) => item.abort.abort())
    inflight.value = []
    void loadModels()
  },
)

onMounted(() => {
  void loadModels()
})
</script>

<template>
  <section class="model-upload-panel">
    <p class="upload-tab-hint">{{ t('upload.modelHint') }}</p>
    <div class="upload-current-card">
      <h3 class="upload-project-name">{{ projectName }}</h3>
      <div class="upload-actions">
        <AppButton variant="primary" :disabled="!projectId" @click="triggerUpload">
          {{ t('upload.selectFileUpload') }}
        </AppButton>
        <AppButton @click="router.push('/app/projects')">{{ t('projects.backHome') }}</AppButton>
      </div>
    </div>

    <div class="model-upload-list cloud-card">
      <div class="training-job-header">
        <h3 class="section-title">{{ t('upload.modelListTitle') }}</h3>
        <AppButton :disabled="!projectId" @click="loadModels">{{ t('common.refresh') }}</AppButton>
      </div>
      <p v-if="!projectId" class="upload-empty-text">{{ t('upload.openProjectFirst') }}</p>
      <p v-else-if="loading && !hasRows" class="upload-empty-text">{{ t('common.loading') }}</p>
      <p v-else-if="listError" class="upload-error">{{ listError }}</p>
      <p v-else-if="!hasRows" class="upload-empty-text">{{ t('upload.noModels') }}</p>
      <div v-else class="training-job-list">
        <article v-for="row in inflight" :key="row.localId" class="training-job-item">
          <div class="training-job-item__header">
            <div>
              <strong>{{ row.fileName }}</strong>
              <p class="training-job-meta">{{ formatBytes(row.sizeBytes) }}</p>
            </div>
            <div class="training-job-item__header-actions">
              <span class="cloud-badge" :class="row.error ? 'cloud-badge--danger' : 'cloud-badge--warning'">
                {{ row.error ? t('upload.uploadFailed') : t('common.uploading') }}
              </span>
              <AppButton v-if="!row.error" compact @click="cancelUpload(row)">{{ t('upload.cancelUpload') }}</AppButton>
              <AppButton v-else compact @click="dismissFailed(row.localId)">{{ t('common.dismiss') }}</AppButton>
            </div>
          </div>
          <p v-if="row.error" class="upload-error">{{ row.error }}</p>
        </article>
        <article v-for="model in sortedModels" :key="model.id" class="training-job-item">
          <div class="training-job-item__header">
            <div>
              <strong>{{ model.fileName }}</strong>
              <p class="training-job-meta">
                {{ model.format }} · {{ formatBytes(model.sizeBytes) }} · {{ formatDateTime(model.updatedAt || model.createdAt) }}
              </p>
            </div>
            <div class="training-job-item__header-actions">
              <span class="cloud-badge cloud-badge--success">{{ t('upload.modelReady') }}</span>
              <AppButton compact variant="primary" @click="openModel(model)">{{ t('upload.viewModel') }}</AppButton>
              <AppButton compact :disabled="Boolean(downloadById[model.id])" @click="downloadModel(model)">
                {{ t('upload.downloadModel') }}
              </AppButton>
              <AppButton compact variant="destructive" @click="deleteModel(model)">{{ t('upload.deleteModel') }}</AppButton>
            </div>
          </div>
        </article>
      </div>
    </div>
    <p v-if="actionError" class="upload-error">{{ actionError }}</p>
    <input ref="uploadInputRef" class="visually-hidden" type="file" accept=".ply,.spz" @change="handleUpload" />
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
  </section>
</template>
