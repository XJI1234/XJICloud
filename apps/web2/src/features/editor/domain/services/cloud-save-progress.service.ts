export type CloudSavePhase = 'export' | 'upload'

/** Map export/upload bytes onto one 5–99 bar. Export total is unknown. */
export function mapCloudSaveBar(phase: CloudSavePhase, loaded: number, total: number, elapsedMs = 0): number {
  if (phase === 'export') {
    const fromTime = Math.min(38, 5 + Math.floor(Math.max(0, elapsedMs) / 2500))
    if (!Number.isFinite(loaded) || loaded <= 0) {
      return Math.max(5, fromTime)
    }
    const approx = loaded / (loaded + 32 * 1024 * 1024)
    const fromBytes = Math.min(40, 8 + Math.round(approx * 32))
    return Math.min(40, Math.max(fromBytes, fromTime))
  }
  if (!Number.isFinite(total) || total <= 0) {
    return 45
  }
  const ratio = Math.max(0, Math.min(1, loaded / total))
  return Math.min(99, 40 + Math.round(ratio * 59))
}
