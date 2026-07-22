<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { createDownloadToken, listModels, uploadExport, type ModelSummary } from '@/api/models'
import {
  CLOUD_SAVE_DONE,
  CLOUD_SAVE_ERROR,
  CLOUD_SAVE_REQUEST,
  isDirty,
  loadModelInIframe,
} from '@/bridges/supersplatBridge'
import { useProjectStore } from '@/stores/project'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const { t } = useI18n()

const iframeRef = ref<HTMLIFrameElement | null>(null)
const models = ref<ModelSummary[]>([])
const selectedModelId = ref<string | null>(null)
const loadingModels = ref(false)
const loadingEditor = ref(false)
const errorMessage = ref('')

async function ensureProjects() {
  try {
    await projectStore.fetchProjects()
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('supersplat.loadProjectsFailed')
  }
}

async function refreshModels() {
  if (!projectStore.activeProjectId) {
    models.value = []
    selectedModelId.value = null
    return
  }

  loadingModels.value = true
  errorMessage.value = ''

  try {
    models.value = await listModels(projectStore.activeProjectId)
    const queryModelId = typeof route.query.modelId === 'string' ? route.query.modelId : null
    if (queryModelId && models.value.some((model) => model.id === queryModelId)) {
      selectedModelId.value = queryModelId
    } else if (!selectedModelId.value || !models.value.some((model) => model.id === selectedModelId.value)) {
      selectedModelId.value = models.value[0]?.id ?? null
    }
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('supersplat.loadModelsFailed')
    models.value = []
  } finally {
    loadingModels.value = false
  }
}

async function loadEditor() {
  const model = models.value.find((item) => item.id === selectedModelId.value)
  const iframe = iframeRef.value
  if (!model || !iframe) {
    return
  }

  loadingEditor.value = true
  errorMessage.value = ''

  try {
    const token = await createDownloadToken(model.id)
    loadModelInIframe(iframe, {
      signedUrl: token.url,
      fileName: model.fileName,
      modelId: model.id,
    })
    await router.replace({
      path: route.path,
      query: { ...route.query, modelId: model.id },
    })
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : t('supersplat.loadEditorFailed')
  } finally {
    loadingEditor.value = false
  }
}

async function handleCloudSaveRequest(event: MessageEvent) {
  const data = event.data as {
    type?: string
    modelId?: string
    fileName?: string
    buffer?: ArrayBuffer
  }

  if (data?.type !== CLOUD_SAVE_REQUEST || !data.modelId || !data.fileName || !data.buffer) {
    return
  }

  const source = event.source as Window | null
  if (!source) {
    return
  }

  try {
    const blob = new Blob([data.buffer], { type: 'application/octet-stream' })
    await uploadExport(data.modelId, blob, data.fileName)
    source.postMessage({ type: CLOUD_SAVE_DONE }, event.origin)
    await refreshModels()
  } catch (error) {
    source.postMessage({
      type: CLOUD_SAVE_ERROR,
      message: error instanceof Error ? error.message : t('supersplat.saveToCloudFailed'),
    }, event.origin)
  }
}

function goToProjects() {
  router.push('/app/projects')
}

function goToHome() {
  router.push('/app/home')
}

watch(
  () => projectStore.activeProjectId,
  async () => {
    await refreshModels()
  },
)

watch(selectedModelId, async (modelId, previousId) => {
  if (modelId && modelId !== previousId) {
    await loadEditor()
  }
})

onMounted(async () => {
  window.addEventListener('message', handleCloudSaveRequest)
  await ensureProjects()
  await refreshModels()
  if (selectedModelId.value) {
    await loadEditor()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleCloudSaveRequest)
})

onBeforeRouteLeave(async (_to, _from, next) => {
  const iframe = iframeRef.value
  if (!iframe?.contentWindow) {
    next()
    return
  }

  try {
    const dirty = await isDirty(iframe)
    if (dirty && !window.confirm(t('supersplat.leaveConfirm'))) {
      next(false)
      return
    }
  } catch {
    // ignore handshake errors when leaving
  }

  next()
})
</script>

<template>
  <div class="supersplat-page">
    <section v-if="projectStore.activeProject && selectedModelId && !errorMessage" class="supersplat-stage">
      <iframe
        ref="iframeRef"
        class="supersplat-iframe"
        :title="t('supersplat.title')"
        allow="fullscreen"
      />
      <p v-if="loadingEditor" class="supersplat-loading">{{ t('supersplat.loadingEditor') }}</p>
    </section>

    <section v-else-if="projectStore.activeProject && !loadingModels" class="supersplat-empty section-card">
      <h3 class="section-title">{{ errorMessage || t('supersplat.noModels') }}</h3>
      <p v-if="!errorMessage">{{ t('supersplat.uploadHint') }}</p>
      <button class="side-button primary" type="button" @click="router.push('/app/upload')">{{ t('supersplat.goToUpload') }}</button>
    </section>

    <section v-else-if="!projectStore.activeProject && !loadingModels" class="supersplat-empty section-card">
      <h3 class="section-title">{{ errorMessage || t('supersplat.noProjectOpen') }}</h3>
      <p v-if="!errorMessage">{{ t('supersplat.noProjectHint') }}</p>
      <div class="supersplat-empty-actions">
        <button class="side-button primary" type="button" @click="goToHome">{{ t('supersplat.goToHome') }}</button>
        <button class="side-button" type="button" @click="goToProjects">{{ t('supersplat.goToProjects') }}</button>
      </div>
    </section>

    <p v-else class="supersplat-loading">{{ t('supersplat.preparing') }}</p>
  </div>
</template>
