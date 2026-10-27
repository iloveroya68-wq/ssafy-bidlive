import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import { extractApiErrorCode, extractApiErrorStatus } from './apiError'
import { ApiError } from '@/shared/api/errors'
import { isResultNotReady } from '@/features/result/resultSource'

// `http.ts`의 인터셉터가 모든 실패를 `ApiError`로 감싸 던지는데, 이 유틸은 raw axios 모양
// (`error.response.data.code`)만 보고 있었다 — 화면에 도달하는 값에는 `response`가 없어서
// **항상 null을 돌려줬다.** 입찰 거절 사유·예측 배타 잠금·결과 확정 전 판정이 전부 일반 오류로
// 뭉개졌고, 목 모드에서는 서버 코드가 올 일이 없어 드러나지 않았다 (S15P11A704-511).

/**
 * **진짜 `AxiosError`를 만든다.** 합성 객체(`{ response: { data: { code } } }`)로 검사하면
 * axios가 항상 붙이는 자기 `code`(`ERR_BAD_REQUEST` 등)가 없어서, 덕 타이핑 버그를 놓친다 —
 * 실제로 그렇게 통과시켰다(MR !229 리뷰 1).
 */
function axiosError(status: number, data: unknown): AxiosError {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError(
    'Request failed',
    status >= 500 ? 'ERR_BAD_RESPONSE' : 'ERR_BAD_REQUEST',
    config,
    {},
    { status, statusText: '', headers: new AxiosHeaders(), config, data },
  )
}

describe('extractApiErrorCode', () => {
  it('ApiError의 코드를 읽는다 — 화면에 실제로 도달하는 모양이다', () => {
    const error = new ApiError('하한가보다 낮아요', {
      status: 409,
      code: 'BID_BELOW_RESERVE_PRICE',
    })

    expect(extractApiErrorCode(error)).toBe('BID_BELOW_RESERVE_PRICE')
  })

  it('ApiError의 기본 코드 UNKNOWN은 "코드 없음"으로 되돌린다', () => {
    // `ApiError.code`는 `options.code ?? UNKNOWN`이라 절대 null이 아니다.
    // 그대로 두면 호출부의 상태 코드 폴백이 영영 도달하지 못한다
    expect(extractApiErrorCode(new ApiError('서버에 연결하지 못했어요'))).toBeNull()
  })

  it('진짜 AxiosError에서 axios 내부 코드가 아니라 서버 코드를 읽는다', () => {
    // AxiosError는 항상 자기 code(ERR_BAD_REQUEST)를 갖는다.
    // 덕 타이핑하면 그게 먼저 잡혀 서버 코드가 가려진다
    const error = axiosError(409, { code: 'AUCTION_NOT_FOUND' })

    expect(error.code).toBe('ERR_BAD_REQUEST') // 전제 확인
    expect(extractApiErrorCode(error)).toBe('AUCTION_NOT_FOUND')
  })

  it('중첩된 error.code 래핑도 읽는다 — 서버 오류 바디 규격이 확정 전이다', () => {
    const error = axiosError(403, { error: { code: 'NOT_AUCTION_SELLER' } })

    expect(extractApiErrorCode(error)).toBe('NOT_AUCTION_SELLER')
  })

  it('코드가 없으면 null이다 — 네트워크 오류는 서버 코드가 없다', () => {
    expect(extractApiErrorCode(new Error('Network Error'))).toBeNull()
    expect(extractApiErrorCode(null)).toBeNull()
    expect(extractApiErrorCode(axiosError(500, {}))).toBeNull()
  })
})

describe('extractApiErrorStatus', () => {
  it('ApiError의 상태를 읽는다', () => {
    const error = new ApiError('아직 결과가 확정되지 않았습니다.', {
      status: 409,
      code: 'PREDICTION_RESULT_NOT_AVAILABLE',
    })

    expect(extractApiErrorStatus(error)).toBe(409)
  })

  it('raw axios 오류의 상태도 읽는다', () => {
    expect(extractApiErrorStatus(axiosError(425, {}))).toBe(425)
  })

  it('응답이 없으면 null이다', () => {
    expect(extractApiErrorStatus(new Error('boom'))).toBeNull()
    expect(extractApiErrorStatus(new ApiError('연결 실패'))).toBeNull()
  })
})

describe('isResultNotReady', () => {
  it('코드로 알아본다', () => {
    const error = new ApiError('아직', { status: 409, code: 'PREDICTION_RESULT_NOT_AVAILABLE' })

    expect(isResultNotReady(error)).toBe(true)
  })

  it('코드에 안 걸려도 409·425면 확정 전으로 본다', () => {
    // 명세 오류표에는 503 AUCTION_RESULT_UNAVAILABLE도 있고 값이 더 늘 수 있다.
    // 코드 판정과 상태 판정을 else로 묶으면 이 경로가 죽는다
    expect(isResultNotReady(new ApiError('아직', { status: 409, code: 'SOMETHING_ELSE' }))).toBe(
      true,
    )
    expect(isResultNotReady(new ApiError('아직', { status: 425 }))).toBe(true)
  })

  it('그 밖의 실패는 확정 전이 아니다 — 일반 오류 화면으로 보내야 한다', () => {
    expect(
      isResultNotReady(new ApiError('없어요', { status: 404, code: 'AUCTION_NOT_FOUND' })),
    ).toBe(false)
    expect(isResultNotReady(new Error('Network Error'))).toBe(false)
  })
})
