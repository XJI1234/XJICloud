export type ChunkRange = {
  start: number
  endInclusive: number
  endExclusive: number
}

export function nextChunkRange(receivedBytes: number, sizeBytes: number, chunkSize: number): ChunkRange | null {
  if (receivedBytes < 0 || sizeBytes < 0 || chunkSize <= 0) {
    return null
  }
  if (receivedBytes >= sizeBytes) {
    return null
  }
  const start = receivedBytes
  const endExclusive = Math.min(receivedBytes + chunkSize, sizeBytes)
  return {
    start,
    endInclusive: endExclusive - 1,
    endExclusive,
  }
}

export const MODEL_MAX_SIZE_BYTES = 2 * 1024 * 1024 * 1024
