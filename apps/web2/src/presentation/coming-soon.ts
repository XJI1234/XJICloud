import { ref } from 'vue'

const message = ref('')
let hideTimer: number | undefined

export function useComingSoonHint() {
  return { message }
}

export function showComingSoon(text: string) {
  message.value = text
  if (hideTimer) {
    window.clearTimeout(hideTimer)
  }
  hideTimer = window.setTimeout(() => {
    message.value = ''
  }, 2800)
}
