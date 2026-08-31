<script setup lang="ts">
import { computed, ref } from 'vue'
import { useLoginCreatures } from '@/features/identity/presentation/composables/useLoginCreatures'

const props = defineProps<{
  curious: boolean
  hiding: boolean
  layoutTick: number
}>()

const root = ref<HTMLElement | null>(null)
const curious = computed(() => props.curious)
const hiding = computed(() => props.hiding)
const layoutTick = computed(() => props.layoutTick)

useLoginCreatures(root, curious, hiding, layoutTick)
</script>

<template>
  <div ref="root" class="login-creatures" aria-hidden="true">
    <div class="creature creature--purple" data-creature="purple">
      <div class="body" data-body>
        <div class="fill" data-fill />
        <div class="face" data-face>
          <span class="eye"><i class="pupil" data-max="5" /></span>
          <span class="eye"><i class="pupil" data-max="5" /></span>
        </div>
      </div>
    </div>
    <div class="creature creature--black" data-creature="black">
      <div class="body" data-body>
        <div class="fill" data-fill />
        <div class="face" data-face>
          <span class="eye"><i class="pupil" data-max="4" /></span>
          <span class="eye"><i class="pupil" data-max="4" /></span>
        </div>
      </div>
    </div>
    <div class="creature creature--orange" data-creature="orange">
      <div class="body" data-body>
        <div class="fill" data-fill />
        <div class="face" data-face>
          <i class="dot" data-max="5" />
          <i class="dot" data-max="5" />
        </div>
      </div>
    </div>
    <div class="creature creature--yellow" data-creature="yellow">
      <div class="body" data-body>
        <div class="fill" data-fill />
        <div class="face" data-face>
          <i class="dot" data-max="5" />
          <i class="dot" data-max="5" />
          <span class="mouth" />
        </div>
      </div>
    </div>
  </div>
</template>
