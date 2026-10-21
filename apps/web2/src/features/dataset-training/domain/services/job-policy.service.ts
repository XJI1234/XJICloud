import type { JobStatus, TrainingJob } from '../entities/training-job.entity'

const ACTIVE_STATUSES: readonly JobStatus[] = ['PENDING', 'UPLOADING', 'QUEUED', 'RUNNING']
const TERMINAL_STATUSES: readonly JobStatus[] = ['COMPLETED', 'FAILED', 'CANCELLED']
const INSTANT_PATTERN = /^([+-]\d{6}|\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|([+-])(\d{2}):(\d{2}))$/

type ParsedInstant = { milliseconds: number; subMillisecondNanoseconds: number }

export type ComputeStatusCategory = 'waiting' | 'starting' | 'running' | 'succeeded' | 'failed' | 'cancelled'

function parseInstant(value: string): ParsedInstant | null {
  const match = INSTANT_PATTERN.exec(value)
  if (!match) {
    return null
  }

  const [, yearText, monthText, dayText, hourText, minuteText, secondText, fractionText = '', zone, sign, offsetHourText, offsetMinuteText] = match
  const year = Number(yearText)
  const month = Number(monthText)
  const day = Number(dayText)
  const hour = Number(hourText)
  const minute = Number(minuteText)
  const second = Number(secondText)
  const local = new Date(0)
  local.setUTCFullYear(year, month - 1, day)
  local.setUTCHours(hour, minute, second, 0)
  if (
    !Number.isFinite(local.getTime())
    || local.getUTCFullYear() !== year
    || local.getUTCMonth() !== month - 1
    || local.getUTCDate() !== day
    || hour > 23
    || minute > 59
    || second > 59
  ) {
    return null
  }

  let offsetMinutes = 0
  if (zone !== 'Z') {
    const offsetHours = Number(offsetHourText)
    const offsetRemainder = Number(offsetMinuteText)
    if (offsetHours > 18 || offsetRemainder > 59 || (offsetHours === 18 && offsetRemainder !== 0)) {
      return null
    }
    offsetMinutes = (offsetHours * 60 + offsetRemainder) * (sign === '+' ? 1 : -1)
  }

  const nanoseconds = Number(fractionText.padEnd(9, '0').slice(0, 9))
  return {
    milliseconds: local.getTime() - offsetMinutes * 60_000 + Math.floor(nanoseconds / 1_000_000),
    subMillisecondNanoseconds: nanoseconds % 1_000_000,
  }
}

function compareInstants(left: string, right: string): number | null {
  const parsedLeft = parseInstant(left)
  const parsedRight = parseInstant(right)
  if (!parsedLeft || !parsedRight) {
    return null
  }
  if (parsedLeft.milliseconds !== parsedRight.milliseconds) {
    return parsedLeft.milliseconds < parsedRight.milliseconds ? -1 : 1
  }
  if (parsedLeft.subMillisecondNanoseconds === parsedRight.subMillisecondNanoseconds) {
    return 0
  }
  return parsedLeft.subMillisecondNanoseconds < parsedRight.subMillisecondNanoseconds ? -1 : 1
}

export function isActiveJob(status: JobStatus): boolean {
  return ACTIVE_STATUSES.includes(status)
}

export function isTerminalJob(status: JobStatus): boolean {
  return TERMINAL_STATUSES.includes(status)
}

export function mergeJobByUpdatedAt<T extends Pick<TrainingJob, 'updatedAt'>>(
  current: T | undefined,
  incoming: T,
): T {
  if (!current || !current.updatedAt || !incoming.updatedAt) {
    return incoming
  }
  return compareInstants(incoming.updatedAt, current.updatedAt) === -1 ? current : incoming
}

export function canCancelJob(job: Pick<TrainingJob, 'status' | 'cancellationPending' | 'cleanupPending'>): boolean {
  return isActiveJob(job.status) && job.cancellationPending !== true
}

export function canDeleteJob(job: Pick<TrainingJob, 'status' | 'cancellationPending' | 'cleanupPending'>): boolean {
  return !isActiveJob(job.status) && job.cancellationPending !== true && job.cleanupPending !== true
}

export function jobMutation(
  job: Pick<TrainingJob, 'status' | 'cancellationPending' | 'cleanupPending'>,
): 'cancel' | 'delete' | null {
  if (canCancelJob(job)) {
    return 'cancel'
  }
  return canDeleteJob(job) ? 'delete' : null
}

export function shouldWatchJob(
  job: Pick<TrainingJob, 'status' | 'cancellationPending' | 'cleanupPending'>,
): boolean {
  return isActiveJob(job.status) || job.cancellationPending === true || job.cleanupPending === true
}

export function computeStatusCategory(
  status: string | null | undefined,
  cloudStatus: string | null | undefined,
): ComputeStatusCategory | null {
  for (const candidate of [status, cloudStatus]) {
    switch (candidate?.toUpperCase()) {
      case 'PENDING':
      case 'QUEUED':
      case 'CREATE_PENDING':
      case 'WAITING':
      case 'WAITING_FOR_CREATE':
        return 'waiting'
      case 'STARTING':
      case 'CREATING':
      case 'PROVISIONING':
        return 'starting'
      case 'RUNNING':
      case 'ACTIVE':
        return 'running'
      case 'SUCCEEDED':
        return 'succeeded'
      case 'FAILED':
        return 'failed'
      case 'CANCELLED':
        return 'cancelled'
      default:
        break
    }
  }
  return null
}

export function isCleanupPending(
  job: Pick<TrainingJob, 'status' | 'cancellationPending' | 'cleanupPending'>,
): boolean {
  return job.cleanupPending === true && (job.cancellationPending === true || !isActiveJob(job.status))
}

export function applyJobProgress<T extends Pick<TrainingJob, 'status' | 'progress' | 'stage' | 'message'>>(
  job: T,
  event: {
    status: JobStatus
    progress: number
    stage: string
    message: string
    computeStatus?: string | null
    cloudStatus?: string | null
    computeStatusReason?: string | null
    cancellationPending?: boolean
    cleanupPending?: boolean
    updatedAt?: string
  },
): T {
  return {
    ...job,
    status: event.status,
    progress: event.progress,
    stage: event.stage,
    message: event.message,
    computeStatus: event.computeStatus !== undefined ? event.computeStatus : ('computeStatus' in job ? job.computeStatus : null),
    cloudStatus: event.cloudStatus !== undefined ? event.cloudStatus : ('cloudStatus' in job ? job.cloudStatus : null),
    computeStatusReason: event.computeStatusReason !== undefined
      ? event.computeStatusReason
      : ('computeStatusReason' in job ? job.computeStatusReason : null),
    cancellationPending: event.cancellationPending ?? ('cancellationPending' in job ? job.cancellationPending : false),
    cleanupPending: event.cleanupPending ?? ('cleanupPending' in job ? job.cleanupPending : false),
    updatedAt: event.updatedAt || ('updatedAt' in job ? job.updatedAt : ''),
  } as T
}

export function upsertJob<T extends { id: string }>(jobs: T[], job: T): T[] {
  const index = jobs.findIndex((item) => item.id === job.id)
  if (index >= 0) {
    const next = jobs.slice()
    next[index] = job
    return next
  }
  return [job, ...jobs]
}
