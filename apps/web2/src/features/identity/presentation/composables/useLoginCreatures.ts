import { onMounted, onUnmounted, watch, type Ref } from 'vue'

type Spring = {
  x: number
  v: number
  target: number
  omega: number
  zeta: number
  set: (target: number) => void
  step: (dt: number) => void
}

function createSpring(response: number, zeta: number): Spring {
  return {
    x: 0,
    v: 0,
    target: 0,
    omega: (2 * Math.PI) / response,
    zeta,
    set(target: number) {
      this.target = target
    },
    step(dt: number) {
      const accel = -2 * this.zeta * this.omega * this.v - this.omega * this.omega * (this.x - this.target)
      this.v += accel * dt
      this.x += this.v * dt
    },
  }
}

const LEAN = {
  purple: { peekSkew: -12, avertSkew: 9, peekX: 36, avertX: 16, mouse: 120 },
  black: { peekSkew: -6, avertSkew: 11, peekX: 12, avertX: 10, mouse: 110 },
  orange: { peekSkew: -4, avertSkew: 6, peekX: 8, avertX: 8, mouse: 150 },
  yellow: { peekSkew: -4, avertSkew: 6, peekX: 10, avertX: 8, mouse: 140 },
} as const

type CreatureKind = keyof typeof LEAN

export function useLoginCreatures(
  root: Ref<HTMLElement | null>,
  curious: Ref<boolean>,
  hiding: Ref<boolean>,
  layoutTick: Ref<number>,
) {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const curiosity = createSpring(0.34, 1)
  const privacy = createSpring(0.3, 1)
  const neck = createSpring(0.38, 1)
  const mouse = { x: window.innerWidth * 0.72, y: window.innerHeight * 0.42 }
  const rest = new Map<HTMLElement, { cx: number; cy: number; h: number }>()
  let frame = 0
  let last = performance.now()
  const blinkTimers: number[] = []

  function creatures() {
    return [...(root.value?.querySelectorAll<HTMLElement>('[data-creature]') ?? [])]
  }

  function measureRest() {
    creatures().forEach((el) => {
      const body = el.querySelector<HTMLElement>('[data-body]')
      const fill = el.querySelector<HTMLElement>('[data-fill]')
      if (!body || !fill) return
      const prevBody = body.style.transform
      const prevFill = fill.style.transform
      body.style.transform = 'none'
      fill.style.transform = 'none'
      const box = el.getBoundingClientRect()
      rest.set(el, {
        cx: box.left + box.width / 2,
        cy: box.top + box.height / 3,
        h: box.height,
      })
      body.style.transform = prevBody
      fill.style.transform = prevFill
    })
  }

  function onPointerMove(event: PointerEvent) {
    mouse.x = event.clientX
    mouse.y = event.clientY
  }

  function tick(now: number) {
    const dt = Math.min(0.032, (now - last) / 1000) || 0.016
    last = now
    curiosity.set(curious.value ? 1 : 0)
    privacy.set(hiding.value ? 1 : 0)
    neck.set(curious.value ? 1 : 0)
    if (reduced) {
      curiosity.x = curiosity.target
      privacy.x = privacy.target
      neck.x = neck.target
    } else {
      curiosity.step(dt)
      privacy.step(dt)
      neck.step(dt)
    }

    const peek = curiosity.x
    const avert = privacy.x

    creatures().forEach((creature) => {
      const kind = creature.dataset.creature as CreatureKind
      const amount = LEAN[kind]
      if (!amount) return
      const origin = rest.get(creature) || { cx: 0, cy: 0, h: 0 }
      const mouseSkew = Math.max(-6, Math.min(6, -(mouse.x - origin.cx) / amount.mouse))
      const skew = mouseSkew + amount.peekSkew * peek + amount.avertSkew * avert
      const tx = amount.peekX * peek - amount.avertX * avert
      const stretch = kind === 'purple' ? 1 + 0.32 * neck.x : 1
      const slim = kind === 'purple' ? 1 - 0.22 * neck.x : 1
      const body = creature.querySelector<HTMLElement>('[data-body]')
      const fill = creature.querySelector<HTMLElement>('[data-fill]')
      const face = creature.querySelector<HTMLElement>('[data-face]')
      if (!body || !fill || !face) return
      body.style.transform = `translate3d(${tx}px, 0, 0) skewX(${skew}deg) scaleX(${slim})`
      fill.style.transform = Math.abs(stretch - 1) < 0.004 ? 'none' : `scaleY(${stretch})`

      const follow = 1 - Math.min(1, avert * 1.2)
      const lift = (stretch - 1) * origin.h
      const faceX = Math.max(-12, Math.min(12, ((mouse.x - origin.cx - tx) / 24) * follow))
      const faceY = Math.max(-9, Math.min(9, ((mouse.y - origin.cy) / 34) * follow + avert * 3)) - lift
      face.style.transform = `translate3d(${faceX}px, ${faceY}px, 0)`

      creature.querySelectorAll<HTMLElement>('.pupil, .dot').forEach((dot) => {
        const max = Number(dot.dataset.max)
        let px: number
        let py: number
        if (avert > 0.12) {
          px = -max
          py = 1
        } else {
          const box = dot.getBoundingClientRect()
          const dx = mouse.x - (box.left + box.width / 2)
          const dy = mouse.y - (box.top + box.height / 2)
          const dist = Math.min(Math.hypot(dx, dy), max)
          const angle = Math.atan2(dy, dx)
          px = Math.cos(angle) * dist
          py = Math.sin(angle) * dist
        }
        if (dot.classList.contains('pupil')) {
          dot.style.transform = `translate3d(calc(-50% + ${px}px), calc(-50% + ${py}px), 0)`
        } else {
          dot.style.transform = `translate3d(${px}px, ${py}px, 0)`
        }
      })
    })

    frame = requestAnimationFrame(tick)
  }

  function blinkLoop(selector: string) {
    const pulse = () => {
      const eyes = root.value?.querySelectorAll(selector)
      eyes?.forEach((eye) => eye.classList.add('is-blink'))
      window.setTimeout(() => {
        eyes?.forEach((eye) => eye.classList.remove('is-blink'))
      }, 120)
      blinkTimers.push(window.setTimeout(pulse, 2800 + Math.random() * 4200))
    }
    blinkTimers.push(window.setTimeout(pulse, 1600 + Math.random() * 2200))
  }

  onMounted(() => {
    measureRest()
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    window.addEventListener('resize', measureRest)
    blinkLoop('.creature--purple .eye')
    blinkLoop('.creature--black .eye')
    frame = requestAnimationFrame(tick)
  })

  onUnmounted(() => {
    cancelAnimationFrame(frame)
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('resize', measureRest)
    blinkTimers.forEach((id) => clearTimeout(id))
  })

  watch(layoutTick, () => {
    requestAnimationFrame(measureRest)
  })
}
