import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { parse } from 'vue/compiler-sfc'

const srcRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
function walk(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => {
    const full = path.join(dir, entry.name)
    return entry.isDirectory() ? walk(full) : /\.(ts|vue)$/.test(full) && !full.endsWith('.spec.ts') ? [full] : []
  })
}

function violations(file: string, source: string) {
  const filename = file.replaceAll('\\', '/')
  if (filename.endsWith('.vue')) {
    const { descriptor } = parse(source)
    source = [descriptor.script?.content, descriptor.scriptSetup?.content].filter(Boolean).join('\n')
  }
  const ast = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  const errors: string[] = []
  const layer = filename.match(/\/(domain|application|presentation)\//)?.[1]
  const imports: Array<{ target: string; names: string }> = []
  function targetPath(target: string) {
    return (target.startsWith('@/') ? path.join(srcRoot, target.slice(2)) : target.startsWith('.')
      ? path.resolve(path.dirname(file), target) : target).replaceAll('\\', '/')
  }
  function inspect(node: ts.Node) {
    if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) {
      imports.push({ target: targetPath(node.moduleSpecifier.text), names: ts.isImportDeclaration(node) ? node.importClause?.getText(ast) ?? '' : node.exportClause?.getText(ast) ?? '' })
    }
    if (ts.isImportTypeNode(node) && ts.isLiteralTypeNode(node.argument) && ts.isStringLiteral(node.argument.literal)) {
      imports.push({ target: targetPath(node.argument.literal.text), names: node.qualifier?.getText(ast) ?? '' })
    }
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword && ts.isStringLiteral(node.arguments[0]!)) {
      imports.push({ target: targetPath(node.arguments[0].text), names: '' })
    }
    if ((layer === 'domain' || layer === 'presentation') && (ts.isCallExpression(node) || ts.isNewExpression(node))) {
      if (/^(?:(?:window|globalThis)\.)?(fetch|XMLHttpRequest)$/.test(node.expression.getText(ast))) errors.push('direct network access')
    }
    ts.forEachChild(node, inspect)
  }
  inspect(ast)
  for (const { target, names } of imports) {
    if (layer === 'domain' && (/^(vue(?:-router)?|pinia)$/.test(target) || /\/(infrastructure|application|presentation)\//.test(target))) errors.push('domain dependency')
    if (layer === 'application' && (/\/(infrastructure|presentation|mappers)\//.test(target) || /\.vue$/.test(target) || /\b\w*Dto\b/.test(names))) errors.push('application dependency')
    if (layer === 'presentation' && (/\/(infrastructure|mappers)\//.test(target) || /\b\w*Dto\b/.test(names))) errors.push('presentation dependency')
    if (!filename.includes('/infrastructure/') && !filename.endsWith('/app/create-container.ts') && /\/infrastructure\//.test(target) && /\bcreate[A-Z]\w*/.test(names)) errors.push('composition outside root')
  }
  return errors
}

describe('layer dependency rules (TS and Vue scripts)', () => {
  it('enforces the actual production imports', () => {
    const files = walk(srcRoot)
    expect(files.some(file => file.endsWith('.vue'))).toBe(true)
    for (const file of files) expect(violations(file, readFileSync(file, 'utf8')), file).toEqual([])
  })
  it.each([
    ['features/a/domain/rule.ts', "import { ref } from 'vue'"],
    ['features/a/domain/rule.ts', 'fetch("/api")'],
    ['features/a/application/a.ts', "import type { ADto } from '../infrastructure/mappers/a'"],
    ['features/a/presentation/A.vue', '<script setup lang="ts">import { createRepo } from "../infrastructure/repo"</script><template/>'],
    ['features/a/presentation/A.vue', '<script setup>window.fetch("/api")</script>'],
    ['features/a/presentation/a.ts', 'import("../infrastructure/repo")'],
    ['features/a/application/a.ts', 'export type { ADto } from "../infrastructure/dto"'],
    ['features/a/application/a.ts', 'type Dto = import("../infrastructure/dto").ADto'],
    ['app/other.ts', 'import { createRepo } from "@/features/a/infrastructure/repo"'],
  ])('rejects an illegal fixture %s', (file, source) => {
    expect(violations(path.join(srcRoot, file), source).length).toBeGreaterThan(0)
  })
  it.each([
    ['features/a/presentation/A.vue', '<script setup lang="ts">import type { Model } from "@/features/b/domain/model"; import { useB } from "@/features/b/presentation/composables/useB"</script>'],
    ['features/a/application/a.ts', 'import type { Port } from "../domain/repositories/port"'],
    ['features/a/domain/a.ts', '// infrastructure and fetch("x") in a comment are not imports'],
    ['app/create-container.ts', 'import { createRepo } from "@/features/a/infrastructure/repo"'],
  ])('accepts a legitimate fixture %s', (file, source) => {
    expect(violations(path.join(srcRoot, file), source)).toEqual([])
  })
})
