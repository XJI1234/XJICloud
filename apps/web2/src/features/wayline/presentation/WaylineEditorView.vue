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
    <header class="wayline-editor-bar">
      <button class="wayline-back" type="button" @click="backToGate">
        <span class="wayline-back__icon" aria-hidden="true">←</span>
        <span>返回前置页</span>
      </button>
      <div class="wayline-editor-bar__meta">
        <strong>航线规划</strong>
        <span>三维采样编辑器</span>
      </div>
    </header>

    <div class="wayline-stage">
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
  </div>
</template>

<style scoped>
.wayline-page {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--surface, #f5f6f8);
}

.wayline-editor-bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line, rgba(28, 31, 36, 0.08));
  background: var(--elevated, #ffffff);
}

.wayline-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 12px 0 10px;
  border: 1px solid var(--line-strong, rgba(28, 31, 36, 0.14));
  border-radius: var(--radius-ctrl, 10px);
  background: transparent;
  color: var(--ink, #1c1f24);
  font: inherit;
  font-size: 13px;
  font-weight: 550;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease, color 140ms ease;
}

.wayline-back:hover {
  background: var(--accent-soft, rgba(61, 107, 138, 0.12));
  border-color: var(--accent, #3d6b8a);
  color: var(--accent, #3d6b8a);
}

.wayline-back:active {
  transform: scale(0.97);
}

.wayline-back__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.1em;
  font-size: 14px;
  line-height: 1;
}

.wayline-editor-bar__meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.wayline-editor-bar__meta strong {
  font-size: 14px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ink, #1c1f24);
}

.wayline-editor-bar__meta span {
  font-size: 12px;
  color: var(--ink-muted, #5c6370);
}

.wayline-stage {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  background: var(--map-chrome-bg, #dfe7ee);
}

.wayline-iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: transparent;
}

.wayline-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  margin: 0;
  padding: 24px;
  text-align: center;
  color: var(--ink-muted, #5c6370);
  background: color-mix(in srgb, var(--surface, #f5f6f8) 88%, transparent);
  pointer-events: none;
}

.wayline-loading--error {
  color: var(--danger, #b42318);
  line-height: 1.6;
}
</style>
