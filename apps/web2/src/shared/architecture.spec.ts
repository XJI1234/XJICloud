import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const srcRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = path.join(dir, entry)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      return walk(full)
    }
    return full.endsWith('.ts') ? [full] : []
  })
}

describe('layer dependency rules', () => {
  const files = walk(srcRoot)

  it('domain never imports vue, fetch, or infrastructure', () => {
    const domainFiles = files.filter(
      (file) => file.includes(`${path.sep}domain${path.sep}`) && !file.endsWith('.spec.ts'),
    )
    expect(domainFiles.length).toBeGreaterThan(0)
    for (const file of domainFiles) {
      const source = readFileSync(file, 'utf8')
      expect(source, file).not.toMatch(/from ['"]vue['"]/)
      expect(source, file).not.toMatch(/\bfetch\s*\(/)
      expect(source, file).not.toMatch(/infrastructure/)
    }
  })

  it('composables never import dto mappers', () => {
    const composables = files.filter((file) => file.includes(`${path.sep}composables${path.sep}`))
    expect(composables.length).toBeGreaterThan(0)
    for (const file of composables) {
      const source = readFileSync(file, 'utf8')
      expect(source, file).not.toMatch(/mappers\//)
      expect(source, file).not.toMatch(/Dto/)
    }
  })
})
