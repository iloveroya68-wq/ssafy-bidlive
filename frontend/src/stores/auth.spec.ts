import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from './auth'
import { useWalletStore } from './wallet'
import * as authApi from '@/shared/api/auth'
import * as httpApi from '@/shared/api/http'
import * as membersApi from '@/shared/api/members'
import * as termsApi from '@/shared/api/terms'
import { resetWalletServer, walletHttpStub } from '@/mocks/walletServer'
import { getAccessToken, reportAuthFailure, setAccessToken } from '@/shared/api/authToken'
import { API_ERROR_CODE, ApiError } from '@/shared/api/errors'
import type { AuthMemberSummary } from '@/shared/api/auth'

// `establishSession`은 **로그아웃을 거치지 않은 재로그인**에서도 불린다.
// 401·refresh 실패로 세션이 끊긴 뒤 다시 로그인하면 이전 회원의 상태가 남은 채로 새 세션이 얹힌다.
// (223에서 `onAuthFailure` 핸들러를 붙여 그 경로도 비우게 됐지만, `establishSession` 자신이
//  비우는 것은 그대로 필요하다 — 핸들러가 안 붙는 경로가 또 생길 수 있다.) 그때 새는 값 셋은 전부 **화면상 멀쩡해 보인다**:
//   - agreements → 남의 동의 이력으로 온보딩 게이트가 통과된다
//   - terms      → `agreed`가 회원별 값이라 남의 동의 상태를 본다
//   - 지갑       → `loaded` 가드로 재조회를 건너뛰어 남의 잔액이 보인다
// 그래서 눈으로는 못 잡고 여기서 잠근다 (MR !91 리뷰).
//
// S15P11A704-223부터 Access Token이 **메모리에만** 있다. 그 결과로 두 가지가 필수가 됐다:
//   - 로그아웃이 **서버를 거쳐야** 한다 (안 그러면 Refresh 쿠키가 살아 새로고침에 도로 로그인)
//   - 401로 세션이 끊기면 회원 범위를 비워야 한다 (`onAuthFailure` 등록)

/**
 * MEM-02 약관 픽스처. 실 연동으로 목 분기를 지웠으므로(S15P11A704-504) 스펙이 직접 준다.
 * **필수 1건이 미동의**여야 `needsOnboarding` 게이트가 살아 있는 상태를 만들 수 있다.
 */
function terms(): termsApi.TermsItem[] {
  return [
    {
      termId: 1,
      code: 'SERVICE_TERMS',
      title: '서비스 이용약관',
      content: '',
      version: 'v1',
      required: true,
      agreed: false,
    },
    {
      termId: 3,
      code: 'MARKETING_CONSENT',
      title: '마케팅 수신 동의',
      content: '',
      version: 'v1',
      required: false,
      agreed: false,
    },
  ]
}

/** 세션을 세우는 경로가 전부 거치는 호출들. 스텁이 없으면 실 서버로 나간다 */
function stubSessionApis() {
  vi.spyOn(termsApi, 'getTerms').mockResolvedValue(terms())
  vi.spyOn(authApi, 'logout').mockResolvedValue(undefined)
  // `establishSession`이 지갑 범위를 비우고 화면이 다시 받는다 — 지갑도 이제 실 API다(504)
  resetWalletServer()
  vi.spyOn(httpApi.http, 'get').mockImplementation(walletHttpStub as never)
}

function member(over: Partial<AuthMemberSummary> = {}): AuthMemberSummary {
  return {
    memberId: 25,
    nickname: null,
    onboardingRequired: true,
    status: 'ACTIVE',
    role: 'USER',
    ...over,
  }
}

describe('useAuthStore.establishSession', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    stubSessionApis()
  })

  it('이전 회원의 동의 이력을 물려받지 않는다', async () => {
    const auth = useAuthStore()
    auth.agreements = [{ termsId: 1, isAgreed: true, agreedAt: null, withdrawnAt: null }]

    await auth.establishSession(member())

    // 목 약관에 `agreed: true`인 항목이 없으므로 새로 시드된 이력도 비어 있어야 한다
    expect(auth.agreements.some((a) => a.termsId === 1)).toBe(false)
  })

  it('이전 회원의 지갑을 물려받지 않는다 — loaded가 풀려 다시 조회한다', async () => {
    const auth = useAuthStore()
    const wallet = useWalletStore()
    await wallet.loadWallet()
    expect(wallet.loaded).toBe(true)

    await auth.establishSession(member())

    expect(wallet.loaded).toBe(false)
  })

  it('새 회원 정보로 갈아 끼운다', async () => {
    const auth = useAuthStore()

    await auth.establishSession(member({ memberId: 77, nickname: '준호', status: 'SUSPENDED' }))

    expect(auth.user).toMatchObject({ id: 77, nickname: '준호', status: 'SUSPENDED' })
    expect(auth.isLoggedIn).toBe(true)
  })

  it('SUSPENDED 회원은 로그인되지만 isSuspended가 선다', async () => {
    // 명세 「OAuth 로그인 코드 교환」 §회원 상태 처리 정책: SUSPENDED는 토큰을 발급하되
    // 프론트가 제한 기능을 잠근다. 값이 안 들어오면 이 판정이 조용히 통과한다.
    const auth = useAuthStore()

    await auth.establishSession(member({ nickname: '준호', status: 'SUSPENDED' }))

    expect(auth.isSuspended).toBe(true)
  })

  it('ACTIVE 회원은 잠기지 않는다', async () => {
    const auth = useAuthStore()

    await auth.establishSession(member({ nickname: '준호', status: 'ACTIVE' }))

    expect(auth.isSuspended).toBe(false)
  })

  it('약관 목록을 이 회원 기준으로 다시 받는다', async () => {
    const auth = useAuthStore()
    auth.terms = [
      {
        termId: 999,
        code: 'STALE',
        title: '이전 회원이 보던 약관',
        content: '',
        version: 'v0',
        required: true,
        agreed: true,
      },
    ]

    await auth.establishSession(member())

    expect(auth.terms.some((t) => t.termId === 999)).toBe(false)
    expect(auth.terms.length).toBeGreaterThan(0)
  })
})

describe('useAuthStore.completeOnboarding — S15P11A704-432', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubSessionApis()
  })

  it('실 API를 부른다 — 목이던 시절엔 아무 요청도 안 나가 서버 닉네임이 NULL로 남았다', async () => {
    // 화면상으로는 온보딩이 끝난 것처럼 보이고 새로고침해야 드러나는 형태라 눈으로 못 잡는다
    const spy = vi.spyOn(membersApi, 'updateMyProfile')
    const auth = useAuthStore()
    await auth.establishSession(member())

    await auth.completeOnboarding('택림')

    expect(spy).toHaveBeenCalledWith({ nickname: '택림' })
  })

  it('앞뒤 공백을 떼고 보낸다 — 서버도 trim하지만 길이 검사가 먼저다', async () => {
    const spy = vi.spyOn(membersApi, 'updateMyProfile')
    const auth = useAuthStore()
    await auth.establishSession(member())

    await auth.completeOnboarding('  택림  ')

    expect(spy).toHaveBeenCalledWith({ nickname: '택림' })
  })

  it('닉네임을 응답값으로 채운다 — 화면이 보낸 문자열이 아니라 서버가 저장한 값이 정본이다', async () => {
    vi.spyOn(membersApi, 'updateMyProfile').mockResolvedValue({
      memberId: 25,
      nickname: '서버가정규화한값',
      onboardingRequired: false,
      updatedAt: '2026-07-29T00:00:00+09:00',
    })
    const auth = useAuthStore()
    await auth.establishSession(member())

    const result = await auth.completeOnboarding('택림')

    expect(result).toBe('done')
    expect(auth.user?.nickname).toBe('서버가정규화한값')
  })

  it('409 중복은 화면 문장으로 바꾼다 — 사전 확인을 통과해도 여기서 다시 걸릴 수 있다', async () => {
    vi.spyOn(membersApi, 'updateMyProfile').mockRejectedValue(
      new ApiError('conflict', { status: 409, code: API_ERROR_CODE.NICKNAME_ALREADY_EXISTS }),
    )
    const auth = useAuthStore()
    await auth.establishSession(member())

    const result = await auth.completeOnboarding('준호')

    expect(result).toBe('failed')
    expect(auth.onboardingError).toBe('이미 사용 중인 닉네임이에요')
    expect(auth.user?.nickname).toBeNull()
  })

  it('모르는 코드는 서버 메시지를 살린다 — 뭉개면 서버가 준 설명까지 지워진다', async () => {
    vi.spyOn(membersApi, 'updateMyProfile').mockRejectedValue(
      new ApiError('점검 중이에요', { status: 503, code: 'MEMBER_SERVICE_UNAVAILABLE' }),
    )
    const auth = useAuthStore()
    await auth.establishSession(member())

    await auth.completeOnboarding('택림')

    expect(auth.onboardingError).toBe('점검 중이에요')
  })

  it('길이가 벗어나면 요청을 아예 안 보낸다', async () => {
    const spy = vi.spyOn(membersApi, 'updateMyProfile')
    const auth = useAuthStore()
    await auth.establishSession(member())

    const result = await auth.completeOnboarding('   ')

    expect(result).toBe('failed')
    expect(spy).not.toHaveBeenCalled()
  })

  it('onboardingRequired가 남아 있으면 통과시키지 않는다 — 필수 약관이 덜 됐다는 뜻이다', async () => {
    // 닉네임 저장은 성공했으므로 user.nickname은 채워진다. 게이트는 약관 쪽에서 계속 잡는다.
    vi.spyOn(membersApi, 'updateMyProfile').mockResolvedValue({
      memberId: 25,
      nickname: '택림',
      onboardingRequired: true,
      updatedAt: '2026-07-29T00:00:00+09:00',
    })
    const auth = useAuthStore()
    await auth.establishSession(member())

    const result = await auth.completeOnboarding('택림')

    expect(result).toBe('terms-required')
    expect(auth.user?.nickname).toBe('택림')
    expect(auth.needsOnboarding).toBe(true)
  })

  it('낡은 로컬 동의가 남아 pendingRequiredTerms가 0이어도 terms-required를 돌려준다', async () => {
    // 화면이 개수로 판단하면 여기서 막다른 길이 된다 — 되돌아가지 못하고 같은 문구만 반복된다.
    // `seedAgreementsFromTerms`가 더하기만 하고 낡은 동의를 지우지 않아서 실제로 생기는 상태다.
    vi.spyOn(membersApi, 'updateMyProfile').mockResolvedValue({
      memberId: 25,
      nickname: '택림',
      onboardingRequired: true,
      updatedAt: '2026-07-29T00:00:00+09:00',
    })
    const auth = useAuthStore()
    await auth.establishSession(member())
    // 서버는 아직 미동의로 보는데 로컬에만 남아 있는 동의 이력
    auth.agreements = auth.terms
      .filter((t) => t.required)
      .map((t) => ({ termsId: t.termId, isAgreed: true, agreedAt: null, withdrawnAt: null }))
    expect(auth.pendingRequiredTerms.length).toBe(0)

    const result = await auth.completeOnboarding('택림')

    expect(result).toBe('terms-required')
    expect(auth.pendingRequiredTerms.length).toBe(0)
  })
})

describe('useAuthStore.logout — S15P11A704-223', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubSessionApis()
  })

  it('서버 로그아웃을 부른다 — 로컬만 비우면 Refresh 쿠키가 살아남는다', async () => {
    // 부팅 refresh가 생긴 뒤로, 쿠키가 남으면 새로고침 한 번에 도로 로그인된다
    const spy = vi.spyOn(authApi, 'logout')
    const auth = useAuthStore()
    await auth.establishSession(member({ nickname: '준호' }))

    await auth.logout()

    expect(spy).toHaveBeenCalledTimes(1)
    expect(auth.isLoggedIn).toBe(false)
  })

  it('서버 로그아웃이 실패해도 로컬은 비운다 — "눌렀는데 그대로"가 제일 나쁘다', async () => {
    vi.spyOn(authApi, 'logout').mockRejectedValue(new Error('boom'))
    const auth = useAuthStore()
    await auth.establishSession(member({ nickname: '준호' }))

    await expect(auth.logout()).rejects.toThrow()

    expect(auth.isLoggedIn).toBe(false)
  })
})

describe('useAuthStore.restoreSession — S15P11A704-223', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubSessionApis()
  })

  // 예전에는 「목 모드에서는 서버를 부르지 않는다」를 잠갔다. 그 단락이 없어져(535) 모드와 무관하게
  // refresh를 한 번 시도하고, 쿠키가 없으면 401을 삼켜 비로그인으로 판정한다.
  it('쿠키가 없으면 비로그인으로 판정한다 — 첫 방문자의 정상 경로다', async () => {
    vi.spyOn(httpApi, 'refreshAccessToken').mockRejectedValue(
      new ApiError('no cookie', { status: 401 }),
    )
    const auth = useAuthStore()

    await auth.restoreSession()

    expect(auth.sessionRestored).toBe(true)
    expect(auth.isLoggedIn).toBe(false)
  })

  it('두 번 불러도 한 번만 판정한다 — 가드가 내비게이션마다 부른다', async () => {
    const refresh = vi
      .spyOn(httpApi, 'refreshAccessToken')
      .mockRejectedValue(new ApiError('no cookie', { status: 401 }))
    const auth = useAuthStore()

    await auth.restoreSession()
    const first = auth.sessionRestored
    await auth.restoreSession()

    expect(first).toBe(true)
    expect(auth.sessionRestored).toBe(true)
    expect(refresh).toHaveBeenCalledTimes(1) // 두 번째는 판정이 끝난 걸 보고 즉시 돌아간다
  })

  it('프로필과 약관을 동시에 받는다 — 직렬이면 왕복이 하나 더 늘어난다', async () => {
    // 회귀해도 화면은 멀쩡하고 첫 내비게이션이 느려질 뿐이라 눈으로 못 잡는다.
    // 둘은 서로 독립이고 refresh가 준 토큰만 있으면 된다.
    //
    // 판정은 **호출 순서**로 한다. 병렬이면 약관 조회가 먼저 출발하고 프로필이 나중에 끝나
    // `['terms', 'profile']`이 되지만, 직렬이면 프로필이 끝난 **뒤에야** 약관이 출발한다.
    vi.spyOn(httpApi, 'refreshAccessToken').mockResolvedValue('token')

    const order: string[] = []
    vi.spyOn(termsApi, 'getTerms').mockImplementation(async () => {
      order.push('terms')
      return []
    })
    vi.spyOn(membersApi, 'getMyProfile').mockImplementation(async () => {
      await new Promise((r) => setTimeout(r, 20))
      order.push('profile')
      return {
        memberId: 25,
        nickname: '준호',
        status: 'ACTIVE',
        role: 'USER',
      } as Awaited<ReturnType<typeof membersApi.getMyProfile>>
    })

    const auth = useAuthStore()
    await auth.restoreSession()

    expect(order).toEqual(['terms', 'profile'])
    expect(auth.user?.nickname).toBe('준호')
  })

  it('약관 조회가 실패해도 세션은 복원된다 — loadTerms는 실패를 삼킨다', async () => {
    vi.spyOn(httpApi, 'refreshAccessToken').mockResolvedValue('token')
    vi.spyOn(termsApi, 'getTerms').mockRejectedValue(new Error('boom'))
    vi.spyOn(membersApi, 'getMyProfile').mockResolvedValue({
      memberId: 25,
      nickname: '준호',
      status: 'ACTIVE',
      role: 'USER',
    } as Awaited<ReturnType<typeof membersApi.getMyProfile>>)

    const auth = useAuthStore()
    await auth.restoreSession()

    expect(auth.isLoggedIn).toBe(true)
    expect(auth.termsError).not.toBeNull()
  })

  it('겹쳐 불러도 같은 프라미스를 공유한다 — 판정 전에 두 번째 이동이 들어온다', async () => {
    // 가드가 매 내비게이션마다 부르므로, 첫 판정이 끝나기 전 두 번째가 들어오면 refresh가 두 번 나간다.
    // refresh를 **느리게** 만들어 두 호출이 실제로 겹치게 한다 — 즉시 끝나면 두 번째는 이미
    // `sessionRestored`를 보고 돌아가서 이 가드를 안 거친다.
    const refresh = vi.spyOn(httpApi, 'refreshAccessToken').mockImplementation(async () => {
      await new Promise((r) => setTimeout(r, 20))
      throw new ApiError('no cookie', { status: 401 })
    })
    const auth = useAuthStore()

    const a = auth.restoreSession()
    const b = auth.restoreSession()
    await Promise.all([a, b])

    expect(auth.sessionRestored).toBe(true)
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  // 온보딩 미완료 회원은 프로필 조회가 409로 거절된다 — 실패가 아니라 「세션은 유효하다」는 신호다.
  // 예전에는 `catch`가 전부 삼켜 비로그인이 됐고, 가드가 /onboarding을 /login으로 되돌려
  // **온보딩 중 새로고침하면 튕겼다** (S15P11A704-451).
  describe('온보딩 미완료 회원 (S15P11A704-451)', () => {
    function stubProfile(error: unknown) {
      vi.spyOn(httpApi, 'refreshAccessToken').mockResolvedValue('token')
      vi.spyOn(membersApi, 'getMyProfile').mockRejectedValue(error)
    }

    it('409면 로그인 상태로 두고 온보딩으로 보낸다', async () => {
      stubProfile(
        new ApiError('온보딩을 완료해 주세요', {
          status: 409,
          code: API_ERROR_CODE.ONBOARDING_NOT_COMPLETED,
        }),
      )
      const auth = useAuthStore()

      await auth.restoreSession()

      expect(auth.isLoggedIn).toBe(true) // 가드의 `/onboarding` 진입 조건이 이 값이다
      expect(auth.needsOnboarding).toBe(true)
      expect(auth.user?.nickname).toBeNull()
    })

    it('회원 번호는 지어내지 않는다 — 409 응답에 없다', async () => {
      stubProfile(
        new ApiError('온보딩을 완료해 주세요', {
          status: 409,
          code: API_ERROR_CODE.ONBOARDING_NOT_COMPLETED,
        }),
      )
      const auth = useAuthStore()

      await auth.restoreSession()

      expect(auth.user?.id).toBeNull()
    })

    it('403 탈퇴는 여전히 비로그인이다 — 409만 갈라낸다', async () => {
      stubProfile(
        new ApiError('탈퇴한 회원', {
          status: 403,
          code: API_ERROR_CODE.MEMBER_NOT_AVAILABLE,
        }),
      )
      const auth = useAuthStore()

      await auth.restoreSession()

      expect(auth.isLoggedIn).toBe(false)
      expect(auth.sessionRestored).toBe(true)
    })
  })
})

describe('401 세션 끊김 — S15P11A704-223', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    setAccessToken(null)
  })

  it('핸들러를 등록하면 회원 범위가 비워진다 — 안 그러면 이전 회원 상태가 그대로 남는다', async () => {
    const auth = useAuthStore()
    await auth.establishSession(member({ nickname: '준호' }))
    const unregister = auth.registerAuthFailureHandler()

    reportAuthFailure()

    expect(auth.isLoggedIn).toBe(false)
    // 토큰도 함께 지워진다 (reportAuthFailure의 계약)
    expect(getAccessToken()).toBeNull()
    unregister()
  })

  it('로그아웃 API를 다시 쏘지 않는다 — 이미 서버가 끊은 세션이라 401만 한 번 더 난다', async () => {
    const spy = vi.spyOn(authApi, 'logout')
    const auth = useAuthStore()
    await auth.establishSession(member({ nickname: '준호' }))
    const unregister = auth.registerAuthFailureHandler()

    reportAuthFailure()

    expect(spy).not.toHaveBeenCalled()
    unregister()
  })
})

describe('Access Token 보관 — S15P11A704-223', () => {
  it('223 이전 버전이 남긴 토큰을 지운다 — 안 지우면 스토리지에 영구히 남는다', async () => {
    // 브라우저에서 실제로 잡았다. 새 코드가 쓰지도 읽지도 않으니 옛 값이 그대로 방치되고,
    // 보관을 메모리로 옮긴 이유가 통째로 무효가 된다.
    globalThis.localStorage.setItem('bidlive.accessToken', 'leftover-token')

    // 모듈이 처음 로드될 때 한 번 청소한다
    vi.resetModules()
    await import('@/shared/api/authToken')

    expect(globalThis.localStorage.getItem('bidlive.accessToken')).toBeNull()
  })

  it('메모리에만 둔다 — localStorage에 흔적이 남으면 XSS로 그대로 털린다', () => {
    setAccessToken('secret-token')

    expect(getAccessToken()).toBe('secret-token')
    // 스토리지 어디에도 값이 남으면 안 된다
    const dumped = JSON.stringify({ ...globalThis.localStorage })
    expect(dumped).not.toContain('secret-token')

    setAccessToken(null)
  })
})
