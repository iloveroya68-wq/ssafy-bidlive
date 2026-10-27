// Access Token 보관소 (S15P11A704-58 · 223)
//
// 명세 근거 (MEM-01):
// - `POST /api/v1/auth/oauth2/token` 응답 본문으로 Access Token을 받고, Refresh Token은 HttpOnly 쿠키다.
//   → 즉 **FE가 직접 들고 있어야 하는 토큰은 Access Token 하나뿐**이다.
// - `POST /api/v1/auth/logout` 규칙: "프론트는 응답 후 보관 중인 Access Token을 제거한다."
//
// **보관 위치: 메모리(모듈 변수)뿐이다.** 예전에는 `localStorage`에 넣어 새로고침 후에도
// 세션이 유지되게 했는데, 그러면 **XSS 하나로 토큰이 그대로 탈취된다.** 지속성은 토큰이 아니라
// HttpOnly 쿠키(Refresh Token)가 담당하고, 새로고침 시 세션은 부팅 refresh 1회로 되살린다
// (`stores/auth.ts`의 `restoreSession`). 스크립트가 읽을 수 있는 곳에는 **수명 짧은 토큰만** 둔다.
//
// 백엔드 확인 (2026-07-28, 문서가 아니라 코드로):
//   `OAuthTokenController` · `AccessTokenRefreshController` · `LogoutController` 셋 다
//   `ResponseCookie...httpOnly(true)`로 Refresh 쿠키를 내린다. 전제가 성립한다.
//
// ⚠️ 대가: **다른 탭과 세션을 공유하지 못한다.** 탭마다 부팅 refresh를 따로 돈다.
//    쿠키가 공유되므로 결과는 같지만 요청이 탭 수만큼 나간다. 토큰을 스토리지에 두는 것보다
//    이쪽이 낫다고 봤다.

let accessToken: string | null = null

/**
 * 현재 토큰의 만료 시각(epoch ms). 발급 응답의 `expiresIn`(초)을 받은 경우에만 채워진다.
 * 없으면 null = **모름**이지 만료가 아니다 — 모른다고 만료로 취급하면 멀쩡한 세션을 매번 재발급하게 된다.
 */
let accessTokenExpiresAt: number | null = null

/**
 * 223 이전 버전이 토큰을 넣어 두던 자리. **한 번은 지워 줘야 한다** —
 * 새 코드가 쓰지도 읽지도 않으니 그냥 두면 예전에 받은 토큰이 스토리지에 **영구히 남는다.**
 * 보관을 메모리로 옮긴 이유가 통째로 무효가 되는 구멍이라 마이그레이션이 필요하다.
 *
 * 브라우저에서 실제로 확인했다 — 전환 후에도 `bidlive.accessToken`이 그대로 있었다.
 * 옛 키를 쓰던 클라이언트가 다 사라지면 이 블록은 걷어낼 수 있다.
 */
const LEGACY_STORAGE_KEY = 'bidlive.accessToken'

try {
  // 사파리 프라이빗 모드 등에서는 접근 자체가 던진다 — 지울 게 없다는 뜻이므로 무시한다
  globalThis.localStorage?.removeItem(LEGACY_STORAGE_KEY)
} catch {
  /* 무시 */
}

/** 현재 Access Token. 없으면 null (비로그인, 부팅 복원 전, 또는 만료 후 정리됨) */
export function getAccessToken(): string | null {
  return accessToken
}

/**
 * 현재 Access Token의 만료 시각(epoch ms). 만료 정보 없이 저장된 토큰이면 null.
 *
 * STOMP는 CONNECT 때 한 번 인증한 세션을 계속 쓰기 때문에, 토큰이 만료되면 화면은 「연결됨」인 채
 * 발신만 조용히 막힌다. 라이브 소켓이 만료 **전에** 미리 재인증하려면 만료 시각을 알아야 한다
 * (`features/live/socket/liveSocket.ts`).
 */
export function getAccessTokenExpiresAt(): number | null {
  return accessTokenExpiresAt
}

/**
 * 로그인·재발급 성공 시 저장. null을 넣으면 삭제와 같다.
 * @param expiresInSeconds 발급 응답의 `expiresIn`(초). 생략하면 만료 시각을 「모름」으로 둔다.
 */
export function setAccessToken(token: string | null, expiresInSeconds?: number): void {
  accessToken = token
  accessTokenExpiresAt =
    token !== null && typeof expiresInSeconds === 'number' && expiresInSeconds > 0
      ? Date.now() + expiresInSeconds * 1000
      : null
}

export function clearAccessToken(): void {
  setAccessToken(null)
}

/** 세션이 끊어졌을 때 실행할 처리 (로그아웃 상태 반영·로그인 화면 이동 등) */
export type AuthFailureHandler = () => void

const authFailureHandlers = new Set<AuthFailureHandler>()

/**
 * 401 → refresh 재시도까지 실패했을 때 호출될 핸들러를 등록한다.
 * `main.ts`가 부팅 때 `auth.registerAuthFailureHandler()`로 붙인다 (S15P11A704-223).
 * 스토어가 붙이는 것은 `resetMemberScope`이지 `logout()`이 **아니다** — 이 경로는 이미 서버가
 * 세션을 끊은 뒤라 `POST /auth/logout`을 또 쏘면 401만 한 번 더 난다.
 * @returns 등록 해제 함수
 */
export function onAuthFailure(handler: AuthFailureHandler): () => void {
  authFailureHandlers.add(handler)
  return () => authFailureHandlers.delete(handler)
}

/**
 * 세션 종료를 알린다 — 토큰을 먼저 지우고 핸들러를 부른다.
 * 핸들러 하나가 던져도 나머지는 실행되어야 하므로 개별로 감싼다.
 */
export function reportAuthFailure(): void {
  clearAccessToken()
  for (const handler of authFailureHandlers) {
    try {
      handler()
    } catch (error) {
      console.error('[api] onAuthFailure 핸들러에서 예외가 발생했다', error)
    }
  }
}
