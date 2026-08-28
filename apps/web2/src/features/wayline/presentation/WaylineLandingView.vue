<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '@/presentation/components/AppButton.vue'
import WaylineRouteHero from './components/WaylineRouteHero.vue'

const router = useRouter()
const heroGifReady = ref(false)
/** Public URL built at runtime so Vite does not rewrite it as a module import. */
const heroGifSrc = ['/wayline-route-hero', '.gif'].join('')

onMounted(() => {
  const probe = new Image()
  probe.onload = () => {
    heroGifReady.value = true
  }
  probe.onerror = () => {
    heroGifReady.value = false
  }
  probe.src = `${heroGifSrc}?v=${Date.now()}`
})

function enterPlanner() {
  void router.push({ name: 'wayline-editor' })
}
</script>

<template>
  <div class="wayline-intro">
    <div class="wayline-intro__grid">
      <div class="wayline-intro__copy">
        <p class="wayline-intro__eyebrow">
          <img class="wayline-intro__logo" src="/logo.jpg" alt="" />
          <span>玄境创新 · 航线规划</span>
        </p>

        <h1 class="wayline-intro__title">
          <span>智能航线</span>
          <span>三维采样</span>
        </h1>
        <p class="wayline-intro__desc">
          环绕与立面采样、信息增益补拍，为 3D 高斯泼溅采集高质量视角素材。
        </p>
        <div class="wayline-intro__actions">
          <AppButton variant="primary" @click="enterPlanner">开始规划</AppButton>
        </div>
      </div>

      <div class="wayline-intro__visual">
        <div class="wayline-intro__visual-mist" aria-hidden="true" />
        <img
          v-if="heroGifReady"
          class="wayline-intro__gif"
          :src="heroGifSrc"
          alt="航线沿建筑高亮生成示意"
        />
        <WaylineRouteHero v-else />
      </div>
    </div>
    <img class="wayline-intro__mark" src="/logo_nw.png" alt="" aria-hidden="true" />
  </div>
</template>

<style scoped>
.wayline-intro {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  font-family: var(--font);
  color: var(--ink);
  background: var(--surface);
}

.wayline-intro__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(240px, 0.95fr) minmax(280px, 1.05fr);
  align-items: center;
  gap: clamp(28px, 4.5vw, 56px);
  width: 100%;
  max-width: 1180px;
  min-height: min(100%, 640px);
  margin: 0 auto;
  padding: clamp(28px, 4.5vw, 48px) clamp(28px, 5vw, 56px) 100px;
}

.wayline-intro__copy {
  min-width: 0;
  padding-top: 4px;
}

.wayline-intro__eyebrow {
  margin: 0 0 18px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px 6px 6px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--elevated);
  box-shadow: var(--shadow-soft);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.04em;
  color: var(--accent);
}

.wayline-intro__logo {
  display: block;
  width: 22px;
  height: 22px;
  object-fit: cover;
  border-radius: 50%;
  box-shadow: 0 0 0 1px var(--line);
}

.wayline-intro__title {
  margin: 0;
  display: grid;
  gap: 0.04em;
  max-width: 10em;
  font-size: clamp(2rem, 2.4vw + 1rem, 3rem);
  font-weight: 650;
  line-height: 1.12;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.wayline-intro__desc {
  margin: 16px 0 0;
  max-width: 32em;
  font-size: 16px;
  line-height: 1.55;
  color: var(--ink-muted);
}

.wayline-intro__actions {
  display: flex;
  gap: 10px;
  margin-top: 28px;
}

.wayline-intro__visual {
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 280px;
  padding: clamp(16px, 2.5vw, 28px);
  border: 1px solid var(--line);
  border-radius: var(--radius-sheet);
  background:
    radial-gradient(ellipse at 30% 20%, rgba(61, 107, 138, 0.18), transparent 52%),
    linear-gradient(160deg, #e8eef3 0%, #d5e0e9 45%, #f2f4f6 100%);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.wayline-intro__visual-mist {
  position: absolute;
  inset: 10% 14% auto 10%;
  height: 42%;
  border-radius: 40% 60% 50% 50%;
  background: rgba(255, 255, 255, 0.55);
  filter: blur(22px);
  pointer-events: none;
}

.wayline-intro__gif {
  position: relative;
  z-index: 1;
  display: block;
  width: min(100%, 520px);
  height: auto;
  border-radius: var(--radius-ctrl);
  object-fit: cover;
}

.wayline-intro__mark {
  position: absolute;
  left: 50%;
  bottom: clamp(40px, 7vh, 80px);
  z-index: 0;
  width: min(38vw, 320px);
  height: auto;
  transform: translateX(-50%);
  opacity: 0.1;
  pointer-events: none;
  user-select: none;
  mix-blend-mode: multiply;
}

@media (prefers-reduced-transparency: reduce) {
  .wayline-intro__mark {
    mix-blend-mode: normal;
    opacity: 0.05;
  }
}

@media (max-width: 960px) {
  .wayline-intro__grid {
    grid-template-columns: 1fr;
    min-height: 0;
    padding-bottom: 72px;
  }

  .wayline-intro__visual {
    order: -1;
    max-width: 520px;
    margin-inline: auto;
  }
}
</style>
