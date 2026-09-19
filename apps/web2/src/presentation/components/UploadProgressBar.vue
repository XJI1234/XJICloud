<script setup lang="ts">
defineProps<{
  percent: number
  speed?: string
}>()
</script>

<template>
  <div class="cloud-progress-block">
    <div class="cloud-progress">
      <div class="cloud-progress-bar">
        <div class="cloud-progress-bar__fill" :style="{ width: `${percent}%` }" />
      </div>
      <span>{{ percent }}%</span>
    </div>
    <p v-if="speed" class="cloud-progress-speed">{{ speed }}</p>
  </div>
</template>
