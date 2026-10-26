import { DomainError } from '@/shared/domain-error'
import type { TokenProvider, UnauthorizedHandler } from '@/shared/infrastructure/http-client'
import type { JobEventPort } from '../../domain/repositories/job-event.port'
import { extractSseJsonPayloads } from '../../domain/services/sse-buffer.service'
import { mapProgressEventFromDto, type JobProgressEventDto } from '../mappers/job.mapper'

export function createFetchJobEventAdapter(options: {
  baseUrl?: string
  fetchImpl?: typeof fetch
  unauthorized?: UnauthorizedHandler
  tokenProvider: TokenProvider
}): JobEventPort {
  const baseUrl = options.baseUrl ?? ''
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)

  return {
    subscribe(jobId, onEvent, onClose) {
      const controller = new AbortController()
      const token = options.tokenProvider.getToken()
      let reader: ReadableStreamDefaultReader<Uint8Array> | undefined

      void (async () => {
        try {
          const response = await fetchImpl(`${baseUrl}/api/v1/jobs/${jobId}/events`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
            signal: controller.signal,
          })
          if (controller.signal.aborted) { await response.body?.cancel(); return }
          if (response.status === 401 && token && token === options.tokenProvider.getToken()) {
            options.unauthorized?.notifyUnauthorized(token)
            throw new DomainError('AUTH_UNAUTHORIZED')
          }
          if (!response.ok || !response.body) {
            throw new DomainError('SSE_CONNECT_FAILED')
          }
          reader = response.body.getReader()
          const decoder = new TextDecoder()
          let buffer = ''
          while (true) {
            const { done, value } = await reader.read()
            if (done) {
              break
            }
            buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
            const extracted = extractSseJsonPayloads(buffer)
            buffer = extracted.rest
            for (const payload of extracted.payloads) {
              const dto = payload as JobProgressEventDto | null
              if (!dto || dto.jobId !== jobId || !['PENDING', 'UPLOADING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'].includes(dto.status ?? '') || typeof dto.progress !== 'number' || !Number.isFinite(dto.progress)) {
                throw new DomainError('SSE_CONNECT_FAILED')
              }
              if (!controller.signal.aborted) onEvent(mapProgressEventFromDto(dto))
            }
          }
          if (!controller.signal.aborted) onClose?.()
        } catch (error) {
          if (!controller.signal.aborted) onClose?.(error instanceof DomainError ? error : new DomainError('SSE_CONNECT_FAILED'))
        } finally {
          if (reader) {
            try { await reader.cancel() } catch { /* Already closed/aborted. */ }
            reader.releaseLock()
          }
        }
      })()

      return () => {
        controller.abort()
        void reader?.cancel().catch(() => undefined)
      }
    },
  }
}
