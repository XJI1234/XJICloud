<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const iframeRef = ref<HTMLIFrameElement | null>(null)
const loading = ref(true)
/** Bust browser/iframe cache so Wayline always picks up latest /route/index.html → hashed JS. */
const waylineSrc = `/route/index.html?v=${Date.now()}`

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

function backToGate() {
  void router.push({ name: 'wayline' })
}
</script>

<template>
  <div class="wayline-page">
    <button class="wayline-back" type="button" title="返回航线前置页" @click="backToGate">
      返回
    </button>
    <iframe
      ref="iframeRef"
      class="wayline-iframe"
      :src="waylineSrc"
      title="航线规划"
      allow="fullscreen"
    />
    <p v-if="loading" class="wayline-loading">正在加载航线规划...</p>
  </div>
</template>
