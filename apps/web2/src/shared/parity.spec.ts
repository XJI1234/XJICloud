import { describe, expect, it } from 'vitest'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

const requiredUseCases = [
  'features/identity/application/use-cases/auth.usecase.ts',
  'features/project/application/use-cases/project.usecase.ts',
  'features/dataset-training/application/use-cases/submit-dataset.usecase.ts',
  'features/model-asset/application/use-cases/model-asset.usecase.ts',
  'features/viewer/application/use-cases/viewer-config.usecase.ts',
  'features/editor/application/use-cases/editor.usecase.ts',
]

describe('use case parity with apps/web capabilities', () => {
  it('keeps a use case module for every original user capability', () => {
    for (const relative of requiredUseCases) {
      expect(existsSync(path.join(root, relative)), relative).toBe(true)
    }
  })
})
