import { apiRequest, getAuthToken } from '@/shared/infrastructure/http/client'

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

export function cancelJob(jobId: string) {
  return apiRequest<JobResponse>(`/api/v1/jobs/${jobId}/cancel`, {
    method: 'POST',
  })
}

export function deleteJob(jobId: string) {
  return apiRequest<void>(`/api/v1/jobs/${jobId}`, {
    method: 'DELETE',
  })
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
      reject(new Error(`OSS ä¸ä¼ å¤±è´¥ (${xhr.status})`))
    }

    xhr.onerror = () =>
      reject(
        new Error(
          'OSS ä¸ä¼ ç½ç»éè¯¯ï¼å¤ä¸º Bucket æªéç½®æµè§å¨è·¨åï¼CORSï¼ãè¯·å¨ OSS æ§å¶å°ä¸º Bucket æ·»å åç«¯ Originï¼å«åè®®ï¼å¦ http://192.168.230.132ï¼ï¼åè®¸ PUT/GET/HEAD ä¸ Headers *ãåç«¯ãæµè¯è¿æ¥ãä»éªè¯æå¡ç«¯ï¼ä¸è¦çæ­¤é¡¹ã',
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
        throw new Error('æ æ³è¿æ¥ä»»å¡è¿åº¦æµ')
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
