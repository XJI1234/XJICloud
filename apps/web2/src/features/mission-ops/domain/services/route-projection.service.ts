import type { RoutePoint } from '../entities/route-preview.entity'

/** Normalized [0, 100] positions; unwrap longitudes so dateline crossings stay short. */
export function projectRoute(points: readonly RoutePoint[]): readonly Readonly<{ x: number; y: number }>[] {
  if (!points.length) return []
  const longitudes: number[] = [points[0]!.longitude]
  for (let index = 1; index < points.length; index += 1) {
    let next = points[index]!.longitude
    const previous = longitudes[index - 1]!
    while (next - previous > 180) next -= 360
    while (next - previous < -180) next += 360
    longitudes.push(next)
  }
  const minX = Math.min(...longitudes)
  const maxX = Math.max(...longitudes)
  const minY = Math.min(...points.map((point) => point.latitude))
  const maxY = Math.max(...points.map((point) => point.latitude))
  const span = Math.max(maxX - minX, maxY - minY, 0.000001)
  return points.map((point, index) => ({
    x: 10 + ((longitudes[index]! - minX) / span) * 80,
    y: 90 - ((point.latitude - minY) / span) * 80,
  }))
}
