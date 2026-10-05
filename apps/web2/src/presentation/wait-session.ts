export type WaitPhase = 'hidden' | 'busy' | 'progress' | 'done'

export type WaitView = {
  visible: boolean
  phase: WaitPhase
  title: string
  percent: number
  speed: string
  complete: boolean
  awaitConfirm: boolean
}

export function hiddenWait(): WaitView {
  return {
    visible: false,
    phase: 'hidden',
    title: '',
    percent: 0,
    speed: '',
    complete: false,
    awaitConfirm: false,
  }
}

export function busyWait(title: string, awaitConfirm = false): WaitView {
  return {
    visible: true,
    phase: 'busy',
    title,
    percent: 0,
    speed: '',
    complete: false,
    awaitConfirm,
  }
}

export function progressWait(
  title: string,
  percent: number,
  speed = '',
  awaitConfirm = false,
): WaitView {
  return {
    visible: true,
    phase: 'progress',
    title,
    percent: Math.max(0, Math.min(100, percent)),
    speed,
    complete: false,
    awaitConfirm,
  }
}

export function doneWait(title: string, percent = 100, speed = ''): WaitView {
  return {
    visible: true,
    phase: 'done',
    title,
    percent: Math.max(0, Math.min(100, percent)),
    speed,
    complete: true,
    awaitConfirm: true,
  }
}
