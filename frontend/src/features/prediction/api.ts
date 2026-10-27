// PRED-01 낙찰가 예측 베팅 접수 — **화면 표현 계층** (S15P11A704-395)
//
// 제출 함수·요청/응답 타입·목은 `shared/api/predictions.ts`의 `submitPrediction`이 갖는다.
// 여기 남은 것은 화면에만 필요한 것 둘뿐이다: 서버 오류 코드 → 문장 매핑, 배타 잠금 신호 판정.
//
// 신 설계 기준이다 — 구간(bucket)·상한·프리셋이 없고, 금액을 직접 입력한다(AGENTS.md 규칙 7).
// 진행 중에는 타인의 예측 정보나 분포를 서버가 반환하지 않으므로 분포 UI 자체가 성립하지 않는다.

import { extractApiErrorCode } from '@/shared/lib/apiError'

// PRED-01 「낙찰가 예측 베팅 접수」의 오류 코드 표 12개를 그대로 옮긴 것.
// 이전에는 표가 없어 BID-06 작명 규칙으로 추측했는데, 표가 신설되며 5개가 전부 다른 이름으로
// 확정됐다(S15P11A704-322). 추측했던 이름은 남기지 않는다 — 서버가 보내지 않는 코드다.
const PREDICTION_ERROR_MESSAGE: Record<string, string> = {
  INVALID_PREDICTED_PRICE: '예상 낙찰가를 다시 확인해 주세요',
  INVALID_STAKE_AMOUNT: '스테이크는 0보다 커야 해요',
  INVALID_CLIENT_REQUEST_ID: '요청 형식이 잘못됐어요 — 잠시 후 다시 시도해 주세요',
  UNAUTHORIZED: '로그인이 필요해요',
  SELLER_CANNOT_PREDICT: '판매자는 자기 방에 베팅할 수 없어요',
  BIDDER_CANNOT_PREDICT: '이 방에서 입찰에 참여해서 예측 베팅을 할 수 없어요',
  AUCTION_NOT_FOUND: '경매를 찾을 수 없어요',
  AUCTION_NOT_LIVE: '지금은 베팅할 수 있는 상태가 아니에요',
  PREDICTION_CLOSED: '예측 접수가 마감됐어요 — 마감 판정은 서버 시각 기준이에요',
  PREDICTION_ALREADY_EXISTS: '이 방에서는 이미 예측 베팅을 했어요 (방당 1회)',
  INSUFFICIENT_BETTING_POINTS: '보유 베팅 포인트가 부족해요',
  IDEMPOTENCY_KEY_REUSED: '같은 요청이 다른 내용으로 재전송됐어요 — 새로고침 후 다시 시도해 주세요',
}

export function predictionErrorMessage(error: unknown): string {
  const code = extractApiErrorCode(error)
  if (code && code in PREDICTION_ERROR_MESSAGE) return PREDICTION_ERROR_MESSAGE[code]
  return '예측 베팅을 접수하지 못했어요 — 잠시 후 다시 시도해 주세요'
}

/**
 * 서버가 "이미 입찰에 참여했다"고 거절한 경우 — 화면 잠금을 서버 판정으로 되돌리는 신호.
 * 실제 코드는 `BIDDER_CANNOT_PREDICT`다(이전 구현의 `BID_ALREADY_JOINED`는 추측한 이름이라
 * 서버 응답과 매칭되지 않았고, 그래서 거절을 받고도 잠금이 걸리지 않았다).
 */
export function isBidAlreadyJoined(error: unknown): boolean {
  return extractApiErrorCode(error) === 'BIDDER_CANNOT_PREDICT'
}
