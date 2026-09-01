import { DomainError } from '@/shared/domain-error'
import type { TokenProvider } from '@/shared/infrastructure/http-client'
import type { JobEventPort } from '../../domain/repositories/job-event.port'
import { extractSseJsonPayloads } from '../../domain/services/sse-buffer.service'
import { mapProgressEventFromDto, type JobProgressEventDto } from '../mappers/job.mapper'

export function createFetchJobEventAdapter(options: {
  baseUrl?: string
  fetchImpl?: typeof fetch
  tokenProvider: TokenProvider
}): JobEventPort {
  const baseUrl = options.baseUrl ?? ''
  const fetchImpl = options.fetchImpl ?? globalThis.fetch.bind(globalThis)

  return {
    subscribe(jobId, onEvent) {
      const controller = new AbortController()
      const token = options.tokenProvider.getToken()

      void (async () => {
        try {
          const response = await fetchImpl(`${baseUrl}/api/v1/jobs/${jobId}/events`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
            signal: controller.signal,
          })
          if (!response.ok || !response.body) {
            throw new DomainError('SSE_CONNECT_FAILED')
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
            const extracted = extractSseJsonPayloads(buffer)
            buffer = extracted.rest
            for (const payload of extracted.payloads) {
              onEvent(mapProgressEventFromDto(payload as JobProgressEventDto))
            }
          }
        } catch (error) {
          if (!(error instanceof DOMException && error.name === 'AbortError')) {
            console.error(error)
          }
        }
      })()

      return () => controller.abort()
    },
  }
}
