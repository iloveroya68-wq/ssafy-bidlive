import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAuctionResult, isSoldResult } from './results'
import { http } from './http'

// DEAL-06·DEAL-08 결과 조회의 **실 모드 경로와 유니온 통과**를 잠근다 (S15P11A704-518).
//
// 목 분기를 걷어낸 함수라 잘못되면 조용히 틀린다: 경로가 어긋나면 404가 「결과 확정 전」으로
// 오해되고(`resultSource.isResultNotReady`가 상태 폴백을 갖고 있다), 응답을 가공하면
// `resultType` 판별자가 깨져 화면이 낙찰/유찰을 잘못 고른다.

function soldResponse() {
  return {
    data: {
      auctionId: 7,
      resultType: 'SOLD',
      viewerRole: 'WINNING_BIDDER',
      winnerLabel: '나',
      finalPrice: 74_000,
      priceDecisionType: 'SECOND_PRICE',
      myBidSettlement: {
        result: 'WON',
        submittedAmount: 82_000,
        paymentAmount: 74_000,
        releasedAmount: 8_000,
      },
      myPredictionSettlement: null,
      bids: [{ bidderLabel: '나', amount: 82_000, submittedAt: '2026-07-29T00:00:00Z' }],
      predictionBands: [],
    },
  }
}

describe('getAuctionResult', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(soldResponse())

    await getAuctionResult(7)

    expect(get.mock.calls[0][0]).toBe('/auction-rooms/7/result')
  })

  it('응답을 가공하지 않고 그대로 돌려준다 — 마스킹·정산은 전부 서버 값이다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(soldResponse())

    const result = await getAuctionResult(7)

    expect(result).toEqual(soldResponse().data)
    expect(isSoldResult(result)).toBe(true)
  })

  it('유찰 응답은 bids·finalPrice 없이 와도 유니온이 유찰로 갈린다', async () => {
    // 서버가 `@JsonInclude(NON_NULL)`로 **없는 필드를 아예 안 보낸다** — 유찰에 finalPrice: null이
    // 섞이지 않는 것이 계약이라, 판별자만으로 분기가 서야 한다.
    vi.spyOn(http, 'get').mockResolvedValue({
      data: {
        auctionId: 9,
        resultType: 'HOST_LEFT',
        viewerRole: 'PREDICTOR',
        message: '판매자 이탈로 종료됐어요',
        myBidSettlement: null,
        myPredictionSettlement: { stakeAmount: 300, result: 'REFUNDED', refundAmount: 300 },
        canRelist: false,
      },
    })

    const result = await getAuctionResult(9)

    expect(isSoldResult(result)).toBe(false)
    expect(result.resultType).toBe('HOST_LEFT')
  })
})
