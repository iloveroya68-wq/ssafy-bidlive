// MEM-01 카카오 인증 진입 — 로그인 화면과 회원가입 화면이 **똑같이** 쓰는 처리.
//
// OAuth라 두 화면의 동작이 같다: 돌아올 목적지를 세션에 맡기고 카카오로 이탈시키는 것뿐이다.
// 돌아온 뒤의 처리(세션 수립·온보딩 분기)는 콜백 화면(`/oauth/callback`) 몫이고, 신규/기존 판별은
// 서버가 카카오 계정 식별자로 한다. 화면이 둘로 갈린 뒤(슬라이스 3) 이 로직이 두 번째로 등장해 뽑았다.
//
// 목 로그인 경로는 없다 — 토큰 없는 세션을 만들어 「로그인된 것처럼 보이지만 전부 401」이 됐고,
// 되살릴 이유가 없어 지웠다 (S15P11A704-506).

import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { kakaoLoginStartUrl } from '@/shared/api/auth'
import { useAuthStore } from '@/stores/auth'
import { rememberLoginRedirect } from './loginRedirect'

export function useKakaoAuth() {
  const route = useRoute()
  const auth = useAuthStore()

  /** 인증 후 돌아갈 곳 (가드가 붙여 준 redirect 쿼리). 외부 URL로 튕기지 않게 `/`로 시작하는 값만 받는다 */
  const redirectTo = computed(() => {
    const raw = route.query.redirect
    return typeof raw === 'string' && raw.startsWith('/') ? raw : '/'
  })

  const pending = computed(() => auth.authPending)

  /**
   * bfcache(뒤로가기) 복원 대응 (S15P11A704-302).
   *
   * 실 모드 `startKakao`는 `authPending=true`로 버튼을 잠근 뒤 `window.location.assign`으로 카카오로 이탈한다.
   * 카카오에서 뒤로가기를 하면 이 페이지가 **bfcache에서 통째로 복원**되는데, 이때 자바스크립트 상태가
   * 얼어붙은 그대로 되살아나(=`onMounted`는 다시 돌지 않는다) `authPending`이 `true`로 남아 버튼이
   * "카카오 인증 중…"에 영구 잠긴다. bfcache 복원(`event.persisted`)에서만 잠금을 푼다 —
   * 일반 로드는 스토어가 새로 초기화돼 이미 `false`다.
   */
  function handlePageShow(event: PageTransitionEvent) {
    if (event.persisted && auth.authPending) auth.authPending = false
  }

  onMounted(() => {
    window.addEventListener('pageshow', handlePageShow)
    // 예전에는 여기서 목 전용으로 약관을 미리 받았다. 목 로그인이 없어졌고(506) 약관 조회는
    // 토큰이 있어야 하는 회원별 응답이라, 로그인 **전에** 부를 이유가 없다.
  })

  onUnmounted(() => {
    window.removeEventListener('pageshow', handlePageShow)
  })

  function startKakao() {
    if (auth.authPending) return

    auth.authPending = true
    // 문서를 통째로 떠나므로 라우터 상태가 남지 않는다. 돌아올 때 쓸 목적지를 세션에 맡긴다 —
    // 콜백 URL은 서버가 만들고 `code`만 실려 있어서 쿼리로는 못 넘긴다 (S15P11A704-389).
    // 온보딩 분기는 돌아온 뒤 콜백 화면이 `establishSession` 결과로 판정한다.
    rememberLoginRedirect(redirectTo.value)
    window.location.assign(kakaoLoginStartUrl())
  }

  return { redirectTo, pending, startKakao }
}
