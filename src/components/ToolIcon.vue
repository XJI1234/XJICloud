<script setup lang="ts">
import { computed, h } from 'vue'

const props = defineProps<{
  name: 'home' | 'route' | 'upload' | 'view' | 'edit'
}>()

const svgAttrs = {
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  'stroke-width': '1.5',
  'stroke-linecap': 'round' as const,
  'stroke-linejoin': 'round' as const,
  'aria-hidden': 'true',
}

const icons = {
  home: () =>
    h('svg', svgAttrs, [
      h('path', { d: 'M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1v-9.5z' }),
    ]),
  route: () =>
    h('svg', svgAttrs, [
      h('path', { d: 'M12 21s6-5.2 6-10a6 6 0 1 0-12 0c0 4.8 6 10 6 10z' }),
      h('circle', { cx: '12', cy: '11', r: '2', fill: 'currentColor', stroke: 'none' }),
      h('path', { d: 'M5 3l3 3M19 3l-3 3', 'stroke-width': '1.25' }),
    ]),
  upload: () =>
    h('svg', svgAttrs, [
      h('rect', { x: '3', y: '4', width: '18', height: '12', rx: '1.5' }),
      h('path', { d: 'M8 20h8' }),
      h('path', { d: 'M12 16v4' }),
      h('path', { d: 'M6 13l3-3 3 2 3-4 3 3', 'stroke-width': '1.75' }),
    ]),
  view: () =>
    h('svg', svgAttrs, [
      h('path', { d: 'M12 4 4 8.5v7L12 20l8-4.5v-7L12 4z' }),
      h('path', { d: 'M4 8.5 12 13l8-4.5M12 13v7' }),
    ]),
  edit: () =>
    h('svg', svgAttrs, [
      h('path', { d: 'M4 20h4l10-10-4-4L4 16v4z' }),
      h('path', { d: 'M13 6l4 4' }),
      h('path', { d: 'M3 21h18', 'stroke-width': '1.25' }),
    ]),
} as const

const icon = computed(() => icons[props.name])
</script>

<template>
  <component :is="icon" />
</template>
