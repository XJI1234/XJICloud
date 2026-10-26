<script setup lang="ts">
import { nextTick, ref, watch, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { useModalFocus } from '@/presentation/composables/useModalFocus'
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
    cancellable?: boolean
  }>(),
  {
    percent: 0,
    speed: '',
    complete: false,
    awaitConfirm: false,
    cancellable: false,
  },
)

const emit = defineEmits<{
  dismiss: []
  cancel: []
}>()

const { t } = useI18n()
const panelRef = ref<HTMLElement | null>(null)
const showMeter = () => props.phase === 'progress' || props.phase === 'done'

function tryDismiss() {
  if (props.complete) {
    emit('dismiss')
  }
}

function tryCancel() {
  if (props.complete) emit('dismiss')
  else if (props.cancellable) emit('cancel')
}

const titleId = useId()
useModalFocus(() => props.visible, panelRef, tryCancel)
watch(() => props.visible, async (visible, _old, onCleanup) => {
  let cancelled = false
  onCleanup(() => { cancelled = true })
  if (!visible) return
  await nextTick()
  if (!cancelled && panelRef.value) materializeIn(panelRef.value)
}, { immediate: true })

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
          tabindex="-1"
          aria-modal="true"
          :aria-busy="!complete"
          :aria-labelledby="titleId"
          @click.stop
        >
          <div class="wait-panel__body">
            <WaitActivityMark v-if="phase === 'busy'" />
            <h2 :id="titleId" class="wait-panel__title">{{ title }}</h2>
            <UploadProgressBar v-if="showMeter()" :percent="percent" :speed="speed" />
          </div>
          <footer v-if="awaitConfirm || cancellable" class="wait-panel__footer">
            <AppButton :disabled="!complete && !cancellable" @click="tryCancel">{{ t('common.cancel') }}</AppButton>
            <AppButton variant="primary" :disabled="!complete" @click="tryDismiss">
              {{ t('common.confirm') }}
            </AppButton>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
