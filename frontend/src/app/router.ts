import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // `/`는 세션에 따라 landing(비로그인)/main(로그인)이 된다 — 경로를 나누지 않는다 (docs/02).
    { path: '/', name: 'home', component: () => import('@/features/home/HomeGate.vue') },
    {
      // 전체 방 목록. 이름 `room-list`는 기존 호출부(features/live 등)가 쓰므로 유지한다.
      path: '/rooms',
      name: 'room-list',
      component: () => import('@/features/room/RoomListView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/LoginView.vue'),
      meta: { chrome: false },
    },
    {
      // MEM-01 회원가입 — login과 별도 화면이다 (docs/02 라우트 표).
      path: '/signup',
      name: 'signup',
      component: () => import('@/features/auth/SignupView.vue'),
      meta: { chrome: false },
    },
    {
      // MEM-01 카카오 인증 복귀 지점. **경로를 임의로 바꾸지 않는다** —
      // 명세 「카카오 OAuth Callback」이 `{frontendBaseUrl}/oauth/callback`으로 못박았고
      // 서버 설정(`app.oauth2.frontend-callback-uri`)도 그 값이다. 여기 없으면 로그인이 돌아올 곳을 잃는다.
      path: '/oauth/callback',
      name: 'oauth-callback',
      component: () => import('@/features/auth/OAuthCallbackView.vue'),
      meta: { chrome: false },
    },
    {
      // MEM 온보딩 — 닉네임(nullable)·필수 약관 동의를 받는 게이트.
      // 약관이 개정되면 로그인한 회원도 다시 여기로 온다 (docs/04 §5.1).
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('@/features/auth/OnboardingView.vue'),
      meta: { chrome: false },
    },
    {
      path: '/rooms/new',
      name: 'room-create',
      component: () => import('@/features/room/RoomCreateView.vue'),
    },
    {
      // ROOM-04 방 수정. 대기(WAITING) 상태에서만 진입시키며, 콘솔에서만 들어온다.
      path: '/rooms/:id/edit',
      name: 'room-edit',
      component: () => import('@/features/room/RoomEditView.vue'),
    },
    {
      path: '/rooms/:id/waiting',
      name: 'waiting-room',
      component: () => import('@/features/room/WaitingRoomView.vue'),
    },
    {
      path: '/rooms/:id/live',
      name: 'live',
      component: () => import('@/features/live/LiveView.vue'),
    },
    {
      // 판매자 진행 콘솔. 구매자(live)와 완전히 다른 화면이라 별도 라우트다 (docs/02).
      path: '/rooms/:id/console',
      name: 'console',
      component: () => import('@/features/console/ConsoleView.vue'),
    },
    {
      path: '/rooms/:id/result',
      name: 'result',
      component: () => import('@/features/result/ResultView.vue'),
    },
    {
      // DEAL-08 유찰·강제 종료 안내(S-12). 결과와 별도 라우트다 —
      // 낙찰자·미낙찰자가 없어 역할 분기 자체가 없고 정산 카드 구성도 다르다 (docs/02 「라우트 설계 판단」).
      // 두 화면은 서로의 결과를 받으면 상대 라우트로 replace한다.
      path: '/rooms/:id/nosale',
      name: 'nosale',
      component: () => import('@/features/result/NoSaleView.vue'),
    },
    // PNT-02 원장. 표의 정본 경로는 /wallet/ledger이고 /wallet은 그리로 보낸다.
    { path: '/wallet', redirect: { name: 'wallet' } },
    {
      path: '/wallet/ledger',
      name: 'wallet',
      component: () => import('@/features/wallet/WalletView.vue'),
    },
    {
      path: '/wallet/charge',
      name: 'wallet-charge',
      component: () => import('@/features/wallet/ChargeView.vue'),
      // 진입 직전 경로로 복귀하는 전체 화면이다. 헤더를 띄우면 지갑 칩이 이 화면 자신을 가리켜
      // `redirect`가 한 겹씩 쌓인다(취소해도 제자리). 이탈은 화면 안의 취소 버튼이 담당한다.
      meta: { chrome: false },
    },
    {
      path: '/wallet/withdraw',
      name: 'wallet-withdraw',
      component: () => import('@/features/wallet/WithdrawView.vue'),
      // 충전과 같은 이유로 헤더를 띄우지 않는다 — 진입 직전 경로로 복귀하는 전체 화면이다
      meta: { chrome: false },
    },
    {
      path: '/me',
      name: 'my-page',
      component: () => import('@/features/auth/MyPageView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // 운영자 콘솔. **라우터 가드를 두지 않는다** — 서버가 403을 주므로 화면이 그걸 그린다.
      // 가드를 두면 role 판정이 서버와 라우터 두 곳에 생기고, 둘이 어긋나면 조용히 갈린다.
      path: '/admin',
      name: 'admin',
      component: () => import('@/features/admin/AdminView.vue'),
    },
  ],
})

// MEM 온보딩 가드.
// `member.nickname`이 nullable이고 필수 약관 동의가 별도 테이블이라, "로그인은 됐지만 온보딩 미완료"
// 상태가 정상적으로 존재한다. 그 상태에서는 /onboarding에 붙잡는다.
// 로그아웃 상태는 아무것도 막지 않는다 — 목 단계에서 비로그인 열람을 유지하기 위해서다.
router.beforeEach(async (to) => {
  // 슬라이스 3 이전 경로 보존 — 회원가입은 이제 `/signup`이 정본이다.
  // 북마크·외부 링크로 들어오는 `/login?mode=signup`을 조용히 옮겨 준다.
  if (to.path === '/login' && to.query.mode === 'signup') {
    const query = { ...to.query }
    delete query.mode
    return { path: '/signup', query }
  }

  const auth = useAuthStore()

  // **아래 판정은 전부 "로그인했는가"에 달려 있다.** Access Token이 메모리에만 있어서
  // (S15P11A704-223) 새로고침 직후에는 그걸 모른다 — 기다리지 않으면 로그인한 사용자를
  // 매번 온보딩·로그인으로 튕긴다. 마운트를 막는 대신 여기서 기다려 앱 셸은 먼저 띄운다.
  //
  // `/oauth/callback`은 예외다. **일회용 로그인 코드를 교환하는 자리**라 왕복을 하나
  // 더 끼우면 그만큼 늦어지고, 애초에 되살릴 세션이 없어서 401 한 번을 버리는 셈이다.
  if (to.path !== '/oauth/callback') {
    await auth.restoreSession()
  }
  // 본인 데이터만 있는 화면은 비로그인이면 홈으로 돌려보낸다.
  // 로그아웃 직후 **뒤로가기**가 이 경로를 되살리는 것을 막는 것이 주목적이다 —
  // 컴포넌트는 다시 마운트되지만 껍데기(빈 닉네임 + 조회 401)라 "로그인된 것처럼" 보인다.
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'home' }
  }
  if (to.path === '/onboarding' && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.query.redirect ?? '/' } }
  }
  // `/oauth/callback`도 예외다. 게이트가 여기서 튕기면 **화면이 뜨기 전에** 나가므로
  // 일회용 로그인 코드를 교환하지 못한 채 태워 버린다(재시도하면 409 LOGIN_CODE_ALREADY_USED).
  // 온보딩 판정은 콜백이 세션을 만든 뒤 그 화면이 직접 한다.
  if (
    auth.needsOnboarding &&
    to.path !== '/onboarding' &&
    to.path !== '/login' &&
    to.path !== '/signup' &&
    to.path !== '/oauth/callback'
  ) {
    return { path: '/onboarding', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
