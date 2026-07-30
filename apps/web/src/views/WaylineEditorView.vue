<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const iframeRef = ref<HTMLIFrameElement | null>(null)
const loading = ref(true)

/** Default VM host when the cloud UI is opened via Cursor port-forward (localhost). */
const DEFAULT_WAYLINE_HOST = '192.168.63.129'

/**
 * Resolve Wayline iframe URL.
 * Prefer nginx /route on the LAN host — never point at the developer's local
 * machine when the parent page is localhost (SSH / Cursor port forwarding).
 */
function resolveWaylineSrc() {
  const cacheBust = `v=${Date.now()}`
  const configured = String(import.meta.env.VITE_WAYLINE_ORIGIN || '').replace(/\/$/, '')
  if (configured) {
    return `${configured}/route/index.html?${cacheBust}`
  }

  if (typeof window !== 'undefined') {
    const { protocol, hostname, port } = window.location
    const isLoopback = hostname === 'localhost' || hostname === '127.0.0.1'
    // Dev on Vite, or any loopback parent: load Wayline from the VM nginx.
    if (import.meta.env.DEV || isLoopback || port === '5174') {
      const host = isLoopback ? DEFAULT_WAYLINE_HOST : hostname
      return `${protocol}//${host}/route/index.html?${cacheBust}`
    }
  }

  return `/route/index.html?${cacheBust}`
}

const waylineSrc = resolveWaylineSrc()

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
      referrerpolicy="no-referrer-when-downgrade"
    />
    <p v-if="loading" class="wayline-loading">正在加载航线规划...</p>
  </div>
</template>
