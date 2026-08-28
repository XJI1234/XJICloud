<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const iframeRef = ref<HTMLIFrameElement | null>(null)
const loading = ref(true)
const loadError = ref('')

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

  iframe.addEventListener(
    'load',
    () => {
      loading.value = false
      // Same-origin only: detect when /route/ is misconfigured and serves the cloud SPA.
      try {
        const doc = iframe.contentDocument
        const title = doc?.title?.trim() ?? ''
        const hasCesium = Boolean(doc?.querySelector('#cesiumContainer, .cesium-viewer, [data-wayline-root]'))
        if (doc && title && /xji\s*cloud|云平台/i.test(title) && !hasCesium) {
          loadError.value = '航线规划页面未正确部署（/route/ 指向了云平台）。请联系运维将 Wayline 静态资源同步到服务器 /route/。'
        }
      } catch {
        // Cross-origin iframe — cannot inspect; assume load succeeded.
      }
    },
    { once: true },
  )
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
    <p v-else-if="loadError" class="wayline-loading wayline-loading--error">{{ loadError }}</p>
  </div>
</template>

<style scoped>
.wayline-page {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #0a0b0d;
}

.wayline-back {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 5;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 6px;
  background: rgba(12, 14, 18, 0.72);
  color: #e8eef3;
  font-size: 13px;
  cursor: pointer;
  backdrop-filter: blur(8px);
}

.wayline-back:hover {
  border-color: rgba(61, 205, 192, 0.55);
}

.wayline-iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #0a0b0d;
}

.wayline-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  padding: 24px;
  text-align: center;
  color: rgba(232, 238, 243, 0.7);
  background: rgba(10, 11, 13, 0.78);
  pointer-events: none;
}

.wayline-loading--error {
  color: #f0a8a8;
  max-width: none;
  place-items: center;
  line-height: 1.6;
}
</style>
