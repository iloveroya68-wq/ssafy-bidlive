// BID 입찰 API (S15P11A704-59)
//
// 명세 근거
// - 금액 직접 입력 입찰: POST /api/v1/auctions/{auctionId}/bids        (BID-06, `Idempotency-Key` 헤더)
// - 판매자 방송 연장:    POST /api/v1/auctions/{auctionId}/extensions  (BID-03, 본문 없음·항상 5분)
//
// ⚠️ **`GET /api/v1/auctions/{auctionId}/bid-flow`(BID-08 「입찰 흐름 그래프」)는 폐기됐다** —
// 명세에서 폐기 확정됐다(2026-08-01). **낙찰** 결과 화면의 입찰 내역은 경매 결과 조회 응답의
// `bids[]`가 그대로 주고(`SoldAuctionResult`), 유찰 화면은 애초에 입찰 내역을 그리지 않는다
// (`NoSaleAuctionResult`에는 `bids[]`도 `reservePrice`도 없다 — `shared/api/results.ts`).
// 즉 이 API를 쓸 화면이 없었다. FE에 있던 `getBidFlow`·`BidFlowResponse`는
// 호출처가 없어 걷어냈다(S15P11A704-544).
//
// WS-03 경매 상태 스냅샷은 여기 없다 — `features/live/socket/realtimeState.ts`가 유일한 구현이다
// (S15P11A704-388). 예전에 이 파일에도 같은 타입·함수가 있었지만 아무도 쓰지 않는 사본이었다.
//
// AGENTS.md 규칙 6(비크리 비공개): 입찰 REST 응답에는 **본인 금액과 홀드 정보만** 들어온다.
// 타인 금액·최고가·2등가·순위는 종료 후 결과 조회에서만, 그것도 마스킹된 채로 온다.

import { http } from '@/shared/api/http'
import { idempotent, newRequestId } from '@/shared/api/idempotency'
import type { Id, IsoDateTime } from '@/shared/api/types'

export const AUCTIONS_PATH = '/auctions'

// ── 입찰 (BID-06) ──

export interface PlaceBidRequest {
  /** 사용자가 새로 제출하는 최종 입찰 금액. 하한가 이상 + 기존 본인 입찰가 초과여야 한다 */
  amount: number
}

export interface PlaceBidResponse {
  auctionId: Id
  myBidAmount: number
  /** 첫 입찰이면 null (상향 입찰이면 직전 금액) */
  previousBidAmount: number | null
  /** 이번 요청으로 **추가** 홀드된 금액 (상향 차액) */
  additionalHoldAmount: number
  totalHeldAmount: number
  /** 홀드 반영 후 남은 가용 거래 포인트 */
  availableBalance: number
  submittedAt: IsoDateTime
}

// ── 연장 (BID-03) ──

export interface ExtendAuctionResponse {
  auctionId: Id
  previousEndAt: IsoDateTime
  endAt: IsoDateTime
  /** `auction_room.extension_count BETWEEN 0 AND 3` */
  extensionCount: number
  remainingExtensions: number
}

// ── 마스킹된 입찰 내역 (결과 조회 응답의 `bids[]`가 쓴다) ──

/**
 * 본인 행을 가리키는 `bidderLabel` 값.
 *
 * 화면 여러 곳이 이 문자열로 "내 입찰"을 골라내고, 그중 하나는 정산 카드의 홀드 금액으로
 * 직결된다. 리터럴을 흩어 두면 서버가 표기를 바꿨을 때 타입 에러 없이 금액만 조용히 틀리므로
 * 한 곳에서만 정의한다.
 */
export const MY_BID_LABEL = '나'

export interface MaskedBidEntry {
  /** 서버가 마스킹한 라벨. 본인 행은 `MY_BID_LABEL`로 내려온다 */
  bidderLabel: string
  amount: number
  submittedAt: IsoDateTime
}

// ── 참여 역할 ──

/**
 * 재접속 후 §4-1 배타 잠금 상태를 복원하는 근거값.
 *
 * ⚠️ `shared/lib/participation.ts`에도 같은 union이 있다. 여기 것은 `shared/api/myAuctions.ts`가
 * import하고 있어 남겼다 — 어느 쪽을 단일 소스로 둘지는 별건이다.
 */
export type ParticipationType = 'NONE' | 'BID' | 'PREDICTION'

// ── 엔드포인트 ──

/**
 * BID-06 입찰 제출. **이 엔드포인트의 유일한 구현이다** (S15P11A704-395).
 *
 * 화면 문장 매핑과 배타 잠금 판정은 `features/bid/api.ts`가 갖는다 — 계약·경로·목은 여기,
 * 표현은 거기로 나눈다. 예전에는 양쪽에 제출 함수가 하나씩 있었고 화면이 쓰는 쪽만
 * 경로가 틀려서(`/api/v1/v1/...`) 실 모드에서 404였다.
 *
 * @param idempotencyKey 재시도 시 **같은 키를 다시 넘겨야** 중복 입찰이 되지 않는다.
 *                       생략하면 새 UUID를 만든다(= 최초 시도).
 * @param mockContext 목 모드 전용. 실 모드에서는 쓰이지 않는다.
 */
export async function placeBid(
  auctionId: Id,
  request: PlaceBidRequest,
  idempotencyKey: string = newRequestId(),
): Promise<PlaceBidResponse> {
  // 경로는 baseURL(`/api/v1`) 기준 상대경로다 — `/v1`을 또 붙이면 `/api/v1/v1/...`가 된다(394).
  const { data } = await http.post<PlaceBidResponse>(
    `${AUCTIONS_PATH}/${auctionId}/bids`,
    request,
    idempotent(idempotencyKey),
  )
  return data
}

/**
 * BID-03 방송 5분 연장 (판매자 전용, 최대 3회, 남은 시간 1분 이상일 때만).
 *
 * **목 분기 없이 항상 실 호출이다** (S15P11A704-548). 본문도 헤더도 없다 —
 * 연장에는 `Idempotency-Key`가 없다(명세 미확정, 입찰·예측과 다르다). 임의로 붙이지 않는다.
 * 목 시절엔 잔여가 늘 「2/3회」로 고정이라 한도·시점 판정이 화면에서 전혀 검증되지 않았다.
 */
export async function extendAuction(auctionId: Id): Promise<ExtendAuctionResponse> {
  const { data } = await http.post<ExtendAuctionResponse>(
    `${AUCTIONS_PATH}/${auctionId}/extensions`,
  )
  return data
}
