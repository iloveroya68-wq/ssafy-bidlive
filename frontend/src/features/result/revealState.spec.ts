import { afterEach, describe, expect, it, vi } from 'vitest'
import { REVEAL_SETTLING_DURATION_MS, useRevealState } from './revealState'

const endedEvent = {
  type: 'AUCTION_ENDED' as const,
  data: { endedAt: '2026-08-04T00:00:00.000Z', status: 'SETTLING' },
}

const noSaleEvent = {
  type: 'AUCTION_NO_SALE' as const,
  data: { reason: 'NO_BID', endedAt: '2026-08-04T00:00:00.000Z' },
}

// 연속 도착한 종료 이벤트가 봉투 단계를 건너뛰지 않는지 검증한다.
describe('revealState 종료 이벤트 흐름', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('terminal 이벤트가 즉시 와도 최소 봉투 시간을 보장한다', () => {
    vi.useFakeTimers()
    const reveal = useRevealState()

    reveal.apply(endedEvent)
    reveal.apply(noSaleEvent)

    expect(reveal.phase.value).toBe('settling')
    vi.advanceTimersByTime(REVEAL_SETTLING_DURATION_MS - 1)
    expect(reveal.phase.value).toBe('settling')

    vi.advanceTimersByTime(1)
    expect(reveal.phase.value).toBe('nosale')
  })

  it('봉투 시간이 지난 뒤 도착한 terminal 이벤트는 즉시 공개한다', () => {
    vi.useFakeTimers()
    const reveal = useRevealState()

    reveal.apply(endedEvent)
    vi.advanceTimersByTime(REVEAL_SETTLING_DURATION_MS)
    reveal.apply(noSaleEvent)

    expect(reveal.phase.value).toBe('nosale')
  })
})
