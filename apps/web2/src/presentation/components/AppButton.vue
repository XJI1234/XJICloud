<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'destructive'
    disabled?: boolean
    compact?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'ghost', disabled: false, compact: false, type: 'button' },
)
</script>

<template>
  <button
    class="app-btn"
    :class="[`app-btn--${props.variant}`, { 'app-btn--compact': props.compact }]"
    :type="props.type"
    :disabled="props.disabled"
  >
    <slot />
  </button>
</template>
