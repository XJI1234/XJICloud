<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError } from '@/api/client'
import {
  completeDataset,
  createDataset,
  putToOss,
  type CreateDatasetResponse,
} from '@/api/datasets'
import { useTrainingJobStore } from '@/stores/trainingJob'
import { buildDatasetArchive, formatBytes, type DatasetArchive } from '@/utils/datasetArchive'

const props = defineProps<{
  projectId: string
}>()

const trainingJobStore = useTrainingJobStore()
const folderInputRef = ref<HTMLInputElement | null>(null)
const datasetName = ref('')
const archive = ref<DatasetArchive | null>(null)
const pending = ref(false)
const uploadProgress = ref(0)
const statusMessage = ref('')
const errorMessage = ref('')

const totalUploadBytes = computed(() => {
  if (!archive.value) {
    return 0
  }
  return archive.value.files.reduce((sum, file) => sum + file.sizeBytes, archive.value.manifestBlob.size)
})

function triggerFolderSelect() {
  folderInputRef.value?.click()
}

function handleFolderSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''

  errorMessage.value = ''
  statusMessage.value = ''

  try {
    archive.value = buildDatasetArchive(files)
    if (!datasetName.value.trim()) {
      datasetName.value = `数据集 ${new Date().toLocaleString('zh-CN')}`
    }
    statusMessage.value = `已归档 ${archive.value.files.length} 张图片，共 ${formatBytes(totalUploadBytes.value)}`
  } catch (error) {
    archive.value = null
    errorMessage.value = error instanceof Error ? error.message : '文件夹解析失败'
  }
}

async function uploadArchive() {
  if (!archive.value) {
    errorMessage.value = '请先选择图片文件夹'
    return
  }

  pending.value = true
  uploadProgress.value = 0
  errorMessage.value = ''
  statusMessage.value = '正在申请上传地址...'

  try {
    const response: CreateDatasetResponse = await createDataset(props.projectId, {
      name: datasetName.value.trim() || `数据集 ${new Date().toLocaleString('zh-CN')}`,
      files: archive.value.files.map(({ archivedName, originalName, contentType, sizeBytes }) => ({
        archivedName,
        originalName,
        contentType,
        sizeBytes,
      })),
    })

    let completedBytes = 0
    statusMessage.value = '正在上传图片到 OSS...'

    for (const fileItem of archive.value.files) {
      const uploadItem = response.uploads.find((item) => item.archivedName === fileItem.archivedName)
      if (!uploadItem) {
        throw new Error(`缺少 ${fileItem.archivedName} 的上传地址`)
      }

      await putToOss(uploadItem.uploadUrl, fileItem.file, fileItem.contentType, (loaded) => {
        uploadProgress.value = Math.min(
          100,
          Math.round(((completedBytes + loaded) / totalUploadBytes.value) * 100),
        )
      })
      completedBytes += fileItem.sizeBytes
      uploadProgress.value = Math.min(100, Math.round((completedBytes / totalUploadBytes.value) * 100))
    }

    const manifestUpload = response.uploads.find((item) => item.archivedName === 'manifest.json')
    if (manifestUpload) {
      const manifestSize = archive.value.manifestBlob.size
      await putToOss(
        manifestUpload.uploadUrl,
        archive.value.manifestBlob,
        'application/json',
        (loaded) => {
          uploadProgress.value = Math.min(
            100,
            Math.round(((completedBytes + loaded) / totalUploadBytes.value) * 100),
          )
        },
      )
      completedBytes += manifestSize
    }

    uploadProgress.value = 100
    statusMessage.value = '上传完成，正在提交训练任务...'

    const job = await completeDataset(props.projectId, response.jobId)
    trainingJobStore.upsertJob(job)
    trainingJobStore.watchJob(job.id)
    statusMessage.value = '训练任务已提交，可在下方查看实时进度'
    archive.value = null
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : (error instanceof Error ? error.message : '上传失败')
    statusMessage.value = ''
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <section class="cloud-card dataset-upload-panel">
    <div class="dataset-upload-header">
      <div>
        <p class="eyebrow">图片数据集</p>
        <h3 class="section-title">选择文件夹并归档上传</h3>
      </div>
      <span class="cloud-badge cloud-badge--info">JPG / PNG / WebP</span>
    </div>

    <label class="cloud-field">
      <span>数据集名称</span>
      <input v-model="datasetName" class="cloud-input" type="text" placeholder="例如：建筑外观采集 2026-06-21" />
    </label>

    <div class="dataset-upload-actions">
      <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="pending" @click="triggerFolderSelect">
        选择图片文件夹
      </button>
      <button
        class="cloud-btn cloud-btn--primary"
        type="button"
        :disabled="pending || !archive"
        @click="uploadArchive"
      >
        {{ pending ? '上传中...' : '开始上传并训练' }}
      </button>
    </div>

    <input
      ref="folderInputRef"
      class="visually-hidden"
      type="file"
      webkitdirectory
      directory
      multiple
      @change="handleFolderSelect"
    />

    <div v-if="archive" class="dataset-archive-summary">
      <p>已选择 {{ archive.files.length }} 张图片，总大小 {{ formatBytes(totalUploadBytes) }}</p>
      <ul class="dataset-file-list">
        <li v-for="file in archive.files.slice(0, 8)" :key="file.archivedName">
          {{ file.archivedName }} ← {{ file.originalName }}
        </li>
        <li v-if="archive.files.length > 8">... 另有 {{ archive.files.length - 8 }} 张</li>
      </ul>
    </div>

    <div v-if="pending || uploadProgress > 0" class="cloud-progress">
      <div class="cloud-progress-bar">
        <div class="cloud-progress-bar__fill" :style="{ width: `${uploadProgress}%` }" />
      </div>
      <span class="cloud-progress-label">{{ uploadProgress }}%</span>
    </div>

    <p v-if="statusMessage" class="upload-status">{{ statusMessage }}</p>
    <p v-if="errorMessage" class="upload-error">{{ errorMessage }}</p>
  </section>
</template>
