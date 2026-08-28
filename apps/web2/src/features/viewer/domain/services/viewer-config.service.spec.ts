import { describe, expect, it } from 'vitest'
import { coerceProjectInfoConfig, createEmptyViewerConfig, parseViewerConfig } from './viewer-config.service'
import { PROJECT_INFO_BUILTIN_KEYS, VIEWER_CONFIG_VERSION } from '../entities/viewer-config.entity'

describe('viewer config', () => {
  it('fills builtin fields and keeps custom keys', () => {
    const info = coerceProjectInfoConfig({
      projectName: '  Tower  ',
      fields: [
        { key: 'height', label: 'H', value: '12' },
        { key: 'custom', label: 'C', value: '1' },
        { key: 'custom', label: 'dup', value: '2' },
        { key: '', value: 'skip' },
      ],
    })
    expect(info.projectName).toBe('Tower')
    expect(info.fields.map((field) => field.key)).toEqual([...PROJECT_INFO_BUILTIN_KEYS, 'custom'])
    expect(info.fields.find((field) => field.key === 'height')?.value).toBe('12')
    expect(info.fields.filter((field) => field.key === 'custom')).toHaveLength(1)
  })

  it('parses unknown payloads into a v2 empty aggregate', () => {
    const empty = parseViewerConfig(null)
    expect(empty).toEqual(createEmptyViewerConfig())
    expect(empty.version).toBe(VIEWER_CONFIG_VERSION)
    const parsed = parseViewerConfig({
      version: 2,
      defaultView: null,
      pointAnnotations: [{ id: '1', text: 'a', position: [0, 0, 0], edgeColor: '#fff' }],
      cubeMarkers: [],
      projectInfo: { projectName: 'P', fields: [] },
    })
    expect(parsed.pointAnnotations).toHaveLength(1)
    expect(parsed.projectInfo.fields).toHaveLength(PROJECT_INFO_BUILTIN_KEYS.length)
  })
})
