import { apiRequest, getAuthToken } from '@/api/client'

export type JobStatus =
  | 'PENDING'
  | 'UPLOADING'
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface PresignedUploadItem {
  archivedName: string
  ossKey: string
  uploadUrl: string
  contentType: string
}

export interface CreateDatasetResponse {
  jobId: string
  manifestUploadUrl: string
  uploads: PresignedUploadItem[]
}

export interface JobResponse {
  id: string
  projectId: string
  name: string
  status: JobStatus
  progress: number
  stage: string | null
  message: string | null
  downloadUrl: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface JobProgressEvent {
  jobId: string
  status: JobStatus
  progress: number
  stage: string
  message: string
}

export interface CreateDatasetPayload {
  name: string
  files: Array<{
    archivedName: string
    originalName: string
    contentType: string
    sizeBytes: number
  }>
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function createDataset(projectId: string, payload: CreateDatasetPayload) {
  return apiRequest<CreateDatasetResponse>(`/api/v1/projects/${projectId}/datasets`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function completeDataset(projectId: string, jobId: string) {
  return apiRequest<JobResponse>(`/api/v1/projects/${projectId}/datasets/${jobId}/complete`, {
    method: 'POST',
  })
}

export function listProjectJobs(projectId: string) {
  return apiRequest<JobResponse[]>(`/api/v1/projects/${projectId}/jobs`)
}

export function getJob(jobId: string) {
  return apiRequest<JobResponse>(`/api/v1/jobs/${jobId}`)
}

export function putToOss(
  uploadUrl: string,
  blob: Blob,
  contentType: string,
  onProgress?: (loaded: number, total: number) => void,
) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('PUT', uploadUrl)
    xhr.setRequestHeader('Content-Type', contentType)

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        onProgress?.(event.loaded, event.total)
      }
    }

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve()
        return
      }
      reject(new Error(`OSS 上传失败 (${xhr.status})`))
    }

    xhr.onerror = () =>
      reject(
        new Error(
          'OSS 上传网络错误：多为 Bucket 未配置浏览器跨域（CORS）。请在 OSS 控制台为 Bucket 添加前端 Origin（含协议，如 http://192.168.230.132），允许 PUT/GET/HEAD 与 Headers *。后端「测试连接」仅验证服务端，不覆盖此项。',
        ),
      )
    xhr.send(blob)
  })
}

export function subscribeJobEvents(jobId: string, onEvent: (event: JobProgressEvent) => void) {
  const controller = new AbortController()
  const token = getAuthToken()

  void (async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/jobs/${jobId}/events`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        signal: controller.signal,
      })

      if (!response.ok || !response.body) {
        throw new Error('无法连接任务进度流')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const chunks = buffer.split('\n\n')
        buffer = chunks.pop() ?? ''

        for (const chunk of chunks) {
          const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
          if (!dataLine) {
            continue
          }
          const payload = JSON.parse(dataLine.slice(5).trim()) as JobProgressEvent
          onEvent(payload)
        }
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        console.error(error)
      }
    }
  })()

  return () => controller.abort()
}
