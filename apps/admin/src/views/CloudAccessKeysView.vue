<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch, watchEffect } from 'vue'
import {
  ApiError,
  createCloudAccessKey,
  deleteCloudAccessKey,
  fetchCloudAccessKeys,
  fetchCloudResourceConfig,
  resetCloudResourceConfigSection,
  saveCloudResourceConfigSection,
  selectCloudAccessKey,
  testCloudAccessKeyDraft,
  testSavedCloudAccessKey,
  updateCloudAccessKey,
  type CloudAccessKeyResource,
  type CloudAccessKeyResponse,
  type CloudAccessKeyTestResponse,
  type CloudResourceConfigField,
  type CloudResourceConfigResponse,
  type CloudResourceConfigSectionId,
} from '@/api/adminClient'

type KeyKind = 'ECI_AccessKey' | 'OSS_AccessKey' | 'COMMON'
type TestFeedback = { result: CloudAccessKeyTestResponse } | { error: string }
type SavedTestSnapshot = {
  id: string
  resource: CloudAccessKeyResource
  updatedAt: string
  requestVersion: number
}
type SavedTestFeedback = { snapshot: SavedTestSnapshot; feedback: TestFeedback }
type ConfigFeedback = { message: string } | { error: string }
type ConfigState = {
  fields: CloudResourceConfigField[]
  label: string
  values: Record<string, string>
  dirty: Record<string, boolean>
  fieldErrors: Record<string, string>
  feedback: ConfigFeedback | null
  pending: boolean
}

const resourceOptions: { value: CloudAccessKeyResource; label: string }[] = [
  { value: 'ECI_OPENAPI', label: 'ECI_AccessKey' },
  { value: 'OSS', label: 'OSS_AccessKey' },
]
const allKinds = 'ALL'
const allLabels = 'ALL'
const unlabelled = 'UNLABELLED'

const keys = ref<CloudAccessKeyResponse[]>([])
const currentIds = reactive<Record<CloudAccessKeyResource, string | null>>({
  ECI_OPENAPI: null,
  OSS: null,
})
const loading = ref(true)
const loaded = ref(false)
const loadError = ref('')
const actionMessage = ref('')
const actionError = ref('')
const mutationPending = ref(false)
const selectedIds = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const searchText = ref('')
const kindFilter = ref(allKinds)
const labelFilter = ref(allLabels)
const selectResource = ref<CloudAccessKeyResource | ''>('')
const labelEditorOpen = ref(false)
const labelDraft = ref('')
const formOpen = ref(false)
const draft = reactive({
  accessKeyId: '',
  accessKeySecret: '',
  kind: 'COMMON' as KeyKind,
  label: '',
  testResource: '' as CloudAccessKeyResource | '',
})
const draftErrors = reactive({ accessKeyId: '', accessKeySecret: '', kind: '', label: '' })
const draftTest = ref<TestFeedback | null>(null)
const draftTestPending = ref(false)
const savedTestPendingIds = reactive<Record<string, boolean>>({})
const savedTestResources = reactive<Record<string, CloudAccessKeyResource>>({})
const savedTestResults = reactive<Record<string, SavedTestFeedback | null>>({})
const savedTestRequestVersions = reactive<Record<string, number>>({})
const savedTestKeyVersions = reactive<Record<string, string>>({})
let draftTestVersion = 0
const addFormElement = ref<HTMLElement | null>(null)
const accessKeyInput = ref<HTMLInputElement | null>(null)
const pageCheckbox = ref<HTMLInputElement | null>(null)
const deleteDialog = ref<HTMLDialogElement | null>(null)
const deleteCancelButton = ref<HTMLButtonElement | null>(null)
const deleteIds = ref<string[]>([])

const configSectionIds: CloudResourceConfigSectionId[] = ['network', 'oss', 'advanced']
const booleanValueType = 'BOOLEAN'
const booleanOptions = [
  { value: 'true', label: '是' },
  { value: 'false', label: '否' },
]
const configView = ref<CloudResourceConfigResponse | null>(null)
const configLoading = ref(true)
const configLoadError = ref('')
const configState = reactive<Record<CloudResourceConfigSectionId, ConfigState>>({
  network: { fields: [], label: '', values: {}, dirty: {}, fieldErrors: {}, feedback: null, pending: false },
  oss: { fields: [], label: '', values: {}, dirty: {}, fieldErrors: {}, feedback: null, pending: false },
  advanced: { fields: [], label: '', values: {}, dirty: {}, fieldErrors: {}, feedback: null, pending: false },
})
let configSaveQueue: Promise<void> = Promise.resolve()

const kindOptions: { value: KeyKind | typeof allKinds; label: string }[] = [
  { value: allKinds, label: '全部种类' },
  { value: 'ECI_AccessKey', label: 'ECI_AccessKey' },
  { value: 'OSS_AccessKey', label: 'OSS_AccessKey' },
  { value: 'COMMON', label: '通用' },
]

function kindOf(key: CloudAccessKeyResponse): KeyKind {
  const hasEci = key.resources.includes('ECI_OPENAPI')
  const hasOss = key.resources.includes('OSS')
  if (hasEci && hasOss) return 'COMMON'
  return hasEci ? 'ECI_AccessKey' : 'OSS_AccessKey'
}

function resourcesForKind(kind: KeyKind): CloudAccessKeyResource[] {
  if (kind === 'ECI_AccessKey') return ['ECI_OPENAPI']
  if (kind === 'OSS_AccessKey') return ['OSS']
  return ['ECI_OPENAPI', 'OSS']
}

function displayKind(kind: KeyKind) {
  return kind === 'COMMON' ? '通用' : kind
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}

function clearActionFeedback() {
  actionMessage.value = ''
  actionError.value = ''
}

const configSections = computed<{ id: CloudResourceConfigSectionId; title: string; fields: CloudResourceConfigField[] }[]>(() => [
  { id: 'network', title: configState.network.label, fields: configState.network.fields.filter((field) => !field.groupLabel) },
  { id: 'oss', title: configState.oss.label, fields: configState.oss.fields.filter((field) => !field.groupLabel) },
  { id: 'advanced', title: configState.advanced.label, fields: [] },
])

const advancedGroups = computed(() => {
  const fields = configState.advanced.fields
  const grouped: { label: string; fields: CloudResourceConfigField[] }[] = []
  for (const field of fields) {
    if (!field.groupLabel) continue
    const group = grouped.find((item) => item.label === field.groupLabel)
    if (group) group.fields.push(field)
    else grouped.push({ label: field.groupLabel, fields: [field] })
  }
  return grouped
})

function isBooleanConfigField(field: { valueType: string }) {
  return field.valueType === booleanValueType
}

const labelOptions = computed(() => {
  const labels = [...new Set(keys.value.map((key) => (key.label ?? '').trim()).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
  return [
    { value: allLabels, label: '全部标签' },
    { value: unlabelled, label: '未标签' },
    ...labels.map((label) => ({ value: `label:${label}`, label })),
  ]
})

const filteredKeys = computed(() => {
  const query = searchText.value.trim().toLocaleLowerCase()
  return keys.value.filter((key) => {
    const kindMatches = kindFilter.value === allKinds || kindOf(key) === kindFilter.value
    const label = (key.label ?? '').trim()
    const labelMatches =
      labelFilter.value === allLabels ||
      (labelFilter.value === unlabelled ? !label : labelFilter.value === `label:${label}`)
    const searchable = `${key.id} ${key.accessKeyHint} ${label}`.toLocaleLowerCase()
    return kindMatches && labelMatches && (!query || searchable.includes(query))
  })
})

const pageCount = computed(() => Math.max(1, Math.ceil(filteredKeys.value.length / pageSize.value)))
const pageKeys = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredKeys.value.slice(start, start + pageSize.value)
})
const pageKeyIds = computed(() => pageKeys.value.map((key) => key.id))
const allPageSelected = computed(
  () => pageKeyIds.value.length > 0 && pageKeyIds.value.every((id) => selectedIds.value.includes(id)),
)
const somePageSelected = computed(
  () => pageKeyIds.value.some((id) => selectedIds.value.includes(id)) && !allPageSelected.value,
)
const selectedKeys = computed(() => keys.value.filter((key) => selectedIds.value.includes(key.id)))
const selectedKey = computed(() =>
  selectedIds.value.length === 1 ? keys.value.find((key) => key.id === selectedIds.value[0]) : undefined,
)
const hasSelectedCurrentKey = computed(() => selectedKeys.value.some(isCurrentlyUsed))
const canSelect = computed(
  () =>
    !loading.value &&
    !mutationPending.value &&
    selectedIds.value.length === 1 &&
    !!selectedKey.value?.secretConfigured &&
    !!selectResource.value &&
    selectedKey.value.resources.includes(selectResource.value),
)
const canDelete = computed(
  () => !loading.value && !mutationPending.value && selectedIds.value.length > 0 && !hasSelectedCurrentKey.value,
)

function isCurrentlyUsed(key: CloudAccessKeyResponse) {
  return currentIds.ECI_OPENAPI === key.id || currentIds.OSS === key.id
}

function mergeDirectory(
  eciItems: CloudAccessKeyResponse[],
  ossItems: CloudAccessKeyResponse[],
) {
  const byId = new Map<string, CloudAccessKeyResponse>()
  for (const item of [...eciItems, ...ossItems]) {
    const previous = byId.get(item.id)
    byId.set(item.id, {
      ...previous,
      ...item,
      resources: [...new Set([...(previous?.resources ?? []), ...item.resources])],
      selectedResources: [...new Set([...(previous?.selectedResources ?? []), ...item.selectedResources])],
      label: item.label ?? previous?.label ?? '',
    })
  }
  return [...byId.values()].sort((a, b) => {
    const byCreated = Date.parse(b.createdAt) - Date.parse(a.createdAt)
    return byCreated || a.id.localeCompare(b.id)
  })
}

async function loadDirectory(options: { page?: number } = {}) {
  const requestedPage = options.page ?? currentPage.value
  loading.value = true
  loaded.value = false
  loadError.value = ''
  keys.value = []
  currentIds.ECI_OPENAPI = null
  currentIds.OSS = null
  selectedIds.value = []
  currentPage.value = 1
  clearActionFeedback()

  try {
    const [eciResult, ossResult] = await Promise.allSettled([
      fetchCloudAccessKeys('ECI_OPENAPI'),
      fetchCloudAccessKeys('OSS'),
    ])
    if (eciResult.status === 'rejected' || ossResult.status === 'rejected') {
      const failures = [
        eciResult.status === 'rejected'
          ? `ECI（${errorMessage(eciResult.reason, '请求失败')}）`
          : '',
        ossResult.status === 'rejected'
          ? `OSS（${errorMessage(ossResult.reason, '请求失败')}）`
          : '',
      ].filter(Boolean)
      loadError.value = `读取云访问密钥目录失败：${failures.join('；')}`
      return false
    }
    const eci = eciResult.value
    const oss = ossResult.value
    keys.value = mergeDirectory(eci.items, oss.items)
    const keysById = new Map(keys.value.map((key) => [key.id, key]))
    currentIds.ECI_OPENAPI = eci.currentId
    currentIds.OSS = oss.currentId

    for (const id of Object.keys(savedTestKeyVersions)) {
      const key = keysById.get(id)
      if (!key) {
        invalidateSavedTest(id)
        delete savedTestResults[id]
        delete savedTestResources[id]
        delete savedTestKeyVersions[id]
      } else if (savedTestKeyVersions[id] !== key.updatedAt) {
        invalidateSavedTest(id)
        savedTestKeyVersions[id] = key.updatedAt
      }
    }
    for (const key of keys.value) {
      if (!savedTestResources[key.id] || !key.resources.includes(savedTestResources[key.id])) {
        if (savedTestResources[key.id]) invalidateSavedTest(key.id)
        savedTestResources[key.id] = key.resources[0]
      }
      savedTestKeyVersions[key.id] = key.updatedAt
    }
    loaded.value = true
    await nextTick()
    currentPage.value = Math.min(Math.max(1, requestedPage), pageCount.value)
    await nextTick()
    return true
  } catch (error) {
    keys.value = []
    currentIds.ECI_OPENAPI = null
    currentIds.OSS = null
    loadError.value = `读取云访问密钥目录失败：${errorMessage(error, '请求失败')}`
    return false
  } finally {
    loading.value = false
  }
}

function markConfigDirty(section: CloudResourceConfigSectionId, key: string) {
  configState[section].dirty[key] = true
  configState[section].fieldErrors[key] = ''
  if (configState[section].feedback && 'error' in configState[section].feedback) {
    configState[section].feedback = null
  }
}

function loadConfigSection(section: CloudResourceConfigSectionId, source: CloudResourceConfigResponse) {
  const state = configState[section]
  state.fields = source[section].fields
  state.label = source[section].label
  state.values = {}
  state.dirty = {}
  state.fieldErrors = {}
  for (const field of state.fields) {
    state.values[field.key] = field.value ?? ''
  }
}

async function loadCloudResourceConfig() {
  configLoading.value = true
  configLoadError.value = ''
  try {
    const view = await fetchCloudResourceConfig()
    configView.value = view
    for (const section of configSectionIds) loadConfigSection(section, view)
  } catch (error) {
    configLoadError.value = `读取云资源配置失败：${errorMessage(error, '请求失败')}`
  } finally {
    configLoading.value = false
  }
}

function showConfigFieldError(section: CloudResourceConfigSectionId, message: string) {
  for (const field of configState[section].fields) {
    if (message.startsWith(`${field.displayName}：`)) {
      configState[section].fieldErrors[field.key] = message
      return true
    }
  }
  return false
}

function configFeedbackMessage(section: CloudResourceConfigSectionId) {
  const feedback = configState[section].feedback
  return feedback && 'message' in feedback ? feedback.message : ''
}

function configFeedbackError(section: CloudResourceConfigSectionId) {
  const feedback = configState[section].feedback
  return feedback && 'error' in feedback ? feedback.error : ''
}

/* 保存请求串行执行，避免并发修改同一份运行时配置。 */
function enqueueConfigRequest(action: () => Promise<void>) {
  const operation = configSaveQueue.then(action)
  configSaveQueue = operation.catch(() => undefined)
}

function saveConfigSection(section: CloudResourceConfigSectionId, keys: string[]) {
  const state = configState[section]
  const values: Record<string, string> = {}
  for (const key of keys) values[key] = state.values[key] ?? ''
  return saveCloudResourceConfigSection(section, values).then((view) => {
    loadConfigSection(section, view)
    state.feedback = { message: `${state.label}已保存` }
  })
}

function resetConfigSectionRequest(section: CloudResourceConfigSectionId) {
  return resetCloudResourceConfigSection(section).then((view) => {
    loadConfigSection(section, view)
    configState[section].feedback = { message: `${configState[section].label}已恢复默认` }
  })
}

function queueConfigSave(section: CloudResourceConfigSectionId) {
  const state = configState[section]
  if (state.pending) return
  const keys = Object.keys(state.dirty)
  state.pending = true
  state.feedback = null
  enqueueConfigRequest(async () => {
    try {
      await saveConfigSection(section, keys)
    } catch (error) {
      const message = errorMessage(error, '保存失败')
      if (!showConfigFieldError(section, message)) state.feedback = { error: message }
    } finally {
      state.pending = false
    }
  })
}

function queueConfigReset(section: CloudResourceConfigSectionId) {
  const state = configState[section]
  if (state.pending) return
  state.pending = true
  state.feedback = null
  enqueueConfigRequest(async () => {
    try {
      await resetConfigSectionRequest(section)
    } catch (error) {
      state.feedback = { error: errorMessage(error, '恢复默认失败') }
    } finally {
      state.pending = false
    }
  })
}

function clearDraft() {
  draftTestVersion += 1
  draftTestPending.value = false
  draft.accessKeyId = ''
  draft.accessKeySecret = ''
  draft.kind = 'COMMON'
  draft.label = ''
  draft.testResource = resourcesForKind(draft.kind)[0]
  draftTest.value = null
  draftErrors.accessKeyId = ''
  draftErrors.accessKeySecret = ''
  draftErrors.kind = ''
  draftErrors.label = ''
}

async function openAddForm() {
  if (formOpen.value) {
    await nextTick()
    accessKeyInput.value?.focus()
    addFormElement.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
    return
  }
  draft.testResource = resourcesForKind(draft.kind)[0]
  formOpen.value = true
  await nextTick()
  addFormElement.value?.scrollIntoView({ block: 'start', behavior: 'auto' })
  accessKeyInput.value?.focus()
}

function cancelAddForm() {
  clearDraft()
  formOpen.value = false
}

function firstDraftError() {
  const id = draft.accessKeyId.trim()
  const secret = draft.accessKeySecret.trim()
  draftErrors.accessKeyId = !id ? '请填写 AccessKeyId' : id.length > 128 ? 'AccessKeyId 不能超过 128 个字符' : id.includes('****') ? '请填写完整的 AccessKeyId' : ''
  draftErrors.accessKeySecret = !secret ? '请填写 AccessKeySecret' : secret.length > 512 ? 'AccessKeySecret 不能超过 512 个字符' : secret.includes('****') ? '请填写完整的 AccessKeySecret' : ''
  draftErrors.kind = ''
  draftErrors.label = draft.label.trim().length > 128 ? '标签不能超过 128 个字符' : ''
  if (draftErrors.accessKeyId) return 'accessKeyId'
  if (draftErrors.accessKeySecret) return 'accessKeySecret'
  if (draftErrors.label) return 'label'
  return ''
}

function validateDraft() {
  const error = firstDraftError()
  if (error === 'accessKeyId') accessKeyInput.value?.focus()
  if (error === 'accessKeySecret') document.getElementById('cloud-key-secret')?.focus()
  if (error === 'label') document.getElementById('cloud-key-label')?.focus()
  return !error
}

async function testDraftKey() {
  clearActionFeedback()
  if (!validateDraft()) return
  const resource = draft.testResource || resourcesForKind(draft.kind)[0]
  if (!resourcesForKind(draft.kind).includes(resource)) {
    actionError.value = '测通用途必须适用于当前种类'
    return
  }
  const testVersion = ++draftTestVersion
  draftTestPending.value = true
  draftTest.value = null
  try {
    const result = await testCloudAccessKeyDraft({
      resource,
      accessKeyId: draft.accessKeyId.trim(),
      accessKeySecret: draft.accessKeySecret.trim(),
    })
    if (testVersion === draftTestVersion) draftTest.value = { result }
  } catch (error) {
    if (testVersion === draftTestVersion) {
      draftTest.value = { error: errorMessage(error, '草稿测通请求失败') }
    }
  } finally {
    if (testVersion === draftTestVersion) draftTestPending.value = false
  }
}

async function saveDraft() {
  clearActionFeedback()
  if (mutationPending.value || !validateDraft()) return
  mutationPending.value = true
  const resources = resourcesForKind(draft.kind)
  try {
    const created = await createCloudAccessKey({
      name: `key-${crypto.randomUUID()}`,
      accessKeyId: draft.accessKeyId.trim(),
      accessKeySecret: draft.accessKeySecret.trim(),
      resources,
      label: draft.label.trim(),
    })
    clearDraft()
    formOpen.value = false
    searchText.value = ''
    kindFilter.value = allKinds
    labelFilter.value = allLabels
    currentPage.value = 1
    const refreshed = await loadDirectory({ page: 1 })
    if (refreshed && keys.value.some((key) => key.id === created.id)) selectedIds.value = [created.id]
    actionMessage.value = refreshed
      ? 'key 已保存，选择用途后可设置为当前 key。'
      : 'key 已保存，目录刷新失败，请重试刷新。'
  } catch (error) {
    actionError.value = errorMessage(error, '保存 key 失败')
  } finally {
    mutationPending.value = false
  }
}

async function copyCurrentId(resource: CloudAccessKeyResource) {
  const id = currentIds[resource]
  if (!id) return
  try {
    await navigator.clipboard.writeText(id)
    actionMessage.value = `已复制 ${resourceOptions.find((option) => option.value === resource)?.label} 编号。`
    actionError.value = ''
  } catch {
    actionError.value = '复制编号失败，请检查浏览器剪贴板权限。'
    actionMessage.value = ''
  }
}

function setPageSelection(checked: boolean) {
  if (checked) {
    selectedIds.value = [...new Set([...selectedIds.value, ...pageKeyIds.value])]
  } else {
    selectedIds.value = selectedIds.value.filter((id) => !pageKeyIds.value.includes(id))
  }
}

function toggleKeySelection(id: string, checked: boolean) {
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((selectedId) => selectedId !== id)
}

function openLabelEditor() {
  if (mutationPending.value || selectedIds.value.length === 0) return
  labelDraft.value = ''
  labelEditorOpen.value = true
}

function cancelLabelEditor() {
  labelEditorOpen.value = false
  labelDraft.value = ''
}

async function saveLabel() {
  if (mutationPending.value || selectedIds.value.length === 0) return
  const label = labelDraft.value.trim()
  if (label.length > 128) {
    actionError.value = '标签不能超过 128 个字符'
    return
  }
  clearActionFeedback()
  mutationPending.value = true
  const ids = [...selectedIds.value]
  const failed: { id: string; reason: string }[] = []
  let succeeded = 0
  try {
    for (const id of ids) {
      try {
        await updateCloudAccessKey(id, { label })
        succeeded += 1
      } catch (error) {
        failed.push({ id, reason: errorMessage(error, '更新失败') })
      }
    }
    const refreshed = await loadDirectory({ page: currentPage.value })
    if (refreshed && failed.length) {
      const failedIds = failed.map(({ id }) => id)
      const firstIndex = filteredKeys.value.findIndex((key) => failedIds.includes(key.id))
      if (firstIndex >= 0) currentPage.value = Math.floor(firstIndex / pageSize.value) + 1
      await nextTick()
      selectedIds.value = pageKeys.value.map((key) => key.id).filter((id) => failedIds.includes(id))
    }
    if (!failed.length) {
      labelEditorOpen.value = false
      labelDraft.value = ''
    }
    actionMessage.value = refreshed
      ? `已更新 ${succeeded} 项，${failed.length} 项失败。`
      : `已更新 ${succeeded} 项，目录刷新失败，请重试刷新。`
    if (failed.length) actionError.value = `失败编号：${failed.map(({ id }) => id).join('、')}。${failed[0].reason}`
  } finally {
    mutationPending.value = false
  }
}

async function selectCurrentKey() {
  const key = selectedKey.value
  const resource = selectResource.value
  if (!key || !resource || !canSelect.value) return
  clearActionFeedback()
  mutationPending.value = true
  try {
    await selectCloudAccessKey(key.id, resource)
    const refreshed = await loadDirectory({ page: currentPage.value })
    actionMessage.value = refreshed
      ? `已将 key ${key.id} 设置为 ${resourceOptions.find((option) => option.value === resource)?.label}。`
      : '设置成功，目录刷新失败，请重试刷新。'
    if (!refreshed) return
    if (keys.value.some((item) => item.id === key.id)) {
      selectedIds.value = [key.id]
    }
  } catch (error) {
    actionError.value = errorMessage(error, '设置当前 key 失败')
  } finally {
    mutationPending.value = false
  }
}

function openDeleteDialog() {
  clearActionFeedback()
  if (!canDelete.value) {
    actionError.value = hasSelectedCurrentKey.value
      ? '当前使用的 key 不能删除，请先换用其他 key。'
      : '请先选择要删除的 key。'
    return
  }
  deleteIds.value = [...selectedIds.value]
  nextTick(() => {
    if (!deleteDialog.value?.open) deleteDialog.value?.showModal()
    deleteCancelButton.value?.focus()
  })
}

function closeDeleteDialog() {
  if (deleteDialog.value?.open) deleteDialog.value.close()
  deleteIds.value = []
}

async function confirmDelete() {
  const ids = [...deleteIds.value]
  if (ids.length === 0 || mutationPending.value) return
  if (selectedKeys.value.some(isCurrentlyUsed)) {
    closeDeleteDialog()
    actionError.value = '当前使用的 key 不能删除，请先换用其他 key。'
    return
  }
  closeDeleteDialog()
  clearActionFeedback()
  mutationPending.value = true
  let succeeded = 0
  const failed: { id: string; reason: string }[] = []
  try {
    for (const id of ids) {
      try {
        await deleteCloudAccessKey(id)
        succeeded += 1
      } catch (error) {
        failed.push({ id, reason: errorMessage(error, '删除失败') })
      }
    }
    const refreshed = await loadDirectory({ page: currentPage.value })
    if (refreshed && failed.length) {
      const failedIds = failed.map(({ id }) => id)
      const firstIndex = filteredKeys.value.findIndex((key) => failedIds.includes(key.id))
      if (firstIndex >= 0) currentPage.value = Math.floor(firstIndex / pageSize.value) + 1
      await nextTick()
      selectedIds.value = pageKeys.value.map((key) => key.id).filter((id) => failedIds.includes(id))
    }
    actionMessage.value = refreshed
      ? `已删除 ${succeeded} 项，${failed.length} 项失败。`
      : `已删除 ${succeeded} 项，目录刷新失败，请重试刷新。`
    if (failed.length) actionError.value = `失败编号：${failed.map(({ id }) => id).join('、')}。${failed[0].reason}`
  } finally {
    mutationPending.value = false
  }
}

function savedTestResource(key: CloudAccessKeyResponse) {
  const value = savedTestResources[key.id]
  return key.resources.includes(value) ? value : key.resources[0]
}

function invalidateSavedTest(id: string) {
  savedTestRequestVersions[id] = (savedTestRequestVersions[id] ?? 0) + 1
  savedTestResults[id] = null
  delete savedTestPendingIds[id]
}

function savedTestResult(key: CloudAccessKeyResponse) {
  const saved = savedTestResults[key.id]
  if (
    !saved ||
    saved.snapshot.id !== key.id ||
    saved.snapshot.resource !== savedTestResource(key) ||
    saved.snapshot.updatedAt !== key.updatedAt ||
    saved.snapshot.requestVersion !== savedTestRequestVersions[key.id]
  ) return null
  return saved.feedback
}

function onSavedTestResourceChange(id: string) {
  invalidateSavedTest(id)
}

async function testSavedKey(key: CloudAccessKeyResponse) {
  const resource = savedTestResource(key)
  if (
    !key.secretConfigured ||
    !resource ||
    !key.resources.includes(resource) ||
    savedTestPendingIds[key.id]
  ) return
  const requestVersion = (savedTestRequestVersions[key.id] ?? 0) + 1
  savedTestRequestVersions[key.id] = requestVersion
  const snapshot: SavedTestSnapshot = {
    id: key.id,
    resource,
    updatedAt: key.updatedAt,
    requestVersion,
  }
  savedTestPendingIds[key.id] = true
  savedTestResults[key.id] = null
  try {
    const result = await testSavedCloudAccessKey(key.id, resource)
    if (savedTestRequestVersions[key.id] === requestVersion) {
      savedTestResults[key.id] = { snapshot, feedback: { result } }
    }
  } catch (error) {
    if (savedTestRequestVersions[key.id] === requestVersion) {
      savedTestResults[key.id] = {
        snapshot,
        feedback: { error: errorMessage(error, '已保存 key 测通请求失败') },
      }
    }
  } finally {
    if (savedTestRequestVersions[key.id] === requestVersion) delete savedTestPendingIds[key.id]
  }
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未知' : date.toLocaleString('zh-CN')
}

function testFeedbackText(feedback: TestFeedback) {
  return 'result' in feedback
    ? `${resourceOptions.find((option) => option.value === feedback.result.resource)?.label}：${feedback.result.ok ? '测通通过' : '测通未通过'}。${feedback.result.detail}`
    : `测通请求失败：${feedback.error}`
}

function isFailedTestFeedback(feedback: TestFeedback | null) {
  return !!feedback && ('error' in feedback || !feedback.result.ok)
}

watch([searchText, kindFilter, labelFilter, pageSize], () => {
  currentPage.value = 1
  selectedIds.value = []
  labelEditorOpen.value = false
  clearActionFeedback()
})

watch(currentPage, () => {
  selectedIds.value = []
  labelEditorOpen.value = false
})

watch(selectedIds, () => {
  selectResource.value = ''
  if (selectedIds.value.length === 0) labelEditorOpen.value = false
})

watch(
  [() => draft.accessKeyId, () => draft.accessKeySecret, () => draft.testResource],
  () => {
    draftTestVersion += 1
    draftTest.value = null
    draftTestPending.value = false
  },
)

watch(
  () => draft.kind,
  (kind) => {
    const supported = resourcesForKind(kind)
    if (!draft.testResource || !supported.includes(draft.testResource)) draft.testResource = supported[0]
    draftTestVersion += 1
    draftTest.value = null
    draftTestPending.value = false
  },
)

watchEffect(() => {
  if (pageCheckbox.value) pageCheckbox.value.indeterminate = somePageSelected.value
})

onMounted(() => {
  void loadDirectory({ page: 1 })
  void loadCloudResourceConfig()
})
</script>

<template>
  <div class="cloud-key-page">
    <header class="cloud-key-heading">
      <div>
        <h2 class="cloud-key-title">云资源配置</h2>
        <p class="cloud-key-subtitle">管理 ECI 和 OSS 的访问密钥与云资源配置。</p>
      </div>
      <div class="cloud-key-heading-actions">
        <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="loading || mutationPending" @click="loadDirectory({ page: currentPage })">
          刷新
        </button>
        <button class="cloud-btn cloud-btn--primary" type="button" :disabled="mutationPending" @click="openAddForm">
          添加 key
        </button>
      </div>
    </header>

    <section class="admin-card cloud-key-current" aria-labelledby="cloud-key-current-title">
      <h3 id="cloud-key-current-title" class="cloud-key-section-title">当前使用</h3>
      <div class="cloud-key-current-grid">
        <div v-for="resource in resourceOptions" :key="resource.value" class="cloud-key-current-item">
          <h4>{{ resource.label }}</h4>
          <template v-if="loading">
            <p class="cloud-key-current-state">加载中…</p>
          </template>
          <template v-else-if="loadError">
            <p class="cloud-key-current-state error-text">读取失败</p>
          </template>
          <template v-else-if="currentIds[resource.value]">
            <div class="cloud-key-current-id">
              <code>{{ currentIds[resource.value] }}</code>
              <button class="cloud-btn cloud-btn--ghost cloud-key-copy" type="button" @click="copyCurrentId(resource.value)">
                复制编号
              </button>
            </div>
            <p v-if="keys.find((key) => key.id === currentIds[resource.value])" class="cloud-key-hint">
              {{ keys.find((key) => key.id === currentIds[resource.value])?.accessKeyHint }}
            </p>
          </template>
          <p v-else class="cloud-key-current-state">尚未设置</p>
        </div>
      </div>
      <p class="cloud-key-current-note">此处 OSS key 用于算力服务；对象存储参数请在「OSS 配置」中管理。</p>
    </section>

    <section v-if="formOpen" ref="addFormElement" class="admin-card cloud-key-add" aria-labelledby="cloud-key-add-title">
      <h3 id="cloud-key-add-title" class="cloud-key-section-title">添加 key</h3>
      <form class="cloud-key-form" @submit.prevent="saveDraft">
        <label class="admin-field">
          <span>AccessKeyId <b aria-hidden="true">*</b></span>
          <input
            ref="accessKeyInput"
            v-model="draft.accessKeyId"
            class="admin-input"
            type="text"
            maxlength="128"
            autocomplete="off"
            :aria-invalid="!!draftErrors.accessKeyId"
            aria-describedby="cloud-key-id-error"
            @input="draftErrors.accessKeyId = ''"
          />
          <span v-if="draftErrors.accessKeyId" id="cloud-key-id-error" class="error-text">{{ draftErrors.accessKeyId }}</span>
        </label>
        <label class="admin-field">
          <span>AccessKeySecret <b aria-hidden="true">*</b></span>
          <input
            id="cloud-key-secret"
            v-model="draft.accessKeySecret"
            class="admin-input"
            type="password"
            maxlength="512"
            autocomplete="new-password"
            :aria-invalid="!!draftErrors.accessKeySecret"
            aria-describedby="cloud-key-secret-error"
            @input="draftErrors.accessKeySecret = ''"
          />
          <span v-if="draftErrors.accessKeySecret" id="cloud-key-secret-error" class="error-text">{{ draftErrors.accessKeySecret }}</span>
        </label>
        <label class="admin-field">
          <span>种类 <b aria-hidden="true">*</b></span>
          <select v-model="draft.kind" class="admin-input">
            <option value="COMMON">通用（ECI / OSS）</option>
            <option value="ECI_AccessKey">ECI_AccessKey</option>
            <option value="OSS_AccessKey">OSS_AccessKey</option>
          </select>
          <span v-if="draftErrors.kind" class="error-text">{{ draftErrors.kind }}</span>
        </label>
        <label class="admin-field">
          <span>标签</span>
          <input
            id="cloud-key-label"
            v-model="draft.label"
            class="admin-input"
            type="text"
            maxlength="128"
            autocomplete="off"
            :aria-invalid="!!draftErrors.label"
            aria-describedby="cloud-key-label-error"
            @input="draftErrors.label = ''"
          />
          <span v-if="draftErrors.label" id="cloud-key-label-error" class="error-text">{{ draftErrors.label }}</span>
        </label>
        <div class="cloud-key-form-footer">
          <label class="cloud-key-inline-field">
            <span>测通用途</span>
            <select v-model="draft.testResource" class="admin-input">
              <option v-for="resource in resourcesForKind(draft.kind)" :key="resource" :value="resource">
                {{ resourceOptions.find((option) => option.value === resource)?.label }}
              </option>
            </select>
          </label>
          <div class="cloud-key-form-actions">
            <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="draftTestPending || mutationPending" @click="testDraftKey">
              {{ draftTestPending ? '测通中…' : '测通草稿' }}
            </button>
            <span class="cloud-key-action-spacer"></span>
            <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="mutationPending" @click="cancelAddForm">取消</button>
            <button class="cloud-btn cloud-btn--primary" type="submit" :disabled="mutationPending">保存 key</button>
          </div>
        </div>
        <p v-if="draftTest" class="cloud-key-test-feedback" :class="{ 'error-text': 'error' in draftTest || !draftTest.result.ok }" aria-live="polite">
          {{ testFeedbackText(draftTest) }}
        </p>
      </form>
    </section>

    <section class="admin-card cloud-key-directory" aria-labelledby="cloud-key-directory-title">
      <h3 id="cloud-key-directory-title" class="cloud-key-section-title">密钥目录</h3>
      <div class="cloud-key-filters">
        <label class="admin-field cloud-key-search">
          <span>搜索编号 / 标签 / hint</span>
          <input v-model="searchText" class="admin-input" type="search" autocomplete="off" />
        </label>
        <label class="admin-field cloud-key-filter">
          <span>种类</span>
          <select v-model="kindFilter" class="admin-input">
            <option v-for="option in kindOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="admin-field cloud-key-filter">
          <span>标签</span>
          <select v-model="labelFilter" class="admin-input">
            <option v-for="option in labelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <button class="cloud-btn cloud-btn--ghost cloud-key-reset" type="button" @click="searchText = ''; kindFilter = allKinds; labelFilter = allLabels">
          重置
        </button>
      </div>

      <div class="cloud-key-bulk-bar">
        <span>已选 {{ selectedIds.length }} 项</span>
        <label class="cloud-key-inline-field cloud-key-select-target">
          <span class="sr-only">设置 key 为</span>
          <select v-model="selectResource" class="admin-input" :disabled="loading || mutationPending || selectedIds.length !== 1 || !selectedKey?.secretConfigured">
            <option value="">设置 key 为</option>
            <option
              v-for="resource in resourceOptions"
              :key="resource.value"
              :value="resource.value"
              :disabled="!!selectedKey && !selectedKey.resources.includes(resource.value)"
            >
              {{ resource.label }}{{ selectedKey && !selectedKey.resources.includes(resource.value) ? '（种类不匹配）' : '' }}
            </option>
          </select>
        </label>
        <button class="cloud-btn cloud-btn--primary" type="button" :disabled="!canSelect" @click="selectCurrentKey">设置 key</button>
        <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="loading || mutationPending || selectedIds.length === 0" @click="openLabelEditor">修改标签</button>
        <button class="cloud-btn cloud-btn--danger" type="button" :disabled="!canDelete" @click="openDeleteDialog">删除</button>
        <span v-if="selectedIds.length > 1" class="cloud-key-selection-hint">设置 key 仅支持单选</span>
        <span v-else-if="selectedKey && !selectedKey.secretConfigured" class="cloud-key-selection-hint">密钥不完整</span>
        <span v-else-if="hasSelectedCurrentKey" class="cloud-key-selection-hint">当前使用的 key 不能删除，请先换用其他 key</span>
      </div>

      <div v-if="labelEditorOpen" class="cloud-key-label-editor">
        <label class="admin-field cloud-key-label-input">
          <span>将选中的 {{ selectedIds.length }} 个 key 的标签改为</span>
          <input v-model="labelDraft" class="admin-input" type="text" maxlength="128" autocomplete="off" />
          <small>留空将清除选中 key 的标签。</small>
        </label>
        <div class="admin-actions">
          <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="mutationPending" @click="cancelLabelEditor">取消</button>
          <button class="cloud-btn cloud-btn--primary" type="button" :disabled="mutationPending || selectedIds.length === 0" @click="saveLabel">
            {{ mutationPending ? '保存中…' : '保存标签' }}
          </button>
        </div>
      </div>

      <p v-if="actionMessage" class="cloud-key-action-feedback" aria-live="polite">{{ actionMessage }}</p>
      <p v-if="actionError" class="cloud-key-action-feedback error-text" role="alert">{{ actionError }}</p>
      <div v-if="loadError" class="cloud-key-load-error" role="alert">
        <p class="error-text">{{ loadError }}</p>
        <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="loading || mutationPending" @click="loadDirectory({ page: currentPage })">重试</button>
      </div>

      <div class="cloud-key-table-scroll">
        <table class="admin-table cloud-key-table">
          <thead>
            <tr>
              <th class="cloud-key-checkbox-cell">
                <input
                  ref="pageCheckbox"
                  type="checkbox"
                  aria-label="选择当前页全部 key"
                  :checked="allPageSelected"
                  :disabled="loading || mutationPending || pageKeyIds.length === 0"
                  @change="setPageSelection(($event.target as HTMLInputElement).checked)"
                />
              </th>
              <th>编号 / hint</th>
              <th>种类</th>
              <th>标签</th>
              <th>当前用途</th>
              <th>更新时间</th>
              <th>测通</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="7" class="cloud-key-table-state">正在加载 key…</td>
            </tr>
            <tr v-else-if="loadError">
              <td colspan="7" class="cloud-key-table-state">目录未加载</td>
            </tr>
            <tr v-else-if="loaded && keys.length === 0">
              <td colspan="7" class="cloud-key-table-state">
                <span>暂无 key，添加后可测通并设置使用。</span>
                <button class="cloud-btn cloud-btn--primary" type="button" :disabled="mutationPending" @click="openAddForm">添加 key</button>
              </td>
            </tr>
            <tr v-else-if="filteredKeys.length === 0">
              <td colspan="7" class="cloud-key-table-state">
                <span>没有符合条件的 key</span>
                <button class="cloud-btn cloud-btn--ghost" type="button" @click="searchText = ''; kindFilter = allKinds; labelFilter = allLabels">重置筛选</button>
              </td>
            </tr>
            <template v-for="key in pageKeys" :key="key.id">
              <tr :class="{ 'is-selected': selectedIds.includes(key.id) }">
                <td class="cloud-key-checkbox-cell">
                  <input
                    type="checkbox"
                    :aria-label="`选择 key ${key.id}`"
                    :checked="selectedIds.includes(key.id)"
                    :disabled="loading || mutationPending"
                    @change="toggleKeySelection(key.id, ($event.target as HTMLInputElement).checked)"
                  />
                </td>
                <td>
                  <code class="cloud-key-short-id" :title="key.id" :aria-label="`完整编号 ${key.id}`">
                    {{ key.id.slice(0, 8) }}…{{ key.id.slice(-4) }}
                  </code>
                  <small class="cloud-key-hint">{{ key.accessKeyHint }}</small>
                  <small v-if="!key.secretConfigured" class="cloud-key-incomplete">密钥不完整</small>
                </td>
                <td><span class="badge">{{ displayKind(kindOf(key)) }}</span></td>
                <td class="cloud-key-label-cell">{{ key.label?.trim() || '未标签' }}</td>
                <td>
                  <div class="cloud-key-resource-badges">
                    <span v-for="resource in key.selectedResources" :key="resource" class="badge badge--success">
                      {{ resourceOptions.find((option) => option.value === resource)?.label ?? resource }}
                    </span>
                    <span v-if="key.selectedResources.length === 0" class="cloud-key-hint">未设置</span>
                  </div>
                </td>
                <td>{{ formatDate(key.updatedAt) }}</td>
                <td>
                  <div class="cloud-key-row-test">
                    <select
                      v-model="savedTestResources[key.id]"
                      class="admin-input"
                      :disabled="loading || mutationPending || savedTestPendingIds[key.id] || !key.secretConfigured"
                      :aria-label="`key ${key.id} 的测通用途`"
                      @change="onSavedTestResourceChange(key.id)"
                    >
                      <option v-for="resource in key.resources" :key="resource" :value="resource">
                        {{ resourceOptions.find((option) => option.value === resource)?.label ?? resource }}
                      </option>
                    </select>
                    <button
                      class="cloud-btn cloud-btn--ghost"
                      type="button"
                      :disabled="loading || mutationPending || savedTestPendingIds[key.id] || !key.secretConfigured"
                      @click="testSavedKey(key)"
                    >
                      {{ savedTestPendingIds[key.id] ? '测通中…' : '测通' }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="savedTestResult(key)" class="cloud-key-test-row">
                <td colspan="7" :class="{ 'error-text': isFailedTestFeedback(savedTestResult(key)) }" aria-live="polite">
                  {{ testFeedbackText(savedTestResult(key)!) }}
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>

      <footer class="cloud-key-pagination">
        <span>共 {{ filteredKeys.length }} 条</span>
        <label class="cloud-key-inline-field">
          <span>每页</span>
          <select v-model.number="pageSize" class="admin-input" :disabled="loading || mutationPending">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </label>
        <div class="cloud-key-pagination-controls">
          <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="loading || mutationPending || currentPage <= 1" @click="currentPage--">上一页</button>
          <span>{{ currentPage }} / {{ pageCount }}</span>
          <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="loading || mutationPending || currentPage >= pageCount" @click="currentPage++">下一页</button>
        </div>
      </footer>
    </section>

    <section v-if="configLoading" class="admin-card cloud-config-card" aria-live="polite">
      <h3 class="cloud-key-section-title">云资源配置</h3>
      <p class="cloud-config-state">正在加载云资源配置…</p>
    </section>

    <section v-else-if="configLoadError" class="admin-card cloud-config-card" role="alert">
      <h3 class="cloud-key-section-title">云资源配置</h3>
      <p class="error-text">{{ configLoadError }}</p>
      <button class="cloud-btn cloud-btn--ghost" type="button" @click="loadCloudResourceConfig">重试</button>
    </section>

    <template v-else-if="configView">
      <section
        v-for="section in configSections"
        :key="section.id"
        class="admin-card cloud-config-card"
        :aria-labelledby="`cloud-config-${section.id}-title`"
      >
        <h3 :id="`cloud-config-${section.id}-title`" class="cloud-key-section-title">{{ section.title }}</h3>
        <div class="cloud-config-fields">
          <label v-for="field in section.fields" :key="field.key" class="admin-field">
            <span>{{ field.displayName }}</span>
            <select
              v-if="isBooleanConfigField(field)"
              v-model="configState[section.id].values[field.key]"
              class="admin-input"
              :disabled="configState[section.id].pending"
              @change="markConfigDirty(section.id, field.key)"
            >
              <option v-for="option in booleanOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <input
              v-else
              v-model="configState[section.id].values[field.key]"
              class="admin-input"
              type="text"
              autocomplete="off"
              :disabled="configState[section.id].pending"
              @input="markConfigDirty(section.id, field.key)"
            />
            <span v-if="configState[section.id].fieldErrors[field.key]" class="error-text">{{ configState[section.id].fieldErrors[field.key] }}</span>
          </label>
        </div>
        <div v-if="section.id === 'advanced'" class="cloud-config-groups">
          <div v-for="group in advancedGroups" :key="group.label" class="cloud-config-group">
            <h4>{{ group.label }}</h4>
            <div class="cloud-config-fields">
              <label v-for="field in group.fields" :key="field.key" class="admin-field">
                <span>{{ field.displayName }}</span>
                <input
                  v-model="configState.advanced.values[field.key]"
                  class="admin-input"
                  type="text"
                  autocomplete="off"
                  :disabled="configState.advanced.pending"
                  @input="markConfigDirty('advanced', field.key)"
                />
                <span v-if="configState.advanced.fieldErrors[field.key]" class="error-text">{{ configState.advanced.fieldErrors[field.key] }}</span>
              </label>
            </div>
          </div>
        </div>
        <p v-if="configFeedbackMessage(section.id)" class="cloud-key-action-feedback" aria-live="polite">
          {{ configFeedbackMessage(section.id) }}
        </p>
        <p v-if="configFeedbackError(section.id)" class="cloud-key-action-feedback error-text" role="alert">
          {{ configFeedbackError(section.id) }}
        </p>
        <div class="cloud-config-actions">
          <button class="cloud-btn cloud-btn--primary" type="button" :disabled="configState[section.id].pending" @click="queueConfigSave(section.id)">
            {{ configState[section.id].pending ? '保存中…' : '保存' }}
          </button>
          <button class="cloud-btn cloud-btn--ghost" type="button" :disabled="configState[section.id].pending" @click="queueConfigReset(section.id)">
            恢复默认
          </button>
        </div>
      </section>
    </template>

    <dialog ref="deleteDialog" class="cloud-key-dialog" @cancel.prevent="closeDeleteDialog">
      <form method="dialog" @submit.prevent="confirmDelete">
        <h3>删除 {{ deleteIds.length }} 个 key？</h3>
        <p>删除后无法恢复。</p>
        <ul class="cloud-key-delete-summary">
          <li v-for="id in deleteIds.slice(0, 5)" :key="id"><code>{{ id }}</code></li>
          <li v-if="deleteIds.length > 5">等 {{ deleteIds.length }} 项</li>
        </ul>
        <div class="admin-actions">
          <button ref="deleteCancelButton" class="cloud-btn cloud-btn--ghost" type="button" @click="closeDeleteDialog">取消</button>
          <button class="cloud-btn cloud-btn--danger" type="submit">删除</button>
        </div>
      </form>
    </dialog>
  </div>
</template>

<style scoped>
.cloud-key-page {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.cloud-key-directory {
  min-width: 0;
}

.cloud-key-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.cloud-key-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.cloud-key-subtitle {
  margin: 8px 0 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.5;
}

.cloud-key-heading-actions,
.cloud-key-form-actions,
.cloud-key-inline-field,
.cloud-key-pagination-controls,
.cloud-key-row-test {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cloud-key-heading-actions {
  flex-shrink: 0;
}

.cloud-key-section-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
}

.cloud-key-current-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.cloud-key-current-item {
  min-width: 0;
}

.cloud-key-current-item h4 {
  margin: 0 0 8px;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 600;
}

.cloud-key-current-state {
  margin: 0;
  color: #94a3b8;
}

.cloud-key-current-id {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.cloud-key-current-id code {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.cloud-key-copy {
  min-height: 30px;
  padding: 0 8px;
  flex-shrink: 0;
  font-size: 12px;
}

.cloud-key-current-note,
.cloud-key-hint {
  color: #94a3b8;
  font-size: 12px;
}

.cloud-key-current-note {
  margin: 14px 0 0;
}

.cloud-key-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 20px;
}

.cloud-key-form b {
  color: #fca5a5;
  font-weight: 500;
}

.cloud-key-form-footer,
.cloud-key-test-feedback {
  grid-column: 1 / -1;
}

.cloud-key-form-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.cloud-key-inline-field {
  min-width: 0;
  color: #cbd5e1;
  font-size: 13px;
}

.cloud-key-inline-field .admin-input {
  min-width: 150px;
}

.cloud-key-form-actions {
  flex-wrap: wrap;
}

.cloud-key-action-spacer {
  width: 8px;
}

.cloud-key-test-feedback,
.cloud-key-action-feedback {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.cloud-key-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 14px;
}

.cloud-key-filters .admin-field {
  gap: 6px;
  color: #94a3b8;
  font-size: 12px;
}

.cloud-key-search {
  width: min(280px, 100%);
}

.cloud-key-filter {
  min-width: 156px;
}

.cloud-key-reset {
  min-height: 40px;
}

.cloud-key-bulk-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 46px;
  padding: 4px 0 12px;
  color: #cbd5e1;
  font-size: 13px;
}

.cloud-key-select-target select {
  min-width: 190px;
}

.cloud-key-selection-hint {
  color: #94a3b8;
  font-size: 12px;
}

.cloud-key-label-editor {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-top: 1px solid #243041;
}

.cloud-key-label-input {
  width: min(420px, 100%);
}

.cloud-key-label-input small {
  color: #94a3b8;
  font-size: 12px;
}

.cloud-key-action-feedback {
  margin: 4px 0 10px;
}

.cloud-key-load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0 0 12px;
}

.cloud-key-load-error p {
  margin: 0;
}

.cloud-key-table-scroll {
  width: 100%;
  overflow-x: auto;
  border-top: 1px solid #243041;
}

.cloud-key-table {
  min-width: 1000px;
  table-layout: fixed;
}

.cloud-key-table th,
.cloud-key-table td {
  vertical-align: middle;
}

.cloud-key-table tbody tr.is-selected {
  background: #1b2a3d;
}

.cloud-key-checkbox-cell {
  width: 40px;
}

.cloud-key-checkbox-cell input {
  width: 16px;
  height: 16px;
  accent-color: #3b82f6;
}

.cloud-key-table th:nth-child(2) {
  width: 190px;
}

.cloud-key-table th:nth-child(3) {
  width: 130px;
}

.cloud-key-table th:nth-child(4) {
  width: 150px;
}

.cloud-key-table th:nth-child(5) {
  width: 150px;
}

.cloud-key-table th:nth-child(6) {
  width: 160px;
}

.cloud-key-table th:nth-child(7) {
  width: 250px;
}

.cloud-key-short-id {
  display: block;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.cloud-key-hint,
.cloud-key-incomplete {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
}

.cloud-key-incomplete {
  color: #fcd34d;
  font-size: 12px;
}

.cloud-key-label-cell {
  overflow-wrap: anywhere;
}

.cloud-key-resource-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.cloud-key-resource-badges .badge {
  white-space: nowrap;
}

.cloud-key-row-test {
  min-width: 230px;
}

.cloud-key-row-test select {
  width: 145px;
  min-width: 0;
}

.cloud-key-row-test .cloud-btn {
  min-height: 34px;
  padding: 0 10px;
}

.cloud-key-table-state {
  height: 88px;
  color: #94a3b8;
  text-align: center !important;
}

.cloud-key-table-state .cloud-btn {
  margin-left: 12px;
}

.cloud-key-test-row td {
  background: #111820;
  color: #6ee7b7;
  font-size: 13px;
}

.cloud-key-test-row td.error-text {
  color: #f87171;
}

.cloud-key-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-top: 14px;
  color: #94a3b8;
  font-size: 13px;
}

.cloud-key-pagination .admin-input {
  min-width: 72px;
}

.cloud-key-dialog {
  width: min(520px, calc(100vw - 32px));
  padding: 20px;
  border: 1px solid #2b3f57;
  border-radius: 8px;
  background: #151d27;
  color: #e2e8f0;
}

.cloud-key-dialog::backdrop {
  background: rgba(0, 0, 0, 0.65);
}

.cloud-key-dialog h3 {
  margin: 0;
  font-size: 18px;
}

.cloud-key-dialog p {
  color: #94a3b8;
}

.cloud-key-delete-summary {
  display: grid;
  gap: 6px;
  max-height: 180px;
  overflow: auto;
  padding-left: 20px;
}

.cloud-key-delete-summary code {
  overflow-wrap: anywhere;
}

.cloud-config-card {
  display: grid;
  gap: 16px;
}

.cloud-config-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 20px;
}

.cloud-config-groups {
  display: grid;
  gap: 16px;
}

.cloud-config-group {
  display: grid;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #243041;
}

.cloud-config-group h4 {
  margin: 0;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 600;
}

.cloud-config-actions {
  display: flex;
  gap: 12px;
}

.cloud-config-state {
  margin: 0;
  color: #94a3b8;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.cloud-key-page :deep(.admin-input):focus-visible,
.cloud-key-page :deep(.cloud-btn):focus-visible,
.cloud-key-page input[type='checkbox']:focus-visible {
  outline: 2px solid #60a5fa;
  outline-offset: 2px;
}

.cloud-key-page :deep(.cloud-btn:disabled),
.cloud-key-page :deep(.admin-input:disabled) {
  cursor: not-allowed;
  opacity: 0.45;
}

@media (max-width: 720px) {
  .cloud-key-page {
    gap: 16px;
  }

  .cloud-key-heading,
  .cloud-key-form-footer,
  .cloud-key-label-editor {
    align-items: stretch;
    flex-direction: column;
  }

  .cloud-key-heading-actions {
    width: 100%;
  }

  .cloud-key-heading-actions .cloud-btn {
    flex: 1;
  }

  .cloud-key-current-grid,
  .cloud-key-form,
  .cloud-config-fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .cloud-key-form-footer,
  .cloud-key-test-feedback {
    grid-column: auto;
  }

  .cloud-key-form-actions {
    width: 100%;
  }

  .cloud-key-action-spacer {
    flex: 1;
  }

  .cloud-key-filters .admin-field,
  .cloud-key-search,
  .cloud-key-filter,
  .cloud-key-label-input {
    width: 100%;
    min-width: 0;
  }

  .cloud-key-reset {
    width: 100%;
  }

  .cloud-key-select-target,
  .cloud-key-select-target select {
    width: 100%;
  }

  .cloud-key-pagination {
    align-items: flex-start;
  }

  .cloud-key-pagination-controls {
    width: 100%;
    justify-content: space-between;
  }

  .admin-card {
    padding: 16px;
  }
}
</style>
