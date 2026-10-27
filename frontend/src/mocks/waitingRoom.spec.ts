import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import { seedMockServerClock } from './waitingRoom'

// 목 대기방 서버 시계 시드 (S15P11A704-448).
//
// 대기방 카운트다운은 serverOffset 없이는 계산되지 않는다(규칙 5). 실 모드에서 그 값을 세우는 것은
// TIME_SYNC 하나뿐인데 목 대기방은 소켓이 없어 그 이벤트가 오지 않아, 칩이 영구히 빈 값이었다.
// 목은 서버 시각 = 로컬 시각이라 오프셋 0이 사실이다.

describe('seedMockServerClock (S15P11A704-448)', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('오프셋이 없으면 0으로 세운다 — 목은 서버 시각이 곧 로컬 시각이다', () => {
    const live = useLiveStore()
    expect(live.serverOffset).toBeNull() // 기본값: TIME_SYNC 미수신

    seedMockServerClock(live)

    expect(live.serverOffset).toBe(0)
  })

  it('이미 오프셋이 있으면 덮지 않는다 — 목 시드가 서버 값을 밀어내면 안 된다', () => {
    const live = useLiveStore()
    live.serverOffset = 1_500 // 실 TIME_SYNC로 얻은 값이라고 가정

    seedMockServerClock(live)

    expect(live.serverOffset).toBe(1_500)
  })

  it('0도 값으로 취급한다 — null(모름)과 0(정확히 일치)을 구분한다', () => {
    const live = useLiveStore()
    live.serverOffset = 0

    seedMockServerClock(live)

    expect(live.serverOffset).toBe(0) // 재호출이 상태를 흔들지 않는다(멱등)
  })
})
