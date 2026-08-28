import type { DomainError } from './domain-error'

export type Result<T, E = DomainError> = readonly [error: E | null, data: T | null]

export function ok<T>(data: T): Result<T> {
  return [null, data]
}

export function err<T>(error: DomainError): Result<T> {
  return [error, null]
}

export function isOk<T, E>(result: Result<T, E>): result is readonly [null, T] {
  return result[0] === null
}
