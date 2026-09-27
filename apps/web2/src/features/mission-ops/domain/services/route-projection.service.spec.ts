import { describe, expect, it } from 'vitest'
import { projectRoute } from './route-projection.service'

describe('projectRoute', () => {
  it('projects points into viewBox and keeps north above south', () => {
    const points = projectRoute([
      { longitude: 120, latitude: 30, altitude: null },
      { longitude: 121, latitude: 31, altitude: null },
    ])
    expect(points[0]).toEqual({ x: 10, y: 90 })
    expect(points[1]).toEqual({ x: 90, y: 10 })
  })

  it('unwraps a dateline crossing instead of drawing around the world', () => {
    const points = projectRoute([
      { longitude: 179.9, latitude: 0, altitude: null },
      { longitude: -179.9, latitude: 0, altitude: null },
    ])
    expect(points[1]!.x).toBeGreaterThan(points[0]!.x)
  })
})
