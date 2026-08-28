<script setup lang="ts">
/**
 * Animated hero: building footprints light up while a planned orbit/facade
 * route traces around them — stands in for a route-planning demo GIF.
 */
</script>

<template>
  <div class="route-hero" aria-hidden="true">
    <div class="route-hero__glow" />
    <svg class="route-hero__svg" viewBox="0 0 640 520" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="wl-building" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="#d8e4ef" />
          <stop offset="55%" stop-color="#9eb4c8" />
          <stop offset="100%" stop-color="#6a8499" />
        </linearGradient>
        <linearGradient id="wl-route" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#1a8f7a" />
          <stop offset="50%" stop-color="#3dcdc0" />
          <stop offset="100%" stop-color="#1a6fb5" />
        </linearGradient>
        <filter id="wl-soft" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="6" result="b" />
          <feMerge>
            <feMergeNode in="b" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
        <filter id="wl-glow">
          <feGaussianBlur stdDeviation="3.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      <!-- Ground plane -->
      <ellipse class="route-hero__ground" cx="320" cy="430" rx="250" ry="48" fill="#3a4652" opacity="0.75" />

      <!-- Building A (main) -->
      <g class="route-hero__building route-hero__building--a" filter="url(#wl-soft)">
        <polygon points="220,360 360,320 360,160 220,200" fill="url(#wl-building)" />
        <polygon points="360,320 430,350 430,190 360,160" fill="#7f96aa" />
        <polygon points="220,200 360,160 430,190 290,230" fill="#e8f0f6" />
        <g class="route-hero__windows" stroke="#eef5fb" stroke-width="1.2" opacity="0.7">
          <line x1="245" y1="230" x2="245" y2="340" />
          <line x1="275" y1="220" x2="275" y2="330" />
          <line x1="305" y1="210" x2="305" y2="320" />
          <line x1="335" y1="200" x2="335" y2="310" />
        </g>
      </g>

      <!-- Building B -->
      <g class="route-hero__building route-hero__building--b" filter="url(#wl-soft)">
        <polygon points="120,390 200,365 200,250 120,275" fill="#a8bac9" />
        <polygon points="200,365 245,385 245,270 200,250" fill="#7d92a5" />
        <polygon points="120,275 200,250 245,270 165,295" fill="#d7e3ed" />
      </g>

      <!-- Building C -->
      <g class="route-hero__building route-hero__building--c" filter="url(#wl-soft)">
        <polygon points="430,380 520,350 520,240 430,270" fill="#9aafc1" />
        <polygon points="520,350 560,370 560,260 520,240" fill="#6f869a" />
        <polygon points="430,270 520,240 560,260 470,290" fill="#d2e0eb" />
      </g>

      <!-- Highlight rings on facade targets -->
      <g class="route-hero__highlights" fill="none" stroke="#1f8f7c" stroke-width="2" filter="url(#wl-glow)">
        <ellipse class="route-hero__pulse" cx="290" cy="250" rx="18" ry="10" />
        <ellipse class="route-hero__pulse route-hero__pulse--delay" cx="480" cy="290" rx="14" ry="8" />
        <ellipse class="route-hero__pulse route-hero__pulse--delay2" cx="175" cy="310" rx="12" ry="7" />
      </g>

      <!-- Planned orbit + facade path -->
      <path
        class="route-hero__path-glow"
        d="M 165 300 C 140 250, 160 170, 250 145 C 340 120, 430 145, 500 200 C 560 250, 545 340, 470 365 C 390 392, 280 390, 210 350 C 175 328, 165 300, 165 300"
        fill="none"
        stroke="url(#wl-route)"
        stroke-width="10"
        opacity="0.22"
        stroke-linecap="round"
      />
      <path
        id="wl-route-path"
        class="route-hero__path"
        d="M 165 300 C 140 250, 160 170, 250 145 C 340 120, 430 145, 500 200 C 560 250, 545 340, 470 365 C 390 392, 280 390, 210 350 C 175 328, 165 300, 165 300"
        fill="none"
        stroke="url(#wl-route)"
        stroke-width="3.5"
        stroke-linecap="round"
        stroke-dasharray="14 10"
        filter="url(#wl-glow)"
      />

      <!-- Waypoints -->
      <g class="route-hero__waypoints" fill="#1a6fb5" stroke="#fff" stroke-width="1.5">
        <circle cx="165" cy="300" r="5" />
        <circle cx="250" cy="145" r="5" />
        <circle cx="500" cy="200" r="5" />
        <circle cx="470" cy="365" r="5" />
      </g>

      <!-- Moving craft -->
      <g class="route-hero__craft">
        <animateMotion dur="7s" repeatCount="indefinite" rotate="auto">
          <mpath href="#wl-route-path" />
        </animateMotion>
        <circle r="7" fill="#1f8f7c" stroke="#fff" stroke-width="2" />
        <circle r="14" fill="none" stroke="#3dcdc0" stroke-width="1.5" opacity="0.7">
          <animate attributeName="r" values="10;18;10" dur="1.6s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0.8;0.15;0.8" dur="1.6s" repeatCount="indefinite" />
        </circle>
      </g>
    </svg>
    <p class="route-hero__caption">建筑高亮 · 环绕 / 立面航线实时生成示意</p>
  </div>
</template>

<style scoped>
.route-hero {
  position: relative;
  width: min(100%, 640px);
  aspect-ratio: 640 / 520;
  margin-inline: auto;
}

.route-hero__glow {
  position: absolute;
  inset: 12% 8% 8%;
  border-radius: 40% 40% 36% 36%;
  background: radial-gradient(ellipse at 50% 40%, rgba(61, 205, 192, 0.2), transparent 68%);
  pointer-events: none;
}

.route-hero__svg {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: auto;
}

.route-hero__path {
  animation: wl-dash 2.4s linear infinite;
}

.route-hero__building--a {
  animation: wl-lift 5s ease-in-out infinite;
}

.route-hero__building--b {
  animation: wl-lift 5s ease-in-out infinite 0.4s;
}

.route-hero__building--c {
  animation: wl-lift 5s ease-in-out infinite 0.8s;
}

.route-hero__pulse {
  animation: wl-pulse 2.2s ease-in-out infinite;
}

.route-hero__pulse--delay {
  animation-delay: 0.5s;
}

.route-hero__pulse--delay2 {
  animation-delay: 1s;
}

.route-hero__caption {
  margin: 8px 0 0;
  text-align: center;
  font-size: 12px;
  letter-spacing: 0.04em;
  color: rgba(232, 238, 243, 0.45);
}

@keyframes wl-dash {
  to {
    stroke-dashoffset: -48;
  }
}

@keyframes wl-lift {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px);
  }
}

@keyframes wl-pulse {
  0%,
  100% {
    opacity: 0.35;
    stroke-width: 1.5;
  }
  50% {
    opacity: 1;
    stroke-width: 2.5;
  }
}

@media (prefers-reduced-motion: reduce) {
  .route-hero__path,
  .route-hero__building--a,
  .route-hero__building--b,
  .route-hero__building--c,
  .route-hero__pulse,
  .route-hero__craft {
    animation: none !important;
  }
}
</style>
