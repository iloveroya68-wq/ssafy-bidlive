// API 에러 정규화 (S15P11A704-58)
//
// 화면이 `axios.isAxiosError()` + `err.response?.data?.code` 체이닝을 반복하지 않도록
// 이 레이어에서 한 번만 정규화한다. `http.ts`의 응답 인터셉터가 모든 실패를 ApiError로 바꿔 reject한다.
//
// 근거: 「최종(종합) 명세서」 각 엔드포인트 문서의 "오류 응답" 표 (HTTP + 코드 문자열)

import axios from 'axios'

/**
 * 명세서에 실제로 적혀 있는 에러 코드.
 * **닫힌 목록이 아니다** — 78개 문서에 흩어져 있고 계속 늘어나므로 `ApiErrorCode`는 string으로 두고,
 * 여기 있는 값은 화면 분기용 상수로만 쓴다.
 */
export const API_ERROR_CODE = {
  // ── FE가 붙이는 코드 (서버 코드가 아니다) ──
  /** 응답 자체가 없음 (오프라인·CORS·서버 다운) */
  NETWORK_ERROR: 'NETWORK_ERROR',
  /** axios timeout */
  TIMEOUT: 'TIMEOUT',
  /** 분류 실패 */
  UNKNOWN: 'UNKNOWN',
  /** 401 이후 refresh까지 실패해 세션이 끊어졌다 */
  AUTH_REFRESH_FAILED: 'AUTH_REFRESH_FAILED',
  /**
   * 미디어 토큰 응답이 약속된 `{ type, data }` 봉투가 아니다 (S15P11A704-521).
   * **서버 계약이 바뀌었다는 뜻**이라 네트워크 실패·401과 구분해 둔다 — 섞이면 로그에서
   * 「연결이 안 된다」로만 보이고, 실제로 봐야 할 것은 응답 모양이다.
   */
  MEDIA_TOKEN_SHAPE_UNEXPECTED: 'MEDIA_TOKEN_SHAPE_UNEXPECTED',
  /**
   * API 자리에서 200 `text/html`이 왔다 — nginx SPA 폴백이 index.html을 돌려준 것이다
   * (S15P11A704-671). 그냥 두면 화면이 HTML을 데이터로 받아 「로딩 성공 + 빈 화면」이 된다.
   */
  SPA_FALLBACK_HTML: 'SPA_FALLBACK_HTML',

  // ── 인증·회원 (MEM-01·MEM-02) ──
  UNAUTHORIZED: 'UNAUTHORIZED',
  REFRESH_TOKEN_MISSING: 'REFRESH_TOKEN_MISSING',
  REFRESH_TOKEN_INVALID: 'REFRESH_TOKEN_INVALID',
  REFRESH_TOKEN_EXPIRED: 'REFRESH_TOKEN_EXPIRED',
  /**
   * ⚠️ **같은 「쓸 수 없는 회원 403」을 서버가 엔드포인트마다 다른 철자로 보낸다.** 둘 다 필요하다 —
   * 코드로 분기할 때 어느 API의 응답인지 보고 골라야 한다 (S15P11A704-536에서 BE 통일을 요청 중).
   *
   * - `MEMBER_UNAVAILABLE` — `POST /auth/refresh` (`AccessTokenRefreshErrorCode`).
   *   바로 위 `REFRESH_TOKEN_*` 셋과 함께 그 enum 네 값을 그대로 옮긴 자리다.
   * - `MEMBER_NOT_AVAILABLE` — `GET /members/me`(`ProfileQueryErrorCode`)와
   *   로그인 교환(`OAuthTokenExchangeErrorCode`). `features/auth/oauthErrors.ts`가 쓰는 쪽이다.
   */
  MEMBER_UNAVAILABLE: 'MEMBER_UNAVAILABLE',
  MEMBER_NOT_AVAILABLE: 'MEMBER_NOT_AVAILABLE',
  MEMBER_NOT_FOUND: 'MEMBER_NOT_FOUND',
  INVALID_LOGIN_CODE: 'INVALID_LOGIN_CODE',
  LOGIN_CODE_ALREADY_USED: 'LOGIN_CODE_ALREADY_USED',
  LOGIN_CODE_EXPIRED: 'LOGIN_CODE_EXPIRED',
  ONBOARDING_NOT_COMPLETED: 'ONBOARDING_NOT_COMPLETED',
  NICKNAME_ALREADY_EXISTS: 'NICKNAME_ALREADY_EXISTS',
  INVALID_NICKNAME: 'INVALID_NICKNAME',
  EMPTY_UPDATE_REQUEST: 'EMPTY_UPDATE_REQUEST',

  // ── 약관 (MEM-02, `terms.ts`) ──
  INVALID_TERMS_PURPOSE: 'INVALID_TERMS_PURPOSE',
  ACTIVE_TERMS_NOT_FOUND: 'ACTIVE_TERMS_NOT_FOUND',
  TERMS_SERVICE_UNAVAILABLE: 'TERMS_SERVICE_UNAVAILABLE',
  INVALID_TERMS_REQUEST: 'INVALID_TERMS_REQUEST',
  DUPLICATE_TERM_ID: 'DUPLICATE_TERM_ID',
  TERM_NOT_FOUND: 'TERM_NOT_FOUND',
  /** 필수 약관을 빠뜨리고 제출했다 — 화면이 막고 있어도 재동의·개정 시점에 걸릴 수 있다 */
  REQUIRED_TERMS_NOT_AGREED: 'REQUIRED_TERMS_NOT_AGREED',
  /** 화면이 들고 있던 약관이 그사이 개정됐다 → 목록을 다시 받아야 한다 */
  TERMS_VERSION_OUTDATED: 'TERMS_VERSION_OUTDATED',
  TERM_NOT_ACTIVE: 'TERM_NOT_ACTIVE',
  TERMS_AGREEMENT_UNAVAILABLE: 'TERMS_AGREEMENT_UNAVAILABLE',

  // ── 경매방 (ROOM) ──
  AUCTION_NOT_FOUND: 'AUCTION_NOT_FOUND',
  AUCTION_ROOM_NOT_FOUND: 'AUCTION_ROOM_NOT_FOUND',
  AUCTION_DELETED: 'AUCTION_DELETED',
  INVALID_AUCTION_ID: 'INVALID_AUCTION_ID',
  INVALID_AUCTION_STATUS: 'INVALID_AUCTION_STATUS',
  INVALID_AUCTION_REQUEST: 'INVALID_AUCTION_REQUEST',
  INVALID_PRODUCT_REQUEST: 'INVALID_PRODUCT_REQUEST',
  INVALID_SCHEDULE_TIME: 'INVALID_SCHEDULE_TIME',
  INVALID_CURSOR: 'INVALID_CURSOR',
  CATEGORY_NOT_FOUND: 'CATEGORY_NOT_FOUND',
  NOT_AUCTION_SELLER: 'NOT_AUCTION_SELLER',
  AUCTION_START_NOT_ALLOWED: 'AUCTION_START_NOT_ALLOWED',
  AUCTION_ALREADY_STARTED: 'AUCTION_ALREADY_STARTED',
  AUCTION_UPDATE_NOT_ALLOWED: 'AUCTION_UPDATE_NOT_ALLOWED',
  AUCTION_HAS_PARTICIPATION: 'AUCTION_HAS_PARTICIPATION',
  AUCTION_MODERATION_BLOCKED: 'AUCTION_MODERATION_BLOCKED',
  ROOM_ACCESS_DENIED: 'ROOM_ACCESS_DENIED',
  AUCTION_STATE_UNAVAILABLE: 'AUCTION_STATE_UNAVAILABLE',

  // ── 방송 (LIVE-02) ──
  /**
   * 404 — 종료할 방송 기록(`live_broadcast`)이 없다. 카메라가 안 잡혀 송출을 **시작조차 못 한** 방이
   * 이 코드를 받는다. 나머지 셋(`NOT_BROADCAST_OWNER`·`LIVE_NOT_ACTIVE`·`MEDIA_SERVER_ERROR`)은
   * 화면이 분기하지 않고 서버 문구를 그대로 보여 주므로 여기 올리지 않았다.
   */
  LIVE_NOT_FOUND: 'LIVE_NOT_FOUND',

  // ── 입찰 (BID-06·BID-03) ──
  INVALID_BID_AMOUNT: 'INVALID_BID_AMOUNT',
  SELLER_CANNOT_BID: 'SELLER_CANNOT_BID',
  AUCTION_NOT_LIVE: 'AUCTION_NOT_LIVE',
  AUCTION_ALREADY_ENDED: 'AUCTION_ALREADY_ENDED',
  BID_BELOW_RESERVE_PRICE: 'BID_BELOW_RESERVE_PRICE',
  BID_NOT_HIGHER_THAN_PREVIOUS: 'BID_NOT_HIGHER_THAN_PREVIOUS',
  /** 이미 예측자로 참여한 방에서 입찰을 시도했다 (docs/03 §4-1 배타 참여) */
  PREDICTION_ALREADY_JOINED: 'PREDICTION_ALREADY_JOINED',
  INSUFFICIENT_AVAILABLE_BALANCE: 'INSUFFICIENT_AVAILABLE_BALANCE',
  EXTENSION_LIMIT_EXCEEDED: 'EXTENSION_LIMIT_EXCEEDED',
  EXTENSION_TOO_LATE: 'EXTENSION_TOO_LATE',

  // ── 채팅·질문 큐 (CHAT-02·CHAT-06) ──
  /** 해당 경매의 호스트(판매자)가 아니다 — 질문 큐 조회·처리, 고정 공지 등록·해제 공통 */
  NOT_AUCTION_HOST: 'NOT_AUCTION_HOST',
  QUESTION_NOT_FOUND: 'QUESTION_NOT_FOUND',
  INVALID_PAGE_SIZE: 'INVALID_PAGE_SIZE',

  // ── 예측 (PRED-02) ──
  PREDICTION_RESULT_NOT_AVAILABLE: 'PREDICTION_RESULT_NOT_AVAILABLE',

  // ── 포인트 (PNT-01·PNT-02) ──
  WALLET_NOT_FOUND: 'WALLET_NOT_FOUND',
  TRADE_WALLET_NOT_FOUND: 'TRADE_WALLET_NOT_FOUND',
  INVALID_CHARGE_AMOUNT: 'INVALID_CHARGE_AMOUNT',
  CHARGE_LIMIT_EXCEEDED: 'CHARGE_LIMIT_EXCEEDED',
  INVALID_REFUND_AMOUNT: 'INVALID_REFUND_AMOUNT',
  INSUFFICIENT_AVAILABLE_AMOUNT: 'INSUFFICIENT_AVAILABLE_AMOUNT',
  INVALID_LEDGER_FILTER: 'INVALID_LEDGER_FILTER',
  DUPLICATE_REQUEST: 'DUPLICATE_REQUEST',
} as const

export type KnownApiErrorCode = (typeof API_ERROR_CODE)[keyof typeof API_ERROR_CODE]

/** 서버 에러 코드. 목록이 닫혀 있지 않아 string으로 둔다 (알려진 값은 `API_ERROR_CODE` 참조). */
export type ApiErrorCode = string

/**
 * 서버 에러 본문의 후보 모양.
 * ⚠️ **공통 에러 응답 래퍼 규격이 명세서에 없다** — 각 문서가 "HTTP + 코드" 표만 주고
 * 실제 JSON 본문 예시는 주지 않는다. 그래서 흔한 키를 방어적으로 훑는다.
 */
interface ErrorBodyShape {
  code?: unknown
  errorCode?: unknown
  message?: unknown
  error?: unknown
  details?: unknown
}

export interface ApiErrorOptions {
  status?: number | null
  code?: ApiErrorCode
  details?: unknown
  cause?: unknown
}

/** 화면이 다루는 단일 에러 타입. HTTP 상태·서버 코드·원본을 모두 들고 있다. */
export class ApiError extends Error {
  /** HTTP 상태 코드. 응답 자체가 없었으면 null */
  readonly status: number | null
  readonly code: ApiErrorCode
  /** 서버가 준 부가 정보(필드 검증 오류 등). 규격 미확정이라 unknown */
  readonly details: unknown

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.status = options.status ?? null
    this.code = options.code ?? API_ERROR_CODE.UNKNOWN
    this.details = options.details ?? null
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** 특정 코드인지 확인 — `if (hasErrorCode(e, API_ERROR_CODE.BID_BELOW_RESERVE_PRICE))` */
export function hasErrorCode(error: unknown, ...codes: ApiErrorCode[]): boolean {
  return isApiError(error) && codes.includes(error.code)
}

function pickString(...candidates: unknown[]): string | null {
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.length > 0) return candidate
  }
  return null
}

/**
 * 무엇이 오든 ApiError로 정규화한다.
 * @param overrideCode 호출부가 아는 맥락이 서버 코드보다 우선할 때 (예: refresh 실패)
 */
export function toApiError(error: unknown, overrideCode?: ApiErrorCode): ApiError {
  if (isApiError(error)) return error

  if (axios.isAxiosError(error)) {
    const status = error.response?.status ?? null
    const body = (error.response?.data ?? null) as ErrorBodyShape | null

    let code: ApiErrorCode
    if (overrideCode) {
      code = overrideCode
    } else if (status === null) {
      code = error.code === 'ECONNABORTED' ? API_ERROR_CODE.TIMEOUT : API_ERROR_CODE.NETWORK_ERROR
    } else {
      code =
        pickString(body?.code, body?.errorCode) ??
        (status === 401 ? API_ERROR_CODE.UNAUTHORIZED : API_ERROR_CODE.UNKNOWN)
    }

    const message =
      pickString(body?.message, body?.error) ??
      (status === null ? '서버에 연결하지 못했어요' : error.message)

    return new ApiError(message, { status, code, details: body?.details ?? null, cause: error })
  }

  if (error instanceof Error) {
    return new ApiError(error.message, { code: overrideCode, cause: error })
  }

  return new ApiError('알 수 없는 오류가 발생했어요', { code: overrideCode, cause: error })
}
