// MEM-01 카카오 로그인 왕복에서 나올 수 있는 오류 → 사용자 문구.
//
// 두 갈래가 있고 **전달 경로가 다르다.**
//   ① 콜백 리다이렉트의 `?error=` — 카카오 인증·회원 생성 단계에서 실패. HTTP 응답이 아니라 쿼리로 온다
//   ② 코드 교환(`POST /auth/oauth2/token`)의 오류 응답 `{ code, message }`
//
// 값 목록의 근거는 명세 두 문서의 「오류 응답」 표다 — 각각 6종·5종이고,
// 백엔드 실코드(`OAuth2CallbackErrorCode` · `OAuthTokenExchangeErrorCode`)와 HTTP 상태까지 일치하는 것을 확인했다.
//   콜백:  명세 「카카오 OAuth Callback」 — "프론트는 error 값을 기준으로 실패 화면 또는 재시도 안내를 표시한다"
//   교환:  명세 「OAuth 로그인 코드 교환」
//
// 서버도 한국어 `message`를 주지만 그대로 쓰지 않는다 — 서버 문구는 "탈퇴한 회원은 로그인할 수 없습니다"처럼
// 원인만 알려주고 **다음 행동을 알려주지 않는다.** 여기서는 사용자가 뭘 하면 되는지까지 적는다.
// 모르는 코드는 서버 `message`로 폴백한다(서버가 값을 늘려도 빈 화면이 되지 않게).

/** 콜백 리다이렉트 `?error=` 값 (OAuth2CallbackErrorCode) */
const CALLBACK_MESSAGE: Record<string, string> = {
  INVALID_OAUTH_STATE: '인증이 만료됐어요. 다시 시도해 주세요.',
  OAUTH_ACCESS_DENIED: '카카오 로그인 동의가 취소됐어요.',
  OAUTH_AUTHENTICATION_FAILED: '카카오 인증에 실패했어요. 다시 시도해 주세요.',
  SOCIAL_ACCOUNT_CONFLICT: '이미 다른 계정에 연결된 카카오 계정이에요.',
  OAUTH_MEMBER_CREATION_FAILED: '회원 정보를 만들지 못했어요. 잠시 후 다시 시도해 주세요.',
  OAUTH_PROVIDER_UNAVAILABLE: '카카오 로그인을 일시적으로 사용할 수 없어요.',
}

/** 코드 교환 오류 (OAuthTokenExchangeErrorCode) */
const EXCHANGE_MESSAGE: Record<string, string> = {
  // 코드는 1회용이라 새로고침·뒤로가기로 같은 코드를 다시 쓰면 여기로 온다 (409)
  LOGIN_CODE_ALREADY_USED: '이미 사용된 로그인이에요. 다시 로그인해 주세요.',
  LOGIN_CODE_EXPIRED: '로그인 유효 시간이 지났어요. 다시 로그인해 주세요.',
  INVALID_LOGIN_CODE: '로그인 정보가 올바르지 않아요. 다시 시도해 주세요.',
  MEMBER_NOT_FOUND: '회원 정보를 찾을 수 없어요. 다시 로그인해 주세요.',
  // 탈퇴 회원은 교환 단계에서 막힌다 — 재시도해도 소용없으므로 문구를 다르게 준다
  MEMBER_NOT_AVAILABLE: '탈퇴한 계정이라 로그인할 수 없어요.',
}

const FALLBACK = '로그인에 실패했어요. 다시 시도해 주세요.'

export function callbackErrorMessage(code: string): string {
  return CALLBACK_MESSAGE[code] ?? FALLBACK
}

/**
 * @param code   서버 오류 코드. 없으면(네트워크 오류 등) null
 * @param serverMessage 서버가 준 문구 — 모르는 코드일 때만 쓴다
 */
export function exchangeErrorMessage(code: string | null, serverMessage?: string): string {
  if (code !== null && code in EXCHANGE_MESSAGE) return EXCHANGE_MESSAGE[code]
  return serverMessage?.trim() || FALLBACK
}

/**
 * 다시 로그인해서 풀리는 오류인지. `false`면 재시도 버튼을 보여줄 이유가 없다 —
 * 탈퇴 계정은 몇 번을 눌러도 같은 결과다.
 */
export function isRetryableLoginError(code: string | null): boolean {
  return code !== 'MEMBER_NOT_AVAILABLE' && code !== 'SOCIAL_ACCOUNT_CONFLICT'
}
