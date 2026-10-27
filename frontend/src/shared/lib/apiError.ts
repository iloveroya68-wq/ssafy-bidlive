// 서버 오류 코드 추출 유틸.
//
// 백엔드 명세(「금액 직접 입력 입찰」 등)는 오류를 HTTP 상태 + 코드 문자열로 정의하지만
// **오류 바디의 래핑 형태는 명세에 없다.** 그래서 흔한 두 모양을 모두 받아들인다:
//   { "code": "BID_BELOW_RESERVE_PRICE", "message": "..." }
//   { "error": { "code": "BID_BELOW_RESERVE_PRICE", ... } }
// 확정되면 이 함수 하나만 고치면 된다 (호출부는 코드 문자열만 본다).
//
// **axios를 직접 import하지 않는다** — AGENTS.md 규칙 8이 HTTP 호출을 `shared/api/http.ts`로
// 제한하고, 여기서는 던져진 값의 모양만 좁히면 충분하다. `isAxiosError` 대신 계약 레이어의
// `isApiError`를 쓰는 것도 같은 이유다(그쪽이 axios 의존을 이미 흡수하고 있다).

import { API_ERROR_CODE, isApiError } from '@/shared/api/errors'

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : null
}

/**
 * 서버가 준 오류 코드 문자열. 코드를 알 수 없으면 null.
 *
 * ⚠️ **먼저 `ApiError`인지 타입 가드로 좁힌다.** `shared/api/http.ts`의 인터셉터가 모든 실패를
 * `Promise.reject(toApiError(error))`로 감싸므로 화면에 도달하는 값에는 `response`가 없다 —
 * raw axios 분기만 있던 동안 이 함수는 **항상 null을 돌려주고 있었다**(S15P11A704-511).
 *
 * ⚠️ **`typeof error.code === 'string'`으로 덕 타이핑하면 안 된다.** `AxiosError`도 자기 `code`를
 * 갖는다(`ERR_BAD_REQUEST`·`ERR_BAD_RESPONSE`·`ERR_NETWORK`). 그렇게 하면 인터셉터를 거치지 않은
 * 오류에서 **서버 코드 대신 axios 내부 코드가 새어 나가** 폴백을 남긴 목적 자체가 무너진다.
 */
export function extractApiErrorCode(error: unknown): string | null {
  if (isApiError(error)) {
    // `ApiError.code`는 기본값이 `UNKNOWN`이라 **절대 null이 아니다**.
    // 호출부가 "코드 없음"을 판별할 수 있게 여기서 되돌린다 — 안 그러면 상태 코드 폴백이 죽는다.
    return error.code === API_ERROR_CODE.UNKNOWN ? null : error.code
  }

  // 인터셉터를 거치지 않은 raw axios 오류 (테스트·직접 호출 경로)
  const data = asRecord(asRecord(asRecord(error)?.response)?.data)
  if (!data) return null

  if (typeof data.code === 'string') return data.code

  const nested = asRecord(data.error)
  if (nested && typeof nested.code === 'string') return nested.code

  return null
}

/** HTTP 상태. `ApiError.status`와 raw axios의 `response.status`를 모두 받는다 */
export function extractApiErrorStatus(error: unknown): number | null {
  if (isApiError(error)) return error.status

  const response = asRecord(asRecord(error)?.response)
  return typeof response?.status === 'number' ? response.status : null
}
