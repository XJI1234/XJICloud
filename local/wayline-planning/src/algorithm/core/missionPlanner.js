import { planBuildingFootprintMission, planSinglePointOrbitMission } from '@/utils/routePlanner'
import { buildObstacleAwareMission } from '@/utils/routeCollisionPlanner'
import { appendInformationGainReshoot } from '@/utils/informationGainReshootPlanner'
import {
  assembleMultiUavPlan,
  normalizeDroneCount,
  planMultiUavFromBasePlan,
} from '@/utils/multiUavPlanner'
import { planOrbitMissionWithWasm } from '@/wasm/waylinePlannerKernel'

const createUnavailableObstacleAnalysisResult = (basePlan, message) => ({
  ...basePlan,
  obstacleAnalysis: {
    ...basePlan.obstacleAnalysis,
    enabled: false,
    analyzed: false,
    status: 'unavailable',
    messages: [message],
  },
})

const buildOrbitMissionInput = ({ form, centerPoint, edgePoint }) => ({
  ...form,
  centerPoint,
  edgePoint,
})

const buildBuildingMissionInput = ({ form, selectedModelTarget }) => ({
  ...form,
  footprintPoints: selectedModelTarget.footprintPoints,
})

const resolveDroneCount = (form) => normalizeDroneCount(form?.droneCount ?? 1)

const attachMissionMeta = (plan, { planningRuntime, form }) => ({
  ...plan,
  planningRuntime,
  planningInputs: {
    ...(plan.planningInputs || {}),
    droneCount: resolveDroneCount(form),
  },
})

export const buildBaseMissionPlanAsync = async ({ plannerType, form, centerPoint, edgePoint, selectedModelTarget }) => {
  if (plannerType === 'building') {
    if (!selectedModelTarget?.footprintPoints?.length) {
      throw new Error('建筑采样至少需要 3 个轮廓点')
    }

    return attachMissionMeta(
      planBuildingFootprintMission(buildBuildingMissionInput({ form, selectedModelTarget })),
      { planningRuntime: 'javascript', form },
    )
  }

  if (!centerPoint || !edgePoint) {
    throw new Error('单点环绕采样需要中心点和边缘点')
  }

  // Prefer JavaScript first for reliability in iframe / production deploy.
  // WASM is optional acceleration; never block planning if it fails.
  try {
    return attachMissionMeta(
      planSinglePointOrbitMission(buildOrbitMissionInput({ form, centerPoint, edgePoint })),
      { planningRuntime: 'javascript', form },
    )
  } catch (jsError) {
    console.warn('JavaScript 轨道规划失败，尝试 WASM', jsError)
    try {
      const wasmPlan = await planOrbitMissionWithWasm({
        form,
        centerPoint,
        edgePoint,
      })
      return attachMissionMeta(wasmPlan, { planningRuntime: 'wasm-cpp', form })
    } catch (wasmError) {
      const jsMessage = jsError instanceof Error ? jsError.message : String(jsError)
      const wasmMessage = wasmError instanceof Error ? wasmError.message : String(wasmError)
      throw new Error(`航线规划失败：${jsMessage}；WASM 回退也失败：${wasmMessage}`)
    }
  }
}

export const buildBaseMissionPlan = ({ plannerType, form, centerPoint, edgePoint, selectedModelTarget }) => {
  if (plannerType === 'building') {
    if (!selectedModelTarget?.footprintPoints?.length) {
      throw new Error('建筑采样至少需要 3 个轮廓点')
    }

    return attachMissionMeta(
      planBuildingFootprintMission(buildBuildingMissionInput({ form, selectedModelTarget })),
      { planningRuntime: 'javascript', form },
    )
  }

  if (!centerPoint || !edgePoint) {
    throw new Error('单点环绕采样需要中心点和边缘点')
  }

  return attachMissionMeta(
    planSinglePointOrbitMission(buildOrbitMissionInput({ form, centerPoint, edgePoint })),
    { planningRuntime: 'javascript', form },
  )
}

const analyzeMissionObstacles = async (missionPlan, {
  obstacleSampler,
  unavailableMessage,
  analysisReason,
}) => {
  if (typeof obstacleSampler !== 'function') {
    return createUnavailableObstacleAnalysisResult(missionPlan, unavailableMessage)
  }

  try {
    return await buildObstacleAwareMission(missionPlan, {
      sampleHeights: obstacleSampler,
      analysisReason,
    })
  } catch (error) {
    console.warn('白模障碍分析失败，已退回基础航线', error)
    const message = error instanceof Error ? error.message : String(error)
    return createUnavailableObstacleAnalysisResult(
      missionPlan,
      `白模障碍分析失败（${message}），已按基础航线生成。`,
    )
  }
}

export const planMissionWithObstacleAnalysis = async (
  { plannerType, form, centerPoint, edgePoint, selectedModelTarget },
  {
    obstacleSampler,
    unavailableMessage = '未执行障碍分析，已按基础圆环航线生成。',
    analysisReason = '障碍分析不可用，已退回基础圆环航线。',
  } = {},
) => {
  const droneCount = resolveDroneCount(form)
  const seedPlan = await buildBaseMissionPlanAsync({
    plannerType,
    form,
    centerPoint,
    edgePoint,
    selectedModelTarget,
  })

  const basePlan = appendInformationGainReshoot(seedPlan, {
    enabled: form?.igReshootEnabled !== false,
    qualitySpeedBalance: form?.igQualitySpeedBalance,
  })

  const partitioned = planMultiUavFromBasePlan(basePlan, droneCount)

  if (!partitioned.multiUav || !partitioned.missions?.length) {
    const analyzed = await analyzeMissionObstacles(partitioned.missions?.[0] || partitioned, {
      obstacleSampler,
      unavailableMessage,
      analysisReason,
    })

    if (!partitioned.missions?.length) {
      return {
        ...analyzed,
        informationGainReshoot: basePlan.informationGainReshoot,
      }
    }

    return assembleMultiUavPlan(
      {
        ...basePlan,
        ...analyzed,
        rings: analyzed.rings || partitioned.rings,
        informationGainReshoot: basePlan.informationGainReshoot,
      },
      [{
        ...partitioned.missions[0],
        ...analyzed,
        color: partitioned.missions[0].color,
        droneId: partitioned.missions[0].droneId,
        droneIndex: partitioned.missions[0].droneIndex,
        droneCount: 1,
      }],
      droneCount,
    )
  }

  const analyzedMissions = []
  for (const mission of partitioned.missions) {
    const missionPlan = {
      ...basePlan,
      rings: mission.rings,
      waypoints: mission.waypoints,
      baseWaypoints: mission.baseWaypoints,
      routeSegments: [],
      photosPerLoop: mission.photosPerLoop,
      summary: mission.summary,
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
    }

    const analyzed = await analyzeMissionObstacles(missionPlan, {
      obstacleSampler,
      unavailableMessage,
      analysisReason,
    })

    analyzedMissions.push({
      ...mission,
      rings: analyzed.rings || mission.rings,
      waypoints: analyzed.waypoints || mission.waypoints,
      baseWaypoints: analyzed.baseWaypoints || mission.baseWaypoints,
      routeSegments: analyzed.routeSegments || [],
      summary: analyzed.summary || mission.summary,
      obstacleAnalysis: analyzed.obstacleAnalysis,
    })
  }

  return assembleMultiUavPlan(basePlan, analyzedMissions, droneCount)
}
