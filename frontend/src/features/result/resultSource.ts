// 결과 화면 데이터 소스 (S15P11A704-67·68·69).
//
// HTTP는 전부 `shared/api`의 함수를 거친다 (AGENTS.md 규칙 8) — 여기서 fetch/axios를 직접 부르지 않는다.
// 이 모듈이 하는 일은 둘뿐이다.
//   ① 목 모드일 때 `mocks/resultFixtures.ts`의 시나리오를 대신 돌려준다 — 역할 4분기(-67)·
//      유찰 사유 3분기(-69)를 실제 경매를 끝내지 않고 확인하기 위한 데모 경로다.
//      ⚠️ **이 단락을 "목 정리"로 오해해 지우지 않는다** — `shared/api/results.ts`에는 폴백이
//      없어서(S15P11A704-518에서 목 분기를 걷어냈다) 지우는 순간 **목 모드가 실 서버를 때리고**
//      데모 시나리오가 통째로 사라진다. `resultSource.spec.ts`가 그 사실을 잠가 두었다.
//   ② "결과 확정 전"을 화면이 구분할 수 있게 오류 코드를 판정한다.

import { extractApiErrorCode, extractApiErrorStatus } from '@/shared/lib/apiError'
import { isMockApi, mockDelay } from '@/shared/api/mock'
import { getAuctionResult, type AuctionResultResponse } from '@/shared/api/results'
import { getPredictionResults, type PredictionResultsResponse } from '@/shared/api/predictions'
import { mockAuctionResult, mockPredictionResults } from '@/mocks/resultFixtures'

/** DEAL-06·08 결과 조회 */
export async function loadAuctionResult(auctionId: number): Promise<AuctionResultResponse> {
  if (isMockApi()) {
    await mockDelay()
    return mockAuctionResult(auctionId)
  }
  return getAuctionResult(auctionId)
}

/**
 * PRED-02 밴드 분포. 종료 + 예측 정산 완료 후에만 열린다.
 * 호출 계기는 **화면 진입 1회 + PRED-03 신호 수신 1회 + 사용자가 「다시 확인」을 누를 때**뿐이다
 * (S15P11A704-543) — 폴링하지 않는다.
 */
export async function loadPredictionResults(auctionId: number): Promise<PredictionResultsResponse> {
  if (isMockApi()) {
    await mockDelay(420)
    return mockPredictionResults(auctionId)
  }
  return getPredictionResults(auctionId)
}

/**
 * "아직 결과가 확정되지 않았다"인지.
 *
 * 「경매 결과 조회」에 오류 응답 표가 생겨(2026-07-25 확인) `409 PREDICTION_RESULT_NOT_AVAILABLE`로
 * 확정됐다 — PRED-02와 같은 코드다. 코드 없는 응답 대비 409/425 폴백은 남겨 둔다:
 * 해당 문서가 아직 `작성 중`이라 값이 더 늘어날 수 있다.
 */
export function isResultNotReady(error: unknown): boolean {
  // **코드와 상태를 OR로 본다.** 예전에는 코드가 있으면 상태를 안 봤는데, `ApiError.code`가
  // 기본값 `UNKNOWN`이라 그 분기가 항상 참이 되어 상태 폴백이 도달 불가였다(S15P11A704-511).
  // 명세 오류표에는 503 `AUCTION_RESULT_UNAVAILABLE`도 있고 값이 더 늘 수 있으므로,
  // 코드에 안 걸려도 409·425면 「확정 전」으로 본다.
  const code = extractApiErrorCode(error)
  const matchedByCode =
    code !== null &&
    (code.includes('NOT_AVAILABLE') || code.includes('NOT_READY') || code.includes('SETTLING'))
  const status = extractApiErrorStatus(error)
  return matchedByCode || status === 409 || status === 425
}
