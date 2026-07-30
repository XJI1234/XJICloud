<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WaylineRouteHero from '@/components/WaylineRouteHero.vue'

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
    <div class="wayline-intro__panel">
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
        <button class="wayline-intro__cta" type="button" @click="enterPlanner">
          开始规划
        </button>
      </div>

      <div class="wayline-intro__visual">
        <img
          v-if="heroGifReady"
          class="wayline-intro__gif"
          :src="heroGifSrc"
          alt="航线沿建筑高亮生成示意"
        />
        <WaylineRouteHero v-else />
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;700;900&family=Sora:wght@500;600&display=swap');

.wayline-intro {
  --intro-accent: #3dcdc0;
  --intro-accent-deep: #2aa899;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: clamp(16px, 2.2vw, 28px);
  overflow: auto;
  font-family: 'Noto Sans SC', 'Sora', sans-serif;
}

.wayline-intro__panel {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(240px, 0.92fr) minmax(260px, 1.08fr);
  align-items: center;
  gap: clamp(20px, 3vw, 40px);
  width: 100%;
  min-height: min(100%, 640px);
  max-width: 1180px;
  margin: 0 auto;
  padding: clamp(20px, 3vw, 36px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background:
    radial-gradient(ellipse 70% 80% at 88% 42%, rgba(61, 205, 192, 0.1), transparent 58%),
    linear-gradient(160deg, rgba(30, 34, 42, 0.96), rgba(18, 20, 26, 0.98));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.wayline-intro__copy {
  min-width: 0;
}

.wayline-intro__eyebrow {
  margin: 0 0 18px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: var(--intro-accent);
}

.wayline-intro__logo {
  display: block;
  width: 22px;
  height: 22px;
  object-fit: cover;
  border-radius: 5px;
  opacity: 0.88;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1);
}

.wayline-intro__title {
  margin: 0;
  display: grid;
  gap: 0.06em;
  font-size: clamp(32px, 4.2vw, 52px);
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: 0.02em;
  color: #f3f6f8;
}

.wayline-intro__desc {
  margin: 16px 0 0;
  max-width: 32em;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(232, 238, 243, 0.62);
}

.wayline-intro__cta {
  margin-top: 28px;
  min-width: 148px;
  min-height: 44px;
  padding: 0 22px;
  border: 0;
  border-radius: 8px;
  background: var(--intro-accent);
  color: #0b1214;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 160ms ease, transform 160ms ease;
}

.wayline-intro__cta:hover {
  background: var(--intro-accent-deep);
  color: #fff;
  transform: translateY(-1px);
}

.wayline-intro__visual {
  display: grid;
  place-items: center;
  min-width: 0;
  padding: 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.wayline-intro__gif {
  display: block;
  width: min(100%, 520px);
  height: auto;
  border-radius: 8px;
  object-fit: cover;
}

@media (max-width: 960px) {
  .wayline-intro__panel {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .wayline-intro__visual {
    order: -1;
    max-width: 480px;
    margin-inline: auto;
  }
}
</style>
