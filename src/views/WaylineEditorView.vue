<script setup lang="ts">
import { onMounted, ref } from 'vue'

const iframeRef = ref<HTMLIFrameElement | null>(null)
const loading = ref(true)

onMounted(() => {
  const iframe = iframeRef.value
  if (!iframe) {
    loading.value = false
    return
  }

  iframe.addEventListener('load', () => {
    loading.value = false
  }, { once: true })
})
</script>

<template>
  <div class="wayline-page">
    <iframe
      ref="iframeRef"
      class="wayline-iframe"
      src="/route/index.html"
      title="航线规划"
      allow="fullscreen"
    />
    <p v-if="loading" class="wayline-loading">正在加载航线规划...</p>
  </div>
</template>
