import { nextTick, watch, type Ref } from 'vue'

type Modal = { panel: HTMLElement; close: () => void }
const stack: Modal[] = []
const originalInert = new Map<HTMLElement, boolean>()
let originalOverflow = ''
const candidates = 'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'

function focusable(panel: HTMLElement) {
  return [...panel.querySelectorAll<HTMLElement>(candidates)].filter(el =>
    el.tabIndex >= 0 && !el.matches(':disabled') && !el.closest('[inert], [hidden]') && getComputedStyle(el).display !== 'none' && getComputedStyle(el).visibility !== 'hidden')
}
function focusFirst(panel: HTMLElement) { (focusable(panel)[0] ?? panel).focus() }
function refreshBackground() {
  originalInert.forEach((value, el) => { el.inert = value })
  originalInert.clear()
  const top = stack.at(-1)
  if (!top) return
  let root = top.panel
  while (root.parentElement && root.parentElement !== document.body) root = root.parentElement
  for (const child of document.body.children) {
    if (child instanceof HTMLElement && child !== root) {
      originalInert.set(child, child.inert)
      child.inert = true
    }
  }
}
function onKeydown(event: KeyboardEvent) {
  const top = stack.at(-1)
  if (!top) return
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopImmediatePropagation()
    top.close()
  } else if (event.key === 'Tab') {
    const elements = focusable(top.panel)
    const first = elements[0] ?? top.panel
    const last = elements.at(-1) ?? top.panel
    if (!elements.length || !top.panel.contains(document.activeElement) ||
        (event.shiftKey && (document.activeElement === first || document.activeElement === top.panel)) ||
        (!event.shiftKey && document.activeElement === last)) {
      event.preventDefault()
      ;(event.shiftKey ? last : first).focus()
    }
  }
}
function onFocus(event: FocusEvent) {
  const top = stack.at(-1)
  if (top && !top.panel.contains(event.target as Node)) focusFirst(top.panel)
}

export function useModalFocus(visible: () => boolean, panel: Ref<HTMLElement | null>, close: () => void) {
  watch(visible, async (open, _old, onCleanup) => {
    if (!open) return
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    let cancelled = false
    let modal: Modal | undefined
    onCleanup(() => {
      cancelled = true
      if (!modal) return
      const wasTop = stack.at(-1) === modal
      stack.splice(stack.indexOf(modal), 1)
      refreshBackground()
      if (!stack.length) {
        document.body.style.overflow = originalOverflow
        document.removeEventListener('keydown', onKeydown, true)
        document.removeEventListener('focusin', onFocus, true)
      }
      if (wasTop) {
        if (previous?.isConnected && !previous.closest('[inert]')) previous.focus()
        else if (stack.at(-1)) focusFirst(stack.at(-1)!.panel)
      }
    })
    await nextTick()
    if (cancelled || !panel.value) return
    if (!stack.length) {
      originalOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onKeydown, true)
      document.addEventListener('focusin', onFocus, true)
    }
    modal = { panel: panel.value, close }
    stack.push(modal)
    refreshBackground()
    focusFirst(modal.panel)
  }, { immediate: true, flush: 'post' })
}
