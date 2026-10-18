// @vitest-environment node
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
// @ts-expect-error Node gateway scripts are intentionally outside the frontend TS graph.
import { createPlaybackDiagnosticLog, playbackDiagnostic } from '../../../../scripts/sky-playback-diagnostics.mjs'

const input = (change = {}) => ({ action: 'video-playback-diagnostic', reason: JSON.stringify({
  pageId: 'page-1', generation: 1, atMs: 50_000, event: 'loading-complete', reason: '', workspace: 'flight', hidden: false,
  time: 12.5, bufferedEnd: 13, frames: 300, ...change,
}) })

describe('local playback diagnostic log', () => {
  it('keeps publisher lifecycle and backpressure evidence, and rejects uncontrolled new details', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'sky-playback-test-'))
    const log = createPlaybackDiagnosticLog(directory)
    try {
      const events = [
        { kind: 'rtmp-published', deviceId: 'phone-1', detail: 'RTMP publish started' },
        { kind: 'http-flv-client-backpressure', deviceId: 'phone-1', detail: 'HTTP-FLV player queue reached the limit; skipped until the next keyframe' },
        { kind: 'rtmp-unpublished', deviceId: 'phone-1', detail: 'RTMP publish ended' },
      ]
      for (const event of events) log.recordMedia(event)
      for (const event of events) log.recordMedia({ ...event, detail: 'private-token path' })
      await log.flush()
      const records = (await readFile(path.join(directory, 'playback.ndjson'), 'utf8')).trim().split('\n').map(line => JSON.parse(line))
      expect(records.map(({ kind, deviceId, detail }) => ({ kind, deviceId, detail }))).toEqual(events)
    } finally { await log.flush(); await rm(directory, { recursive: true, force: true }) }
  })

  it('retains evidence while discarding unknown fields, URLs, exceptions and malformed records', () => {
    const record = playbackDiagnostic(input({ url: 'http://secret.invalid/token', stack: 'private path', reason: 'http://secret.invalid/token' }))
    expect(record.reason).toBe('unclassified')
    expect(record.time).toBe(12.5)
    expect(JSON.stringify(record)).not.toMatch(/secret|token|stack|private/)
    expect(playbackDiagnostic({ action: 'flight-confirm', reason: '{}' })).toBeNull()
    expect(playbackDiagnostic({ action: 'video-playback-diagnostic', reason: '{' })).toBeNull()
    expect(playbackDiagnostic(input({ atMs: null }))).toBeNull()
  })

  it('persists browser and connection events in order, and bounds a burst without blocking callers', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'sky-playback-test-'))
    const log = createPlaybackDiagnosticLog(directory)
    try {
      expect(log.recordPlayback(input())).toBe(true)
      log.recordMedia({ kind: 'http-flv-viewer-close', deviceId: 'phone-1', detail: 'viewer=1;reason=response-closed;bytes=400' })
      let rejected = 0
      for (let i = 0; i < 2000; i++) if (!log.recordPlayback(input())) rejected++
      expect(rejected).toBeGreaterThan(0)
      await log.flush()
      const lines = (await readFile(path.join(directory, 'playback.ndjson'), 'utf8')).trim().split('\n').map(line => JSON.parse(line))
      expect(lines.find(line => line.kind === 'playback').event).toBe('loading-complete')
      expect(lines.some(line => line.kind === 'http-flv-viewer-close')).toBe(true)
      expect(lines.some(line => line.kind === 'dropped')).toBe(true)
      expect(lines.length).toBeLessThanOrEqual(1025)
    } finally { await log.flush(); await rm(directory, { recursive: true, force: true }) }
  })

  it('rotates existing logs twice and reports file errors without interrupting other writes', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'sky-playback-test-'))
    const errors: string[] = []
    const log = createPlaybackDiagnosticLog(directory, () => errors.push('write-failed'))
    const file = path.join(directory, 'playback.ndjson')
    try {
      for (let i = 0; i < 2; i++) {
        await writeFile(file, 'x'.repeat(8 * 1024 * 1024))
        log.recordPlayback(input())
        await log.flush()
        expect((await readFile(`${file}.previous`, 'utf8')).length).toBe(8 * 1024 * 1024)
        expect(JSON.parse((await readFile(file, 'utf8')).trim()).event).toBe('loading-complete')
      }
      const unavailable = createPlaybackDiagnosticLog(file, () => errors.push('write-failed'))
      expect(unavailable.recordPlayback(input())).toBe(true)
      await expect(unavailable.flush()).resolves.toBeUndefined()
      expect(errors).toEqual(['write-failed'])
      log.recordPlayback(input())
      await log.flush()
      expect((await readFile(file, 'utf8')).trim().split('\n')).toHaveLength(2)
    } finally { await log.flush(); await rm(directory, { recursive: true, force: true }) }
  })
})
