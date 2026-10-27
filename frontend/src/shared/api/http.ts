// HTTP 클라이언트 + 인터셉터 (S15P11A704-58)
//
// AGENTS.md 규칙 8: **HTTP 호출은 이 파일의 axios 인스턴스만 사용한다.**
// 컴포넌트·스토어에서 `fetch`나 개별 `axios`를 직접 부르지 않는다.
//
// 인터셉터가 하는 일은 셋이다.
// 1. 요청: Access Token이 있으면 `Authorization: Bearer {accessToken}` 주입
// 2. 응답(성공): `text/html`이면 API가 아니라 SPA 폴백이 온 것이라 실패로 바꾼다 (S15P11A704-671)
// 3. 응답(실패): 401이면 `POST /api/v1/auth/refresh`(MEM-01)로 **1회** 재발급 후 원요청 재시도,
//    재발급까지 실패하면 토큰을 지우고 세션 종료를 알린다.

import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import {
  clearAccessToken,
  getAccessToken,
  reportAuthFailure,
  setAccessToken,
} from '@/shared/api/authToken'
import { API_ERROR_CODE, ApiError, toApiError } from '@/shared/api/errors'

/**
 * API 베이스 URL. `.env.example` 기준 `http://localhost:8080/api/v1`이고,
 * 이 레이어의 모든 경로 상수는 **이 베이스에 상대적**이다.
 * 예: `REFRESH_PATH = '/auth/refresh'` → 최종 `POST /api/v1/auth/refresh` (명세와 일치)
 *
 * ⚠️ `??`가 아니라 `||`다 — `??`는 빈 문자열을 nullish로 보지 않아 폴백이 안 걸린다
 * (`'' ?? '/api/v1'` === `''`). 빈 값 env로 배포해 사고가 났다 (S15P11A704-671).
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1'

/** MEM-01 Access Token 재발급. Request Body 없음, HttpOnly 쿠키(`refreshToken`)로만 인증한다. */
export const REFRESH_PATH = '/auth/refresh'

/** 재발급 응답 (명세 200 OK) */
export interface RefreshTokenResponse {
  tokenType: 'Bearer'
  accessToken: string
  /** 초 단위 만료 시간 (명세 예시 1800) */
  expiresIn: number
}

/** 401 재시도가 이미 일어났는지 표시하는 내부 플래그 (무한 루프 방지) */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _authRetried?: boolean
}

const COMMON_CONFIG = {
  baseURL: API_BASE_URL,
  // Refresh Token이 HttpOnly 쿠키(Path=/api/v1/auth)로 오간다 — 크로스 오리진에서도 쿠키를 실어야 한다.
  withCredentials: true,
  timeout: 15_000,
}

/** 애플리케이션 전용 인스턴스. 인터셉터가 붙어 있다. */
export const http = axios.create(COMMON_CONFIG)

/**
 * 재발급 전용 인스턴스. **인터셉터를 붙이지 않는다** —
 * 재발급 요청이 401을 받았을 때 다시 재발급을 시도하는 재귀를 구조적으로 막기 위해서다.
 */
const refreshClient = axios.create(COMMON_CONFIG)

/** 동시에 401이 여러 개 터져도 재발급은 한 번만 (single-flight) */
let inFlightRefresh: Promise<string> | null = null

/**
 * Access Token을 재발급받아 저장하고 반환한다.
 * 동시 호출은 진행 중인 요청 하나를 공유한다.
 */
export function refreshAccessToken(): Promise<string> {
  if (inFlightRefresh === null) {
    inFlightRefresh = refreshClient
      .post<RefreshTokenResponse>(REFRESH_PATH)
      .then((response) => {
        const token = response.data?.accessToken
        if (typeof token !== 'string' || token.length === 0) {
          throw new Error('재발급 응답에 accessToken이 없다')
        }
        // 만료 시각까지 같이 보관한다 — STOMP 세션은 CONNECT 때 한 번 인증하고 끝이라,
        // 라이브 소켓이 만료 전에 미리 재인증하려면 `expiresIn`이 필요하다.
        setAccessToken(token, response.data?.expiresIn)
        return token
      })
      .finally(() => {
        inFlightRefresh = null
      })
  }
  return inFlightRefresh
}

// ── 요청 인터셉터: Bearer 주입 ──
http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token !== null) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ── 응답 인터셉터: text/html 200 차단 + 401 → refresh 1회 재시도 ──
//
// 둘을 한 인터셉터에 둔 이유: 성공 핸들러에서 던진 오류는 **같은 쌍의 실패 핸들러로 가지 않아서**
// (axios는 [성공, 실패]를 쌍으로 체인한다) 401 재시도 로직과 섞이지 않는다.
http.interceptors.response.use(
  (response) => {
    // nginx SPA 폴백이 API 자리에서 index.html을 200으로 돌려준 것 (S15P11A704-671).
    // `text/html`만 정확히 본다 — 이미지·파일 등 다른 비JSON 응답은 건드리지 않는다.
    if (String(response.headers?.['content-type'] ?? '').includes('text/html')) {
      console.error(
        '[api] API 자리에서 text/html 200이 왔다 (SPA 폴백)',
        response.config?.baseURL ?? null,
        response.config?.url ?? null,
      )
      throw new ApiError('서버 응답이 올바르지 않아요. 잠시 후 다시 시도해 주세요', {
        status: response.status,
        code: API_ERROR_CODE.SPA_FALLBACK_HTML,
      })
    }
    return response
  },
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined

    const canRetry =
      config !== undefined &&
      error.response?.status === 401 &&
      config._authRetried !== true &&
      // 애초에 토큰이 없던 요청(비로그인 열람)은 재발급을 시도하지 않는다.
      // 세션이 있었다면 부팅 시 복원된 토큰이 남아 있으므로 이 조건에 걸리지 않는다.
      getAccessToken() !== null

    if (!canRetry) {
      return Promise.reject(toApiError(error))
    }

    config._authRetried = true

    let renewedToken: string
    try {
      renewedToken = await refreshAccessToken()
    } catch (refreshError) {
      // 재발급 실패 = 세션 종료. 토큰을 지우고 구독자(auth 스토어 등)에게 알린다.
      clearAccessToken()
      reportAuthFailure()
      return Promise.reject(toApiError(refreshError, API_ERROR_CODE.AUTH_REFRESH_FAILED))
    }

    config.headers.set('Authorization', `Bearer ${renewedToken}`)
    return http.request(config)
  },
)
