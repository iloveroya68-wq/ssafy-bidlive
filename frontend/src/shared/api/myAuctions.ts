// MEM-02 내 경매 목록 3종 (S15P11A704-335)
//
// 명세 근거 (셋 다 확정, 전부 커서 페이지 · 기본 8 · 최대 20)
// - 낙찰 받은 경매: GET /api/v1/members/me/auctions/won
// - 개최한 경매:   GET /api/v1/members/me/auctions/hosted
// - 참여한 경매:   GET /api/v1/members/me/auctions/participated
//
// ⚠️ 세 응답은 **필드가 서로 다르다.** 공통 타입으로 합치지 않는다 —
//    합치면 대부분이 옵셔널이 되어 화면이 무엇을 그릴 수 있는지 타입으로 알 수 없다.
//    카드도 탭별로 따로 그린다(`features/auth/MyAuctionsPanel.vue`).
//
// `result`는 참여 유형으로 갈린다 — 입찰은 `WON`/`LOST`, 예측은 `HIT`/`MISS`/`REFUNDED`.
// 「경매 결과 조회」의 `myPredictionSettlement.result`도 같은 값을 쓴다(S15P11A704-323에서 통일 확정).

import { http } from '@/shared/api/http'
import type { ParticipationType } from '@/shared/api/bids'
import type { PredictionBand } from '@/shared/api/predictions'
import type { AuctionResultType, BidResultValue, PredictionResultValue } from '@/shared/api/results'
import type { CursorPage, CursorPageQuery, Id, IsoDateTime } from '@/shared/api/types'

export const MY_AUCTIONS_PATH = {
  won: '/members/me/auctions/won',
  hosted: '/members/me/auctions/hosted',
  participated: '/members/me/auctions/participated',
} as const

/** 기본 8건, 최대 20건 */
export const MY_AUCTIONS_PAGE_SIZE = 8

/** 거래 진행 상태. 값 목록이 명세 본문에 열거돼 있지 않아 좁히지 않는다 */
export type DealStatus = string

/** 낙찰 후 해야 할 일. `actionRequired`가 true일 때만 채워진다 */
export interface DealAction {
  actionRequired: boolean
  /** 예: `SUBMIT_ADDRESS`, `CONFIRM_SHIPMENT` */
  actionCode: string | null
  /** 서버가 주는 표시 문구 — 화면이 코드로 문구를 짜지 않는다 */
  actionLabel: string | null
}

/** 내가 낙찰받은 경매 */
export interface WonAuction extends DealAction {
  auctionId: Id
  dealId: Id
  productTitle: string
  imageUrl: string | null
  sellerNickname: string
  /** 비크리 — 실제 지불액(2등가) */
  finalPrice: number
  /** 내가 넣었던 입찰가. 낙찰가보다 높다 */
  myFinalBid: number
  dealStatus: DealStatus
  /** 낙찰 순간 방송 스냅샷 (LIVE-06). 없으면 null */
  snapshotUrl: string | null
  endedAt: IsoDateTime
}

/** 내가 개최한 경매 */
export interface HostedAuction extends DealAction {
  auctionId: Id
  /** 낙찰 전이거나 유찰이면 null */
  dealId: Id | null
  productTitle: string
  imageUrl: string | null
  roomStatus: string
  /** `SOLD` 등. 종료 전이면 null */
  resultType: string | null
  finalPrice: number | null
  winnerNickname: string | null
  dealStatus: DealStatus | null
  bidCount: number
  /** 입찰 참여자 수 */
  participantCount: number
  /** 예측 베팅 참여자 수 — 입찰 참여자와 다른 값이다 */
  predictionParticipantCount: number
  scheduledAt: IsoDateTime | null
  startedAt: IsoDateTime | null
  endedAt: IsoDateTime | null
}

/**
 * 참여 방식 — 한 방에서 한 사람은 둘 중 하나로만 참여한다 (AGENTS.md 규칙 7).
 * 스냅샷용 `ParticipationType`(`bids.ts`)에는 미참여(`NONE`)가 있지만,
 * **참여한 경매 목록에는 참여한 건만 오므로** 그 값을 뺀 형태다.
 */
export type JoinedParticipationType = Exclude<ParticipationType, 'NONE'>

/** 내가 참여한 경매 (입찰 또는 예측) */
export interface ParticipatedAuction {
  auctionId: Id
  participationType: JoinedParticipationType
  productTitle: string
  imageUrl: string | null
  roomStatus: string
  /** `SOLD` · `NO_BID` · `HOST_LEFT` · `FORCED`. 종료 전이면 null */
  resultType: AuctionResultType | null
  /**
   * 내 결과. `participationType`으로 값 집합이 갈린다 —
   * `BID`면 `WON`/`LOST`, `PREDICTION`이면 `HIT`/`MISS`/`REFUNDED`.
   * 진행 중인 참여 내역이면 null이다.
   */
  result: BidResultValue | PredictionResultValue | null
  /** 서버가 주는 표시 문구 (예: "미낙찰", "예측 적중"). 화면이 코드로 문구를 짜지 않는다 */
  resultLabel: string | null
  finalPrice: number | null
  // ── 입찰 참여일 때 ──
  myFinalBid: number | null
  holdReleasedAmount: number | null
  // ── 예측 참여일 때 ──
  predictedPrice: number | null
  /** 오차율(%) — 이미 100을 곱한 값이다 (예: 2.70) */
  errorRate: number | null
  /** 환불·결과 미확정이면 null */
  errorBand: PredictionBand | null
  stakeAmount: number | null
  /** 미적중이면 `0` */
  payoutAmount: number | null
  /** 유찰·이탈·강제 종료로 환불된 금액. 환불이 아니면 null */
  refundAmount: number | null
  endedAt: IsoDateTime | null
}

// ── 엔드포인트 ──

/** MEM-02 낙찰 받은 경매 — 최신 종료순 */
export async function getWonAuctions(query: CursorPageQuery = {}): Promise<CursorPage<WonAuction>> {
  const { data } = await http.get<CursorPage<WonAuction>>(MY_AUCTIONS_PATH.won, { params: query })
  return data
}

/** MEM-02 개최한 경매 — 생성·예정 시각 최신순 */
export async function getHostedAuctions(
  query: CursorPageQuery = {},
): Promise<CursorPage<HostedAuction>> {
  const { data } = await http.get<CursorPage<HostedAuction>>(MY_AUCTIONS_PATH.hosted, {
    params: query,
  })
  return data
}

/** MEM-02 참여한 경매 — 입찰·예측 양쪽 */
export async function getParticipatedAuctions(
  query: CursorPageQuery = {},
): Promise<CursorPage<ParticipatedAuction>> {
  const { data } = await http.get<CursorPage<ParticipatedAuction>>(MY_AUCTIONS_PATH.participated, {
    params: query,
  })
  return data
}
