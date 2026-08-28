<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { prefersReducedMotion } from '@/shared/composables/useCloudSpring'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    mode?: 'center' | 'popover' | 'sheet'
    anchorEl?: HTMLElement | null
    labelledBy?: string
  }>(),
  {
    title: '',
    mode: 'center',
    anchorEl: null,
    labelledBy: undefined,
  },
)

const emit = defineEmits<{
  close: []
}>()

const panelRef = ref<HTMLElement | null>(null)
const popoverStyle = ref<Record<string, string>>({})
const isNarrow = ref(false)
const titleSuffix = Math.random().toString(36).slice(2, 9)

const effectiveMode = computed(() => {
  if (props.mode === 'popover' && isNarrow.value) {
    return 'sheet'
  }
  return props.mode
})

const backdropClass = computed(() => ({
  'cloud-sheet-backdrop': true,
  'cloud-sheet-backdrop--popover': effectiveMode.value === 'popover',
  'cloud-sheet-backdrop--sheet': effectiveMode.value === 'sheet',
}))

const panelClass = computed(() => ({
  'cloud-sheet__panel': true,
  'cloud-sheet__panel--popover': effectiveMode.value === 'popover',
  'cloud-sheet__panel--sheet': effectiveMode.value === 'sheet',
}))

const transitionName = computed(() => {
  if (prefersReducedMotion()) {
    return 'cloud-fade'
  }
  return effectiveMode.value === 'popover' ? 'cloud-popover' : 'cloud-sheet'
})

const titleId = computed(
  () => props.labelledBy ?? `cloud-sheet-title-${titleSuffix}`,
)

function updateNarrow() {
  isNarrow.value = typeof window !== 'undefined' && window.innerWidth <= 720
}

function positionPopover() {
  if (effectiveMode.value !== 'popover' || !props.anchorEl) {
    popoverStyle.value = {}
    return
  }

  const rect = props.anchorEl.getBoundingClientRect()
  const gap = 8
  const panelWidth = Math.min(280, window.innerWidth - 24)
  let left = rect.right - panelWidth
  left = Math.max(12, Math.min(left, window.innerWidth - panelWidth - 12))
  const top = Math.min(rect.bottom + gap, window.innerHeight - 24)
  const originX = Math.min(Math.max(rect.left + rect.width / 2 - left, 16), panelWidth - 16)

  popoverStyle.value = {
    top: `${top}px`,
    left: `${left}px`,
    transformOrigin: `${originX}px 0px`,
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.visible) {
    emit('close')
  }
}

function onResize() {
  updateNarrow()
  if (props.visible) {
    positionPopover()
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      updateNarrow()
      await nextTick()
      positionPopover()
      document.addEventListener('keydown', onKeydown)
      window.addEventListener('resize', onResize)
    } else {
      document.removeEventListener('keydown', onKeydown)
      window.removeEventListener('resize', onResize)
    }
  },
)

watch(
  () => [props.anchorEl, props.mode, isNarrow.value] as const,
  async () => {
    if (props.visible) {
      await nextTick()
      positionPopover()
    }
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <Teleport to="body">
    <Transition :name="transitionName">
      <div
        v-if="visible"
        :class="backdropClass"
        @click.self="emit('close')"
      >
        <div
          ref="panelRef"
          :class="panelClass"
          :style="effectiveMode === 'popover' ? popoverStyle : undefined"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="title ? titleId : undefined"
          @click.stop
        >
          <div v-if="title || $slots.header" class="cloud-sheet__header">
            <slot name="header">
              <h2 :id="titleId" class="cloud-sheet__title">{{ title }}</h2>
            </slot>
          </div>
          <div class="cloud-sheet__body">
            <slot />
          </div>
          <div v-if="$slots.footer" class="cloud-sheet__footer">
            <slot name="footer" />
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
