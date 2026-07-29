<script setup lang="ts">
import { ref } from 'vue'
import { ApiError } from '@/api/client'
import { uploadModel } from '@/api/models'

const props = defineProps<{
  projectId: string | null
  accept?: string
  label?: string
  pendingLabel?: string
  primary?: boolean
}>()

const emit = defineEmits<{
  success: [fileName: string]
  error: [message: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)
const pending = ref(false)

function trigger() {
  if (!props.projectId) {
    emit('error', '请先打开一个项目')
    return
  }
  inputRef.value?.click()
}

async function handleChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''

  if (!file || !props.projectId) {
    return
  }

  pending.value = true
  try {
    await uploadModel(props.projectId, file)
    emit('success', file.name)
  } catch (error) {
    emit('error', error instanceof ApiError ? error.message : '上传失败')
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="file-upload-button">
    <button
      class="cloud-btn"
      :class="{ 'cloud-btn--primary': primary !== false }"
      type="button"
      :disabled="pending || !projectId"
      @click="trigger"
    >
      {{ pending ? (pendingLabel ?? '上传中...') : (label ?? '选择文件上传') }}
    </button>
    <input
      ref="inputRef"
      class="visually-hidden"
      type="file"
      :accept="accept ?? '.ply,.spz'"
      @change="handleChange"
    />
  </div>
</template>
