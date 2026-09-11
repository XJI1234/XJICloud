export type CloudSavePhase = 'export' | 'upload'

/** Map export/upload bytes onto one 5–99 bar. Export total is unknown. */
export function mapCloudSaveBar(phase: CloudSavePhase, loaded: number, total: number): number {
  if (phase === 'export') {
    if (!Number.isFinite(loaded) || loaded <= 0) {
      return 5
    }
    const approx = loaded / (loaded + 32 * 1024 * 1024)
    return Math.min(40, 8 + Math.round(approx * 32))
  }
  if (!Number.isFinite(total) || total <= 0) {
    return 45
  }
  const ratio = Math.max(0, Math.min(1, loaded / total))
  return Math.min(99, 40 + Math.round(ratio * 59))
}
