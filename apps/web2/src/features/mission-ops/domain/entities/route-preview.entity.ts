export type RoutePoint = Readonly<{ longitude: number; latitude: number; altitude: number | null }>

export type RoutePreview = Readonly<{ name: string; points: readonly RoutePoint[] }>

export function isValidRoutePoint(point: RoutePoint): boolean {
  return Number.isFinite(point.longitude) && point.longitude >= -180 && point.longitude <= 180
    && Number.isFinite(point.latitude) && point.latitude >= -90 && point.latitude <= 90
    && (point.altitude === null || Number.isFinite(point.altitude))
}
