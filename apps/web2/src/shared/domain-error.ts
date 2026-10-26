export type DomainErrorCode =
  | 'AUTH_CREDENTIALS_REQUIRED'
  | 'AUTH_PHONE_INVALID'
  | 'AUTH_PASSWORD_INVALID'
  | 'AUTH_SMS_REQUIRED'
  | 'AUTH_SMS_UNAVAILABLE'
  | 'AUTH_PROFILE_INVALID'
  | 'AUTH_CAPTCHA_REQUIRED'
  | 'AUTH_CAPTCHA_LOAD_FAILED'
  | 'AUTH_UNAUTHORIZED'
  | 'DATASET_NO_IMAGES'
  | 'DATASET_MISSING_UPLOAD_URL'
  | 'JOB_CANNOT_CANCEL'
  | 'JOB_CANNOT_DELETE'
  | 'PROJECT_NAME_REQUIRED'
  | 'PROJECT_NOT_FOUND'
  | 'MODEL_INVALID_FORMAT'
  | 'MODEL_TOO_LARGE'
  | 'MODEL_PROJECT_REQUIRED'
  | 'MODEL_DELETE_FAILED'
  | 'VIEWER_CONFIG_INVALID'
  | 'VIEWER_WEBGPU_UNAVAILABLE'
  | 'VIEWER_UNSUPPORTED_FORMAT'
  | 'VIEWER_RESOURCE_LIMIT'
  | 'VIEWER_LOAD_FAILED'
  | 'VIEWER_DEVICE_LOST'
  | 'VIEWER_SCREENSHOT_FAILED'
  | 'VIEWER_CLEANUP_FAILED'
  | 'VIEWER_CANCELLED'
  | 'VIEWER_TIMEOUT'
  | 'VIEWER_ACTION_FAILED'
  | 'EDITOR_NOT_READY'
  | 'EDITOR_TIMEOUT'
  | 'EDITOR_EXPORT_FAILED'
  | 'EDITOR_IMPORT_FAILED'
  | 'OSS_UPLOAD_FAILED'
  | 'SSE_CONNECT_FAILED'
  | 'OPERATION_CANCELLED'
  | 'NETWORK'
  | 'UNKNOWN'

export class DomainError extends Error {
  readonly code: DomainErrorCode
  readonly status: number | undefined
  readonly details: Record<string, string> | undefined

  constructor(code: DomainErrorCode, message?: string, options?: { status?: number; details?: Record<string, string> }) {
    super(message ?? code)
    this.name = 'DomainError'
    this.code = code
    this.status = options?.status
    this.details = options?.details
  }
}

export function toDomainError(error: unknown): DomainError {
  if (error instanceof DomainError) {
    return error
  }
  if (error instanceof Error) {
    return new DomainError('UNKNOWN', error.message)
  }
  return new DomainError('UNKNOWN', String(error))
}
