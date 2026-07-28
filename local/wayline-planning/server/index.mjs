import http from 'node:http'
import { planBuildingFootprintMission, planSinglePointOrbitMission } from '../src/utils/routePlanner.js'
import { appendInformationGainReshoot } from '../src/utils/informationGainReshootPlanner.js'
import { normalizeDroneCount, planMultiUavFromBasePlan } from '../src/utils/multiUavPlanner.js'

const DEFAULT_PORT = 8787
const DEFAULT_HOST = '127.0.0.1'
const DEFAULT_DISABLED_ANALYSIS_MESSAGE = '当前版本未接入后端障碍分析，已返回基础航线。'

const parseNumber = (value, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const roundNumber = (value, digits = 2) => Number(Number(value || 0).toFixed(digits))

const clampFormRatio = (value, fallback = 0.55) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return fallback
  }
  return Math.min(Math.max(parsed, 0), 1)
}

const normalizePoint = (point, label) => {
  if (!point || typeof point !== 'object') {
    throw new Error(`${label}不能为空`)
  }

  const longitude = Number(point.longitude)
  const latitude = Number(point.latitude)
  const height = Number(point.height || 0)

  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    throw new Error(`${label}经纬度无效`)
  }

  return {
    longitude,
    latitude,
    height: Number.isFinite(height) ? height : 0,
  }
}

const normalizeFootprintPoint = (point, index) => {
  if (!point || typeof point !== 'object') {
    throw new Error(`建筑轮廓点 #${index + 1} 无效`)
  }

  const longitude = Number(point.longitude)
  const latitude = Number(point.latitude)
  const height = Number(point.height || 0)

  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
    throw new Error(`建筑轮廓点 #${index + 1} 经纬度无效`)
  }

  return {
    longitude,
    latitude,
    height: Number.isFinite(height) ? height : 0,
  }
}

const normalizeCommonForm = (payload = {}) => ({
  focalLengthMm: parseNumber(payload.focalLengthMm, 24),
  megapixelsWan: parseNumber(payload.megapixelsWan, 4800),
  gsdMm: parseNumber(payload.gsdMm, 5),
  lowElevationM: parseNumber(payload.lowElevationM, 0),
  highElevationM: parseNumber(payload.highElevationM, 60),
  pitchDeg: parseNumber(payload.pitchDeg, -10),
  photosPerLoop: Math.max(parseNumber(payload.photosPerLoop, 24), 4),
  verticalOverlapPercent: parseNumber(payload.verticalOverlapPercent, 70),
  horizontalOverlapPercent: parseNumber(payload.horizontalOverlapPercent, 70),
  autoPhotosPerLoop: Boolean(payload.autoPhotosPerLoop),
  igReshootEnabled: payload.igReshootEnabled !== false,
  igQualitySpeedBalance: clampFormRatio(payload.igQualitySpeedBalance, 0.55),
  droneCount: normalizeDroneCount(payload.droneCount ?? 1),
})

const withMultiUavPlan = (plan, payload = {}) => {
  const droneCount = normalizeDroneCount(payload.droneCount ?? plan?.planningInputs?.droneCount ?? 1)
  return planMultiUavFromBasePlan(plan, droneCount)
}

const withDisabledObstacleAnalysis = (plan, message = DEFAULT_DISABLED_ANALYSIS_MESSAGE) => ({
  ...plan,
  obstacleAnalysis: {
    enabled: false,
    analyzed: false,
    status: 'unavailable',
    detouredSegments: 0,
    riskySegments: 0,
    safeSegments: 0,
    suggestions: [],
    messages: [message],
    safetyClearanceMeters: 0,
    minimumClearanceMeters: null,
    maximumObstacleHeightMeters: null,
    collisionPoints: [],
  },
})

const buildOrbitMission = (payload) => {
  const centerPoint = normalizePoint(payload.centerPoint, '中心点')
  const edgePoint = normalizePoint(payload.edgePoint, '边缘点')

  const form = normalizeCommonForm(payload)
  const seedPlan = planSinglePointOrbitMission({
      ...form,
      centerPoint,
      edgePoint,
    })
  const basePlan = appendInformationGainReshoot(seedPlan, {
    enabled: form.igReshootEnabled,
    qualitySpeedBalance: form.igQualitySpeedBalance,
  })

  return withDisabledObstacleAnalysis(withMultiUavPlan(basePlan, form))
}

const buildBuildingMission = (payload) => {
  const footprintPoints = Array.isArray(payload.footprintPoints)
    ? payload.footprintPoints.map(normalizeFootprintPoint)
    : []

  if (footprintPoints.length < 3) {
    throw new Error('建筑采样至少需要 3 个轮廓点')
  }

  const form = normalizeCommonForm(payload)
  const seedPlan = planBuildingFootprintMission({
    ...form,
    footprintPoints,
  })
  const basePlan = appendInformationGainReshoot(seedPlan, {
    enabled: form.igReshootEnabled,
    qualitySpeedBalance: form.igQualitySpeedBalance,
  })

  return withDisabledObstacleAnalysis(withMultiUavPlan({
    ...basePlan,
    sourceFootprints: Array.isArray(payload.footprintGroups)
      ? payload.footprintGroups.map((group) => Array.isArray(group) ? group.map(normalizeFootprintPoint) : []).filter((group) => group.length >= 3)
      : [],
    planningContext: {
      buildingCount: Math.max(parseNumber(payload.buildingCount, 1), 1),
      targetName: typeof payload.targetName === 'string' ? payload.targetName : '',
      roofHeight: roundNumber(parseNumber(payload.roofHeight, 0), 2),
    },
  }, form))
}

const routes = new Map([
  ['/api/health', {
    POST: () => ({ ok: true, service: 'planning-server' }),
    GET: () => ({ ok: true, service: 'planning-server' }),
  }],
  ['/api/planning/orbit', {
    POST: buildOrbitMission,
  }],
  ['/api/planning/building', {
    POST: buildBuildingMission,
  }],
  ['/api/planning/mission', {
    POST: (payload = {}) => {
      if (payload.missionType === 'building') {
        return buildBuildingMission(payload)
      }

      return buildOrbitMission(payload)
    },
  }],
])

const readJsonBody = async (request) => {
  if (request.method === 'GET' || request.method === 'HEAD') {
    return {}
  }

  const chunks = []
  for await (const chunk of request) {
    chunks.push(chunk)
  }

  if (chunks.length === 0) {
    return {}
  }

  const rawText = Buffer.concat(chunks).toString('utf8').trim()
  if (!rawText) {
    return {}
  }

  try {
    return JSON.parse(rawText)
  } catch {
    throw new Error('请求体不是合法 JSON')
  }
}

const writeJson = (response, statusCode, payload) => {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type',
  })
  response.end(JSON.stringify(payload))
}

const server = http.createServer(async (request, response) => {
  if (!request.url) {
    writeJson(response, 400, { message: '缺少请求地址' })
    return
  }

  if (request.method === 'OPTIONS') {
    response.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    })
    response.end()
    return
  }

  const url = new URL(request.url, `http://${request.headers.host || `${DEFAULT_HOST}:${DEFAULT_PORT}`}`)
  const matchedRoute = routes.get(url.pathname)

  if (!matchedRoute || typeof matchedRoute[request.method] !== 'function') {
    writeJson(response, 404, { message: '接口不存在' })
    return
  }

  try {
    const payload = await readJsonBody(request)
    const result = await matchedRoute[request.method](payload)
    writeJson(response, 200, result)
  } catch (error) {
    const message = error instanceof Error ? error.message : '规划服务执行失败'
    writeJson(response, 400, { message })
  }
})

const port = parseNumber(process.env.PLANNING_SERVER_PORT || process.env.PORT, DEFAULT_PORT)
const host = process.env.PLANNING_SERVER_HOST || DEFAULT_HOST

server.listen(port, host, () => {
  console.log(`[planning-server] listening on http://${host}:${port}`)
})
