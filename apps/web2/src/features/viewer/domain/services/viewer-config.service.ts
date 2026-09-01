import {
  PROJECT_INFO_BUILTIN_KEYS,
  VIEWER_CONFIG_VERSION,
  type ProjectInfoConfig,
  type ProjectInfoField,
  type ViewerConfig,
} from '../entities/viewer-config.entity'

const BUILTIN_LABELS: Record<(typeof PROJECT_INFO_BUILTIN_KEYS)[number], string> = {
  coordinates: '经纬度',
  buildingName: '建筑名称',
  floorCount: '楼层数',
  height: '高度',
}

export function createEmptyProjectInfo(): ProjectInfoConfig {
  return {
    projectName: '',
    fields: PROJECT_INFO_BUILTIN_KEYS.map((key) => ({
      key,
      label: BUILTIN_LABELS[key],
      value: '',
    })),
  }
}

export function createEmptyViewerConfig(): ViewerConfig {
  return {
    version: VIEWER_CONFIG_VERSION,
    defaultView: null,
    pointAnnotations: [],
    cubeMarkers: [],
    projectInfo: createEmptyProjectInfo(),
  }
}

export function coerceProjectInfoConfig(rawValue: unknown): ProjectInfoConfig {
  const emptyProjectInfo = createEmptyProjectInfo()
  if (!rawValue || typeof rawValue !== 'object') {
    return emptyProjectInfo
  }

  const parsedValue = rawValue as Partial<ProjectInfoConfig>
  const rawFields = Array.isArray(parsedValue.fields) ? parsedValue.fields : []
  const builtinFields = new Map<string, ProjectInfoField>()
  const customFields: ProjectInfoField[] = []
  const seenCustomKeys = new Set<string>()

  for (const rawField of rawFields) {
    if (!rawField || typeof rawField !== 'object') {
      continue
    }
    const fieldRecord = rawField as Partial<ProjectInfoField>
    const key = typeof fieldRecord.key === 'string' ? fieldRecord.key.trim() : ''
    if (!key) {
      continue
    }
    const builtinKey = PROJECT_INFO_BUILTIN_KEYS.find((item) => item === key)
    const label =
      typeof fieldRecord.label === 'string' && fieldRecord.label.trim()
        ? fieldRecord.label.trim()
        : builtinKey
          ? BUILTIN_LABELS[builtinKey]
          : '自定义字段'
    const value = typeof fieldRecord.value === 'string' ? fieldRecord.value : ''
    if (builtinKey) {
      if (!builtinFields.has(key)) {
        builtinFields.set(key, { key, label, value })
      }
      continue
    }
    if (seenCustomKeys.has(key)) {
      continue
    }
    seenCustomKeys.add(key)
    customFields.push({ key, label, value })
  }

  return {
    projectName: typeof parsedValue.projectName === 'string' ? parsedValue.projectName.trim() : '',
    fields: [
      ...PROJECT_INFO_BUILTIN_KEYS.map((key) => {
        const existingField = builtinFields.get(key)
        return {
          key,
          label: existingField?.label?.trim() || BUILTIN_LABELS[key],
          value: existingField?.value ?? '',
        }
      }),
      ...customFields,
    ],
  }
}

export function parseViewerConfig(raw: unknown): ViewerConfig {
  const empty = createEmptyViewerConfig()
  if (!raw || typeof raw !== 'object') {
    return empty
  }
  const record = raw as Partial<ViewerConfig>
  return {
    version: typeof record.version === 'number' ? record.version : VIEWER_CONFIG_VERSION,
    defaultView: record.defaultView ?? null,
    pointAnnotations: Array.isArray(record.pointAnnotations) ? record.pointAnnotations : [],
    cubeMarkers: Array.isArray(record.cubeMarkers) ? record.cubeMarkers : [],
    projectInfo: coerceProjectInfoConfig(record.projectInfo),
  }
}
