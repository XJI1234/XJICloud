import { ApiError, type ApiResponse } from '@xjicloud/shared'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export { ApiError, type ApiResponse }

function getToken() {
  return localStorage.getItem('xjicloud_admin_token')
}

export function setAdminToken(token: string | null) {
  if (token) {
    localStorage.setItem('xjicloud_admin_token', token)
  } else {
    localStorage.removeItem('xjicloud_admin_token')
  }
}

export async function adminRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers ?? {})
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (!(options.body instanceof FormData) && !headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || !payload.success) {
    throw new ApiError(payload.message ?? response.statusText, response.status)
  }
  return payload.data
}

export interface AdminAuthResponse {
  token: string
  username: string
}

export interface DashboardResponse {
  workerCount: number
  onlineWorkers: number
  queueDepth: number
  runningJobs: number
  completedJobsToday: number
  failedJobs: number
}

export interface OssConfigResponse {
  config: Record<string, string>
}

export interface WorkerResponse {
  id: string
  name: string
  status: 'IDLE' | 'BUSY' | 'OFFLINE'
  gpuInfo: string | null
  lastHeartbeat: string
  currentJobId: string | null
  registeredAt: string
}

export interface JobResponse {
  id: string
  projectId: string
  name: string
  status: string
  progress: number
  stage: string | null
  message: string | null
  downloadUrl: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export type CloudAccessKeyResource = 'ECI_OPENAPI' | 'OSS'

export interface CloudAccessKeyResponse {
  id: string
  name: string
  accessKeyHint: string
  secretConfigured: boolean
  resources: CloudAccessKeyResource[]
  selectedResources: CloudAccessKeyResource[]
  createdAt: string
  updatedAt: string
  label: string
}

export interface CloudAccessKeyListResponse {
  resource: CloudAccessKeyResource
  currentId: string | null
  items: CloudAccessKeyResponse[]
}

export interface CloudAccessKeySelectResponse extends CloudAccessKeyResponse {
  appliedResource: CloudAccessKeyResource
}

export interface CloudAccessKeyTestResponse {
  resource: CloudAccessKeyResource
  ok: boolean
  accessKeyHint: string | null
  detail: string
  persistedCurrent: boolean
  appliedToEciManager: boolean
}

export type CloudResourceConfigSectionId = 'network' | 'oss' | 'advanced'

export interface CloudResourceConfigField {
  key: string
  displayName: string
  groupLabel: string
  value: string
  source: string
  allowEmpty: boolean
  valueType: string
  validationRule: string
}

export interface CloudResourceConfigSection {
  id: CloudResourceConfigSectionId
  label: string
  fields: CloudResourceConfigField[]
}

export interface CloudResourceConfigResponse {
  network: CloudResourceConfigSection
  oss: CloudResourceConfigSection
  advanced: CloudResourceConfigSection
}

export function adminLogin(username: string, password: string) {
  return adminRequest<AdminAuthResponse>('/api/v1/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function fetchDashboard() {
  return adminRequest<DashboardResponse>('/api/v1/admin/dashboard')
}

export function fetchOssConfig() {
  return adminRequest<OssConfigResponse>('/api/v1/admin/oss')
}

export function updateOssConfig(config: Record<string, string>) {
  return adminRequest<OssConfigResponse>('/api/v1/admin/oss', {
    method: 'PUT',
    body: JSON.stringify({ config }),
  })
}

export function testOssConnection() {
  return adminRequest<void>('/api/v1/admin/oss/test', { method: 'POST' })
}

export function fetchWorkers() {
  return adminRequest<WorkerResponse[]>('/api/v1/admin/workers')
}

export function forceWorkerOffline(workerId: string) {
  return adminRequest<void>(`/api/v1/admin/workers/${workerId}/offline`, { method: 'POST' })
}

export function fetchJobs() {
  return adminRequest<JobResponse[]>('/api/v1/admin/jobs')
}

export function retryJob(jobId: string) {
  return adminRequest<JobResponse>(`/api/v1/admin/jobs/${jobId}/retry`, { method: 'POST' })
}

export function cancelJob(jobId: string) {
  return adminRequest<JobResponse>(`/api/v1/admin/jobs/${jobId}/cancel`, { method: 'POST' })
}

export function fetchStats() {
  return adminRequest<Record<string, number>>('/api/v1/admin/stats')
}

export function fetchCloudAccessKeys(resource: CloudAccessKeyResource) {
  return adminRequest<CloudAccessKeyListResponse>(
    `/api/v1/admin/cloud-access-keys?resource=${resource}`,
  )
}

export function createCloudAccessKey(payload: {
  name: string
  accessKeyId: string
  accessKeySecret: string
  resources: CloudAccessKeyResource[]
  label: string
}) {
  return adminRequest<CloudAccessKeyResponse>('/api/v1/admin/cloud-access-keys', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCloudAccessKey(id: string, payload: { label: string }) {
  return adminRequest<CloudAccessKeyResponse>(`/api/v1/admin/cloud-access-keys/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteCloudAccessKey(id: string) {
  return adminRequest<void>(`/api/v1/admin/cloud-access-keys/${id}`, { method: 'DELETE' })
}

export function selectCloudAccessKey(id: string, resource: CloudAccessKeyResource) {
  return adminRequest<CloudAccessKeySelectResponse>(`/api/v1/admin/cloud-access-keys/${id}/select`, {
    method: 'POST',
    body: JSON.stringify({ resource }),
  })
}

export function testCloudAccessKeyDraft(payload: {
  resource: CloudAccessKeyResource
  accessKeyId: string
  accessKeySecret: string
}) {
  return adminRequest<CloudAccessKeyTestResponse>('/api/v1/admin/cloud-access-keys/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function testSavedCloudAccessKey(id: string, resource: CloudAccessKeyResource) {
  return adminRequest<CloudAccessKeyTestResponse>(`/api/v1/admin/cloud-access-keys/${id}/test`, {
    method: 'POST',
    body: JSON.stringify({ resource }),
  })
}

export function fetchCloudResourceConfig() {
  return adminRequest<CloudResourceConfigResponse>('/api/v1/admin/cloud-resource-config')
}

export function saveCloudResourceConfigSection(
  section: CloudResourceConfigSectionId,
  values: Record<string, string>,
) {
  return adminRequest<CloudResourceConfigResponse>(`/api/v1/admin/cloud-resource-config/${section}`, {
    method: 'PUT',
    body: JSON.stringify(values),
  })
}

export function resetCloudResourceConfigSection(section: CloudResourceConfigSectionId) {
  return adminRequest<CloudResourceConfigResponse>(`/api/v1/admin/cloud-resource-config/${section}`, {
    method: 'DELETE',
  })
}
