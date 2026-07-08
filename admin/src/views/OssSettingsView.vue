<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError } from '@/stores/auth'
import { fetchOssConfig, testOssConnection, updateOssConfig } from '@/api/adminClient'

const form = reactive({
  endpoint: '',
  region: '',
  bucket: '',
  accessKey: '',
  secretKey: '',
  pathStyleAccess: 'true',
})
const accessKeyHint = ref('')
const accessKeyConfigured = ref(false)
const secretKeyConfigured = ref(false)
const message = ref('')
const errorMessage = ref('')
const pending = ref(false)

onMounted(async () => {
  try {
    const response = await fetchOssConfig()
    form.endpoint = response.config.endpoint ?? ''
    form.region = response.config.region ?? ''
    form.bucket = response.config.bucket ?? ''
    form.pathStyleAccess = response.config.pathStyleAccess ?? 'true'
    accessKeyHint.value = response.config.accessKeyHint ?? ''
    accessKeyConfigured.value = response.config.accessKeyConfigured === 'true'
    secretKeyConfigured.value = response.config.secretKeyConfigured === 'true'
    form.accessKey = ''
    form.secretKey = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '加载 OSS 配置失败'
  }
})

function buildPayload() {
  const payload: Record<string, string> = {
    endpoint: form.endpoint,
    region: form.region,
    bucket: form.bucket,
    pathStyleAccess: form.pathStyleAccess,
  }
  if (form.accessKey.trim()) {
    payload.accessKey = form.accessKey.trim()
  }
  if (form.secretKey.trim()) {
    payload.secretKey = form.secretKey.trim()
  }
  return payload
}

async function save() {
  pending.value = true
  message.value = ''
  errorMessage.value = ''
  try {
    if (!accessKeyConfigured.value && !form.accessKey.trim()) {
      throw new Error('请填写完整的 Access Key')
    }
    if (!secretKeyConfigured.value && !form.secretKey.trim()) {
      throw new Error('请填写完整的 Secret Key')
    }
    const response = await updateOssConfig(buildPayload())
    message.value = 'OSS 配置已保存'
    accessKeyHint.value = response.config.accessKeyHint ?? ''
    accessKeyConfigured.value = response.config.accessKeyConfigured === 'true'
    secretKeyConfigured.value = response.config.secretKeyConfigured === 'true'
    form.accessKey = ''
    form.secretKey = ''
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : error instanceof Error ? error.message : '保存失败'
  } finally {
    pending.value = false
  }
}

async function testConnection() {
  message.value = ''
  errorMessage.value = ''
  try {
    await testOssConnection()
    message.value = 'OSS 连接测试成功'
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '连接测试失败'
  }
}
</script>

<template>
  <div>
    <p class="admin-hint">
      首次连接参数来自安装向导写入的配置；保存后将热更新到数据库，无需重启后端。
      测试连接使用已保存的密钥；修改 Access Key / Secret Key 后须先保存。
    </p>
    <h2 class="admin-page-title">OSS 对象存储</h2>
    <form class="admin-form" @submit.prevent="save">
      <label class="admin-field">
        <span>Endpoint</span>
        <input v-model="form.endpoint" class="admin-input" type="text" />
      </label>
      <label class="admin-field">
        <span>Region</span>
        <input v-model="form.region" class="admin-input" type="text" />
      </label>
      <label class="admin-field">
        <span>Bucket</span>
        <input v-model="form.bucket" class="admin-input" type="text" />
      </label>
      <label class="admin-field">
        <span>Access Key{{ accessKeyConfigured ? `（已配置 ${accessKeyHint}）` : '' }}</span>
        <input
          v-model="form.accessKey"
          class="admin-input"
          type="text"
          :placeholder="accessKeyConfigured ? `留空则保留 ${accessKeyHint}` : '填写 RAM AccessKey ID'"
          autocomplete="off"
        />
      </label>
      <label class="admin-field">
        <span>Secret Key{{ secretKeyConfigured ? '（已配置）' : '' }}</span>
        <input
          v-model="form.secretKey"
          class="admin-input"
          type="password"
          :placeholder="secretKeyConfigured ? '留空则不修改' : '填写 RAM AccessKey Secret'"
          autocomplete="new-password"
        />
      </label>
      <label class="admin-field">
        <span>Path Style Access</span>
        <select v-model="form.pathStyleAccess" class="admin-input">
          <option value="true">true（MinIO 推荐）</option>
          <option value="false">false（阿里云 OSS）</option>
        </select>
      </label>
      <div class="admin-actions">
        <button class="cloud-btn cloud-btn--primary" type="submit" :disabled="pending">保存配置</button>
        <button class="cloud-btn cloud-btn--ghost" type="button" @click="testConnection">测试连接</button>
      </div>
      <p v-if="message">{{ message }}</p>
      <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
    </form>
  </div>
</template>
