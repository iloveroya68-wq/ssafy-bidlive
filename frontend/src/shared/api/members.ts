// MEM-02 회원 프로필 API (S15P11A704-59)
//
// 명세 근거
// - 프로필 조회:     GET   /api/v1/members/me
// - 내 프로필 수정:  PATCH /api/v1/members/me                       (온보딩 최초 닉네임 설정도 이 API)
// - 닉네임 중복 확인: GET  /api/v1/members/nickname-availability
//
// 명세의 "API 범위 분리" 표: 지갑·거래 목록은 프로필 응답에 넣지 않는다 —
// 갱신 주기와 크기가 달라 프론트가 병렬로 호출한다. (지갑은 `wallets.ts`)
//
// 약관 조회·동의는 `terms.ts`에 있다 (경로가 `/terms`와 `/members/me/terms-agreements`로 갈린다).
//
// 탈퇴 회원은 **상태 값이 아니라 오류로** 온다 — 403 `MEMBER_NOT_AVAILABLE`이라 이 응답 자체가 없다.
// 온보딩 미완료도 마찬가지로 409 `ONBOARDING_NOT_COMPLETED`다.
//
// `status`·`role`은 **실제로 배선돼 있다** (389에서 끝났다). 로그인 응답(`establishSession`)과
// 부팅 복원(`restoreSession`) 양쪽이 `auth.user`에 넣고, `isSuspended`·`isWithdrawn`이 그걸 읽는다.
// 「회원 상태 처리 정책」대로 SUSPENDED는 토큰을 받되 제한 기능을 화면이 잠근다.

import { http } from '@/shared/api/http'
import type { Id, IsoDateTime } from '@/shared/api/types'

export const MEMBERS_PATH = {
  me: '/members/me',
  nicknameAvailability: '/members/nickname-availability',
} as const

/** `member.nickname` VARCHAR(30) UNIQUE — 서버가 길이·금칙어를 최종 검증한다 */
export const NICKNAME_MAX_LENGTH = 30

/** 마이페이지 상단 활동 요약 */
export interface MemberActivitySummary {
  /** 판매자로서 거래 완료(COMPLETED)된 건수 */
  salesCount: number
  /** 구매자로서 거래 완료(COMPLETED)된 건수 */
  purchaseCount: number
  /** 정산이 완료된 예측 참여 건수 (전액 환불된 유찰 건은 제외) */
  predictionParticipationCount: number
  /** 오차율 20% 이내로 적중한 건수 */
  predictionHitCount: number
  /** 적중률(%) — 이미 100을 곱한 값이다. 소수 둘째 자리까지, 참여 0건이면 0.0 */
  predictionHitRate: number
}

/**
 * 회원 상태. `WITHDRAWN`은 이 응답에 **오지 않는다** — 조회 자체가 403으로 거부된다.
 * `SUSPENDED`는 프로필 조회는 되지만 입찰·베팅·채팅 등 제한 기능을 화면에서 잠가야 한다(명세 §회원 상태·권한 정책).
 */
export type MemberStatus = 'ACTIVE' | 'SUSPENDED'

/** 회원 권한. Spring Security의 `ROLE_USER`/`ROLE_ADMIN` 표현과 구분된 값이다 */
export type MemberRole = 'USER' | 'ADMIN'

export interface MyProfileResponse {
  memberId: Id
  /** 마이페이지 진입 시점에는 온보딩이 끝나 있으므로 명세상 필수 값이다 */
  nickname: string
  status: MemberStatus
  /**
   * `ADMIN`은 관리자 화면 **노출 판단**에만 쓴다.
   * 실제 관리자 API 권한은 서버가 검증하므로 이 값으로 권한을 대신하지 않는다.
   */
  role: MemberRole
  activitySummary: MemberActivitySummary
}

export interface UpdateMyProfileRequest {
  nickname?: string
}

export interface UpdateMyProfileResponse {
  memberId: Id
  nickname: string
  /** 저장 후 서버가 다시 계산한 온보딩 필요 여부 */
  onboardingRequired: boolean
  updatedAt: IsoDateTime
}

/** 닉네임 사용 불가 사유 (명세 명시 3종) */
export type NicknameUnavailableReason = 'ALREADY_EXISTS' | 'INVALID_FORMAT' | 'PROHIBITED_WORD'

export interface NicknameAvailabilityResponse {
  nickname: string
  available: boolean
  reason: NicknameUnavailableReason | null
}

/** MEM-02 마이페이지 프로필 요약 */
export async function getMyProfile(): Promise<MyProfileResponse> {
  const { data } = await http.get<MyProfileResponse>(MEMBERS_PATH.me)
  return data
}

/**
 * MEM-02 프로필 수정. 온보딩 최초 닉네임 설정도 같은 API를 쓴다.
 * 중복 닉네임은 409 `NICKNAME_ALREADY_EXISTS`로 온다 — 사전 확인을 했더라도 여기서 다시 걸릴 수 있다.
 */
export async function updateMyProfile(
  request: UpdateMyProfileRequest,
): Promise<UpdateMyProfileResponse> {
  const { data } = await http.patch<UpdateMyProfileResponse>(MEMBERS_PATH.me, request)
  return data
}

/**
 * MEM-02 닉네임 사전 중복 확인.
 * 명세 주의: 이건 **편의용 사전 확인**이고 최종 판정은 저장 API의 DB UNIQUE 제약이 한다.
 */
export async function checkNicknameAvailability(
  nickname: string,
): Promise<NicknameAvailabilityResponse> {
  const { data } = await http.get<NicknameAvailabilityResponse>(MEMBERS_PATH.nicknameAvailability, {
    params: { nickname },
  })
  return data
}
