<script setup lang="ts">
import { computed, h } from 'vue'

const props = defineProps<{
  name: 'route' | 'upload' | 'view' | 'edit'
}>()

const icons = {
  route: () =>
    h('svg', { viewBox: '0 0 24 24', 'aria-hidden': 'true' }, [
      h('path', {
        d: 'M4 6h16v2H4V6zm0 5h10v2H4v-2zm0 5h16v2H4v-2z',
      }),
    ]),
  upload: () =>
    h('svg', { viewBox: '0 0 24 24', 'aria-hidden': 'true' }, [
      h('path', {
        d: 'M12 3l5 5h-3v6h-4V8H7l5-5zm-7 14h14v2H5v-2z',
      }),
    ]),
  view: () =>
    h('svg', { viewBox: '0 0 24 24', 'aria-hidden': 'true' }, [
      h('path', {
        d: 'M12 5c-5 0-9 4.5-9 7s4 7 9 7 9-4.5 9-7-4-7-9-7zm0 11a4 4 0 110-8 4 4 0 010 8z',
      }),
    ]),
  edit: () =>
    h('svg', { viewBox: '0 0 24 24', 'aria-hidden': 'true' }, [
      h('path', {
        d: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zm17.71-10.04a1 1 0 000-1.41l-2.5-2.5a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.99-1.67z',
      }),
    ]),
} as const

const icon = computed(() => icons[props.name])
</script>

<template>
  <component :is="icon" />
</template>
