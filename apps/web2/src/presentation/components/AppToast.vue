<script setup lang="ts">
import { useComingSoonHint } from '@/presentation/coming-soon'

const { message } = useComingSoonHint()
</script>

<template>
  <Transition name="app-fade">
    <p v-if="message" class="app-toast" role="status">{{ message }}</p>
  </Transition>
</template>
