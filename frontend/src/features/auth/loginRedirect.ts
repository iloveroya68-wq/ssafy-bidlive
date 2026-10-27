// 카카오 왕복 사이에 "원래 가려던 곳"을 보관한다.
//
// 실 모드 로그인은 `window.location.assign`으로 **문서를 통째로 떠난다.** 카카오 → 백엔드를 거쳐
// `/oauth/callback?code=...`로 돌아오는데, 그 URL에는 `code`만 실려 있어 목적지가 사라진다.
// 라우터 쿼리로는 왕복을 못 넘기므로(리다이렉트 주소는 서버가 정한다) 세션에 남긴다.
//
// `sessionStorage`인 이유: 탭을 닫으면 사라져야 하고, 다른 탭의 로그인과 섞이면 안 된다.

const KEY = 'bidlive.loginRedirect'

/**
 * 열린 리다이렉트 방지 — 외부 URL·프로토콜 상대 경로를 걸러낸다.
 *
 * ⚠️ `//`만 막으면 부족하다. **브라우저는 URL에서 `\`를 `/`로 정규화**하므로
 * `/\evil.com`이 `//evil.com`으로 읽혀 외부로 나간다. 두 번째 문자가 슬래시류면 전부 막는다.
 */
function isSafePath(value: string): boolean {
  if (!value.startsWith('/')) return false
  const second = value[1]
  return second !== '/' && second !== '\\'
}

export function rememberLoginRedirect(path: string): void {
  if (!isSafePath(path)) return
  try {
    sessionStorage.setItem(KEY, path)
  } catch {
    // 프라이빗 모드 등에서 막힐 수 있다. 목적지를 잃을 뿐 로그인 자체는 진행돼야 한다.
  }
}

/**
 * 보관한 목적지를 꺼내고 **비운다**. 한 번 쓰고 남기면 다음 로그인이 엉뚱한 곳으로 간다.
 * 없거나 안전하지 않으면 `/`.
 */
export function takeLoginRedirect(): string {
  try {
    const raw = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    return raw !== null && isSafePath(raw) ? raw : '/'
  } catch {
    return '/'
  }
}
