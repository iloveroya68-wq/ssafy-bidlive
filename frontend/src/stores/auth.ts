import { defineStore } from 'pinia'
import { API_ERROR_CODE, hasErrorCode, toApiError } from '@/shared/api/errors'
import { useWalletStore } from '@/stores/wallet'
import { useHostedConsoleStore } from '@/stores/hostedConsole'
import { agreeToTerms as submitTermsAgreements, getTerms, type TermsItem } from '@/shared/api/terms'
import { logout as requestLogout, type AuthMemberSummary } from '@/shared/api/auth'
import { getMyProfile, updateMyProfile } from '@/shared/api/members'
import { refreshAccessToken } from '@/shared/api/http'
import { onAuthFailure } from '@/shared/api/authToken'
import type {
  MemberRole as ProfileMemberRole,
  MemberStatus as ProfileMemberStatus,
  UpdateMyProfileResponse,
} from '@/shared/api/members'

// 오너: FE1 (MEM)
// 스키마 정렬: `member`는 nickname / role / status / withdrawn_at 만 가진다.
// 이메일·프로필 이미지·실명 컬럼이 없으므로 회원가입·온보딩에서 그런 입력을 받지 않는다 (docs/04 §3.6).

/**
 * `member.status` — WITHDRAWN이면 `withdrawn_at`이 NOT NULL (ck_member_withdrawn_at).
 * 프로필 응답이 주는 값은 ACTIVE·SUSPENDED 둘뿐이고(`ProfileMemberStatus`), WITHDRAWN은
 * 조회가 403 `MEMBER_NOT_AVAILABLE`로 거부돼 **응답으로는 오지 않는다.**
 * 스토어는 DB 컬럼 쪽을 모델링하므로 여기서만 넓힌다.
 */
export type MemberStatus = ProfileMemberStatus | 'WITHDRAWN'
/** `member.role` — 프로필 응답이 주는 값 그대로. 관리자 화면 **노출 판단**에만 쓴다 */
export type MemberRole = ProfileMemberRole

/** `member.nickname` VARCHAR(30) UNIQUE — 전역 중복 불가 */
export const NICKNAME_MAX_LENGTH = 30

/**
 * MEM-02 「내 프로필 수정」 §오류 응답 → 화면 문장.
 *
 * 여기 없는 코드는 서버 메시지를 그대로 쓴다 — 목록이 닫혀 있지 않아서(`errors.ts` 주석)
 * 모르는 코드에 "알 수 없는 오류"를 씌우면 서버가 준 설명까지 지워진다.
 *
 * `INVALID_NICKNAME`은 **형식 오류와 금칙어가 한 코드로 뭉쳐 온다.** 둘을 갈라 보려면
 * 사전 확인 API의 `reason`(`INVALID_FORMAT`/`PROHIBITED_WORD`)을 봐야 한다 — 그건 화면 몫이다.
 */
const PROFILE_ERROR_MESSAGE: Record<string, string> = {
  [API_ERROR_CODE.NICKNAME_ALREADY_EXISTS]: '이미 사용 중인 닉네임이에요',
  [API_ERROR_CODE.INVALID_NICKNAME]: '쓸 수 없는 닉네임이에요. 한글·영문·숫자만 쓸 수 있어요',
  [API_ERROR_CODE.EMPTY_UPDATE_REQUEST]: '변경할 내용이 없어요',
  [API_ERROR_CODE.MEMBER_NOT_FOUND]: '회원 정보를 찾지 못했어요. 다시 로그인해 주세요',
}

export interface MemberProfile {
  /**
   * `member.member_id` BIGINT — 직렬화 형식 확인 전까지 number 유지 (docs/04 §6.3).
   *
   * **`null`은 「아직 모른다」다.** 온보딩 미완료 회원의 세션 복원 경로가 그렇다 — 프로필 조회가
   * 409로 거절되고 그 응답에는 `code`·`message`뿐이라 회원 번호를 알 수 없다(S15P11A704-451).
   * 지금 이 값을 읽는 화면은 없다. 생기면 null을 먼저 처리해야 한다.
   */
  id: number | null
  /**
   * `member.nickname`은 nullable이다 — 카카오 로그인 직후 닉네임 미설정 = 온보딩 미완료 상태.
   * 이 필드가 null인 동안 라우터 가드가 `/onboarding`에 붙잡는다.
   */
  nickname: string | null
  role: MemberRole
  status: MemberStatus
  /** `member.withdrawn_at` — status가 WITHDRAWN일 때만 값이 있다 */
  withdrawnAt: string | null
}

/** `member_terms_agreement` 한 행. 유효 조합은 동의 / 미동의 / 철회 세 가지뿐이다 (docs/04 §3.6). */
export interface TermsAgreement {
  termsId: number
  isAgreed: boolean
  /**
   * `agreed_at` — 동의한 적이 없으면 null.
   * 조회 응답(`TermsItem.agreed`)에서 채운 행도 null이다 — 명세가 동의 **시각**은 주지 않는다.
   */
  agreedAt: string | null
  /** `withdrawn_at` — 철회했으면 값이 있고 `is_agreed`는 FALSE다 */
  withdrawnAt: string | null
}

/** 온보딩에서 화면이 모아 넘기는 동의 결과 (약관 id → 동의 여부) */
export type TermsAgreementInput = Record<number, boolean>

/**
 * `completeOnboarding`의 결과.
 * - `done` — 게이트 통과. 원래 가려던 곳으로 보낸다
 * - `terms-required` — **닉네임 저장은 성공했고** 필수 약관만 남았다. 약관 단계로 되돌린다
 * - `failed` — 저장 자체가 실패했다. 같은 단계에 머무르며 사유를 보여 준다
 */
export type OnboardingResult = 'done' | 'terms-required' | 'failed'

/**
 * 진행 중인 약관 조회. 로그인 화면 진입(`LoginView`)과 카카오 로그인이 겹치면 같은 조회가 두 번 나가고,
 * 먼저 끝난 쪽의 실패 메시지가 나중에 성공한 목록을 덮어써 "목록은 있는데 에러도 남는" 상태가 된다.
 * `http.ts`의 refresh와 같은 방식으로 프라미스를 공유한다 — 나중에 온 호출도 **기다릴 수 있어야** 하므로
 * (`restoreSession`이 await한다) 조기 return이 아니라 같은 프라미스를 돌려준다.
 */
let inFlightTerms: Promise<void> | null = null

/**
 * 진행 중인 부팅 세션 복원. **라우터 가드가 매 내비게이션마다 부른다** —
 * 첫 판정이 끝나기 전에 두 번째 이동이 들어오면 refresh가 두 번 나간다.
 * `inFlightTerms`와 같은 방식으로 같은 프라미스를 돌려준다.
 */
let inFlightRestore: Promise<void> | null = null

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as MemberProfile | null,
    /** 시행 중인 최신 약관 목록. 필터링은 서버가 한다 (MEM-02 「약관 조회」) */
    terms: [] as TermsItem[],
    /** 내 동의 이력 (`member_terms_agreement`) */
    agreements: [] as TermsAgreement[],
    /** 약관 조회 진행 중 — 목록이 비어 있는 동안 동의 버튼을 열어 두면 안 된다 */
    termsPending: false,
    /** 약관 조회 실패 메시지. 재시도 버튼을 띄우는 근거 */
    termsError: null as string | null,
    /** 카카오 인증 진행 중 — 버튼 중복 클릭 방지 */
    authPending: false,
    /** 닉네임 중복(409) 등 온보딩 제출 실패 메시지 */
    onboardingError: null as string | null,
    /**
     * 부팅 세션 복원이 끝났는가. **Access Token이 메모리에만 있어서**(S15P11A704-223)
     * 새로고침 직후에는 로그인 여부를 알 수 없다.
     *
     * 소비처는 `restoreSession` 자신이다 — 매 내비게이션마다 가드가 부르므로
     * 이 값으로 "이미 판정이 났다"를 알아채고 즉시 돌아간다. 성공·실패 어느 쪽이든
     * 끝나면 true다(**"판정이 났다"는 뜻이지 "로그인됐다"가 아니다**).
     */
    sessionRestored: false,
  }),
  getters: {
    isLoggedIn: (state) => state.user !== null,
    /**
     * `member.status = 'SUSPENDED'` — 입찰·베팅·채팅을 잠그고 안내를 띄운다.
     * 명세 「프로필 조회」 §회원 상태·권한 정책이 이 잠금을 프론트 책임으로 명시했다.
     * UI 잠금과 별개로 서버도 매 요청에서 상태를 검사한다 — 이건 이중 방어다.
     */
    isSuspended: (state) => state.user?.status === 'SUSPENDED',
    /**
     * 탈퇴 회원. **실 API에서는 여기까지 오지 않는다** — 프로필 조회가 403 `MEMBER_NOT_AVAILABLE`로
     * 거부되므로 탈퇴는 상태가 아니라 에러 경로다. 목·데모 회원 전환용으로만 남아 있다.
     */
    isWithdrawn: (state) => state.user?.status === 'WITHDRAWN',
    /** 필수 약관 중 아직 동의가 없는 것 — 개정으로 새 버전이 생기면 여기 다시 걸린다 */
    pendingRequiredTerms(state): TermsItem[] {
      const agreedIds = new Set(state.agreements.filter((a) => a.isAgreed).map((a) => a.termsId))
      return state.terms.filter((t) => t.required && !agreedIds.has(t.termId))
    },
    /**
     * 닉네임 미설정(nullable) 또는 필수 약관 미동의 → 온보딩 게이트에 잡힌다.
     *
     * 서버도 같은 판정을 `onboardingCompleted`(약관 동의 응답)·`onboardingRequired`(프로필 수정 응답)로
     * 돌려주지만 **그 값을 이 게터에 먹이지 않는다.** 서버 플래그를 상태로 들고 있으면
     * 닉네임을 설정한 뒤에도 낡은 값이 남아 가드가 어긋난다. 서버 값은 **제출 결과 확인용**으로만 쓴다
     * (`agreeToTerms`·`completeOnboarding` 참조).
     *
     * 판정 기준이 같다는 것은 확인됐다 — 「내 프로필 수정」 §검증 규칙이
     * "`onboardingRequired=false`는 닉네임이 설정되고 현재 적용 중인 필수 온보딩 약관에
     * 모두 동의한 경우에만 반환한다"로 확정했다. S15P11A704-317 §2가 남긴 유보는 이걸로 해소됐다.
     */
    needsOnboarding(state): boolean {
      if (state.user === null) return false
      if (state.user.nickname === null) return true
      return this.pendingRequiredTerms.length > 0
    },
  },
  actions: {
    /**
     * MEM-02 약관 조회 — 동의 화면을 그리기 전에 시행 중인 최신 약관을 받아 둔다.
     * `GET /terms?purpose=ONBOARDING`은 온보딩 미완료 회원도 호출할 수 있다.
     */
    async loadTerms(): Promise<void> {
      if (inFlightTerms) return inFlightTerms
      this.termsPending = true
      this.termsError = null
      inFlightTerms = (async () => {
        try {
          this.terms = await getTerms('ONBOARDING')
          this.seedAgreementsFromTerms()
        } catch (error) {
          this.termsError = toApiError(error).message
        } finally {
          this.termsPending = false
          inFlightTerms = null
        }
      })()
      return inFlightTerms
    },

    /**
     * 조회 응답의 `agreed`(이 회원이 이 버전에 동의했는지)를 로컬 동의 이력의 초기값으로 삼는다.
     * 이미 이력이 있는 약관은 건드리지 않는다 — 사용자가 방금 화면에서 바꾼 값이 더 최신이다.
     */
    seedAgreementsFromTerms() {
      for (const item of this.terms) {
        if (!item.agreed) continue
        if (this.agreements.some((a) => a.termsId === item.termId)) continue
        this.agreements.push({
          termsId: item.termId,
          isAgreed: true,
          agreedAt: null,
          withdrawnAt: null,
        })
      }
    },

    /**
     * MEM-01 로그인 — 콜백이 받아 온 일회용 코드를 세션으로 바꾼다 (S15P11A704-389).
     *
     * **세션을 만드는 유일한 경로다.** 예전에는 옆에 목 경로(`loginWithKakao`)가 있었는데,
     * 토큰을 만들지 않아 「로그인된 것처럼 보이지만 이후 전부 401」인 세션을 세웠다 —
     * 조용한 오작동이라 되살리지 않고 지웠다 (S15P11A704-506).
     *
     * 로그인 응답에 `status`·`role`이 실려 오는 덕에 **추가 조회 없이** `isSuspended` 판정이 곧바로 선다.
     * 새로고침 뒤 복원은 이 경로가 아니라 `restoreSession`이 「프로필 조회」(`GET /members/me`)로 한다.
     * 단, 온보딩 미완료 409면 프로필 조회 없이 `role`·`status`를 기본값으로 세운다 — 아래 ⚠️ 참조.
     *
     * `withdrawnAt`은 null 고정이다. 탈퇴 회원은 교환 단계에서 403으로 막혀 여기까지 오지 않는다.
     */
    async establishSession(member: AuthMemberSummary) {
      // **이전 세션 잔재부터 비운다.** 로그아웃을 거치지 않은 재로그인(401·refresh 실패 등)에서
      // 남의 동의 이력·약관·지갑을 물려받는 것을 막는다 — 상세는 `resetMemberScope` 주석.
      this.resetMemberScope()

      this.user = {
        id: Number(member.memberId),
        nickname: member.nickname,
        role: member.role,
        status: member.status,
        withdrawnAt: null,
      }
      // 온보딩 게이트는 필수 약관 미동의도 본다 — 목록이 없으면 "미동의 0건"으로 오판해 게이트가 헛돈다.
      // 위에서 비웠으므로 항상 새로 받는다(= 이 회원 기준 `agreed`).
      // `loadTerms`가 내부에서 `seedAgreementsFromTerms`까지 하므로 동의 이력도 이 회원 것으로 채워진다.
      await this.loadTerms()
    },

    /**
     * MEM-02 약관 동의 저장 (`POST /members/me/terms-agreements`).
     * `UNIQUE(member_id, terms_id)`라 같은 약관은 회원당 1행이며 재동의는 UPDATE다.
     * 상태 조합은 동의 / 미동의 / 철회 세 가지만 유효하다.
     *
     * 응답의 `onboardingCompleted`는 **제출이 서버 기준으로도 통과했는지 확인**하는 용도다.
     * 닉네임 단계가 남았는지는 FE가 `needsOnboarding`으로 계속 따로 판정한다 (그 게터 주석 참조).
     *
     * @returns 저장 성공 여부. 실패 사유는 `onboardingError`에 담긴다.
     */
    async agreeToTerms(input: TermsAgreementInput): Promise<boolean> {
      this.onboardingError = null
      const agreements = Object.entries(input).map(([termsIdText, agreed]) => ({
        termId: Number(termsIdText),
        agreed,
      }))

      let now: string
      let onboardingCompleted: boolean
      try {
        const response = await submitTermsAgreements(agreements)
        now = response.agreedAt
        onboardingCompleted = response.onboardingCompleted
      } catch (error) {
        const apiError = toApiError(error)
        this.onboardingError = apiError.message
        // 409 TERMS_VERSION_OUTDATED = 화면이 들고 있는 목록이 낡았다는 뜻이다.
        // 다시 받지 않으면 같은 termId로 재제출 → 같은 409만 무한 반복된다.
        if (hasErrorCode(apiError, API_ERROR_CODE.TERMS_VERSION_OUTDATED)) await this.loadTerms()
        return false
      }

      for (const [termsIdText, isAgreed] of Object.entries(input)) {
        const termsId = Number(termsIdText)
        const existing = this.agreements.find((a) => a.termsId === termsId)
        if (!existing) {
          this.agreements.push({
            termsId,
            isAgreed,
            agreedAt: isAgreed ? now : null,
            withdrawnAt: null,
          })
          continue
        }
        if (isAgreed) {
          existing.isAgreed = true
          existing.agreedAt = now
          existing.withdrawnAt = null
        } else if (existing.isAgreed) {
          // 동의했던 건을 끄는 것은 "철회" — agreed_at은 남기고 withdrawn_at을 채운다
          existing.isAgreed = false
          existing.withdrawnAt = now
        } else {
          existing.isAgreed = false
        }
      }

      // 서버가 완료로 판정했다 = 필수 약관이 **서버 기준으로** 전부 충족됐다는 뜻이다.
      // 이번 제출에 안 실린 필수 약관(이전 버전에서 이미 동의한 건)까지 로컬 이력을 맞춰 둔다.
      if (onboardingCompleted) {
        for (const item of this.terms) {
          if (!item.required) continue
          const existing = this.agreements.find((a) => a.termsId === item.termId)
          if (existing?.isAgreed) continue
          if (existing) {
            existing.isAgreed = true
            existing.agreedAt = now
            existing.withdrawnAt = null
            continue
          }
          this.agreements.push({
            termsId: item.termId,
            isAgreed: true,
            agreedAt: now,
            withdrawnAt: null,
          })
        }
      }
      return true
    },

    /**
     * MEM 온보딩 완료 — 닉네임 설정 (`PATCH /members/me`, S15P11A704-432).
     * 신규 회원의 최초 프로필 설정과 닉네임 변경이 **같은 API**다 (명세 §개요).
     *
     * 성공하면 `needsOnboarding`이 false가 되어 가드가 풀린다.
     * 화면이 사전 확인(`checkNicknameAvailability`)을 통과시켰더라도 그사이 남이 먼저
     * 저장할 수 있어 **여기서 409가 다시 날 수 있다** (명세 §정합성 원칙). 그래서 두 곳 모두 처리한다.
     *
     * @returns 결과 구분. 실패 사유 문구는 `onboardingError`에 담긴다.
     *   불리언이 아닌 이유는 `'terms-required'`가 **닉네임 저장은 성공한** 상태여서,
     *   화면이 이걸 일반 실패와 같이 다루면 프로필 단계에 갇히기 때문이다.
     */
    async completeOnboarding(nickname: string): Promise<OnboardingResult> {
      this.onboardingError = null
      const trimmed = nickname.trim()
      // 서버도 같은 규칙을 검증한다(§검증 규칙). 이 검사는 왕복 한 번을 아끼는 용도다.
      if (trimmed.length === 0 || trimmed.length > NICKNAME_MAX_LENGTH) {
        this.onboardingError = `닉네임은 1~${NICKNAME_MAX_LENGTH}자로 입력해 주세요`
        return 'failed'
      }

      let result: UpdateMyProfileResponse
      try {
        result = await updateMyProfile({ nickname: trimmed })
      } catch (error) {
        const apiError = toApiError(error)
        this.onboardingError = PROFILE_ERROR_MESSAGE[apiError.code] ?? apiError.message
        return 'failed'
      }

      // **응답값**으로 채운다 — 저장되는 것은 서버가 앞뒤 공백을 제거한 값이다(§Request Body).
      // 화면이 보낸 문자열을 그대로 믿으면 마이페이지를 열 때까지 다른 값을 보여 준다.
      if (this.user) this.user.nickname = result.nickname

      // 닉네임은 저장됐는데 서버가 아직 온보딩이 필요하다고 하면 **필수 약관이 남았다는 뜻**이다
      // (§검증 규칙: `onboardingRequired=false`는 닉네임 + 현재 필수 약관 전부 동의일 때만).
      // 로컬 동의 이력이 낡아 화면이 약관 단계를 건너뛴 경우가 여기 걸린다 — 목록을 다시 받아 둔다.
      //
      // ⚠️ 다시 받아도 `pendingRequiredTerms`가 0일 수 있다. `seedAgreementsFromTerms`는
      // 더하기만 하고 낡은 로컬 동의를 지우지 않아서(그쪽 주석의 의도된 동작),
      // 같은 termId에 대한 옛 동의가 남아 있으면 미동의로 되돌아오지 않는다.
      // 그래서 화면은 이 결과값을 보고 되돌려야 한다 — 개수로 판단하면 프로필 단계에 갇힌다.
      if (result.onboardingRequired) {
        await this.loadTerms()
        this.onboardingError = '동의가 필요한 약관이 남아 있어요'
        return 'terms-required'
      }
      return 'done'
    },

    /**
     * 회원 범위 상태를 전부 비운다. **로그아웃과 새 세션 수립 양쪽이 쓴다.**
     *
     * 세션이 끊기는 경로가 로그아웃 버튼만이 아니라서 공용으로 뺐다 —
     * 401·refresh 실패로 끊긴 뒤 다시 로그인하면 **이전 회원의 상태가 그대로 남는다.**
     * (그 경로는 이제 `registerAuthFailureHandler`가 붙어 여기로 들어온다 — S15P11A704-223.
     *  예전에는 핸들러가 아무 데도 등록돼 있지 않아 아예 안 불렸다.)
     * 세 가지가 조용히 새는데 셋 다 화면상 멀쩡해 보인다:
     *   - `agreements` — 이전 회원 동의 이력으로 `pendingRequiredTerms`가 계산돼
     *     **필수 약관 미동의인 신규 회원이 온보딩 게이트를 그냥 통과한다**
     *   - `terms` — `TermsItem.agreed`가 회원별 값이라 남기면 남의 동의 상태를 본다
     *   - 지갑 — `loaded` 가드 때문에 재조회를 건너뛰어 **이전 회원 잔액이 그대로 보인다**
     *
     * (live·prediction 스토어는 각 화면이 이탈 시 `$reset`하므로 여기서 건드리지 않는다.
     *  `stores/live.ts`는 FE2 소유이기도 하다.)
     */
    resetMemberScope() {
      this.user = null
      this.agreements = []
      this.terms = []
      this.onboardingError = null
      this.termsError = null
      // `$reset()`만으로는 부족하다 — 지갑 조회의 single-flight는 모듈 변수라 상태 초기화가
      // 닿지 않는다. 떠 있던 이전 회원의 요청이 살아남아 비워 둔 스토어에 남의 잔액을 쓰고,
      // 그 프라미스가 재사용되기까지 한다(S15P11A704-332). 세대를 먼저 끊는다.
      const wallet = useWalletStore()
      wallet.discardInFlight()
      wallet.$reset()
      // 헤더의 「진행 콘솔」 바로가기도 회원 스코프다 — 안 비우면 다음 회원 화면에
      // **남의 방으로 가는 링크**가 남는다 (S15P11A704-692).
      useHostedConsoleStore().clear()
    },

    /**
     * MEM-01 로그아웃. **서버를 반드시 거친다** (S15P11A704-223).
     *
     * 예전에는 로컬 상태만 비웠는데, 부팅 refresh가 생긴 지금 그러면 **Refresh 쿠키가 살아남아
     * 새로고침 한 번에 도로 로그인된다.** 세션을 끝내는 주체는 쿠키를 지울 수 있는 서버뿐이다.
     *
     * 실패해도 로컬은 비운다 — `shared/api/auth.ts`의 `logout()`이 `finally`로 토큰을 지우고,
     * 명세도 이미 폐기된 세션에 204로 멱등 응답한다. "로그아웃 눌렀는데 그대로"가 제일 나쁘다.
     */
    async logout() {
      try {
        await requestLogout()
      } finally {
        this.resetMemberScope()
      }
    },

    /**
     * 세션 복원 — Refresh 쿠키가 살아 있으면 새 Access Token을 받고 프로필을 채운다.
     * **라우터 가드가 첫 내비게이션에서 기다린다**(`app/router.ts`). 마운트를 막지 않는 이유는
     * 비로그인 첫 방문자까지 401 왕복만큼 백지를 보게 되기 때문이다.
     *
     * **Access Token을 메모리에만 두게 되면서 필요해졌다.** 새로고침하면 토큰이 사라지므로,
     * 이걸 안 돌리면 멀쩡히 로그인된 사용자가 매번 로그아웃 상태로 보인다.
     *
     * 쿠키가 없거나 만료면 refresh가 401을 준다 — **그건 오류가 아니라 "비로그인"이다.**
     * 그래서 실패를 조용히 삼키고 `sessionRestored`만 세운다. 여기서 에러 문구를 띄우면
     * 처음 방문한 사람에게 "세션이 만료됐다"고 말하게 된다.
     */
    restoreSession(): Promise<void> {
      if (this.sessionRestored) return Promise.resolve()
      if (inFlightRestore) return inFlightRestore
      // 예전에는 여기에 「목 모드면 서버를 안 부른다」 단락이 있었다. 근거가 둘 다 무너져 지웠다
      // (S15P11A704-535): 「목 `getMyProfile`이 항상 프로필을 준다」는 504로 목 분기가 걷히며 거짓이 됐고,
      // 506으로 목 로그인이 없어진 뒤로는 **목 모드에도 실 세션이 생긴다**(실 카카오가 유일한 로그인 경로).
      // 그 상태에서 단락을 두면 새로고침이 멀쩡한 세션을 로그아웃으로 만든다.
      //
      // 쿠키가 없으면 refresh가 401을 주고 아래 catch가 삼킨다 — 모드와 무관하게 같은 경로다.
      inFlightRestore = (async () => {
        try {
          await refreshAccessToken()
          // 프로필과 약관은 **서로 독립**이다 — 둘 다 위에서 받은 토큰만 있으면 되고,
          // `seedAgreementsFromTerms`도 `terms`·`agreements`만 건드린다.
          // 직렬로 두면 왕복이 하나 더 늘어 첫 내비게이션이 그만큼 늦어진다.
          //
          // 온보딩 게이트가 필수 약관 미동의도 보므로 **약관도 있어야 판정이 선다.**
          // `loadTerms`는 실패를 내부에서 삼키므로(→ `termsError`) 여기서 reject하는 건
          // 프로필 쪽뿐이다 — 예전과 같은 실패 경로다.
          const [profile] = await Promise.all([getMyProfile(), this.loadTerms()])
          this.user = {
            id: Number(profile.memberId),
            nickname: profile.nickname,
            role: profile.role,
            status: profile.status,
            withdrawnAt: null,
          }
        } catch (error) {
          // 409 ONBOARDING_NOT_COMPLETED는 **실패가 아니다** — 「세션은 유효한데 온보딩만 남았다」다
          // (S15P11A704-451). 여기서 비로그인으로 두면 `isLoggedIn`이 false로 남고, 라우터 가드의
          // `to.path === '/onboarding' && !auth.isLoggedIn`이 온보딩 화면을 로그인으로 되돌린다 —
          // 온보딩 중 새로고침하면 갇히지 않고 **튕긴다.** 그래서 아는 것만으로 회원을 세운다.
          //
          // 아는 것: 닉네임이 없거나 필수 약관이 남았다(그게 409의 정의다). 회원 번호는 응답에 없다.
          // 상태는 ACTIVE로 둔다 — 탈퇴 회원은 409까지 오지 않는다. 위 `refreshAccessToken`이 먼저
          // 403 `MEMBER_UNAVAILABLE`로 떨어지고, 거기를 통과해도 프로필 조회가 403
          // `MEMBER_NOT_AVAILABLE`로 막는다 (같은 상황인데 엔드포인트마다 철자가 다르다, 536).
          // ⚠️ 정지(SUSPENDED) 회원이 온보딩 미완료면 여기서도 409라 잠금 판정이 한 박자 늦는다.
          //    온보딩 화면에는 잠글 대상이 없고, 완료 후 프로필 조회가 200을 주면 실제 상태로 덮인다.
          if (hasErrorCode(error, API_ERROR_CODE.ONBOARDING_NOT_COMPLETED)) {
            this.user = {
              id: null,
              nickname: null,
              role: 'USER',
              status: 'ACTIVE',
              withdrawnAt: null,
            }
          }
          // 그 밖은 비로그인 방문자의 정상 경로다 — 상태를 건드리지 않는다
        } finally {
          this.sessionRestored = true
          inFlightRestore = null
        }
      })()
      return inFlightRestore
    },

    /**
     * 401 → refresh 재시도까지 실패했을 때 회원 범위를 비운다.
     * `main.ts`가 부팅 때 한 번 등록한다 — 등록이 없으면 세션이 끊겨도 화면은 로그인 상태로 남아
     * 이전 회원의 동의 이력·잔액을 계속 보여준다(`resetMemberScope` 주석 참조).
     *
     * 여기서 `logout()`을 부르지 않는 이유: 그 경로는 **이미 서버가 세션을 끊은 뒤**라
     * `POST /auth/logout`을 또 쏘면 401만 한 번 더 난다.
     */
    registerAuthFailureHandler() {
      return onAuthFailure(() => {
        this.resetMemberScope()
      })
    },
  },
})
