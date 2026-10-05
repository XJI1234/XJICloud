import { describe, expect, it } from 'vitest'
import { busyWait, doneWait, hiddenWait, progressWait } from './wait-session'

describe('wait-session snapshots', () => {
  it('hidden wait is not visible and not complete', () => {
    expect(hiddenWait()).toMatchObject({
      visible: false,
      phase: 'hidden',
      complete: false,
      awaitConfirm: false,
    })
  })

  it('busy wait shows the mark phase without a complete footer', () => {
    expect(busyWait('正在导出', true)).toMatchObject({
      visible: true,
      phase: 'busy',
      title: '正在导出',
      complete: false,
      awaitConfirm: true,
    })
  })

  it('progress wait clamps percent and keeps the transfer in flight', () => {
    expect(progressWait('正在上传', 140, '1.2 MB/s', true)).toMatchObject({
      phase: 'progress',
      percent: 100,
      speed: '1.2 MB/s',
      complete: false,
      awaitConfirm: true,
    })
  })

  it('done wait requires confirm before dismiss', () => {
    expect(doneWait('已完成', 100, '2.0 MB/s')).toMatchObject({
      phase: 'done',
      complete: true,
      awaitConfirm: true,
      percent: 100,
    })
  })
})
