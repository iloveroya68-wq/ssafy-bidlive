// BID-06 금액 직접 입력 입찰 — **화면 표현 계층** (S15P11A704-395)
//
// 제출 함수·요청/응답 타입·목은 `shared/api/bids.ts`의 `placeBid`가 갖는다.
// 여기 남은 것은 화면에만 필요한 것 둘뿐이다: 서버 오류 코드 → 문장 매핑, 배타 잠금 신호 판정.
// 한때 이 파일에도 제출 함수가 있었고 두 벌 중 화면이 쓰는 쪽만 경로가 틀려
// 실 모드에서 404였다 — 엔드포인트당 구현은 하나로 둔다.
//
// 입찰은 **소켓이 아니라 REST**다(docs/03 §6-4). 개인 결과는 201 응답으로 즉시 돌아오고,
// 소켓으로는 집계값(BID_ACCEPTED)만 브로드캐스트된다.
// 공개 원칙: 응답에는 본인 금액·홀드만 있고 타인 금액·최고가·2등가·순위는 없다 (AGENTS.md 규칙 6).

import { extractApiErrorCode } from '@/shared/lib/apiError'

/** 명세 「오류 응답」 표의 코드 집합 */
export type BidErrorCode =
  | 'INVALID_BID_AMOUNT'
  | 'SELLER_CANNOT_BID'
  | 'AUCTION_NOT_FOUND'
  | 'AUCTION_NOT_LIVE'
  | 'AUCTION_ALREADY_ENDED'
  | 'BID_BELOW_RESERVE_PRICE'
  | 'BID_NOT_HIGHER_THAN_PREVIOUS'
  | 'PREDICTION_ALREADY_JOINED'
  | 'INSUFFICIENT_AVAILABLE_BALANCE'

/**
 * 서버 거절 사유를 화면 문장으로. 화면 사전 검증과 문장을 맞춰 두면
 * "눌렀을 때 나오는 말"과 "서버가 되돌려준 말"이 달라 보이지 않는다.
 */
const BID_ERROR_MESSAGE: Record<BidErrorCode, string> = {
  INVALID_BID_AMOUNT: '입찰 금액이 올바르지 않아요',
  SELLER_CANNOT_BID: '판매자는 자기 방에 입찰할 수 없어요',
  AUCTION_NOT_FOUND: '경매를 찾을 수 없어요',
  AUCTION_NOT_LIVE: '지금은 입찰할 수 있는 상태가 아니에요',
  AUCTION_ALREADY_ENDED: '이미 마감된 경매예요 — 마감 판정은 서버 시각 기준이에요',
  BID_BELOW_RESERVE_PRICE: '하한가 미만은 접수되지 않아요',
  BID_NOT_HIGHER_THAN_PREVIOUS: '내 이전 입찰가보다 높은 금액만 가능해요',
  PREDICTION_ALREADY_JOINED: '이 방에서 예측 베팅에 참여해서 입찰할 수 없어요',
  INSUFFICIENT_AVAILABLE_BALANCE: '가용 거래 포인트가 부족해요 — 충전 후 입찰할 수 있어요',
}

/** 서버 오류 → 화면 문장. 코드가 없으면(네트워크 등) 일반 문구로 떨어진다. */
export function bidErrorMessage(error: unknown): string {
  const code = extractApiErrorCode(error)
  if (code && code in BID_ERROR_MESSAGE) return BID_ERROR_MESSAGE[code as BidErrorCode]
  return '입찰을 접수하지 못했어요 — 잠시 후 다시 시도해 주세요'
}

/** 서버가 "이미 예측에 참여했다"고 거절한 경우 — 화면 잠금을 서버 판정으로 되돌리는 신호 */
export function isPredictionAlreadyJoined(error: unknown): boolean {
  return extractApiErrorCode(error) === 'PREDICTION_ALREADY_JOINED'
}
