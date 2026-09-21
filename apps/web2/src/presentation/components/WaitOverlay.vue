<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AppButton from '@/presentation/components/AppButton.vue'
import UploadProgressBar from '@/presentation/components/UploadProgressBar.vue'
import WaitActivityMark from '@/presentation/components/WaitActivityMark.vue'
import { materializeIn } from '@/presentation/motion'
import type { WaitPhase } from '@/presentation/wait-session'

const props = withDefaults(
  defineProps<{
    visible: boolean
    phase: WaitPhase
    title: string
    percent?: number
    speed?: string
    complete?: boolean
    awaitConfirm?: boolean
  }>(),
  {
    percent: 0,
    speed: '',
    complete: false,
    awaitConfirm: false,
  },
)

const emit = defineEmits<{
  dismiss: []
}>()

const { t } = useI18n()
const panelRef = ref<HTMLElement | null>(null)
const showMeter = () => props.phase === 'progress' || props.phase === 'done'

function tryDismiss() {
  if (props.complete) {
    emit('dismiss')
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.visible && props.complete) {
    emit('dismiss')
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      document.addEventListener('keydown', onKeydown)
      await nextTick()
      if (panelRef.value) {
        materializeIn(panelRef.value)
      }
    } else {
      document.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="app-fade">
      <div
        v-if="visible"
        class="wait-backdrop"
        role="presentation"
        @click.self="tryDismiss"
      >
        <div
          ref="panelRef"
          class="wait-panel"
          :class="{ 'wait-panel--busy': phase === 'busy' }"
          role="dialog"
          aria-modal="true"
          :aria-busy="!complete"
          :aria-labelledby="'wait-overlay-title'"
          @click.stop
        >
          <div class="wait-panel__body">
            <WaitActivityMark v-if="phase === 'busy'" />
            <h2 id="wait-overlay-title" class="wait-panel__title">{{ title }}</h2>
            <UploadProgressBar v-if="showMeter()" :percent="percent" :speed="speed" />
          </div>
          <footer v-if="awaitConfirm" class="wait-panel__footer">
            <AppButton :disabled="!complete" @click="tryDismiss">{{ t('common.cancel') }}</AppButton>
            <AppButton variant="primary" :disabled="!complete" @click="tryDismiss">
              {{ t('common.confirm') }}
            </AppButton>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
