import { describe, expect, it } from 'vitest'
import { DomainError } from '@/shared/domain-error'
import { formatDomainError } from '@/presentation/errors'

describe('formatDomainError', () => {
  const t = (key: string, values?: Record<string, unknown>) =>
    values?.name ? `${key}:${values.name}` : key

  it('maps domain codes to i18n keys', () => {
    expect(formatDomainError(t, new DomainError('AUTH_CREDENTIALS_REQUIRED'))).toBe('errors.AUTH_CREDENTIALS_REQUIRED')
  })

  it('interpolates details', () => {
    expect(
      formatDomainError(t, new DomainError('DATASET_MISSING_UPLOAD_URL', undefined, { details: { name: '0001.jpg' } })),
    ).toBe('errors.DATASET_MISSING_UPLOAD_URL:0001.jpg')
  })
})
