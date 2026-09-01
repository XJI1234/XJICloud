import { DomainError } from '@/shared/domain-error'
import type { Result } from '@/shared/result'

export function unwrapResult<T>(result: Result<T>): T {
  const [error, data] = result
  if (error || data === null) {
    throw error ?? new DomainError('UNKNOWN')
  }
  return data
}
