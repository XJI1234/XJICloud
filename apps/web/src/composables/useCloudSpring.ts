import { animate } from 'motion'

export type CloudSpringOptions = {
  bounce?: number
  duration?: number
}

/** Critically damped default — graceful settle, no overshoot */
export const CLOUD_SPRING_DEFAULT: CloudSpringOptions = {
  bounce: 0,
  duration: 0.35,
}

/** Slight bounce only for momentum-driven gestures */
export const CLOUD_SPRING_MOMENTUM: CloudSpringOptions = {
  bounce: 0.2,
  duration: 0.35,
}

export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function prefersReducedTransparency(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-reduced-transparency: reduce)').matches
}

function toMotionSpring(options: CloudSpringOptions = CLOUD_SPRING_DEFAULT) {
  if (prefersReducedMotion()) {
    return { duration: 0.2, ease: 'easeOut' as const }
  }
  return {
    type: 'spring' as const,
    bounce: options.bounce ?? 0,
    duration: options.duration ?? 0.35,
  }
}

/** Materialize a floating surface: opacity + scale from the current value */
export function materializeIn(
  el: Element,
  options: CloudSpringOptions = CLOUD_SPRING_DEFAULT,
) {
  if (prefersReducedMotion()) {
    return animate(el, { opacity: [0, 1] }, { duration: 0.2, ease: 'easeOut' })
  }
  return animate(
    el,
    { opacity: [0, 1], scale: [0.96, 1], filter: ['blur(8px)', 'blur(0px)'] },
    toMotionSpring(options),
  )
}

export function materializeOut(
  el: Element,
  options: CloudSpringOptions = CLOUD_SPRING_DEFAULT,
) {
  if (prefersReducedMotion()) {
    return animate(el, { opacity: [1, 0] }, { duration: 0.2, ease: 'easeOut' })
  }
  return animate(
    el,
    { opacity: [1, 0], scale: [1, 0.96], filter: ['blur(0px)', 'blur(8px)'] },
    toMotionSpring(options),
  )
}

export function springAnimate(
  el: Element,
  keyframes: Record<string, string | number | (string | number)[]>,
  options: CloudSpringOptions = CLOUD_SPRING_DEFAULT,
) {
  return animate(el, keyframes, toMotionSpring(options))
}

export function useCloudSpring() {
  return {
    prefersReducedMotion,
    prefersReducedTransparency,
    materializeIn,
    materializeOut,
    springAnimate,
    CLOUD_SPRING_DEFAULT,
    CLOUD_SPRING_MOMENTUM,
  }
}
