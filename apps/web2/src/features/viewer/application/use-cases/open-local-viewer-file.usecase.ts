import { err, ok, type Result } from '@/shared/result'
import { assertModelFile } from '@/features/model-asset/domain/services/model-format.service'

export function openLocalViewerFileUseCase(file: File): Result<File> {
  const formatError = assertModelFile(file)
  if (formatError) {
    return err(formatError)
  }
  return ok(file)
}
