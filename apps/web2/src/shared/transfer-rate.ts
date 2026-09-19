export type TransferSample = {
  loaded: number
  atMs: number
}

const DEFAULT_WINDOW_MS = 800
const MIN_DELTA_MS = 80

export function createTransferRateTracker(windowMs = DEFAULT_WINDOW_MS) {
  let samples: TransferSample[] = []

  return {
    reset() {
      samples = []
    },
    push(loaded: number, atMs: number): number | null {
      if (!Number.isFinite(loaded) || loaded < 0) {
        return null
      }
      const last = samples[samples.length - 1]
      if (last && loaded < last.loaded) {
        samples = [{ loaded, atMs }]
        return null
      }
      samples.push({ loaded, atMs })
      const cutoff = atMs - windowMs
      while (samples.length > 2 && samples[0].atMs < cutoff) {
        samples.shift()
      }
      if (samples.length < 2) {
        return null
      }
      const first = samples[0]
      const latest = samples[samples.length - 1]
      const elapsedMs = latest.atMs - first.atMs
      const deltaBytes = latest.loaded - first.loaded
      if (elapsedMs < MIN_DELTA_MS || deltaBytes < 0) {
        return null
      }
      return (deltaBytes / elapsedMs) * 1000
    },
  }
}

export function formatTransferSpeed(bytesPerSecond: number) {
  if (!Number.isFinite(bytesPerSecond) || bytesPerSecond < 0) {
    return ''
  }
  if (bytesPerSecond < 1024) {
    return `${Math.round(bytesPerSecond)} B/s`
  }
  if (bytesPerSecond < 1024 * 1024) {
    return `${(bytesPerSecond / 1024).toFixed(1)} KB/s`
  }
  if (bytesPerSecond < 1024 * 1024 * 1024) {
    return `${(bytesPerSecond / (1024 * 1024)).toFixed(1)} MB/s`
  }
  return `${(bytesPerSecond / (1024 * 1024 * 1024)).toFixed(2)} GB/s`
}
