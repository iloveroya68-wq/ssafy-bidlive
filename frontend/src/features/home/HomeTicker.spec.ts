import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import HomeTicker from './HomeTicker.vue'
import * as predictionsApi from '@/shared/api/predictions'
import type { LeaderboardEntry } from '@/shared/api/predictions'

// PRED-05 홈 티커 (S15P11A704-76).
//
// 여기서 잡는 건 "깨져도 화면은 그럴듯한" 것들이다 — 마퀴가 어긋나는 건 38초에 한 번 튀고,
// 순위가 한 칸 밀리는 건 숫자가 그대로 있어서 눈으로 못 잡는다.

function entry(over: Partial<LeaderboardEntry> = {}): LeaderboardEntry {
  return {
    rank: 1,
    nickname: '가온',
    settledPredictionCount: 3,
    totalStakedPoints: 1_500,
    totalPayoutPoints: 4_500,
    payoutRate: 300,
    ...over,
  }
}

function mockLeaderboard(items: LeaderboardEntry[]) {
  vi.spyOn(predictionsApi, 'getDailyLeaderboard').mockResolvedValue({
    rankingDate: '2026-07-29',
    calculatedAt: '2026-07-29T00:00:00+09:00',
    items,
    myRank: null,
    nextCursor: null,
    hasNext: false,
  })
}

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }

function mountTicker() {
  return mount(HomeTicker, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

/**
 * jsdom은 레이아웃을 하지 않아 폭이 전부 0이다 — 판정에 쓰는 두 값만 심는다.
 * **마운트 전에** 걸어야 한다: 측정은 `onMounted`에서 한 번 일어나고,
 * jsdom에는 ResizeObserver가 없어 나중에 심어도 다시 재지 않는다.
 */
function stubLayout(trackWidth: number, pillWidth: number) {
  vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(trackWidth)
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(pillWidth)
}

describe('HomeTicker', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('조회 전에는 같은 크기의 자리를 둔다 — 나중에 나타나면 아래 목록이 밀린다', () => {
    mockLeaderboard([entry()])
    const wrapper = mountTicker()

    expect(wrapper.text()).toContain('불러오는 중')
  })

  it('그날 정산된 예측이 없으면 통째로 접는다 — 빈 알약은 고장난 것처럼 보인다', async () => {
    mockLeaderboard([])
    const wrapper = mountTicker()
    await flushPromises()

    expect(wrapper.find('.ticker').exists()).toBe(false)
  })

  it('조회에 실패해도 오류를 띄우지 않고 접는다 — 홈의 곁다리다', async () => {
    vi.spyOn(predictionsApi, 'getDailyLeaderboard').mockRejectedValue(new Error('boom'))
    const wrapper = mountTicker()
    await flushPromises()

    expect(wrapper.find('.ticker').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('못')
  })

  it('서버가 준 rank로 메달을 붙인다 — 인덱스로 매기면 한 칸씩 밀린다', async () => {
    mockLeaderboard([
      entry({ rank: 2, nickname: '가온', payoutRate: 300 }),
      entry({ rank: 4, nickname: '루미', payoutRate: 240 }),
    ])
    const wrapper = mountTicker()
    await flushPromises()

    // 첫 항목이 목록의 0번이라고 🥇를 붙이면 안 된다 — 서버가 2위라고 했다
    expect(wrapper.text()).toContain('🥈')
    expect(wrapper.text()).not.toContain('🥇')
    expect(wrapper.text()).toContain('4위')
  })

  it('항목이 적어 한 벌이 컨테이너보다 좁으면 흐르지 않는다', async () => {
    // 2벌 복제 + translateX(-50%)는 한 벌이 컨테이너보다 넓을 때만 이음매가 맞물린다.
    // 좁은데 흘리면 오른쪽에 빈자리가 생겼다가 38초마다 튄다.
    stubLayout(120, 800)
    mockLeaderboard([entry()])
    const wrapper = mountTicker()
    await flushPromises()

    expect(wrapper.find('.tk-track').exists()).toBe(false)
    // 복제본도 두지 않는다 — 같은 이름이 두 번 보인다
    expect(wrapper.findAll('.tk-half')).toHaveLength(1)
  })

  it('한 벌이 컨테이너보다 넓으면 흐르고 복제본을 둔다', async () => {
    stubLayout(1600, 800)
    mockLeaderboard([entry(), entry({ rank: 2, nickname: '제이든' })])
    const wrapper = mountTicker()
    await flushPromises()

    expect(wrapper.find('.tk-track').exists()).toBe(true)
    expect(wrapper.findAll('.tk-half')).toHaveLength(2)
    // 두 번째 벌은 시각적 반복이라 스크린리더에서 뺀다
    expect(wrapper.findAll('.tk-half')[1].attributes('aria-hidden')).toBe('true')
  })
})
