// MEM-01 인증 세션 API (S15P11A704-59)
//
// 명세 근거
// - 카카오 로그인 시작:   GET  /oauth2/authorization/kakao        (Spring Security 진입점, /api/v1 하위가 아니다)
// - 카카오 Callback:      GET  /login/oauth2/code/kakao           (브라우저가 이동. 프론트 직접 호출 없음)
// - 로그인 코드 교환:     POST /api/v1/auth/oauth2/token
// - Access Token 재발급:  POST /api/v1/auth/refresh               (http.ts 인터셉터가 자동 호출)
// - 로그아웃:             POST /api/v1/auth/logout                (204 No Content)
//
// 보안 원칙(명세): Access/Refresh Token을 리다이렉트 URL에 담지 않는다.
// 프론트는 Callback이 준 `loginCode`를 **해석하지 않고** 교환 API에 그대로 넘긴다.

import { http, API_BASE_URL } from '@/shared/api/http'
import { clearAccessToken, setAccessToken } from '@/shared/api/authToken'
import type { MemberRole, MemberStatus } from '@/shared/api/members'
import type { Id } from '@/shared/api/types'

export const AUTH_PATH = {
  exchange: '/auth/oauth2/token',
  logout: '/auth/logout',
} as const

/** 카카오 인증 진입점. `/api/v1` 하위가 **아니라** 서버 루트 경로다 */
export const KAKAO_LOGIN_START_PATH = '/oauth2/authorization/kakao'

/**
 * 로그인 코드 교환 응답에 실려 오는 회원 요약.
 *
 * 명세 「OAuth 로그인 코드 교환」§회원 응답 필드와 1:1이다 —
 * *"로그인 성공 응답에는 프론트가 회원 상태와 권한을 판단할 수 있도록 `status`, `role`을 포함한다"*.
 * 덕분에 로그인 직후에는 「프로필 조회」(`GET /members/me`)를 한 번 더 돌지 않아도 판정이 선다 —
 * 새로고침 뒤 복원에서는 그쪽이 같은 두 값을 채운다(`stores/auth.ts` `restoreSession`).
 */
export interface AuthMemberSummary {
  memberId: Id
  /** 신규 회원은 닉네임이 없다 (`member.nickname` nullable) */
  nickname: string | null
  /** 닉네임 미설정 등으로 온보딩이 필요한지 — 서버가 계산해 준다(별도 컬럼 아님) */
  onboardingRequired: boolean
  /**
   * 회원 상태. **`WITHDRAWN`은 오지 않는다** — 탈퇴 회원은 교환 단계에서
   * 403 `MEMBER_NOT_AVAILABLE`로 막히므로 이 응답 자체가 없다.
   * `SUSPENDED`는 로그인이 되므로 화면이 제한 기능을 잠가야 한다.
   */
  status: MemberStatus
  role: MemberRole
}

export interface TokenResponse {
  tokenType: 'Bearer'
  accessToken: string
  /** 초 단위 만료 시간 */
  expiresIn: number
}

export interface ExchangeLoginCodeRequest {
  /** OAuth Callback이 발급한 일회용 코드. TTL이 짧고 1회만 쓸 수 있다 */
  loginCode: string
}

export interface ExchangeLoginCodeResponse extends TokenResponse {
  member: AuthMemberSummary
}

/**
 * 카카오 인증 시작 URL.
 * `API_BASE_URL`에서 `/api/v1` 접미사를 떼어 서버 오리진을 얻는다 —
 * 진입점이 `/api/v1` 하위가 아니라서 axios baseURL을 그대로 쓸 수 없기 때문이다.
 */
export function kakaoLoginStartUrl(): string {
  const origin = API_BASE_URL.replace(/\/api\/v\d+\/?$/, '')
  return `${origin}${KAKAO_LOGIN_START_PATH}`
}

/**
 * MEM-01 일회용 로그인 코드를 Access Token으로 교환한다.
 * 성공 시 Access Token을 보관소에 저장한다(이후 모든 요청에 자동 주입).
 * Refresh Token은 HttpOnly 쿠키로 내려오므로 FE가 만질 수 없다.
 */
export async function exchangeLoginCode(loginCode: string): Promise<ExchangeLoginCodeResponse> {
  const body: ExchangeLoginCodeRequest = { loginCode }
  const { data } = await http.post<ExchangeLoginCodeResponse>(AUTH_PATH.exchange, body)
  setAccessToken(data.accessToken, data.expiresIn)
  return data
}

/**
 * MEM-01 로그아웃 — 서버 세션·Refresh 쿠키를 폐기하고 보관 중인 Access Token을 지운다.
 * 명세상 이미 폐기된 세션에도 204로 멱등 응답하므로, 실패해도 로컬 토큰은 반드시 지운다.
 */
export async function logout(): Promise<void> {
  try {
    await http.post<void>(AUTH_PATH.logout)
  } finally {
    clearAccessToken()
  }
}

// 수동 재발급(앱 부팅 시 세션 복원 등)이 필요하면 `http.ts`의 `refreshAccessToken()`을 직접 쓴다.
// 여기서 재노출하지 않는 이유는 배럴(`index.ts`)에서 이름이 중복되기 때문이다.
