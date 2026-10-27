// DEAL-06/08 결과 화면 목 데이터 (S15P11A704-67·68·69).
//
// **백엔드 연동 전 임시 파일이다.** `shared/api/results.ts`의 목 분기는 `LOSING_BIDDER` 낙찰 한 장뿐이라
// 역할 4분기(-67)·유찰 사유 3분기(-69)를 화면에서 확인할 수 없다. 그래서 시나리오를 방 id에 매핑해 둔다.
// 값은 전부 디자인 단일 소스(`비드라이브 인터랙티브.html`의 LEDGER·NOSALE 데이터)에서 그대로 옮겼고,
// 타입은 `shared/api`의 응답 타입을 그대로 쓴다 — **명세에 없는 필드를 지어내지 않는다.**
//
// ⚠️ 역할은 서버가 판정하는 사실이라 쿼리로 지정할 수 없다(docs/02 「화면 내 상태」).
// 그래서 데모용 분기도 쿼리가 아니라 **방 id**로 고른다.

import type { MaskedBidEntry } from '@/shared/api/bids'
import type { PredictionResultsResponse } from '@/shared/api/predictions'
import type {
  AuctionResultResponse,
  NoSaleAuctionResult,
  NoSaleReason,
  ResultViewerRole,
  SoldAuctionResult,
} from '@/shared/api/results'

/** 와이어프레임 LEDGER — 전원 마스킹. 본인 행만 서버가 `"나"`로 바꿔 내려준다 */
function ledger(myRank: 1 | 2 | 3 | 4 | null): MaskedBidEntry[] {
  const rows: Array<Omit<MaskedBidEntry, 'bidderLabel'> & { label: string }> = [
    { label: '낙찰자 ●●3', amount: 82_000, submittedAt: '2026-07-21T21:12:44+09:00' },
    { label: '참여자 ●●7', amount: 74_000, submittedAt: '2026-07-21T21:11:02+09:00' },
    { label: '참여자 ●●3', amount: 61_000, submittedAt: '2026-07-21T21:09:38+09:00' },
    { label: '참여자 ●●1', amount: 55_000, submittedAt: '2026-07-21T21:07:15+09:00' },
  ]
  return rows.map((row, index) => ({
    bidderLabel: myRank !== null && index + 1 === myRank ? '나' : row.label,
    amount: row.amount,
    submittedAt: row.submittedAt,
  }))
}

const FINAL_PRICE = 74_000

/**
 * 역할별 정산. 개정된 응답은 입찰·예측 정산을 **두 필드로 분리**하고 배타 참여(규칙 7)를
 * 구조로 표현한다 — 한 응답에서 둘 중 하나만 non-null이고 관전자는 둘 다 null이다.
 */
function soldResult(auctionId: number, viewerRole: ResultViewerRole): SoldAuctionResult {
  const base = {
    auctionId,
    resultType: 'SOLD' as const,
    viewerRole,
    winnerLabel: '낙찰자 ●●3',
    finalPrice: FINAL_PRICE,
    priceDecisionType: 'SECOND_PRICE' as const,
    // 예측 밴드는 PRED-02로 따로 조회한다 — 결과 응답의 이 필드는 정산 전이면 빈 배열이다
    predictionBands: [],
  }

  if (viewerRole === 'WINNING_BIDDER') {
    return {
      ...base,
      // 82,000P를 홀드했지만 2등가 74,000P만 지불하고 차액 8,000P는 즉시 복구된다 (BID-07)
      myBidSettlement: {
        result: 'WON',
        submittedAmount: 82_000,
        paymentAmount: FINAL_PRICE,
        releasedAmount: 8_000,
      },
      myPredictionSettlement: null,
      bids: ledger(1),
    }
  }
  if (viewerRole === 'LOSING_BIDDER') {
    return {
      ...base,
      myBidSettlement: { result: 'LOST', submittedAmount: 61_000, releasedAmount: 61_000 },
      myPredictionSettlement: null,
      bids: ledger(3),
    }
  }
  if (viewerRole === 'PREDICTOR') {
    // 예측 72,000 vs 낙찰 74,000 → 오차 2.7% → 5% 이내 ×5 → 500 × 5 = 2,500 (PRED-03)
    return {
      ...base,
      myBidSettlement: null,
      myPredictionSettlement: {
        predictionId: 701,
        predictedPrice: 72_000,
        stakeAmount: 500,
        result: 'HIT',
        errorBand: 'WITHIN_5',
        multiplier: 5,
        payoutAmount: 2_500,
      },
      bids: ledger(null),
    }
  }
  // 판매자·관전자는 개인 정산이 없고 원장에도 내 행이 없다
  return { ...base, myBidSettlement: null, myPredictionSettlement: null, bids: ledger(null) }
}

/**
 * 와이어프레임 NOSALE 맵 — 정산 결과는 셋 다 같고 "왜 끝났는지"와 그 뒤 안내만 갈린다.
 *
 * 개정 전 `holdReleaseAmount`·`predictionRefundAmount`는 설명이 "총액"인데 화면은 1인칭이라
 * 개인 값인지 방 전체 합인지 모호했다. 개정된 응답은 두 필드를 없애고 개인 정산 두 필드로
 * 대체해 **모호성을 구조로 해소했다** — 배타 참여도 자연히 표현된다(둘 중 하나만 non-null).
 */
function noSaleResult(auctionId: number, reason: NoSaleReason): NoSaleAuctionResult {
  const byReason: Record<NoSaleReason, Omit<NoSaleAuctionResult, 'auctionId' | 'resultType'>> = {
    // 입찰이 한 건도 없어 유찰 — 조회자를 판매자로 둔다. 재등록 CTA가 도달 가능한 유일한 시나리오다
    // (`canRelist`는 `viewerRole === 'SELLER'`일 때만 의미가 있다).
    NO_BID: {
      viewerRole: 'SELLER',
      message: '낙찰 없이 종료됐어요',
      myBidSettlement: null,
      myPredictionSettlement: null,
      canRelist: true,
    },
    // 예측만 걸었던 참여자 관점 — 스테이크 전액 환불 카드를 확인하는 시나리오
    HOST_LEFT: {
      viewerRole: 'PREDICTOR',
      message: '판매자 이탈로 종료됐어요',
      myBidSettlement: null,
      myPredictionSettlement: { result: 'REFUNDED', stakeAmount: 300, refundAmount: 300 },
      canRelist: false,
    },
    // 제재 검토 중인 상품을 그 자리에서 다시 올리게 두면 안내와 모순된다 — 재등록 CTA를 숨긴다
    FORCED: {
      viewerRole: 'LOSING_BIDDER',
      message: '운영팀이 방송을 종료했어요',
      myBidSettlement: { result: 'LOST', submittedAmount: 45_000, releasedAmount: 45_000 },
      myPredictionSettlement: null,
      canRelist: false,
    },
  }
  return { auctionId, resultType: reason, ...byReason[reason] }
}

/**
 * 방 id → 시나리오. 목 방은 1~9다(`mocks/rooms.ts`).
 * 1~4 낙찰(역할 4분기) · 5~7 유찰 사유 3분기 · 8~9 종료된 방(관전).
 */
export function mockAuctionResult(auctionId: number): AuctionResultResponse {
  switch (auctionId) {
    case 1:
      return soldResult(auctionId, 'WINNING_BIDDER')
    case 2:
      return soldResult(auctionId, 'LOSING_BIDDER')
    case 3:
      return soldResult(auctionId, 'PREDICTOR')
    case 4:
      return soldResult(auctionId, 'SPECTATOR')
    case 5:
      return noSaleResult(auctionId, 'NO_BID')
    case 6:
      return noSaleResult(auctionId, 'HOST_LEFT')
    case 7:
      return noSaleResult(auctionId, 'FORCED')
    default:
      return soldResult(auctionId, 'SPECTATOR')
  }
}

/**
 * PRED-02 밴드 분포. 인원은 와이어프레임 값(3/8/12/27)이고
 * 적중 23명 · 지급 합계 18,400BP가 그 화면의 요약 줄과 맞는다.
 */
export function mockPredictionResults(auctionId: number): PredictionResultsResponse {
  return {
    auctionId,
    settlementStatus: 'COMPLETED',
    bands: [
      { band: 'WITHIN_5', participantCount: 3, multiplier: 5, totalPayoutAmount: 5_500 },
      { band: 'WITHIN_10', participantCount: 8, multiplier: 3, totalPayoutAmount: 8_400 },
      { band: 'WITHIN_20', participantCount: 12, multiplier: 1.5, totalPayoutAmount: 4_500 },
      { band: 'MISS', participantCount: 27, multiplier: 0, totalPayoutAmount: 0 },
    ],
  }
}
