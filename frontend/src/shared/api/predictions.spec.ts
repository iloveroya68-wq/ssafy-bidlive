import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getDailyLeaderboard,
  getMyPredictionAchievements,
  getPredictionResults,
  submitPrediction,
} from './predictions'
import { http } from './http'
import { IDEMPOTENCY_KEY_HEADER } from './idempotency'

// PRED-01 제출의 **실 모드 경로**를 잠근다 (S15P11A704-395).
// 입찰과 달리 멱등키가 헤더가 아니라 **본문 필드**(`clientRequestId`)다 — 명세 그대로다.
// 헤더로 옮겨 보내면 서버는 키 없는 요청으로 보고, 방당 1회 제약과 겹쳐 재시도가 거절로 바뀐다.

function respond() {
  return {
    data: {
      predictionId: 1,
      auctionId: 7,
      predictedPrice: 50_000,
      stakeAmount: 1_000,
      status: 'PLACED',
      submittedAt: '2026-07-29T00:00:00+09:00',
    },
  }
}

describe('submitPrediction', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await submitPrediction(7, { predictedPrice: 50_000, stakeAmount: 1_000 }, 'req-1')

    expect(post.mock.calls[0][0]).toBe('/auctions/7/predictions')
  })

  it('clientRequestId를 본문에 싣는다 — 헤더가 아니다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await submitPrediction(7, { predictedPrice: 50_000, stakeAmount: 1_000 }, 'req-1')

    expect(post.mock.calls[0][1]).toEqual({
      predictedPrice: 50_000,
      stakeAmount: 1_000,
      clientRequestId: 'req-1',
    })
    const config = post.mock.calls[0][2] as { headers?: Record<string, string> } | undefined
    expect(config?.headers?.[IDEMPOTENCY_KEY_HEADER]).toBeUndefined()
  })

  it('키를 생략하면 호출마다 다른 키를 만든다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await submitPrediction(7, { predictedPrice: 50_000, stakeAmount: 1_000 })
    await submitPrediction(7, { predictedPrice: 51_000, stakeAmount: 1_000 })

    const idOf = (i: number) =>
      (post.mock.calls[i][1] as { clientRequestId: string }).clientRequestId
    expect(idOf(0)).toBeTruthy()
    expect(idOf(0)).not.toBe(idOf(1))
  })
})

// PRED-02 결과 분포의 **실 모드 경로와 응답 무가공**을 잠근다 (S15P11A704-520).
//
// 목 분기를 걷어낸 함수라 잘못되면 조용히 틀린다: 경로가 어긋나면 404가 나는데
// `resultSource.isResultNotReady`의 상태 폴백은 409·425만 보므로 「확정 전」으로도 안 잡히고
// 화면이 그냥 오류로 떨어진다. 밴드를 프론트에서 가공하면 배당·지급액이 서버와 갈린다.

function bandsResponse() {
  return {
    data: {
      auctionId: 7,
      settlementStatus: 'COMPLETED',
      bands: [
        { band: 'WITHIN_5', participantCount: 2, multiplier: 5, totalPayoutAmount: 2_500 },
        { band: 'MISS', participantCount: 8, multiplier: 0, totalPayoutAmount: 0 },
      ],
    },
  }
}

describe('getPredictionResults', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(bandsResponse())

    await getPredictionResults(7)

    expect(get.mock.calls[0][0]).toBe('/auctions/7/prediction-results')
  })

  it('응답을 가공하지 않고 그대로 돌려준다 — 배당·지급액은 전부 서버 값이다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(bandsResponse())

    expect(await getPredictionResults(7)).toEqual(bandsResponse().data)
  })

  it('목 모드에서도 실 호출로 나간다 — 데모 밴드는 resultSource가 갖는다', async () => {
    // 목 분기를 되살리면 깨진다. `isMockApi()`는 기본값이 「켬」이라 분기가 있으면 http를 안 탄다.
    const get = vi.spyOn(http, 'get').mockResolvedValue(bandsResponse())

    await getPredictionResults(7)

    expect(get).toHaveBeenCalledOnce()
  })
})

// PRED-05 일일 리더보드의 **실 모드 경로와 쿼리 전달**을 잠근다 (S15P11A704-547).
//
// 목 분기를 걷어낸 함수라 되살아나면 조용히 틀린다 — 목은 항상 8명이 채워져 있어서
// 홈 티커도 마이페이지 탭도 「잘 도는 화면」으로 보인다.
// 쿼리를 흘리면 마이페이지가 커서를 잃고 제자리걸음이다 —
// 서버 기본 `size`는 10이라 홈의 `size=10`은 흘려도 티가 안 난다(그래서 더 조용히 틀린다).
//
// `rankingDate`가 **어제**인 것도 일부러다 — 기본 조회 날짜는 Asia/Seoul 어제이고
// 오늘·미래를 넘기면 400이다. 픽스처를 오늘로 바꾸면 화면 문구를 「오늘」로 되돌려도 안 걸린다.

function leaderboardResponse() {
  return {
    data: {
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
    },
  }
}

describe('getDailyLeaderboard', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('목 모드에서도 실 호출로 나간다 — baseURL 기준 상대경로다', async () => {
    // `isMockApi()`는 기본값이 「켬」이라 목 분기가 있으면 http를 안 탄다.
    const get = vi.spyOn(http, 'get').mockResolvedValue(leaderboardResponse())

    await getDailyLeaderboard()

    expect(get).toHaveBeenCalledOnce()
    expect(get.mock.calls[0][0]).toBe('/predictions/leaderboards/daily')
  })

  it('date·size·cursor를 그대로 쿼리로 넘긴다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(leaderboardResponse())

    await getDailyLeaderboard({ date: '2026-07-31', size: 10, cursor: 'c1' })

    expect(get.mock.calls[0][1]).toEqual({ params: { date: '2026-07-31', size: 10, cursor: 'c1' } })
  })

  it('응답을 가공하지 않고 그대로 돌려준다 — 순위·수익률은 전부 서버 값이다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(leaderboardResponse())

    expect(await getDailyLeaderboard()).toEqual(leaderboardResponse().data)
  })
})

// PRED-04 예측 업적의 **경로 앵커**만 잠근다 (S15P11A704-561).
//
// 경로가 어긋나면 404가 나는데 화면은 업적 실패를 조용히 접으므로 「아직 뱃지가 없다」와 구분이 안 된다 —
// 눈으로는 영영 안 잡히는 종류다. 등급·뱃지 표시명을 서버 값 그대로 쓰는지는
// `LeaderboardPanel.spec.ts`가 화면 쪽에서 본다.

function achievementsResponse() {
  return {
    data: {
      memberId: 3,
      predictionCount: 12,
      hitCount: 5,
      hitRate: 41.7,
      totalStakedPoints: 12_000,
      totalRewardPoints: 18_000,
      grade: { code: 'ORACLE_2', name: '시세 예언자 II', score: 420 },
      badges: [{ badgeCode: 'FIRST_HIT', name: '첫 적중', earnedAt: '2026-07-20T12:00:00+09:00' }],
    },
  }
}

describe('getMyPredictionAchievements', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('쿼리 없이 baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(achievementsResponse())

    await getMyPredictionAchievements()

    expect(get.mock.calls[0][0]).toBe('/members/me/prediction-achievements')
    expect(get.mock.calls[0][1]).toBeUndefined()
  })
})
