export interface ApiResponse<T> {
  success: boolean
  message: string | null
  data: T
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status = 400) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}
