const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export interface ApiResponse<T> {
  success: boolean
  message: string | null
  data: T
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 400) {
    super(message)
    this.status = status
  }
}

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
