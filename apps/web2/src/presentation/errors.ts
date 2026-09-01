import { DomainError } from '@/shared/domain-error'

export function formatDomainError(
  t: (key: string, values?: Record<string, unknown>) => unknown,
  error: unknown,
): string {
  if (error instanceof DomainError) {
    if (error.message && error.message !== error.code) {
      return error.message
    }
    return String(t(`errors.${error.code}`, error.details))
  }
  if (error instanceof Error && error.message) {
    return error.message
  }
  return String(t('errors.UNKNOWN'))
}
