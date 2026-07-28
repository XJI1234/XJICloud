/**
 * Geometric information-gain reshoot planner for 3DGS / SfM seed routes.
 *
 * Without an online 3DGS model, we use a Fisher-style proxy:
 *   uncovered target cells × viewing-angle novelty / travel cost
 * Budget and aggressiveness are driven by qualitySpeedBalance ∈ [0, 1]
 * (0 = speed / few shots, 1 = quality / denser fill-ins).
 */

import {
  buildMissionSummary,
  buildWaypoint,
  calculateBearing,
  calculateWaypointSequenceLength,
  haversineDistance,
  projectDestination,
} from './routePlanner.js'

const DEGREE_TO_RADIAN = Math.PI / 180
const DEFAULT_CRUISE_SPEED = 5

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

const lerp = (from, to, t) => from + (to - from) * clamp(t, 0, 1)

const isPhotoWaypoint = (waypoint) => (
  waypoint
  && !waypoint.closeLoop
  && waypoint.kind !== 'connector'
)

const roundNumber = (value, digits = 2) => Number(Number(value || 0).toFixed(digits))

const normalizeAngleDiffDeg = (a, b) => {
  let diff = Math.abs(Number(a) - Number(b)) % 360
  if (diff > 180) {
    diff = 360 - diff
  }
  return diff
}

/**
 * Map UI balance to planning knobs.
 * @param {number} qualitySpeedBalance 0=speed, 1=quality
 */
export const resolveIgReshootBudget = (qualitySpeedBalance = 0.55, seedPhotoCount = 24) => {
  const beta = clamp(Number(qualitySpeedBalance) ?? 0.55, 0, 1)
  const seed = Math.max(Number(seedPhotoCount) || 24, 8)

  return {
    beta,
    // Extra photo budget as fraction of seed count
    maxExtraPhotos: Math.max(2, Math.round(seed * lerp(0.06, 0.42, beta))),
    // Stop when next best marginal IG falls below this (relative 0–1)
    minMarginalGain: lerp(0.42, 0.12, beta),
    // Angular diversity between selected reshoot views
    minSeparationDeg: lerp(28, 10, beta),
    // Height-band densification aggressiveness
    heightGapWeight: lerp(0.6, 1.35, beta),
    // Optional second standoff radius for parallax (quality side)
    enableSecondaryRadius: beta >= 0.55,
    secondaryRadiusScale: lerp(1.0, 1.18, beta),
    // Travel cost exponent in utility = IG / (1 + travel^exp)
    travelCostExponent: lerp(1.35, 0.75, beta),
    // Azimuth bin resolution
    azimuthBins: Math.round(lerp(24, 48, beta)),
  }
}

const collectSeedPhotos = (basePlan) => {
  const fromBase = (basePlan.baseWaypoints || []).filter(isPhotoWaypoint)
  if (fromBase.length) {
    return fromBase
  }

  return (basePlan.waypoints || []).filter(isPhotoWaypoint)
}

const buildOrbitCoverageModel = (basePlan, budget) => {
  const center = basePlan.center
  const azimuthBins = budget.azimuthBins
  const rings = Array.isArray(basePlan.rings) ? basePlan.rings : []
  const bandCenters = rings.map((ring) => Number(ring.targetBandCenter || ring.flightAltitude || 0))
  const uniqueBands = [...new Set(bandCenters.map((value) => Number(value.toFixed(2))))]
  const heightBins = uniqueBands.length > 0 ? uniqueBands : [Number(basePlan.planningInputs?.lowElevationM || 0)]

  const cells = []
  for (let heightIndex = 0; heightIndex < heightBins.length; heightIndex += 1) {
    for (let azimuthIndex = 0; azimuthIndex < azimuthBins; azimuthIndex += 1) {
      cells.push({
        id: `o-${heightIndex}-${azimuthIndex}`,
        azimuthDeg: (360 / azimuthBins) * (azimuthIndex + 0.5),
        heightM: heightBins[heightIndex],
        heightIndex,
        azimuthIndex,
        coverage: 0,
        // Mid-height and mid-azimuth gaps get slightly higher prior (SfM weak spots)
        prior: 1,
      })
    }
  }

  // Boost prior on band boundaries and every Nth azimuth (corner-ish)
  cells.forEach((cell) => {
    if (cell.heightIndex === 0 || cell.heightIndex === heightBins.length - 1) {
      cell.prior *= 1.15 * budget.heightGapWeight
    }
    if (cell.azimuthIndex % Math.max(Math.floor(azimuthBins / 8), 1) === 0) {
      cell.prior *= 1.1
    }
  })

  return { type: 'orbit', center, cells, azimuthBins, heightBins, orbitRadiusMeters: Number(basePlan.orbitRadiusMeters || 0) }
}

const buildBuildingCoverageModel = (basePlan, budget) => {
  const center = basePlan.center
  const offsetFootprint = Array.isArray(basePlan.offsetFootprint) ? basePlan.offsetFootprint : []
  const rings = Array.isArray(basePlan.rings) ? basePlan.rings : []
  const bandCenters = rings.map((ring) => Number(ring.targetBandCenter || ring.flightAltitude || 0))
  const heightBins = bandCenters.length
    ? [...new Set(bandCenters.map((value) => Number(value.toFixed(2))))]
    : [Number(basePlan.planningInputs?.lowElevationM || 0)]

  // Group seed camera positions by facade edge when available
  const edgeCount = Math.max(
    ...offsetFootprint.map((point) => Number(point.edgeIndex || 0) + 1),
    4,
  )

  const cells = []
  for (let heightIndex = 0; heightIndex < heightBins.length; heightIndex += 1) {
    for (let edgeIndex = 0; edgeIndex < edgeCount; edgeIndex += 1) {
      const segments = Math.max(2, Math.round(lerp(2, 6, budget.beta)))
      for (let segmentIndex = 0; segmentIndex < segments; segmentIndex += 1) {
        cells.push({
          id: `b-${heightIndex}-${edgeIndex}-${segmentIndex}`,
          edgeIndex,
          segmentIndex,
          segments,
          heightM: heightBins[heightIndex],
          heightIndex,
          t: (segmentIndex + 0.5) / segments,
          coverage: 0,
          prior: segmentIndex === 0 || segmentIndex === segments - 1
            ? 1.2 * budget.heightGapWeight
            : 1,
        })
      }
    }
  }

  return {
    type: 'building',
    center,
    cells,
    heightBins,
    edgeCount,
    offsetFootprint,
    orbitRadiusMeters: Number(basePlan.orbitRadiusMeters || 0),
  }
}

const markOrbitCoverage = (model, photos, fieldOfView) => {
  const halfH = Math.max(Number(fieldOfView?.horizontalFovDeg) || 60, 10) * 0.55
  const heightTolerance = Math.max(
    ...(model.heightBins.length > 1
      ? model.heightBins.slice(1).map((value, index) => Math.abs(value - model.heightBins[index]))
      : [15]),
    8,
  ) * 0.65

  photos.forEach((photo) => {
    const azimuth = calculateBearing(model.center, photo)
    const altitude = Number(photo.altitude || 0)
    model.cells.forEach((cell) => {
      if (normalizeAngleDiffDeg(azimuth, cell.azimuthDeg) <= halfH
        && Math.abs(altitude - cell.heightM) <= heightTolerance) {
        cell.coverage += 1
      }
    })
  })
}

const markBuildingCoverage = (model, photos) => {
  const heightTolerance = Math.max(
    ...(model.heightBins.length > 1
      ? model.heightBins.slice(1).map((value, index) => Math.abs(value - model.heightBins[index]))
      : [15]),
    8,
  ) * 0.7

  photos.forEach((photo) => {
    const edgeIndex = Number(photo.edgeIndex || 0) - 1
    const segmentIndex = Number(photo.facadeSegmentIndex || 0) - 1
    const altitude = Number(photo.altitude || 0)

    model.cells.forEach((cell) => {
      const sameEdge = edgeIndex >= 0 ? cell.edgeIndex === edgeIndex : true
      const nearSegment = segmentIndex >= 0
        ? Math.abs(cell.segmentIndex - segmentIndex) <= 1
        : true
      if (sameEdge && nearSegment && Math.abs(altitude - cell.heightM) <= heightTolerance) {
        cell.coverage += 1
      }
    })
  })
}

const uncoveredScore = (cell) => {
  if (cell.coverage <= 0) {
    return cell.prior
  }
  if (cell.coverage === 1) {
    return cell.prior * 0.35
  }
  return 0
}

const totalUncovered = (cells) => cells.reduce((sum, cell) => sum + uncoveredScore(cell), 0)

const createOrbitCandidates = (basePlan, model, budget) => {
  const pitchDeg = Number(basePlan.planningInputs?.pitchDeg ?? -15)
  const radius = Math.max(Number(model.orbitRadiusMeters) || 30, 10)
  const radii = [radius]
  if (budget.enableSecondaryRadius) {
    radii.push(radius * budget.secondaryRadiusScale)
  }

  const candidates = []
  let candidateId = 0

  model.heightBins.forEach((heightM, heightIndex) => {
    const flightAltitude = Math.max(
      heightM - radius * Math.tan((pitchDeg) * DEGREE_TO_RADIAN),
      10,
    )

    for (let azimuthIndex = 0; azimuthIndex < model.azimuthBins; azimuthIndex += 1) {
      const azimuthDeg = (360 / model.azimuthBins) * (azimuthIndex + 0.5)
      radii.forEach((candidateRadius, radiusIndex) => {
        const projected = projectDestination(model.center, azimuthDeg, candidateRadius)
        const cellIds = model.cells
          .filter((cell) => cell.heightIndex === heightIndex && cell.azimuthIndex === azimuthIndex)
          .map((cell) => cell.id)

        candidates.push({
          id: `oc-${candidateId}`,
          azimuthDeg,
          heightM,
          heightIndex,
          radiusIndex,
          cellIds,
          lookAt: model.center,
          camera: {
            longitude: projected.longitude,
            latitude: projected.latitude,
            altitude: flightAltitude,
            pitch: pitchDeg,
          },
        })
        candidateId += 1
      })
    }
  })

  return candidates
}

const createBuildingCandidates = (basePlan, model, budget) => {
  const pitchDeg = Number(basePlan.planningInputs?.pitchDeg ?? -15)
  const footprint = model.offsetFootprint
  if (footprint.length < 3) {
    return []
  }

  const byEdge = new Map()
  footprint.forEach((point) => {
    const edgeIndex = Number(point.edgeIndex || 0)
    if (!byEdge.has(edgeIndex)) {
      byEdge.set(edgeIndex, [])
    }
    byEdge.get(edgeIndex).push(point)
  })

  const candidates = []
  let candidateId = 0
  const standOffScale = budget.enableSecondaryRadius ? budget.secondaryRadiusScale : 1

  model.heightBins.forEach((heightM, heightIndex) => {
    const flightAltitude = Math.max(heightM - 8 * Math.tan(Math.abs(pitchDeg) * DEGREE_TO_RADIAN), 10)

    for (let edgeIndex = 0; edgeIndex < model.edgeCount; edgeIndex += 1) {
      const edgePoints = byEdge.get(edgeIndex) || []
      const segments = Math.max(2, Math.round(lerp(2, 5, budget.beta)))

      for (let segmentIndex = 0; segmentIndex < segments; segmentIndex += 1) {
        const t = (segmentIndex + 0.5) / segments
        let cameraPoint
        let targetPoint

        if (edgePoints.length >= 2) {
          const start = edgePoints[0]
          const end = edgePoints[edgePoints.length - 1]
          cameraPoint = {
            longitude: start.longitude + (end.longitude - start.longitude) * t,
            latitude: start.latitude + (end.latitude - start.latitude) * t,
          }
          targetPoint = {
            longitude: Number(start.targetLongitude ?? model.center.longitude),
            latitude: Number(start.targetLatitude ?? model.center.latitude),
          }
        } else if (footprint.length) {
          const index = Math.min(
            Math.floor(t * footprint.length),
            footprint.length - 1,
          )
          const point = footprint[index]
          cameraPoint = {
            longitude: point.longitude,
            latitude: point.latitude,
          }
          targetPoint = {
            longitude: Number(point.targetLongitude ?? model.center.longitude),
            latitude: Number(point.targetLatitude ?? model.center.latitude),
          }
        } else {
          continue
        }

        if (standOffScale > 1.01 && model.center) {
          const bearing = calculateBearing(model.center, cameraPoint)
          const distance = haversineDistance(model.center, cameraPoint) * standOffScale
          cameraPoint = projectDestination(model.center, bearing, distance)
        }

        const cellIds = model.cells
          .filter((cell) => (
            cell.heightIndex === heightIndex
            && cell.edgeIndex === edgeIndex
            && Math.abs(cell.segmentIndex - segmentIndex) <= 1
          ))
          .map((cell) => cell.id)

        candidates.push({
          id: `bc-${candidateId}`,
          azimuthDeg: calculateBearing(model.center, cameraPoint),
          heightM,
          heightIndex,
          edgeIndex,
          segmentIndex,
          radiusIndex: standOffScale > 1.01 ? 1 : 0,
          cellIds,
          lookAt: targetPoint,
          camera: {
            longitude: cameraPoint.longitude,
            latitude: cameraPoint.latitude,
            altitude: flightAltitude,
            pitch: pitchDeg,
          },
        })
        candidateId += 1
      }
    }
  })

  return candidates
}

const candidateInformationGain = (candidate, cellsById) => {
  let gain = 0
  candidate.cellIds.forEach((cellId) => {
    const cell = cellsById.get(cellId)
    if (cell) {
      gain += uncoveredScore(cell)
    }
  })

  // Secondary standoff adds parallax for 3DGS even on already-covered azimuths.
  if (candidate.radiusIndex > 0) {
    gain = gain * 1.15 + 0.22
  }

  return gain
}

const applyCandidateCoverage = (candidate, cellsById) => {
  candidate.cellIds.forEach((cellId) => {
    const cell = cellsById.get(cellId)
    if (cell) {
      cell.coverage += 1
    }
  })
}

const selectReshootCandidates = ({
  candidates,
  cells,
  seedPhotos,
  budget,
}) => {
  const cellsById = new Map(cells.map((cell) => [cell.id, { ...cell }]))
  const rawUncovered = totalUncovered([...cellsById.values()])
  // Keep a virtual floor so quality-mode parallax (secondary radius) can still fire
  // when the seed route already paints most cells.
  const initialUncovered = Math.max(
    rawUncovered,
    budget.enableSecondaryRadius ? budget.maxExtraPhotos * 0.25 : 0,
  )

  if (initialUncovered <= 1e-6) {
    return {
      selected: [],
      initialUncovered: 0,
      remainingUncovered: 0,
      coverageImprovementRatio: 0,
    }
  }

  const selected = []
  let lastPosition = seedPhotos[seedPhotos.length - 1] || null

  while (selected.length < budget.maxExtraPhotos) {
    let best = null
    let bestUtility = 0

    candidates.forEach((candidate) => {
      if (selected.some((item) => item.id === candidate.id)) {
        return
      }

      // Angular diversity vs already selected reshoot views
      const tooClose = selected.some((item) => (
        normalizeAngleDiffDeg(item.azimuthDeg, candidate.azimuthDeg) < budget.minSeparationDeg
        && Math.abs(item.heightM - candidate.heightM) < 4
        && (item.radiusIndex || 0) === (candidate.radiusIndex || 0)
      ))
      if (tooClose) {
        return
      }

      // Skip near-duplicates of seed photo positions
      const tooCloseToSeed = seedPhotos.some((photo) => {
        const horizontal = haversineDistance(photo, candidate.camera)
        const vertical = Math.abs(Number(photo.altitude || 0) - Number(candidate.camera.altitude || 0))
        return horizontal < 5 && vertical < 4
      })
      if (tooCloseToSeed) {
        return
      }

      const gain = candidateInformationGain(candidate, cellsById)
      if (gain <= 0) {
        return
      }

      const travel = lastPosition
        ? haversineDistance(lastPosition, candidate.camera)
        : 0
      const utility = gain / (1 + (travel / 40) ** budget.travelCostExponent)

      if (utility > bestUtility) {
        bestUtility = utility
        best = { candidate, gain, utility, travel }
      }
    })

    if (!best) {
      break
    }

    // Absolute gain floor: lower for quality mode; secondary-radius parallax keeps a soft floor.
    const absoluteFloor = best.candidate.radiusIndex > 0
      ? lerp(0.2, 0.08, budget.beta)
      : lerp(0.45, 0.12, budget.beta)

    if (best.gain < absoluteFloor) {
      break
    }

    // When seed already covers well, only keep accepting parallax (secondary radius) views.
    if (rawUncovered < 0.35 && !(best.candidate.radiusIndex > 0 && budget.enableSecondaryRadius)) {
      break
    }

    selected.push({
      ...best.candidate,
      informationGain: roundNumber(best.gain, 3),
      utility: roundNumber(best.utility, 3),
      travelMeters: roundNumber(best.travel, 2),
    })
    applyCandidateCoverage(best.candidate, cellsById)
    lastPosition = best.candidate.camera
  }

  const remainingUncovered = totalUncovered([...cellsById.values()])
  const coverageImprovementRatio = rawUncovered > 1e-6
    ? clamp((rawUncovered - remainingUncovered) / rawUncovered, 0, 1)
    : (selected.length > 0 ? clamp(selected.length / Math.max(budget.maxExtraPhotos, 1), 0, 1) * 0.35 : 0)

  return {
    selected,
    initialUncovered: roundNumber(initialUncovered, 3),
    remainingUncovered: roundNumber(remainingUncovered, 3),
    coverageImprovementRatio: roundNumber(coverageImprovementRatio, 3),
  }
}

const appendReshootWaypoints = (basePlan, selected) => {
  if (!selected.length) {
    return {
      rings: basePlan.rings || [],
      waypoints: basePlan.waypoints || [],
      baseWaypoints: basePlan.baseWaypoints || [],
    }
  }

  const reshootRingIndex = (basePlan.rings?.length || 0) + 1
  const reshootWaypoints = selected.map((item, index) => buildWaypoint(
    item.camera,
    item.lookAt || basePlan.center,
    {
      ringIndex: reshootRingIndex,
      pointIndex: index + 1,
      kind: 'ig-reshoot',
      informationGain: item.informationGain,
      edgeIndex: item.edgeIndex != null ? item.edgeIndex + 1 : undefined,
      facadeSegmentIndex: item.segmentIndex != null ? item.segmentIndex + 1 : undefined,
    },
  ))

  const reshootRing = {
    ringIndex: reshootRingIndex,
    targetBandCenter: roundNumber(
      selected.reduce((sum, item) => sum + item.heightM, 0) / selected.length,
      2,
    ),
    flightAltitude: roundNumber(
      selected.reduce((sum, item) => sum + item.camera.altitude, 0) / selected.length,
      2,
    ),
    waypoints: reshootWaypoints,
    kind: 'ig-reshoot',
  }

  const rings = [...(basePlan.rings || []), reshootRing]
  const baseWaypoints = [...(basePlan.baseWaypoints || []), ...reshootWaypoints]

  const missionWaypoints = [...(basePlan.waypoints || [])]
  if (missionWaypoints.length && reshootWaypoints.length) {
    missionWaypoints.push({
      ...reshootWaypoints[0],
      kind: 'connector',
      pointIndex: 0,
      closeLoop: false,
    })
  }
  reshootWaypoints.forEach((waypoint) => {
    missionWaypoints.push(waypoint)
  })

  return { rings, waypoints: missionWaypoints, baseWaypoints }
}

/**
 * Append dynamic information-gain reshoot segment to a seed mission plan.
 */
export const appendInformationGainReshoot = (basePlan, options = {}) => {
  if (!basePlan || options.enabled === false) {
    return {
      ...basePlan,
      informationGainReshoot: {
        enabled: false,
        selectedCount: 0,
        messages: ['信息增益补拍已关闭。'],
      },
    }
  }

  const seedPhotos = collectSeedPhotos(basePlan)
  if (seedPhotos.length < 4) {
    return {
      ...basePlan,
      informationGainReshoot: {
        enabled: true,
        selectedCount: 0,
        messages: ['种子航点不足，跳过信息增益补拍。'],
      },
    }
  }

  const budget = resolveIgReshootBudget(options.qualitySpeedBalance, seedPhotos.length)
  const missionType = basePlan.missionType === 'building' ? 'building' : 'orbit'
  const model = missionType === 'building'
    ? buildBuildingCoverageModel(basePlan, budget)
    : buildOrbitCoverageModel(basePlan, budget)

  if (missionType === 'building') {
    markBuildingCoverage(model, seedPhotos)
  } else {
    markOrbitCoverage(model, seedPhotos, basePlan.fieldOfView)
  }

  const candidates = missionType === 'building'
    ? createBuildingCandidates(basePlan, model, budget)
    : createOrbitCandidates(basePlan, model, budget)

  const selection = selectReshootCandidates({
    candidates,
    cells: model.cells,
    seedPhotos,
    budget,
  })

  const appended = appendReshootWaypoints(basePlan, selection.selected)
  const photosPerLoop = Math.max(
    Number(basePlan.photosPerLoop) || 0,
    selection.selected.length,
  )
  const summary = buildMissionSummary(appended.waypoints, appended.rings, photosPerLoop)
  const extraPathMeters = Math.max(
    calculateWaypointSequenceLength(appended.waypoints) - Number(basePlan.summary?.pathLengthMeters || 0),
    0,
  )

  const profileLabel = budget.beta < 0.34
    ? '速度优先'
    : budget.beta > 0.66
      ? '质量优先'
      : '均衡'

  const messages = selection.selected.length
    ? [
      `信息增益补拍（${profileLabel}）：新增 ${selection.selected.length} 个视点，覆盖缺口改善约 ${(selection.coverageImprovementRatio * 100).toFixed(0)}%。`,
      `预估补拍航程约 ${roundNumber(extraPathMeters, 1)} m（≈ ${roundNumber(extraPathMeters / DEFAULT_CRUISE_SPEED, 0)} s）。`,
    ]
    : ['种子航线覆盖已较充分，未追加信息增益补拍点。']

  return {
    ...basePlan,
    rings: appended.rings,
    waypoints: appended.waypoints,
    baseWaypoints: appended.baseWaypoints,
    photosPerLoop,
    summary,
    planningInputs: {
      ...(basePlan.planningInputs || {}),
      igReshootEnabled: true,
      igQualitySpeedBalance: budget.beta,
    },
    informationGainReshoot: {
      enabled: true,
      profileLabel,
      qualitySpeedBalance: budget.beta,
      selectedCount: selection.selected.length,
      maxExtraPhotos: budget.maxExtraPhotos,
      initialUncovered: selection.initialUncovered,
      remainingUncovered: selection.remainingUncovered,
      coverageImprovementRatio: selection.coverageImprovementRatio,
      extraPathMeters: roundNumber(extraPathMeters, 2),
      extraFlightTimeSeconds: roundNumber(extraPathMeters / DEFAULT_CRUISE_SPEED, 1),
      selected: selection.selected.map((item) => ({
        id: item.id,
        informationGain: item.informationGain,
        azimuthDeg: roundNumber(item.azimuthDeg, 1),
        altitude: roundNumber(item.camera.altitude, 1),
      })),
      messages,
    },
  }
}
