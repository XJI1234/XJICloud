import { ref } from 'vue'
import { createTransferRateTracker, formatTransferSpeed } from '@/shared/transfer-rate'

export function useTransferSpeed() {
  const tracker = createTransferRateTracker()
  const speedLabel = ref('')

  function resetSpeed() {
    tracker.reset()
    speedLabel.value = ''
  }

  function noteLoaded(loaded: number, atMs = Date.now()) {
    const rate = tracker.push(loaded, atMs)
    if (rate != null) {
      speedLabel.value = formatTransferSpeed(rate)
    }
  }

  return { speedLabel, noteLoaded, resetSpeed }
}
