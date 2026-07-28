<template>
  <div class="cesiumContainer" :class="selectionMode && 'pick-mode'">
    <div v-if="selectionHint" class="pick-hint">{{ selectionHint }}</div>
    <div class="mapContainer" ref="mapContainer" id="mapContainer"></div>
  </div>
</template>

<script setup>
  import { ref, onMounted, onUnmounted, watch } from 'vue'
  import { useMapStore } from '@/stores/map'
  import { storeToRefs } from 'pinia'
  import { ElMessage } from 'element-plus'
  import cesiumManager from '@/utils/CesiumManager'
  import { OFFLINE_MAP_INFO, TIANDITU_MAP_INFO, fetchOfflineMapMeta } from '@/utils/mapProviders'
  import { projectDestination } from '@/utils/routePlanner'
  import { getDroneColor } from '@/utils/multiUavPlanner'
  import {
    Cartesian2,
    Cartesian3,
    Color,
    ColorMaterialProperty,
    HeightReference,
    LabelStyle,
    NearFarScalar,
    PolygonHierarchy,
    PolylineGlowMaterialProperty,
    VerticalOrigin,
  } from 'cesium'

  const mapStore = useMapStore()
  const {
    setCesiumManagerInfo,
    applyPickedPoint,
    setCityModelStatus,
    setSelectedModelTarget,
    appendShapePoint,
    updateShapePreview,
  } = mapStore
  const {
    selectionMode,
    selectionHint,
    centerPoint,
    edgePoint,
    routePlan,
    cityModelEnabled,
    activeCityModelId,
    mapProvider,
    selectedModelTarget,
    selectedModelTargets,
    activePlanner,
    shapeDraft,
    shapePreviewFootprint,
    shapeTool,
  } = storeToRefs(mapStore)

  const mapContainer = ref(null)
  const renderEntities = []
  let removeClickListener = null
  let removeMoveListener = null
  let removeCityModelStatusListener = null
  let shapePreviewThrottleAt = 0
  let routePlaybackTimer = null
  let routePlaybackState = null
  let lastRoutePlanIdentity = null

  const ROUTE_FLOWN_COLOR = '#20c997'
  const ROUTE_REMAIN_COLOR = '#4dabf7'
  const ROUTE_CURRENT_COLOR = '#ffd43b'
  const ROUTE_PROGRESS_STEP_MS = 1100
  const ROUTE_LOOP_PAUSE_MS = 2200

  const removeRenderEntities = () => {
    stopRoutePlayback()

    if (!cesiumManager.viewer) {
      return
    }

    renderEntities.splice(0).forEach((entity) => {
      cesiumManager.viewer.entities.remove(entity)
    })

    cesiumManager.removeFrustum()
  }

  const addRenderEntity = (entity) => {
    renderEntities.push(entity)
    return entity
  }

  const getSegmentColor = (status) => {
    if (status === 'risky') {
      return Color.fromCssColorString('#e03131')
    }

    if (status === 'detour') {
      return Color.fromCssColorString('#f08c00')
    }

    return Color.fromCssColorString('#4dabf7')
  }

  const createPointEntity = ({ longitude, latitude, height = 0, color, text, pixelSize = 16 }) => {
    return addRenderEntity(
      cesiumManager.viewer.entities.add({
        position: Cartesian3.fromDegrees(longitude, latitude, height),
        point: {
          pixelSize,
          color,
          outlineColor: Color.WHITE,
          outlineWidth: 2,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          heightReference: HeightReference.NONE,
        },
        label: {
          text,
          font: '14px "Microsoft YaHei"',
          fillColor: Color.WHITE,
          style: LabelStyle.FILL,
          showBackground: true,
          backgroundColor: color.withAlpha(0.78),
          pixelOffset: new Cartesian2(0, -26),
          verticalOrigin: VerticalOrigin.BOTTOM,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
          scaleByDistance: new NearFarScalar(1000, 1, 150000, 0.45),
        },
      })
    )
  }

  const resolveArrowLengthMeters = () => {
    const orbitRadius = Number(routePlan.value?.orbitRadiusMeters || 0)
    if (orbitRadius > 0) {
      return Math.min(18, Math.max(7, orbitRadius * 0.14))
    }

    return 10
  }

  const createCameraHeadingArrow = (waypoint, color, arrowLengthMeters = 10) => {
    const heading = Number(waypoint?.heading)
    if (!Number.isFinite(heading)) {
      return null
    }

    const altitude = Number(waypoint.altitude || 0)
    const origin = {
      longitude: Number(waypoint.longitude),
      latitude: Number(waypoint.latitude),
    }
    const shaftLength = Math.max(arrowLengthMeters, 5)
    const headLength = Math.max(shaftLength * 0.32, 2.2)

    const tip = projectDestination(origin, heading, shaftLength)
    const left = projectDestination(tip, heading + 155, headLength)
    const right = projectDestination(tip, heading - 155, headLength)

    const toCartesian = (point) => Cartesian3.fromDegrees(
      point.longitude,
      point.latitude,
      altitude,
    )

    const material = new ColorMaterialProperty(color)
    const shaft = addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: [toCartesian(origin), toCartesian(tip)],
          width: 3,
          material,
          clampToGround: false,
        },
      })
    )

    const head = addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: [toCartesian(left), toCartesian(tip), toCartesian(right)],
          width: 3,
          material,
          clampToGround: false,
        },
      })
    )

    return { shaft, head }
  }

  const setArrowColor = (arrow, color) => {
    if (!arrow) {
      return
    }

    const material = new ColorMaterialProperty(color)
    if (arrow.shaft?.polyline) {
      arrow.shaft.polyline.material = material
      arrow.shaft.polyline.width = 3
    }

    if (arrow.head?.polyline) {
      arrow.head.polyline.material = material
      arrow.head.polyline.width = 3
    }
  }

  const toRouteCartesian = (waypoint) => Cartesian3.fromDegrees(
    Number(waypoint.longitude),
    Number(waypoint.latitude),
    Number(waypoint.altitude || 0),
  )

  const buildRoutePositions = (waypoints) => {
    if (!Array.isArray(waypoints) || waypoints.length === 0) {
      return []
    }

    if (waypoints.length === 1) {
      const only = toRouteCartesian(waypoints[0])
      return [only, only]
    }

    return waypoints.map((waypoint) => toRouteCartesian(waypoint))
  }

  const stopRoutePlayback = () => {
    if (routePlaybackTimer) {
      window.clearTimeout(routePlaybackTimer)
      routePlaybackTimer = null
    }

    routePlaybackState = null
  }

  const resolveTrackColors = (missionColorCss) => {
    const remainCss = missionColorCss || ROUTE_REMAIN_COLOR
    return {
      remainColor: Color.fromCssColorString(remainCss),
      flownColor: Color.fromCssColorString(ROUTE_FLOWN_COLOR),
      currentColor: Color.fromCssColorString(ROUTE_CURRENT_COLOR),
    }
  }

  const applyTrackPlaybackVisual = (track, progressIndex) => {
    const {
      waypoints,
      flownLine,
      remainLine,
      pointEntities,
      remainColor,
      flownColor,
      currentColor,
    } = track

    const safeIndex = Math.min(Math.max(progressIndex, 0), Math.max(waypoints.length - 1, 0))
    const flownWaypoints = waypoints.slice(0, Math.max(safeIndex + 1, 1))
    const remainWaypoints = waypoints.slice(Math.min(safeIndex, waypoints.length - 1))

    if (flownLine?.polyline) {
      flownLine.polyline.positions = buildRoutePositions(flownWaypoints)
      flownLine.show = waypoints.length > 0
    }

    if (remainLine?.polyline) {
      remainLine.polyline.positions = buildRoutePositions(remainWaypoints)
      remainLine.show = safeIndex < waypoints.length - 1
    }

    pointEntities.forEach((entry) => {
      const isFlown = entry.index < safeIndex
      const isCurrent = entry.index === safeIndex
      let color = remainColor
      let pixelSize = entry.baseSize
      let outlineWidth = 2

      if (isFlown) {
        color = flownColor
      }

      if (isCurrent) {
        color = currentColor
        pixelSize = entry.baseSize + 5
        outlineWidth = 3
      }

      if (entry.entity.point) {
        entry.entity.point.color = color
        entry.entity.point.pixelSize = pixelSize
        entry.entity.point.outlineColor = Color.WHITE
        entry.entity.point.outlineWidth = outlineWidth
      }

      if (entry.entity.label) {
        entry.entity.label.backgroundColor = color.withAlpha(0.88)
        entry.entity.label.fillColor = Color.WHITE
        entry.entity.label.show = Boolean(entry.labelText)
      }

      if (entry.arrow) {
        setArrowColor(entry.arrow, color)
        if (entry.arrow.shaft?.polyline) {
          entry.arrow.shaft.polyline.width = isCurrent ? 4 : 3
        }
        if (entry.arrow.head?.polyline) {
          entry.arrow.head.polyline.width = isCurrent ? 4 : 3
        }
      }
    })
  }

  const applyRoutePlaybackVisual = () => {
    if (!routePlaybackState || !cesiumManager.viewer) {
      return
    }

    const { tracks, progressIndex } = routePlaybackState
    tracks.forEach((track) => {
      applyTrackPlaybackVisual(track, progressIndex)
    })
  }

  const scheduleRoutePlaybackStep = () => {
    if (!routePlaybackState) {
      return
    }

    const { progressIndex, maxProgressIndex } = routePlaybackState
    const atEnd = progressIndex >= maxProgressIndex
    const delay = atEnd ? ROUTE_LOOP_PAUSE_MS : ROUTE_PROGRESS_STEP_MS

    routePlaybackTimer = window.setTimeout(() => {
      if (!routePlaybackState) {
        return
      }

      if (routePlaybackState.progressIndex >= routePlaybackState.maxProgressIndex) {
        routePlaybackState.progressIndex = 0
      } else {
        routePlaybackState.progressIndex += 1
      }

      applyRoutePlaybackVisual()
      scheduleRoutePlaybackStep()
    }, delay)
  }

  const buildPlaybackTrack = (mission, { multi = false } = {}) => {
    const waypoints = (mission.waypoints || []).filter((waypoint) => (
      waypoint
      && Number.isFinite(Number(waypoint.longitude))
      && Number.isFinite(Number(waypoint.latitude))
    ))

    if (waypoints.length < 2) {
      return null
    }

    const colorCss = mission.color || getDroneColor(mission.droneIndex)
    const { remainColor, flownColor, currentColor } = resolveTrackColors(colorCss)

    const flownLine = addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: buildRoutePositions([waypoints[0]]),
          width: multi ? 5 : 6,
          material: new PolylineGlowMaterialProperty({
            glowPower: 0.28,
            color: flownColor,
          }),
        },
      }),
    )

    const remainLine = addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: buildRoutePositions(waypoints),
          width: multi ? 4 : 5,
          material: new PolylineGlowMaterialProperty({
            glowPower: 0.16,
            color: remainColor.withAlpha(0.95),
          }),
        },
      }),
    )

    let labelCounter = 0
    const arrowLengthMeters = resolveArrowLengthMeters()
    const droneId = mission.droneId || `UAV-${(mission.droneIndex || 0) + 1}`
    const pointEntities = waypoints.map((waypoint, index) => {
      const isIgReshoot = waypoint.kind === 'ig-reshoot'
      const isLabeled = !waypoint.closeLoop && waypoint.kind !== 'connector'
      if (isLabeled) {
        labelCounter += 1
      }

      const headingDeg = Number(waypoint.heading)
      const headingSuffix =
        isLabeled && Number.isFinite(headingDeg) ? ` ${headingDeg.toFixed(0)}°` : ''
      const labelText = isLabeled
        ? (multi
          ? `${droneId}-${labelCounter}${headingSuffix}`
          : `${isIgReshoot ? 'IG' : 'WP'}${labelCounter}${headingSuffix}`)
        : ''
      const pointColor = isIgReshoot
        ? Color.fromCssColorString('#12b886')
        : remainColor
      const baseSize = index === 0 ? (multi ? 13 : 14) : isLabeled ? (multi ? 9 : 10) : 7
      const entity = createPointEntity({
        longitude: waypoint.longitude,
        latitude: waypoint.latitude,
        height: waypoint.altitude,
        color: pointColor,
        text: labelText || '·',
        pixelSize: isIgReshoot ? baseSize + 2 : baseSize,
      })

      if (!labelText && entity.label) {
        entity.label.show = false
      }

      // Skip closeLoop / connector duplicates — they share the first WP position.
      const arrow = isLabeled
        ? createCameraHeadingArrow(waypoint, remainColor, arrowLengthMeters)
        : null

      return {
        index,
        entity,
        arrow,
        labelText,
        baseSize,
      }
    })

    return {
      waypoints,
      flownLine,
      remainLine,
      pointEntities,
      remainColor,
      flownColor,
      currentColor,
    }
  }

  const startFleetRoutePlayback = (missions, { shouldFlyTo = false, multi = false } = {}) => {
    stopRoutePlayback()

    if (!cesiumManager.viewer || !Array.isArray(missions) || !missions.length) {
      return
    }

    const tracks = missions
      .map((mission) => buildPlaybackTrack(mission, { multi }))
      .filter(Boolean)

    if (!tracks.length) {
      return
    }

    const maxProgressIndex = Math.max(...tracks.map((track) => track.waypoints.length - 1), 0)

    routePlaybackState = {
      tracks,
      progressIndex: 0,
      maxProgressIndex,
    }

    applyRoutePlaybackVisual()
    scheduleRoutePlaybackStep()

    if (shouldFlyTo) {
      cesiumManager.viewer.flyTo(renderEntities, {
        duration: 1.2,
      })
    }
  }

  const startRoutePlayback = (waypoints, { shouldFlyTo = false } = {}) => {
    startFleetRoutePlayback(
      [{
        droneId: 'UAV-1',
        droneIndex: 0,
        color: ROUTE_REMAIN_COLOR,
        waypoints,
      }],
      { shouldFlyTo, multi: false },
    )
  }

  const addBuildingVolumeHighlight = (footprintPoints, roofHeight) => {
    if (!Array.isArray(footprintPoints) || footprintPoints.length < 3) {
      return
    }

    // Lift highlight slightly above the solid white model to avoid z-fighting.
    const baseHeight = 0.35
    const topHeight = globalThis.Math.max(Number(roofHeight) || 0, 5) + 0.8
    const highlightColor = Color.fromCssColorString('#ffd43b')
    const edgeColor = Color.fromCssColorString('#fab005')

    const topRing = footprintPoints.map((point) =>
      Cartesian3.fromDegrees(point.longitude, point.latitude, topHeight)
    )
    const bottomRing = footprintPoints.map((point) =>
      Cartesian3.fromDegrees(point.longitude, point.latitude, baseHeight)
    )

    // Roof plane only (slightly above white-model roof) — no extruded walls through the mesh.
    addRenderEntity(
      cesiumManager.viewer.entities.add({
        polygon: {
          hierarchy: new PolygonHierarchy(topRing),
          perPositionHeight: true,
          material: highlightColor.withAlpha(0.28),
          outline: false,
          heightReference: HeightReference.NONE,
        },
      })
    )

    // Vertical edges.
    footprintPoints.forEach((point) => {
      addRenderEntity(
        cesiumManager.viewer.entities.add({
          polyline: {
            positions: [
              Cartesian3.fromDegrees(point.longitude, point.latitude, baseHeight),
              Cartesian3.fromDegrees(point.longitude, point.latitude, topHeight),
            ],
            width: 3,
            material: edgeColor.withAlpha(0.98),
            clampToGround: false,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          },
        })
      )
    })

    // Top outline.
    addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: [...topRing, topRing[0]],
          width: 5,
          material: new PolylineGlowMaterialProperty({
            glowPower: 0.18,
            color: edgeColor,
          }),
          clampToGround: false,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      })
    )

    // Bottom footprint outline.
    addRenderEntity(
      cesiumManager.viewer.entities.add({
        polyline: {
          positions: [...bottomRing, bottomRing[0]],
          width: 3,
          material: edgeColor.withAlpha(0.92),
          clampToGround: false,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      })
    )
  }

  const addShapeDraftPreview = () => {
    const footprint = shapePreviewFootprint.value
    const vertices = shapeDraft.value?.vertices || []

    vertices.forEach((point, index) => {
      createPointEntity({
        longitude: point.longitude,
        latitude: point.latitude,
        height: 0,
        color: Color.fromCssColorString('#3b78e7'),
        text: `${index + 1}`,
        pixelSize: 12,
      })
    })

    if (Array.isArray(footprint) && footprint.length >= 2) {
      const ring = footprint.map((point) => Cartesian3.fromDegrees(point.longitude, point.latitude, 0))
      const closed = footprint.length >= 3 ? [...ring, ring[0]] : ring

      addRenderEntity(
        cesiumManager.viewer.entities.add({
          polyline: {
            positions: closed,
            width: 3,
            material: Color.fromCssColorString('#3b78e7').withAlpha(0.95),
            clampToGround: false,
          },
        })
      )
    }

    if (Array.isArray(footprint) && footprint.length >= 3) {
      addRenderEntity(
        cesiumManager.viewer.entities.add({
          polygon: {
            hierarchy: new PolygonHierarchy(
              footprint.map((point) => Cartesian3.fromDegrees(point.longitude, point.latitude))
            ),
            height: 0,
            material: Color.fromCssColorString('#3b78e7').withAlpha(0.22),
            outline: false,
          },
        })
      )
    }

    if (shapeTool.value === 'circle' && vertices[0]) {
      createPointEntity({
        longitude: vertices[0].longitude,
        latitude: vertices[0].latitude,
        height: 0,
        color: Color.fromCssColorString('#60a5fa'),
        text: '圆心',
        pixelSize: 14,
      })
    }
  }

  const renderSelection = () => {
    const isBuildingContext = activePlanner.value === 'building'
      || activePlanner.value === 'search'
      || activePlanner.value === 'shape'
      || routePlan.value?.missionType === 'building'

    if (`${selectionMode.value || ''}`.startsWith('shape-') || (shapeDraft.value?.vertices?.length && !selectedModelTarget.value)) {
      addShapeDraftPreview()
    }

    const highlightTargets = selectedModelTargets.value?.length
      ? selectedModelTargets.value
      : (selectedModelTarget.value ? [selectedModelTarget.value] : [])

    highlightTargets.forEach((target) => {
      if (target?.footprintPoints?.length >= 3) {
        addBuildingVolumeHighlight(
          target.footprintPoints,
          target.roofHeight || target.highlightHeight || target.highElevationM || 30,
        )
      }
    })

    if (!isBuildingContext && selectedModelTarget.value?.centerPoint && selectedModelTarget.value?.radiusMeters) {
      addRenderEntity(
        cesiumManager.viewer.entities.add({
          position: Cartesian3.fromDegrees(selectedModelTarget.value.centerPoint.longitude, selectedModelTarget.value.centerPoint.latitude, 0),
          ellipse: {
            semiMajorAxis: selectedModelTarget.value.radiusMeters,
            semiMinorAxis: selectedModelTarget.value.radiusMeters,
            height: 0,
            material: Color.fromCssColorString('#20c997').withAlpha(0.1),
            outline: true,
            outlineColor: Color.fromCssColorString('#12b886').withAlpha(0.75),
          },
        })
      )
    }

    if (!isBuildingContext && centerPoint.value) {
      createPointEntity({
        ...centerPoint.value,
        color: Color.fromCssColorString('#f76707'),
        text: '目标中心',
        pixelSize: 18,
      })
    }

    if (!isBuildingContext && edgePoint.value) {
      createPointEntity({
        ...edgePoint.value,
        color: Color.fromCssColorString('#fab005'),
        text: '目标边缘',
        pixelSize: 16,
      })
    }

    if (!isBuildingContext && routePlan.value?.targetRadiusMeters && centerPoint.value) {
      addRenderEntity(
        cesiumManager.viewer.entities.add({
          position: Cartesian3.fromDegrees(centerPoint.value.longitude, centerPoint.value.latitude, 0),
          ellipse: {
            semiMajorAxis: routePlan.value.targetRadiusMeters,
            semiMinorAxis: routePlan.value.targetRadiusMeters,
            height: 0,
            material: Color.fromCssColorString('#ffa94d').withAlpha(0.08),
            outline: true,
            outlineColor: Color.fromCssColorString('#ff922b').withAlpha(0.75),
          },
        })
      )
    }
  }

  const renderRoute = () => {
    if (!routePlan.value?.waypoints?.length && !routePlan.value?.missions?.length) {
      lastRoutePlanIdentity = null
      return
    }

    const missions = Array.isArray(routePlan.value.missions) && routePlan.value.missions.length
      ? routePlan.value.missions
      : [{
          droneId: 'UAV-1',
          droneIndex: 0,
          color: ROUTE_REMAIN_COLOR,
          waypoints: routePlan.value.waypoints || [],
          rings: routePlan.value.rings || [],
          routeSegments: routePlan.value.routeSegments || [],
        }]

    const allFlightWaypoints = missions.flatMap((mission) =>
      (mission.waypoints || []).filter((waypoint) => (
        waypoint
        && Number.isFinite(Number(waypoint.longitude))
        && Number.isFinite(Number(waypoint.latitude))
      )),
    )

    if (allFlightWaypoints.length < 2 && missions.every((mission) => (mission.waypoints || []).length < 2)) {
      return
    }

    const routeIdentity = [
      routePlan.value.droneCount || 1,
      allFlightWaypoints.length,
      routePlan.value.summary?.pathLengthMeters ?? '',
      routePlan.value.summary?.waypointCount ?? '',
      allFlightWaypoints[0]?.longitude,
      allFlightWaypoints[0]?.latitude,
      allFlightWaypoints[allFlightWaypoints.length - 1]?.longitude,
      allFlightWaypoints[allFlightWaypoints.length - 1]?.latitude,
    ].join(':')
    const shouldFlyTo = lastRoutePlanIdentity !== routeIdentity
    lastRoutePlanIdentity = routeIdentity

    // Keep obstacle-risk segments as a faint underlay.
    missions.forEach((mission) => {
      ;(mission.routeSegments || routePlan.value.routeSegments || []).forEach((segment) => {
        if (segment.status !== 'risky' && segment.status !== 'detour') {
          return
        }

        const segmentPositions = segment.positions.map((position) =>
          Cartesian3.fromDegrees(position.longitude, position.latitude, position.altitude)
        )

        addRenderEntity(
          cesiumManager.viewer.entities.add({
            polyline: {
              positions: segmentPositions,
              width: 3,
              material: new PolylineGlowMaterialProperty({
                glowPower: 0.12,
                color: getSegmentColor(segment.status).withAlpha(0.55),
              }),
            },
          })
        )
      })
    })

    const isMulti = Boolean(routePlan.value.multiUav && missions.length > 1)

    missions.forEach((mission) => {
      const colorCss = mission.color || getDroneColor(mission.droneIndex)
      const missionColor = Color.fromCssColorString(colorCss)

      ;(mission.rings || []).forEach((ring, ringIndex) => {
        if (routePlan.value.missionType === 'building' && ring.footprintPoints?.length >= 3) {
          const ringPositions = ring.footprintPoints.map((point) =>
            Cartesian3.fromDegrees(point.longitude, point.latitude, point.altitude)
          )

          addRenderEntity(
            cesiumManager.viewer.entities.add({
              polygon: {
                hierarchy: new PolygonHierarchy(ringPositions),
                perPositionHeight: true,
                material: missionColor.withAlpha(0.05),
                outline: false,
              },
            })
          )

          // Multi-UAV uses flowing playback for the route; skip static ring lines
          // to avoid double-drawing over flown/remain segments.
          if (!isMulti) {
            addRenderEntity(
              cesiumManager.viewer.entities.add({
                polyline: {
                  positions: ringPositions.length < 3
                    ? ringPositions
                    : [...ringPositions, ringPositions[0]],
                  width: 3,
                  material: new PolylineGlowMaterialProperty({
                    glowPower: 0.16,
                    color: missionColor.withAlpha(0.92),
                  }),
                  clampToGround: false,
                },
              })
            )
          }

          return
        }

        if (!isMulti && routePlan.value.center && routePlan.value.orbitRadiusMeters) {
          addRenderEntity(
            cesiumManager.viewer.entities.add({
              position: Cartesian3.fromDegrees(routePlan.value.center.longitude, routePlan.value.center.latitude, ring.flightAltitude),
              ellipse: {
                semiMajorAxis: routePlan.value.orbitRadiusMeters,
                semiMinorAxis: routePlan.value.orbitRadiusMeters,
                height: ring.flightAltitude,
                material: Color.fromCssColorString('#228be6').withAlpha(0.06),
                outline: true,
                outlineColor: Color.fromCssColorString(ringIndex % 2 === 0 ? '#4dabf7' : '#74c0fc').withAlpha(0.6),
              },
            })
          )
        }
      })
    })

    routePlan.value.obstacleAnalysis?.collisionPoints?.slice(0, 40).forEach((point, index) => {
      createPointEntity({
        longitude: point.longitude,
        latitude: point.latitude,
        height: point.obstacleHeight,
        color: Color.fromCssColorString('#e03131'),
        text: index === 0 ? '碰撞风险' : `风险${index + 1}`,
        pixelSize: 8,
      })
    })

    if (isMulti) {
      startFleetRoutePlayback(missions, { shouldFlyTo, multi: true })
      return
    }

    const flightWaypoints = (missions[0]?.waypoints || routePlan.value.waypoints || []).filter((waypoint) => (
      waypoint
      && Number.isFinite(Number(waypoint.longitude))
      && Number.isFinite(Number(waypoint.latitude))
    ))
    startRoutePlayback(flightWaypoints, { shouldFlyTo })
  }

  const renderMapScene = () => {
    if (!cesiumManager.viewer) {
      return
    }

    removeRenderEntities()
    renderSelection()
    renderRoute()

    if (!routePlan.value && renderEntities.length > 0) {
      cesiumManager.viewer.flyTo(renderEntities, {
        duration: 0.8,
      })
    }
  }

  onMounted(async () => {
    let offlineMapMeta = OFFLINE_MAP_INFO
    if (mapProvider.value === 'offline') {
      try {
        offlineMapMeta = await fetchOfflineMapMeta()
      } catch (error) {
        console.warn('离线地图元数据加载失败，已回退到内置范围参数', error)
      }
    } else if (mapProvider.value === 'tianditu') {
      offlineMapMeta = {
        ...TIANDITU_MAP_INFO,
        initialView: { ...TIANDITU_MAP_INFO.initialView },
        initialOrientation: { ...TIANDITU_MAP_INFO.initialOrientation },
      }
    }

    await cesiumManager.init(mapContainer.value, {
      mapProvider: mapProvider.value,
      offlineMapMeta,
    })
    // Expose manager as soon as the Viewer exists so planner actions are not
    // blocked by multi-city tileset loading (which can take many seconds).
    setCesiumManagerInfo(cesiumManager)

    removeCityModelStatusListener = cesiumManager.onCityModelStatusChange((status) => {
      setCityModelStatus(status.message)
    })

    removeClickListener = cesiumManager.on('click', async (point, pickedObject) => {
      if (!selectionMode.value || !point) {
        return
      }

      if (selectionMode.value === 'model') {
        const modelTarget = await cesiumManager.extractModelTargetFromPick(pickedObject, point)
        if (!modelTarget) {
          ElMessage.warning('请点击建筑白模中的建筑或模型表面')
          return
        }

        setSelectedModelTarget(modelTarget)
        ElMessage.success(`已选择目标：${modelTarget.name}`)
        return
      }

      if (`${selectionMode.value}`.startsWith('shape-')) {
        const result = appendShapePoint(point)
        if (result.status === 'completed') {
          ElMessage.success(`已完成${result.target?.name || '图形区域'}框选`)
        } else if (result.status === 'invalid') {
          ElMessage.warning('图形过小，请重新绘制')
        } else if (result.status === 'drawing' && result.tool === 'polygon') {
          ElMessage.info(`已添加第 ${result.vertexCount} 个顶点`)
        }
        return
      }

      applyPickedPoint({
        longitude: point.longitude,
        latitude: point.latitude,
        height: point.height > 0 ? point.height : 0,
      })
    })

    removeMoveListener = cesiumManager.on('mousemove', (point) => {
      if (!`${selectionMode.value || ''}`.startsWith('shape-') || !point) {
        return
      }

      const now = Date.now()
      if (now - shapePreviewThrottleAt < 40) {
        return
      }

      shapePreviewThrottleAt = now
      updateShapePreview(point)
    })

    // 天地图：进入时俯瞰中国全境；离线仍可落到南京城区白模。
    const hangzhouView = cesiumManager.getCityModelDefaultView()
    let initialView
    let initialOrientation = { heading: 18, pitch: -45 }

    if (mapProvider.value === 'tianditu') {
      initialView = offlineMapMeta.initialView || TIANDITU_MAP_INFO.initialView
      initialOrientation = offlineMapMeta.initialOrientation || TIANDITU_MAP_INFO.initialOrientation
    } else if (mapProvider.value === 'offline') {
      initialView = cityModelEnabled.value ? hangzhouView : offlineMapMeta.initialView
    } else {
      initialView = cityModelEnabled.value
        ? hangzhouView
        : (offlineMapMeta.initialView || TIANDITU_MAP_INFO.initialView)
    }

    // 先落到目标视角，再开白模，避免加载回调抢相机飞到城区。
    await cesiumManager.flyTo(initialView, initialOrientation, {
      duration: 1.2,
    })

    // City tilesets load in background — do not block sampling / search UI.
    void (async () => {
      try {
        if (cesiumManager.setActiveCityModel) {
          await cesiumManager.setActiveCityModel(activeCityModelId.value, { flyTo: false })
        }
        cesiumManager.setCityModelEnabled(cityModelEnabled.value)
        if (mapProvider.value !== 'tianditu' && cityModelEnabled.value) {
          await cesiumManager.prepareCityModelForPlanning()
        }
      } catch (error) {
        console.warn('建筑白模预加载失败', error)
      }
    })()

    renderMapScene()
  })

  watch(
    cityModelEnabled,
    (enabled) => {
      cesiumManager.setCityModelEnabled(enabled)
    },
    { immediate: false },
  )

  watch(
    [centerPoint, edgePoint, routePlan, selectedModelTarget, selectedModelTargets, activePlanner, shapeDraft, shapePreviewFootprint, selectionMode],
    () => {
      renderMapScene()
    },
    { deep: true },
  )

  onUnmounted(() => {
    stopRoutePlayback()
    removeClickListener?.()
    removeMoveListener?.()
    removeCityModelStatusListener?.()
    setCityModelStatus('建筑白模将在进入覆盖范围后自动加载')
    setCesiumManagerInfo(null)
    cesiumManager.destroy()
  })
</script>

<style scoped lang="scss">
  .cesiumContainer {
    width: 100%;
    height: 100%;
    position: relative;
    background: #1c2028;

    .mapContainer {
      width: 100%;
      height: 100%;
    }

    .pick-hint {
      position: absolute;
      top: 18px;
      left: 50%;
      transform: translateX(-50%);
      padding: 10px 18px;
      border-radius: 999px;
      background: rgba(17, 32, 59, 0.78);
      color: #ffffff;
      font-size: 14px;
      z-index: 50;
      backdrop-filter: blur(12px);
    }
  }

  .pick-mode {
    cursor: crosshair;
  }
</style>
