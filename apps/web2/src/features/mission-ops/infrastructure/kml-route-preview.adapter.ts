import { DomainError } from '@/shared/domain-error'
import { err, ok } from '@/shared/result'
import { isValidRoutePoint, type RoutePoint } from '@/features/mission-ops/domain/entities/route-preview.entity'
import type { RoutePreviewPort } from '@/features/mission-ops/domain/repositories/route-preview.port'

const MAX_KML_BYTES = 2 * 1024 * 1024
const MAX_POINTS = 5000

function parseCoordinates(text: string): RoutePoint[] | null {
  const tokens = text.trim().split(/\s+/u)
  if (!text.trim() || tokens.length > MAX_POINTS) return null
  const points: RoutePoint[] = []
  for (const token of tokens) {
    const fields = token.split(',')
    if (fields.length < 2 || fields.length > 3 || fields.some((field) => !field.trim())) return null
    const [longitude, latitude, altitude] = fields.map((field) => Number(field.trim()))
    const point = { longitude: longitude!, latitude: latitude!, altitude: altitude ?? null }
    if (!isValidRoutePoint(point)) return null
    points.push(point)
  }
  return points
}

export function createKmlRoutePreview(): RoutePreviewPort {
  return {
    async importKml(file) {
      if (!/\.kml$/iu.test(file.name) || file.size === 0 || file.size > MAX_KML_BYTES) {
        return err(new DomainError('ROUTE_PREVIEW_INVALID'))
      }
      try {
        const text = await file.text()
        const xml = new DOMParser().parseFromString(text, 'application/xml')
        if (xml.querySelector('parsererror') || xml.documentElement.localName !== 'kml'
          || xml.documentElement.namespaceURI !== 'http://www.opengis.net/kml/2.2') {
          return err(new DomainError('ROUTE_PREVIEW_INVALID'))
        }
        const tracks = Array.from(xml.getElementsByTagName('*')).filter((node) => node.localName === 'LineString' && node.namespaceURI === xml.documentElement.namespaceURI)
        if (tracks.length !== 1) return err(new DomainError('ROUTE_PREVIEW_INVALID'))
        const coordinateNodes = Array.from(tracks[0]!.children).filter((node) => node.localName === 'coordinates' && node.namespaceURI === xml.documentElement.namespaceURI)
        if (coordinateNodes.length !== 1) return err(new DomainError('ROUTE_PREVIEW_INVALID'))
        const points = parseCoordinates(coordinateNodes[0]!.textContent ?? '')
        if (!points || points.length < 2) return err(new DomainError('ROUTE_PREVIEW_INVALID'))
        return ok({ name: file.name, points })
      } catch {
        return err(new DomainError('ROUTE_PREVIEW_INVALID'))
      }
    },
  }
}
