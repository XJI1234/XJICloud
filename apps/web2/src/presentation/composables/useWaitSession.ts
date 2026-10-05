import { ref } from 'vue'
import { busyWait, doneWait, hiddenWait, progressWait, type WaitView } from '@/presentation/wait-session'

export function useWaitSession() {
  const waitView = ref<WaitView>(hiddenWait())

  function showBusy(title: string, awaitConfirm = false) {
    waitView.value = busyWait(title, awaitConfirm)
  }

  function showProgress(title: string, percent: number, speed = '', awaitConfirm = false) {
    waitView.value = progressWait(title, percent, speed, awaitConfirm)
  }

  function showDone(title: string, percent = 100, speed = '') {
    waitView.value = doneWait(title, percent, speed)
  }

  function hide() {
    waitView.value = hiddenWait()
  }

  return { waitView, showBusy, showProgress, showDone, hide }
}
