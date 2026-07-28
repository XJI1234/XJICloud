const EARTH_RADIUS = 6378137
const DEGREE_TO_RADIAN = Math.PI / 180
const RADIAN_TO_DEGREE = 180 / Math.PI
const DEFAULT_ASPECT_RATIO = { width: 4, height: 3 }
const DEFAULT_SENSOR = { widthMm: 36, heightMm: 24 }
const DEFAULT_CRUISE_SPEED = 5
// 3DGS / COLMAP need dense horizontal overlap; legacy 0.12 was coverage-only and too sparse.
const DEFAULT_BUILDING_HORIZONTAL_OVERLAP_RATIO = 0.7
const DEFAULT_ORBIT_HORIZONTAL_OVERLAP_RATIO = 0.7
const DEFAULT_BUILDING_MIN_EDGE_LENGTH_METERS = 6
const DEFAULT_BUILDING_TURN_TOLERANCE_DEG = 18
const DEFAULT_BUILDING_MAX_SEGMENTS_PER_FACADE = 24
const DEFAULT_BUILDING_MAX_BANDS = 6

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

const toRadians = (value) => value * DEGREE_TO_RADIAN
const toDegrees = (value) => value * RADIAN_TO_DEGREE

const roundNumber = (value, digits = 2) => Number(Number(value || 0).toFixed(digits))

const toLocalMeters = (point, reference) => {
  const latitudeScale = 111320
  const longitudeScale = 111320 * Math.cos(toRadians(reference.latitude || 0))

  return {
    x: (Number(point.longitude) - Number(reference.longitude)) * longitudeScale,
    y: (Number(point.latitude) - Number(reference.latitude)) * latitudeScale,
  }
}

const fromLocalMeters = (point, reference) => {
  const latitudeScale = 111320
  const longitudeScale = 111320 * Math.cos(toRadians(reference.latitude || 0))

  return {
    longitude: Number(reference.longitude) + point.x / longitudeScale,
    latitude: Number(reference.latitude) + point.y / latitudeScale,
  }
}

const polygonSignedArea = (points) => {
  if (!Array.isArray(points) || points.length < 3) {
    return 0
  }

  let area = 0
  for (let index = 0; index < points.length; index += 1) {
    const current = points[index]
    const next = points[(index + 1) % points.length]
    area += current.x * next.y - next.x * current.y
  }

  return area / 2
}

const normalizeVector = (vector) => {
  const length = Math.hypot(vector.x, vector.y)
  if (length < 1e-6) {
    return { x: 0, y: 0 }
  }

  return {
    x: vector.x / length,
    y: vector.y / length,
  }
}

const dotProduct = (left, right) => left.x * right.x + left.y * right.y

const lineIntersection = (lineA, lineB) => {
  const denominator = lineA.direction.x * lineB.direction.y - lineA.direction.y * lineB.direction.x
  if (Math.abs(denominator) < 1e-6) {
    return null
  }

  const deltaX = lineB.point.x - lineA.point.x
  const deltaY = lineB.point.y - lineA.point.y
  const t = (deltaX * lineB.direction.y - deltaY * lineB.direction.x) / denominator

  return {
    x: lineA.point.x + lineA.direction.x * t,
    y: lineA.point.y + lineA.direction.y * t,
  }
}

const createOffsetPolygon = (points, offsetMeters) => {
  if (!Array.isArray(points) || points.length < 3) {
    return []
  }

  const signedArea = polygonSignedArea(points)
  const orientation = signedArea >= 0 ? 1 : -1
  const offsetLines = []

  for (let index = 0; index < points.length; index += 1) {
    const current = points[index]
    const next = points[(index + 1) % points.length]
    const edge = {
      x: next.x - current.x,
      y: next.y - current.y,
    }
    const direction = normalizeVector(edge)
    const outwardNormal = orientation > 0
      ? { x: direction.y, y: -direction.x }
      : { x: -direction.y, y: direction.x }

    offsetLines.push({
      point: {
        x: current.x + outwardNormal.x * offsetMeters,
        y: current.y + outwardNormal.y * offsetMeters,
      },
      direction,
    })
  }

  return offsetLines.map((line, index) => {
    const previousLine = offsetLines[(index - 1 + offsetLines.length) % offsetLines.length]
    const intersection = lineIntersection(previousLine, line)
    return intersection || line.point
  })
}

const computeCentroid = (points) => {
  if (!Array.isArray(points) || points.length === 0) {
    return { x: 0, y: 0 }
  }

  const sum = points.reduce(
    (accumulator, point) => ({
      x: accumulator.x + point.x,
      y: accumulator.y + point.y,
    }),
    { x: 0, y: 0 },
  )

  return {
    x: sum.x / points.length,
    y: sum.y / points.length,
  }
}

const distanceBetweenLocalPoints = (left, right) => {
  return Math.hypot(Number(right.x || 0) - Number(left.x || 0), Number(right.y || 0) - Number(left.y || 0))
}

const dedupePolygonPoints = (points, minDistanceMeters = 0.8) => {
  if (!Array.isArray(points) || points.length === 0) {
    return []
  }

  const deduped = []
  points.forEach((point) => {
    const previous = deduped[deduped.length - 1]
    if (!previous || distanceBetweenLocalPoints(previous, point) >= minDistanceMeters) {
      deduped.push(point)
    }
  })

  if (deduped.length >= 2 && distanceBetweenLocalPoints(deduped[0], deduped[deduped.length - 1]) < minDistanceMeters) {
    deduped.pop()
  }

  return deduped
}

const simplifyPolygonTurns = (points, toleranceMeters = 1.2) => {
  if (!Array.isArray(points) || points.length < 4) {
    return points || []
  }

  const simplified = []

  for (let index = 0; index < points.length; index += 1) {
    const previous = points[(index - 1 + points.length) % points.length]
    const current = points[index]
    const next = points[(index + 1) % points.length]
    const cross =
      (current.x - previous.x) * (next.y - current.y) -
      (current.y - previous.y) * (next.x - current.x)

    if (Math.abs(cross) > toleranceMeters) {
      simplified.push(current)
    }
  }

  return simplified.length >= 3 ? simplified : points
}

const angleBetweenVectorsDeg = (left, right) => {
  const normalizedLeft = normalizeVector(left)
  const normalizedRight = normalizeVector(right)
  const dot = clamp(dotProduct(normalizedLeft, normalizedRight), -1, 1)
  return toDegrees(Math.acos(dot))
}

const simplifyCoveragePolygon = (
  points,
  minEdgeLengthMeters = DEFAULT_BUILDING_MIN_EDGE_LENGTH_METERS,
  turnToleranceDeg = DEFAULT_BUILDING_TURN_TOLERANCE_DEG,
) => {
  if (!Array.isArray(points) || points.length < 4) {
    return points || []
  }

  const simplified = [...points]
  let changed = true

  while (changed && simplified.length > 4) {
    changed = false

    for (let index = 0; index < simplified.length; index += 1) {
      const previous = simplified[(index - 1 + simplified.length) % simplified.length]
      const current = simplified[index]
      const next = simplified[(index + 1) % simplified.length]

      const incoming = {
        x: current.x - previous.x,
        y: current.y - previous.y,
      }
      const outgoing = {
        x: next.x - current.x,
        y: next.y - current.y,
      }

      const incomingLength = Math.hypot(incoming.x, incoming.y)
      const outgoingLength = Math.hypot(outgoing.x, outgoing.y)
      const turnAngle = angleBetweenVectorsDeg(incoming, outgoing)

      const removeAsNearCollinear = turnAngle < turnToleranceDeg
      const removeAsSmallStep =
        (incomingLength < minEdgeLengthMeters || outgoingLength < minEdgeLengthMeters)
        && turnAngle < turnToleranceDeg * 2

      if (removeAsNearCollinear || removeAsSmallStep) {
        simplified.splice(index, 1)
        changed = true
        break
      }
    }
  }

  return simplified
}

const buildConvexHull = (points) => {
  if (!Array.isArray(points) || points.length <= 1) {
    return points || []
  }

  const sorted = [...points].sort((left, right) => {
    if (left.x === right.x) {
      return left.y - right.y
    }

    return left.x - right.x
  })

  const cross = (origin, left, right) => {
    return (left.x - origin.x) * (right.y - origin.y) - (left.y - origin.y) * (right.x - origin.x)
  }

  const lower = []
  sorted.forEach((point) => {
    while (lower.length >= 2 && cross(lower[lower.length - 2], lower[lower.length - 1], point) <= 0) {
      lower.pop()
    }
    lower.push(point)
  })

  const upper = []
  for (let index = sorted.length - 1; index >= 0; index -= 1) {
    const point = sorted[index]
    while (upper.length >= 2 && cross(upper[upper.length - 2], upper[upper.length - 1], point) <= 0) {
      upper.pop()
    }
    upper.push(point)
  }

  lower.pop()
  upper.pop()
  return [...lower, ...upper]
}

const rotatePoint = (point, angleRad) => {
  const cosine = Math.cos(angleRad)
  const sine = Math.sin(angleRad)
  return {
    x: point.x * cosine - point.y * sine,
    y: point.x * sine + point.y * cosine,
  }
}

const createCoverageEnvelopePolygon = (points) => {
  const hull = buildConvexHull(points)
  if (!Array.isArray(hull) || hull.length < 3) {
    return []
  }

  let bestRectangle = null

  for (let index = 0; index < hull.length; index += 1) {
    const start = hull[index]
    const end = hull[(index + 1) % hull.length]
    const edge = { x: end.x - start.x, y: end.y - start.y }
    const angle = Math.atan2(edge.y, edge.x)
    const rotatedHull = hull.map((point) => rotatePoint(point, -angle))

    const minX = Math.min(...rotatedHull.map((point) => point.x))
    const maxX = Math.max(...rotatedHull.map((point) => point.x))
    const minY = Math.min(...rotatedHull.map((point) => point.y))
    const maxY = Math.max(...rotatedHull.map((point) => point.y))
    const area = (maxX - minX) * (maxY - minY)

    if (!bestRectangle || area < bestRectangle.area) {
      bestRectangle = {
        angle,
        area,
        corners: [
          { x: minX, y: minY },
          { x: maxX, y: minY },
          { x: maxX, y: maxY },
          { x: minX, y: maxY },
        ],
      }
    }
  }

  if (!bestRectangle) {
    return []
  }

  return ensureCounterClockwise(bestRectangle.corners.map((point) => rotatePoint(point, bestRectangle.angle)))
}

const ensureCounterClockwise = (points) => {
  if (!Array.isArray(points) || points.length < 3) {
    return points || []
  }

  return polygonSignedArea(points) >= 0 ? points : [...points].reverse()
}

const createCentroidExpandedPolygon = (points, offsetMeters) => {
  if (!Array.isArray(points) || points.length < 3) {
    return []
  }

  const centroid = computeCentroid(points)

  return points.map((point, index) => {
    const vector = {
      x: point.x - centroid.x,
      y: point.y - centroid.y,
    }
    let normalized = normalizeVector(vector)

    if (Math.abs(normalized.x) < 1e-6 && Math.abs(normalized.y) < 1e-6) {
      const previous = points[(index - 1 + points.length) % points.length]
      const next = points[(index + 1) % points.length]
      normalized = normalizeVector({
        x: next.y - previous.y,
        y: -(next.x - previous.x),
      })
    }

    return {
      x: point.x + normalized.x * offsetMeters,
      y: point.y + normalized.y * offsetMeters,
    }
  })
}

const interpolateLocalPoint = (start, end, t) => ({
  x: start.x + (end.x - start.x) * t,
  y: start.y + (end.y - start.y) * t,
})

const closestPointOnSegmentLocal = (point, start, end) => {
  const dx = end.x - start.x
  const dy = end.y - start.y
  const lengthSquared = dx * dx + dy * dy

  if (lengthSquared < 1e-8) {
    return { x: start.x, y: start.y }
  }

  const t = clamp(((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared, 0, 1)
  return {
    x: start.x + dx * t,
    y: start.y + dy * t,
  }
}

const findNearestPointOnPolygonLocal = (point, polygon) => {
  if (!Array.isArray(polygon) || polygon.length === 0) {
    return { x: point.x, y: point.y }
  }

  let nearestPoint = polygon[0]
  let nearestDistance = Number.POSITIVE_INFINITY

  for (let index = 0; index < polygon.length; index += 1) {
    const start = polygon[index]
    const end = polygon[(index + 1) % polygon.length]
    const candidate = closestPointOnSegmentLocal(point, start, end)
    const distance = distanceBetweenLocalPoints(point, candidate)

    if (distance < nearestDistance) {
      nearestDistance = distance
      nearestPoint = candidate
    }
  }

  return nearestPoint
}

const generateOffsetFacadeSamples = (offsetPolygon, sourcePolygon, desiredStepMeters) => {
  if (!Array.isArray(offsetPolygon) || !Array.isArray(sourcePolygon) || offsetPolygon.length < 3 || sourcePolygon.length < 3) {
    return []
  }

  const safeStep = Math.max(Number(desiredStepMeters) || 1, 1)
  const samples = []
  const edgeCount = Math.min(offsetPolygon.length, sourcePolygon.length)

  for (let edgeIndex = 0; edgeIndex < edgeCount; edgeIndex += 1) {
    const cameraStart = offsetPolygon[edgeIndex]
    const cameraEnd = offsetPolygon[(edgeIndex + 1) % edgeCount]
    const facadeStart = sourcePolygon[edgeIndex]
    const facadeEnd = sourcePolygon[(edgeIndex + 1) % edgeCount]
    const facadeLength = distanceBetweenLocalPoints(facadeStart, facadeEnd)
    // Prefer denser ends for SfM matching across adjacent facades / corners.
    const segmentCount = clamp(
      Math.ceil(facadeLength / safeStep),
      facadeLength >= safeStep * 0.6 ? 2 : 1,
      DEFAULT_BUILDING_MAX_SEGMENTS_PER_FACADE,
    )

    for (let sampleIndex = 0; sampleIndex < segmentCount; sampleIndex += 1) {
      const t = (sampleIndex + 0.5) / segmentCount
      const cameraPoint = interpolateLocalPoint(cameraStart, cameraEnd, t)
      const targetPoint = interpolateLocalPoint(facadeStart, facadeEnd, t)

      samples.push({
        cameraPoint,
        targetPoint,
        edgeIndex,
        segmentIndex: sampleIndex,
        segmentCount,
        facadeLength,
      })
    }
  }

  return samples
}

const inferImageSize = (megapixelsWan, aspectRatio = DEFAULT_ASPECT_RATIO) => {
  const totalPixels = Math.max(Number(megapixelsWan) * 10000, 1)
  const width = Math.sqrt(totalPixels * (aspectRatio.width / aspectRatio.height))
  const height = width * (aspectRatio.height / aspectRatio.width)

  return {
    widthPx: Math.round(width),
    heightPx: Math.round(height),
    totalPixels,
  }
}

const calculateFieldOfView = (focalLengthMm, sensor = DEFAULT_SENSOR) => {
  const focal = Math.max(Number(focalLengthMm), 1)

  return {
    horizontalFovDeg: toDegrees(2 * Math.atan(sensor.widthMm / (2 * focal))),
    verticalFovDeg: toDegrees(2 * Math.atan(sensor.heightMm / (2 * focal))),
  }
}

/**
 * Recommend orbit shot count for 3DGS / SfM.
 * Uses the denser of: (1) HFOV×overlap angular step, (2) arc-length step from GSD coverage.
 */
export const recommendOrbitPhotosPerLoop = ({
  focalLengthMm,
  horizontalOverlapPercent = DEFAULT_ORBIT_HORIZONTAL_OVERLAP_RATIO * 100,
  orbitRadiusMeters,
  imageCoverageWidthMeters,
}) => {
  const fieldOfView = calculateFieldOfView(focalLengthMm)
  const overlapRatio = clamp(Number(horizontalOverlapPercent) / 100, 0.1, 0.95)
  const angularStepDeg = Math.max(fieldOfView.horizontalFovDeg * (1 - overlapRatio), 2)
  const fromFov = Math.ceil(360 / angularStepDeg)

  let fromCoverage = 0
  const radius = Number(orbitRadiusMeters)
  const coverageWidth = Number(imageCoverageWidthMeters)
  if (Number.isFinite(radius) && radius > 1 && Number.isFinite(coverageWidth) && coverageWidth > 0.5) {
    const arcStepMeters = Math.max(coverageWidth * (1 - overlapRatio), 1)
    fromCoverage = Math.ceil((2 * Math.PI * radius) / arcStepMeters)
  }

  return clamp(Math.max(fromFov, fromCoverage), 12, 180)
}

export const resolveOrbitPhotosPerLoop = ({
  photosPerLoop,
  focalLengthMm,
  horizontalOverlapPercent,
  autoPhotosPerLoop = false,
  orbitRadiusMeters,
  imageCoverageWidthMeters,
}) => {
  const recommended = recommendOrbitPhotosPerLoop({
    focalLengthMm,
    horizontalOverlapPercent,
    orbitRadiusMeters,
    imageCoverageWidthMeters,
  })
  const requested = Math.round(Number(photosPerLoop) || 0)

  if (autoPhotosPerLoop || requested <= 0) {
    return recommended
  }

  // Never undersample below the overlap-driven minimum when user sets a count.
  return Math.max(requested, 4)
}

export const haversineDistance = (from, to) => {
  const latitude1 = toRadians(from.latitude)
  const latitude2 = toRadians(to.latitude)
  const deltaLatitude = latitude2 - latitude1
  const deltaLongitude = toRadians(to.longitude - from.longitude)

  const a =
    Math.sin(deltaLatitude / 2) ** 2 +
    Math.cos(latitude1) * Math.cos(latitude2) * Math.sin(deltaLongitude / 2) ** 2

  return 2 * EARTH_RADIUS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export const calculateBearing = (from, to) => {
  const latitude1 = toRadians(from.latitude)
  const latitude2 = toRadians(to.latitude)
  const deltaLongitude = toRadians(to.longitude - from.longitude)

  const y = Math.sin(deltaLongitude) * Math.cos(latitude2)
  const x =
    Math.cos(latitude1) * Math.sin(latitude2) -
    Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(deltaLongitude)

  return (toDegrees(Math.atan2(y, x)) + 360) % 360
}

/** Compass heading in [0, 360). North=0, East=90, South=180, West=270. */
export const normalizeHeading360 = (value) => {
  let heading = Number(value)
  if (!Number.isFinite(heading)) {
    return 0
  }

  heading %= 360
  if (heading < 0) {
    heading += 360
  }

  return Number(heading.toFixed(2))
}

/** DJI WPML yaw expects [-180, 180]. */
export const toWpmlHeadingAngle = (heading360) => {
  const heading = normalizeHeading360(heading360)
  return Number((heading > 180 ? heading - 360 : heading).toFixed(2))
}

export const formatHeadingLabel = (heading360) => {
  const heading = normalizeHeading360(heading360)
  return `${heading.toFixed(1)}°`
}

export const projectDestination = (origin, bearingDeg, distanceMeters) => {
  const angularDistance = distanceMeters / EARTH_RADIUS
  const bearing = toRadians(bearingDeg)
  const latitude1 = toRadians(origin.latitude)
  const longitude1 = toRadians(origin.longitude)

  const latitude2 = Math.asin(
    Math.sin(latitude1) * Math.cos(angularDistance) +
      Math.cos(latitude1) * Math.sin(angularDistance) * Math.cos(bearing)
  )

  const longitude2 =
    longitude1 +
    Math.atan2(
      Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(latitude1),
      Math.cos(angularDistance) - Math.sin(latitude1) * Math.sin(latitude2)
    )

  return {
    longitude: toDegrees(longitude2),
    latitude: toDegrees(latitude2),
  }
}

const buildBandCenters = ({ lowElevationM, highElevationM, coverageHeightMeters, overlapRatio }) => {
  const minHeight = Math.min(lowElevationM, highElevationM)
  const maxHeight = Math.max(lowElevationM, highElevationM)
  const objectHeight = Math.max(maxHeight - minHeight, 0)

  if (objectHeight === 0) {
    return [minHeight]
  }

  if (coverageHeightMeters >= objectHeight) {
    return [(minHeight + maxHeight) / 2]
  }

  const verticalStep = Math.max(coverageHeightMeters * (1 - overlapRatio), 1)
  const firstCenter = minHeight + coverageHeightMeters / 2
  const lastCenter = maxHeight - coverageHeightMeters / 2
  const centers = []
  let currentCenter = firstCenter

  while (currentCenter < lastCenter) {
    centers.push(currentCenter)
    currentCenter += verticalStep
  }

  centers.push(lastCenter)

  return [...new Set(centers.map((value) => Number(value.toFixed(3))))]
}

const compressBandCenters = (centers, maxCount = DEFAULT_BUILDING_MAX_BANDS) => {
  if (!Array.isArray(centers) || centers.length <= maxCount) {
    return centers || []
  }

  if (maxCount <= 1) {
    return [centers[Math.floor(centers.length / 2)]]
  }

  const compressed = []
  for (let index = 0; index < maxCount; index += 1) {
    const ratio = index / (maxCount - 1)
    const centerIndex = Math.round(ratio * (centers.length - 1))
    compressed.push(centers[centerIndex])
  }

  return [...new Set(compressed.map((value) => Number(value.toFixed(3))))]
}

const computeAdaptiveBuildingBandLimit = (objectHeightMeters, coverageHeightMeters) => {
  const safeObjectHeight = Math.max(Number(objectHeightMeters) || 0, 0)
  const safeCoverageHeight = Math.max(Number(coverageHeightMeters) || 1, 1)
  const ratio = safeObjectHeight / safeCoverageHeight

  if (ratio <= 1.1) {
    return 1
  }

  if (ratio <= 2.0) {
    return 2
  }

  if (ratio <= 3.0) {
    return 3
  }

  if (ratio <= 4.5) {
    return 4
  }

  if (ratio <= 6.0) {
    return 5
  }

  return DEFAULT_BUILDING_MAX_BANDS
}

export const buildWaypoint = (point, centerPoint, extra = {}) => {
  return {
    longitude: roundNumber(point.longitude, 11),
    latitude: roundNumber(point.latitude, 11),
    altitude: roundNumber(point.altitude, 2),
    heading: normalizeHeading360(calculateBearing(point, centerPoint)),
    pitch: Number(point.pitch || 0),
    ...extra,
    // Keep heading normalized even if callers override via extra.
    ...(extra.heading != null ? { heading: normalizeHeading360(extra.heading) } : {}),
  }
}

export const calculateWaypointSequenceLength = (waypoints) => {
  if (!Array.isArray(waypoints) || waypoints.length < 2) {
    return 0
  }

  let totalDistance = 0

  for (let index = 1; index < waypoints.length; index += 1) {
    const previous = waypoints[index - 1]
    const current = waypoints[index]
    const horizontalDistance = haversineDistance(previous, current)
    const verticalDistance = Number(current.altitude || 0) - Number(previous.altitude || 0)
    totalDistance += Math.sqrt(horizontalDistance ** 2 + verticalDistance ** 2)
  }

  return totalDistance
}

export const buildMissionSummary = (waypoints, rings, photosPerLoop) => {
  const pathLengthMeters = calculateWaypointSequenceLength(waypoints)
  const estimatedFlightTimeSeconds = pathLengthMeters / DEFAULT_CRUISE_SPEED
  const shotIntervalSeconds = waypoints.length > 1 ? estimatedFlightTimeSeconds / Math.max(waypoints.length - 1, 1) : 0

  return {
    ringCount: rings.length,
    pathLengthMeters: roundNumber(pathLengthMeters, 2),
    estimatedFlightTimeSeconds: roundNumber(estimatedFlightTimeSeconds, 1),
    waypointCount: waypoints.length,
    imageCount: Math.max(rings.length * Math.max(Number(photosPerLoop) || 0, 0), 0),
    shotIntervalSeconds: roundNumber(shotIntervalSeconds, 1),
    verticalStepMeters:
      rings.length > 1
        ? roundNumber(Math.abs(rings[1].flightAltitude - rings[0].flightAltitude), 2)
        : 0,
    cruiseSpeedMetersPerSecond: DEFAULT_CRUISE_SPEED,
  }
}

export const planSinglePointOrbitMission = ({
  focalLengthMm,
  megapixelsWan,
  gsdMm,
  lowElevationM,
  highElevationM,
  pitchDeg,
  photosPerLoop,
  verticalOverlapPercent,
  horizontalOverlapPercent,
  autoPhotosPerLoop = false,
  centerPoint,
  edgePoint,
}) => {
  const horizontalOverlapRatio = clamp(
    Number(horizontalOverlapPercent ?? DEFAULT_ORBIT_HORIZONTAL_OVERLAP_RATIO * 100) / 100,
    0.1,
    0.95,
  )
  const overlapRatio = clamp(Number(verticalOverlapPercent || 70) / 100, 0.1, 0.95)
  const safePitchDeg = clamp(Number(pitchDeg || -10), -60, 60)
  const safeGsdMm = Math.max(Number(gsdMm) || 5, 0.1)
  const safeLowElevation = Number(lowElevationM) || 0
  const safeHighElevation = Number(highElevationM) || 0

  const imageSize = inferImageSize(megapixelsWan)
  const fieldOfView = calculateFieldOfView(focalLengthMm)

  const imageCoverageWidthMeters = (imageSize.widthPx * safeGsdMm) / 1000
  const imageCoverageHeightMeters = (imageSize.heightPx * safeGsdMm) / 1000

  const requiredStandOffByWidth =
    imageCoverageWidthMeters / (2 * Math.tan(toRadians(fieldOfView.horizontalFovDeg / 2)))
  const requiredStandOffByHeight =
    imageCoverageHeightMeters / (2 * Math.tan(toRadians(fieldOfView.verticalFovDeg / 2)))
  const cameraStandOffMeters = Math.max(requiredStandOffByWidth, requiredStandOffByHeight)

  const targetRadiusMeters = haversineDistance(centerPoint, edgePoint)
  const orbitRadiusMeters = Math.max(targetRadiusMeters + cameraStandOffMeters, cameraStandOffMeters)
  const verticalCoverageMeters = 2 * orbitRadiusMeters * Math.tan(toRadians(fieldOfView.verticalFovDeg / 2))

  const safePhotosPerLoop = resolveOrbitPhotosPerLoop({
    photosPerLoop,
    focalLengthMm,
    horizontalOverlapPercent: horizontalOverlapRatio * 100,
    autoPhotosPerLoop,
    orbitRadiusMeters,
    imageCoverageWidthMeters,
  })
  const recommendedPhotosPerLoop = recommendOrbitPhotosPerLoop({
    focalLengthMm,
    horizontalOverlapPercent: horizontalOverlapRatio * 100,
    orbitRadiusMeters,
    imageCoverageWidthMeters,
  })

  const bandCenters = compressBandCenters(buildBandCenters({
    lowElevationM: safeLowElevation,
    highElevationM: safeHighElevation,
    coverageHeightMeters: verticalCoverageMeters,
    overlapRatio,
  }))

  const startBearing = calculateBearing(centerPoint, edgePoint)
  const rings = bandCenters.map((targetBandCenter, ringIndex) => {
    const flightAltitude = Math.max(
      targetBandCenter - orbitRadiusMeters * Math.tan(toRadians(safePitchDeg)),
      10,
    )

    return {
      ringIndex: ringIndex + 1,
      targetBandCenter,
      flightAltitude,
      waypoints: [],
    }
  })

  const waypoints = []
  rings.forEach((ring) => {
    for (let pointIndex = 0; pointIndex < safePhotosPerLoop; pointIndex += 1) {
      const currentBearing = startBearing + (360 / safePhotosPerLoop) * pointIndex
      const projectedPoint = projectDestination(centerPoint, currentBearing, orbitRadiusMeters)

      const waypoint = buildWaypoint(
        {
          longitude: projectedPoint.longitude,
          latitude: projectedPoint.latitude,
          altitude: ring.flightAltitude,
          pitch: safePitchDeg,
        },
        centerPoint,
        {
          ringIndex: ring.ringIndex,
          pointIndex: pointIndex + 1,
          kind: 'orbit',
        },
      )

      ring.waypoints.push(waypoint)
      waypoints.push(waypoint)
    }
  })

  const missionWaypoints = []
  rings.forEach((ring, ringIndex) => {
    ring.waypoints.forEach((waypoint) => {
      missionWaypoints.push(waypoint)
    })

    if (ring.waypoints.length > 0) {
      missionWaypoints.push({
        ...ring.waypoints[0],
        pointIndex: ring.waypoints.length + 1,
        closeLoop: true,
      })
    }

    const nextRing = rings[ringIndex + 1]
    if (nextRing?.waypoints?.length && ring.waypoints.length > 0) {
      missionWaypoints.push({
        ...nextRing.waypoints[0],
        kind: 'connector',
        pointIndex: 0,
      })
    }
  })

  const summary = buildMissionSummary(missionWaypoints, rings, safePhotosPerLoop)

  return {
    center: centerPoint,
    edge: edgePoint,
    rings,
    waypoints: missionWaypoints,
    baseWaypoints: waypoints,
    routeSegments: [],
    targetRadiusMeters: roundNumber(targetRadiusMeters, 2),
    orbitRadiusMeters: roundNumber(orbitRadiusMeters, 2),
    fieldOfView,
    imageSize,
    imageCoverageWidthMeters: roundNumber(imageCoverageWidthMeters, 2),
    imageCoverageHeightMeters: roundNumber(imageCoverageHeightMeters, 2),
    verticalCoverageMeters: roundNumber(verticalCoverageMeters, 2),
    startBearing: roundNumber(startBearing, 2),
    photosPerLoop: safePhotosPerLoop,
    recommendedPhotosPerLoop,
    planningInputs: {
      focalLengthMm: Number(focalLengthMm),
      megapixelsWan: Number(megapixelsWan),
      gsdMm: safeGsdMm,
      lowElevationM: safeLowElevation,
      highElevationM: safeHighElevation,
      pitchDeg: safePitchDeg,
      photosPerLoop: safePhotosPerLoop,
      verticalOverlapPercent: Number(verticalOverlapPercent || 70),
      horizontalOverlapPercent: roundNumber(horizontalOverlapRatio * 100, 1),
      autoPhotosPerLoop: Boolean(autoPhotosPerLoop),
    },
    obstacleAnalysis: {
      enabled: false,
      analyzed: false,
      status: 'not-run',
      detouredSegments: 0,
      riskySegments: 0,
      safeSegments: 0,
      suggestions: [],
      messages: [],
      safetyClearanceMeters: 0,
      minimumClearanceMeters: null,
      collisionPoints: [],
    },
    summary,
  }
}

export const planBuildingFootprintMission = ({
  focalLengthMm,
  megapixelsWan,
  gsdMm,
  lowElevationM,
  highElevationM,
  pitchDeg,
  verticalOverlapPercent,
  horizontalOverlapPercent,
  footprintPoints,
}) => {
  if (!Array.isArray(footprintPoints) || footprintPoints.length < 3) {
    throw new Error('建筑采样至少需要 3 个轮廓点')
  }

  const overlapRatio = clamp(Number(verticalOverlapPercent || 70) / 100, 0.1, 0.95)
  const horizontalOverlapRatio = clamp(
    Number(horizontalOverlapPercent ?? DEFAULT_BUILDING_HORIZONTAL_OVERLAP_RATIO * 100) / 100,
    0.1,
    0.95,
  )
  const safePitchDeg = clamp(Number(pitchDeg || -10), -60, 60)
  const safeGsdMm = Math.max(Number(gsdMm) || 5, 0.1)
  const safeLowElevation = Number(lowElevationM) || 0
  const safeHighElevation = Number(highElevationM) || 0

  const imageSize = inferImageSize(megapixelsWan)
  const fieldOfView = calculateFieldOfView(focalLengthMm)
  const imageCoverageWidthMeters = (imageSize.widthPx * safeGsdMm) / 1000
  const imageCoverageHeightMeters = (imageSize.heightPx * safeGsdMm) / 1000

  const requiredStandOffByWidth =
    imageCoverageWidthMeters / (2 * Math.tan(toRadians(fieldOfView.horizontalFovDeg / 2)))
  const requiredStandOffByHeight =
    imageCoverageHeightMeters / (2 * Math.tan(toRadians(fieldOfView.verticalFovDeg / 2)))
  const cameraStandOffMeters = Math.max(requiredStandOffByWidth, requiredStandOffByHeight)

  const referencePoint = footprintPoints[0]
  const localFootprint = ensureCounterClockwise(
    simplifyCoveragePolygon(
      simplifyPolygonTurns(
        dedupePolygonPoints(footprintPoints.map((point) => toLocalMeters(point, referencePoint))),
      ),
      Math.max(DEFAULT_BUILDING_MIN_EDGE_LENGTH_METERS, imageCoverageWidthMeters * 0.3),
    ),
  )

  if (localFootprint.length < 3) {
    throw new Error('建筑轮廓点不足，无法生成稳定航线')
  }

  const coverageEnvelope = createCoverageEnvelopePolygon(localFootprint)
  if (coverageEnvelope.length < 4) {
    throw new Error('建筑覆盖包络生成失败，请重新选择建筑')
  }

  const localCentroid = computeCentroid(coverageEnvelope)
  const offsetPolygon = ensureCounterClockwise(
    dedupePolygonPoints(createOffsetPolygon(coverageEnvelope, cameraStandOffMeters), 0.5),
  )

  if (offsetPolygon.length < 3) {
    throw new Error('建筑外扩轮廓生成失败，请重新选择建筑')
  }

  const alongEdgeStepMeters = Math.max(imageCoverageWidthMeters * (1 - horizontalOverlapRatio), 3)
  const facadeSamples = generateOffsetFacadeSamples(offsetPolygon, coverageEnvelope, alongEdgeStepMeters)

  if (facadeSamples.length < 3) {
    throw new Error('建筑采样航点生成失败，请重新选择建筑')
  }

  const polygonWaypoints = facadeSamples.map((sample) => ({
    ...fromLocalMeters(sample.cameraPoint, referencePoint),
    targetLongitude: fromLocalMeters(sample.targetPoint, referencePoint).longitude,
    targetLatitude: fromLocalMeters(sample.targetPoint, referencePoint).latitude,
    edgeIndex: sample.edgeIndex,
    facadeSegmentIndex: sample.segmentIndex,
    facadeSegmentCount: sample.segmentCount,
    facadeLength: sample.facadeLength,
  }))

  const verticalCoverageMeters = Math.max(imageCoverageHeightMeters, 1)
  const objectHeightMeters = Math.max(safeHighElevation - safeLowElevation, 0)
  const adaptiveBandLimit = computeAdaptiveBuildingBandLimit(objectHeightMeters, verticalCoverageMeters)
  const bandCenters = compressBandCenters(buildBandCenters({
    lowElevationM: safeLowElevation,
    highElevationM: safeHighElevation,
    coverageHeightMeters: verticalCoverageMeters,
    overlapRatio,
  }), adaptiveBandLimit)

  const rings = bandCenters.map((targetBandCenter, ringIndex) => {
    const flightAltitude = Math.max(
      targetBandCenter - cameraStandOffMeters * Math.tan(toRadians(safePitchDeg)),
      10,
    )

    const waypoints = polygonWaypoints.map((point, pointIndex) => {
      const targetPoint = {
        longitude: point.targetLongitude,
        latitude: point.targetLatitude,
      }

      return buildWaypoint(
        {
          longitude: point.longitude,
          latitude: point.latitude,
          altitude: flightAltitude,
          pitch: safePitchDeg,
        },
        targetPoint,
        {
          ringIndex: ringIndex + 1,
          pointIndex: pointIndex + 1,
          edgeIndex: point.edgeIndex + 1,
          facadeSegmentIndex: point.facadeSegmentIndex + 1,
          facadeSegmentCount: point.facadeSegmentCount,
          facadeLengthMeters: roundNumber(point.facadeLength, 2),
          kind: 'building-orbit',
        },
      )
    })

    return {
      ringIndex: ringIndex + 1,
      targetBandCenter,
      flightAltitude,
      waypoints,
      footprintPoints: waypoints.map((waypoint) => ({
        longitude: waypoint.longitude,
        latitude: waypoint.latitude,
        altitude: waypoint.altitude,
      })),
    }
  })

  const baseWaypoints = rings.flatMap((ring) => ring.waypoints)
  const missionWaypoints = []

  rings.forEach((ring, ringIndex) => {
    ring.waypoints.forEach((waypoint) => {
      missionWaypoints.push(waypoint)
    })

    if (ring.waypoints.length > 0) {
      missionWaypoints.push({
        ...ring.waypoints[0],
        pointIndex: ring.waypoints.length + 1,
        closeLoop: true,
      })
    }

    const nextRing = rings[ringIndex + 1]
    if (nextRing?.waypoints?.length && ring.waypoints.length > 0) {
      missionWaypoints.push({
        ...nextRing.waypoints[0],
        kind: 'connector',
        pointIndex: 0,
      })
    }
  })

  const summary = buildMissionSummary(missionWaypoints, rings, polygonWaypoints.length)
  const routeCenter = fromLocalMeters(localCentroid, referencePoint)
  const targetRadiusMeters = footprintPoints.reduce((maximum, point) => Math.max(maximum, haversineDistance(routeCenter, point)), 0)
  const orbitRadiusMeters = polygonWaypoints.reduce((maximum, point) => Math.max(maximum, haversineDistance(routeCenter, point)), 0)

  return {
    missionType: 'building',
    center: routeCenter,
    sourceFootprint: footprintPoints,
    offsetFootprint: polygonWaypoints,
    rings,
    waypoints: missionWaypoints,
    baseWaypoints,
    routeSegments: [],
    targetRadiusMeters: roundNumber(targetRadiusMeters, 2),
    orbitRadiusMeters: roundNumber(orbitRadiusMeters, 2),
    fieldOfView,
    imageSize,
    imageCoverageWidthMeters: roundNumber(imageCoverageWidthMeters, 2),
    imageCoverageHeightMeters: roundNumber(imageCoverageHeightMeters, 2),
    verticalCoverageMeters: roundNumber(verticalCoverageMeters, 2),
    startBearing: 0,
    photosPerLoop: polygonWaypoints.length,
    planningInputs: {
      focalLengthMm: Number(focalLengthMm),
      megapixelsWan: Number(megapixelsWan),
      gsdMm: safeGsdMm,
      lowElevationM: safeLowElevation,
      highElevationM: safeHighElevation,
      pitchDeg: safePitchDeg,
      photosPerLoop: polygonWaypoints.length,
      verticalOverlapPercent: Number(verticalOverlapPercent || 70),
      horizontalOverlapPercent: roundNumber(horizontalOverlapRatio * 100, 1),
    },
    obstacleAnalysis: {
      enabled: false,
      analyzed: false,
      status: 'not-run',
      detouredSegments: 0,
      riskySegments: 0,
      safeSegments: 0,
      suggestions: [],
      messages: [],
      safetyClearanceMeters: 0,
      minimumClearanceMeters: null,
      collisionPoints: [],
    },
    summary,
  }
}