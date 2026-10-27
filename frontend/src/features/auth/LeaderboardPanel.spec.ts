import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import LeaderboardPanel from './LeaderboardPanel.vue'
import * as predictionsApi from '@/shared/api/predictions'
import type { PredictionAchievementsResponse } from '@/shared/api/predictions'

// PRED-04 업적 섹션 (S15P11A704-561).
//
// 잡는 건 "깨져도 화면이 멀쩡해 보이는" 것들이다:
// 한쪽 조회 실패가 다른 쪽으로 옮는 것, 뱃지 0개 줄, 등급명·요약을 서버 값으로 쓰는지,
// 조회 중 자리표시자(사라지면 도착할 때 아래 목록이 밀린다).
// 전부 화면에는 그냥 「업적이 없는 사용자」나 「살짝 움직인 목록」으로 보여서 눈으로는 구분이 안 된다.

function achievements(
  over: Partial<PredictionAchievementsResponse> = {},
): PredictionAchievementsResponse {
  return {
    memberId: 3,
    predictionCount: 12,
    hitCount: 5,
    hitRate: 41.7,
    totalStakedPoints: 12_000,
    totalRewardPoints: 18_000,
    grade: { code: 'ORACLE_2', name: '시세 예언자 II', score: 420 },
    badges: [{ badgeCode: 'FIRST_HIT', name: '첫 적중', earnedAt: '2026-07-20T12:00:00+09:00' }],
    ...over,
  }
}

function mockLeaderboard() {
  return vi.spyOn(predictionsApi, 'getDailyLeaderboard').mockResolvedValue({
    rankingDate: '2026-07-31',
    calculatedAt: '2026-08-01T00:10:00+09:00',
    items: [
      {
        rank: 1,
        nickname: '가온',
        settledPredictionCount: 3,
        totalStakedPoints: 1_500,
        totalPayoutPoints: 4_500,
        payoutRate: 300,
      },
    ],
    myRank: null,
    nextCursor: null,
    hasNext: false,
  })
}

describe('LeaderboardPanel 예측 업적', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('등급명과 누적 요약을 서버 값으로 보여준다 — 코드→라벨 맵을 프론트에 두지 않는다', async () => {
    mockLeaderboard()
    vi.spyOn(predictionsApi, 'getMyPredictionAchievements').mockResolvedValue(achievements())
    const wrapper = mount(LeaderboardPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('시세 예언자 II')
    // 적중률은 헤더 활동 요약이 이미 말한다 — 카드는 헤더에 없는 것만 싣는다
    expect(wrapper.text()).toContain('참여 12회 · 누적 배당 18,000BP')
    expect(wrapper.text()).not.toContain('적중률')
    expect(wrapper.text()).toContain('첫 적중')
  })

  it('뱃지가 0개면 안내 한 줄을 둔다 — 빈 목록은 줄이 사라진 것처럼 보인다', async () => {
    mockLeaderboard()
    vi.spyOn(predictionsApi, 'getMyPredictionAchievements').mockResolvedValue(
      achievements({ badges: [] }),
    )
    const wrapper = mount(LeaderboardPanel)
    await flushPromises()

    expect(wrapper.find('[aria-label="내 예측 업적"] ul').exists()).toBe(false)
    expect(wrapper.text()).toContain('아직 획득한 뱃지가 없어요.')
  })

  it('조회 중에는 자리를 비우지 않는다 — 늦게 나타나면 아래 리더보드가 밀린다', async () => {
    mockLeaderboard()
    vi.spyOn(predictionsApi, 'getMyPredictionAchievements').mockResolvedValue(achievements())
    const wrapper = mount(LeaderboardPanel)

    // 자리를 잡는 건 문구가 아니라 `invisible` 골격이다 — 문구만 보면 골격을 지워도 통과한다
    expect(wrapper.find('.invisible').exists()).toBe(true)
    expect(wrapper.text()).toContain('예측 업적을 불러오는 중이에요')
    await flushPromises()
    expect(wrapper.find('.invisible').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('예측 업적을 불러오는 중이에요')
  })

  it('업적 조회가 실패해도 리더보드는 그대로 뜬다 — 곁들이가 본문을 막으면 안 된다', async () => {
    mockLeaderboard()
    vi.spyOn(predictionsApi, 'getMyPredictionAchievements').mockRejectedValue(new Error('boom'))
    const wrapper = mount(LeaderboardPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('가온')
    expect(wrapper.text()).toContain('🥇')
    // 오류 문구도 띄우지 않는다 — 리더보드가 멀쩡한데 화면이 고장 난 것처럼 읽힌다
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('리더보드가 실패해도 업적은 그대로 뜬다 — 반대 방향도 독립이다', async () => {
    vi.spyOn(predictionsApi, 'getDailyLeaderboard').mockRejectedValue(new Error('boom'))
    vi.spyOn(predictionsApi, 'getMyPredictionAchievements').mockResolvedValue(achievements())
    const wrapper = mount(LeaderboardPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('시세 예언자 II')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })
})
