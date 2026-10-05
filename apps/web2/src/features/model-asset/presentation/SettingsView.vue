<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import { useFormatDateTime } from '@/presentation/composables/useAppLocale'
import { formatBytes } from '@/presentation/format'
import { formatDomainError } from '@/presentation/errors'
import { useModelAssets } from '@/features/model-asset/presentation/composables/useModelAssets'
import type { CachedModelFile } from '@/features/model-asset/domain/entities/model-asset.entity'

const { t } = useI18n()
const { formatDateTime } = useFormatDateTime()
const modelsApi = useModelAssets()

const entries = ref<CachedModelFile[]>([])
const selected = ref<Record<string, boolean>>({})
const loading = ref(false)
const errorMessage = ref('')
const statusMessage = ref('')

async function loadCache() {
  loading.value = true
  errorMessage.value = ''
  const [error, data] = await modelsApi.listCached()
  loading.value = false
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    entries.value = []
    return
  }
  entries.value = data ?? []
  const next: Record<string, boolean> = {}
  for (const entry of entries.value) {
    if (selected.value[entry.cacheKey]) {
      next[entry.cacheKey] = true
    }
  }
  selected.value = next
}

function toggle(cacheKey: string, checked: boolean) {
  selected.value = { ...selected.value, [cacheKey]: checked }
}

const selectedCount = computed(() => Object.values(selected.value).filter(Boolean).length)

async function removeSelected() {
  const keys = Object.entries(selected.value).filter(([, on]) => on).map(([key]) => key)
  if (keys.length === 0) {
    return
  }
  errorMessage.value = ''
  statusMessage.value = ''
  const [error] = await modelsApi.removeCached(keys)
  if (error) {
    errorMessage.value = formatDomainError(t, error)
    return
  }
  statusMessage.value = t('settings.removedCache', { count: keys.length })
  await loadCache()
}

onMounted(() => {
  void loadCache()
})
</script>

<template>
  <div class="help-page">
    <div class="cloud-page-inner">
      <h2 class="help-title">{{ t('settings.title') }}</h2>
      <p class="help-subtitle">{{ t('settings.subtitle') }}</p>
      <section class="cloud-card">
        <div class="training-job-header">
          <h3 class="section-title">{{ t('settings.cacheTitle') }}</h3>
          <div class="upload-actions">
            <AppButton @click="loadCache">{{ t('common.refresh') }}</AppButton>
            <AppButton variant="destructive" :disabled="selectedCount === 0" @click="removeSelected">
              {{ t('settings.deleteSelected') }}
            </AppButton>
          </div>
        </div>
        <p v-if="loading" class="help-text">{{ t('common.loading') }}</p>
        <p v-else-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
        <p v-else-if="entries.length === 0" class="help-text">{{ t('settings.cacheEmpty') }}</p>
        <div v-else class="training-job-list">
          <article v-for="entry in entries" :key="entry.cacheKey" class="training-job-item">
            <div class="training-job-item__header">
              <label class="settings-cache-row">
                <input
                  type="checkbox"
                  :checked="Boolean(selected[entry.cacheKey])"
                  @change="toggle(entry.cacheKey, ($event.target as HTMLInputElement).checked)"
                />
                <span>
                  <strong>{{ entry.fileName }}</strong>
                  <p class="training-job-meta">
                    {{ entry.versionId ? t('settings.historyFile') : t('settings.liveFile') }}
                    · {{ formatBytes(entry.sizeBytes) }}
                    · {{ formatDateTime(entry.cachedAt) }}
                  </p>
                </span>
              </label>
            </div>
          </article>
        </div>
        <p v-if="statusMessage" class="help-text">{{ statusMessage }}</p>
      </section>
    </div>
  </div>
</template>
