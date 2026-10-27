// DEAL-06·DEAL-08 경매 결과 조회 API (S15P11A704-59)
//
// 명세 근거: GET /api/v1/auction-rooms/{auctionId}/result
// 하나의 엔드포인트가 **낙찰(SOLD)과 유찰/강제 종료를 서로 다른 모양**으로 돌려준다.
// 그래서 `resultType`을 판별자로 하는 유니온으로 모델링하고, 화면은 `isSoldResult()`로 분기한다.
// (`docs/02`가 `/rooms/:id/result`와 `/rooms/:id/nosale` 두 화면으로 나눈 것과 대응된다.)

import { http } from '@/shared/api/http'
import { AUCTION_ROOMS_PATH } from '@/shared/api/rooms'
import type { MaskedBidEntry } from '@/shared/api/bids'
import type {
  PredictionBand,
  PredictionBandResult,
  PredictionRewardMultiplier,
} from '@/shared/api/predictions'
import type { Id } from '@/shared/api/types'

/** 유찰·강제 종료 사유 (명세 「종료 사유」 표의 3값) */
export type NoSaleReason = 'NO_BID' | 'HOST_LEFT' | 'FORCED'

/** 결과 종류. `SOLD` 외에는 전부 유찰·강제 종료 분기다 */
export type AuctionResultType = 'SOLD' | NoSaleReason

/**
 * 조회자 역할 — 명세 「경매 결과 조회」 §규칙의 5값 (S15P11A704-322에서 확정값으로 교체).
 * 이전 구현은 `WINNER` 4값이었는데 실제는 `WINNING_BIDDER`이고 `SELLER`가 하나 더 있다.
 */
export type ResultViewerRole =
  'SELLER' | 'WINNING_BIDDER' | 'LOSING_BIDDER' | 'PREDICTOR' | 'SPECTATOR'

/** 비크리 — 낙찰가가 최고가가 아니라 2등가로 결정된다 */
export type PriceDecisionType = 'SECOND_PRICE' | 'RESERVE_PRICE'

// ── 개인 정산 ──
//
// 입찰과 예측은 배타적이라(규칙 7) 한 응답에서 둘 중 하나만 non-null이다.
// 명세 §규칙: "입찰 참여자는 myBidSettlement, 예측 참여자는 myPredictionSettlement,
// 관전자는 둘 다 null". 즉 배타 참여가 응답 구조로 표현된다.

/** 입찰 결과값. 예측의 `HIT`/`MISS`와 섞어 쓰지 않는다 */
export type BidResultValue = 'WON' | 'LOST'

/** 입찰자 개인 정산 */
export interface MyBidSettlement {
  result: BidResultValue
  /** 내가 제출한 입찰가(홀드된 금액) */
  submittedAmount?: number
  /** 낙찰 시 실제 결제액. 비크리라 `submittedAmount`보다 작다(2등가) */
  paymentAmount?: number
  /** 해제된 홀드 — 미낙찰이면 전액, 낙찰이면 제출가와 결제액의 차액 */
  releasedAmount?: number
}

/**
 * 예측 결과값. **입찰의 `WON`/`LOST`와 섞어 쓰지 않는다** —
 * 명세가 "`HIT`, `MISS`는 낙찰가 예측 결과에만 사용한다"로 못박았다(S15P11A704-323에서 통일 확정).
 */
export type PredictionResultValue = 'HIT' | 'MISS' | 'REFUNDED'

/** 예측자 개인 정산 */
export interface MyPredictionSettlement {
  predictionId?: Id
  predictedPrice?: number
  stakeAmount: number
  result: PredictionResultValue
  /** 적중 밴드. `REFUNDED`면 null (미적중이면 `MISS`) */
  errorBand?: PredictionBand | null
  /** 정산 시점에 적용된 배수. 배수는 운영 정책으로 바뀔 수 있어 **서버 값을 그대로 쓴다** */
  multiplier?: PredictionRewardMultiplier | null
  /** 지급액 = `stakeAmount × multiplier`이므로 **원금이 포함된 값**이다 (PRED-03 §정산 규칙) */
  payoutAmount?: number
  /** 유찰·강제 종료로 환불된 스테이크 */
  refundAmount?: number
}

/** 낙찰 결과 */
export interface SoldAuctionResult {
  auctionId: Id
  resultType: 'SOLD'
  viewerRole: ResultViewerRole
  /** 서버가 마스킹한 낙찰자 라벨 */
  winnerLabel: string
  /** 실제 낙찰가(2등가). 최고 입찰가와 다를 수 있다 — 합쳐 표기하면 비크리의 핵심이 사라진다 */
  finalPrice: number
  priceDecisionType: PriceDecisionType
  /**
   * 낙찰 순간 방송 스냅샷 (LIVE-06). 판매자가 캡처를 못 올렸으면 **필드 자체가 없다** —
   * 서버가 null 필드를 지우고 보낸다(`AuctionResultResponse`의 `NON_NULL`).
   * MEM-02 낙찰 목록(`myAuctions.ts`의 `snapshotUrl`)과 같은 값이다.
   */
  snapshotUrl?: string | null
  /** 입찰로 참여했을 때만 값이 있다. 예측 참여자·관전자는 null */
  myBidSettlement: MyBidSettlement | null
  /** 예측으로 참여했을 때만 값이 있다. 입찰 참여자·관전자는 null */
  myPredictionSettlement: MyPredictionSettlement | null
  /** 종료 후에만 공개되는 마스킹된 전체 입찰 내역 (본인 행은 `"나"`) */
  bids: MaskedBidEntry[]
  /** 예측 정산 전이면 빈 배열 */
  predictionBands: PredictionBandResult[]
}

/** 유찰·강제 종료 결과 */
export interface NoSaleAuctionResult {
  auctionId: Id
  resultType: NoSaleReason
  viewerRole: ResultViewerRole
  /** 화면에 그대로 띄우는 안내 문구 (예: "판매자 이탈로 종료됐어요") */
  message: string
  /**
   * 개인 정산. 이전 구현의 `holdReleaseAmount`·`predictionRefundAmount`(총액)를 대체한다 —
   * 개인 값인지 방 전체 총액인지 모호했던 것이 개인 값으로 재설계되며 해소됐다(S15P11A704-266 7·8번).
   */
  myBidSettlement: MyBidSettlement | null
  myPredictionSettlement: MyPredictionSettlement | null
  /**
   * 재등록 가능 여부. §규칙상 "판매자에게만 의미가 있으며 일반 참여자에게는 false 또는 필드 생략"이라
   * 옵셔널이다. **판매자 판정에 쓰지 않는다** — 그건 `viewerRole === 'SELLER'`가 담당한다.
   */
  canRelist?: boolean
}

export type AuctionResultResponse = SoldAuctionResult | NoSaleAuctionResult

/** 판별자 가드 — 낙찰가·낙찰자 영역은 이 분기 안에서만 렌더한다 */
export function isSoldResult(result: AuctionResultResponse): result is SoldAuctionResult {
  return result.resultType === 'SOLD'
}

/**
 * DEAL-06·DEAL-08 경매 결과 조회. `AuctionResultController`(S15P11A704-295)에 붙는다.
 * 결과가 확정되기 전에는 조회할 수 없고, 마스킹은 전부 서버가 한다(AGENTS.md 규칙 6).
 *
 * 목 분기는 없다 — 서버에 컨트롤러가 있으므로 여기서 응답을 지어내면 어긋난 걸 못 잡는다
 * (S15P11A704-518). 목 모드의 역할·유찰 시나리오 데모는 `features/result/resultSource.ts`가
 * `mocks/resultFixtures.ts`로 담당한다.
 */
export async function getAuctionResult(auctionId: Id): Promise<AuctionResultResponse> {
  const { data } = await http.get<AuctionResultResponse>(
    `${AUCTION_ROOMS_PATH}/${auctionId}/result`,
  )
  return data
}
