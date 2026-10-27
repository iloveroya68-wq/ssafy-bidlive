// MEM-02 약관 조회·동의 API (S15P11A704-316)
//
// 명세 근거 (2026-07-23 갱신에서 신설, 둘 다 확정)
// - 약관 조회: GET  /api/v1/terms?purpose=ONBOARDING      (온보딩 미완료 회원도 호출할 수 있다)
// - 약관 동의: POST /api/v1/members/me/terms-agreements   (응답에 `onboardingCompleted`)
//
// ⚠️ 응답이 `terms` 테이블 컬럼과 다르다 — 근거 우선순위대로 명세를 따랐다 (docs/04 §6).
//  1. 식별자는 `termId`, 필수 여부는 `required`다 (`terms_id`·`is_required`가 아니다).
//  2. `terms_type`·`is_active`·`effective_at`은 응답에 없다. "현재 시행 중인 최신 약관만 반환한다"가
//     명세의 규칙이라 프론트가 필터링할 것이 없다 — 그 필터는 목 어댑터만 흉내 낸다.
//  3. 회원별 동의 여부가 `agreed`로 함께 온다. 동의 이력 조회 API는 따로 없다.
//  4. 동의 목록 한 줄 요약(와이어프레임 `onboard`의 `.as`)은 응답에 없다 —
//     화면이 `code`로 문구를 붙인다 (`features/auth/TermsAgreement.vue`).

import { http } from '@/shared/api/http'
import type { Id, IsoDateTime } from '@/shared/api/types'

export const TERMS_PATH = {
  terms: '/terms',
  agreements: '/members/me/terms-agreements',
} as const

/**
 * 조회 목적. 명세에 등장하는 값은 `ONBOARDING` 하나뿐이고,
 * 그 밖의 값은 서버가 400 `INVALID_TERMS_PURPOSE`로 막는다.
 */
export type TermsPurpose = 'ONBOARDING'

/**
 * 명세 응답 예시에 실제로 등장한 코드 3종.
 * **닫힌 목록이라는 근거가 없어** `TermsItem.code`는 string으로 두고, 여기 값은 화면 분기용 상수로만 쓴다.
 */
export const TERMS_CODE = {
  SERVICE_TERMS: 'SERVICE_TERMS',
  PRIVACY_POLICY: 'PRIVACY_POLICY',
  MARKETING_CONSENT: 'MARKETING_CONSENT',
} as const

export interface TermsItem {
  termId: Id
  /** 약관 종류 식별 문자열 (예: `SERVICE_TERMS`) */
  code: string
  title: string
  /** 전문. 길어서 화면은 모달로 보여준다 */
  content: string
  /** 개정되면 새 버전 행이 생기고 재동의를 받아야 한다 */
  version: string
  /** true면 미동의 시 온보딩을 완료할 수 없다 */
  required: boolean
  /** 이 버전에 대한 **현재 회원의** 동의 여부 */
  agreed: boolean
}

export interface TermsListResponse {
  items: TermsItem[]
}

/** 동의 제출 한 건 — 선택 약관을 끄고 보내는 것도 유효한 제출이다 */
export interface TermsAgreementItem {
  termId: Id
  agreed: boolean
}

export interface AgreeToTermsRequest {
  agreements: TermsAgreementItem[]
}

export interface AgreeToTermsResponse {
  /**
   * 서버가 판정한 온보딩 완료 여부. 필수 약관을 하나라도 거부하면 false다.
   * ⚠️ 닉네임(`member.nickname` nullable)까지 포함한 값인지는 명세에 없다 — `stores/auth.ts`가
   *    닉네임 조건을 따로 확인하는 이유다 (S15P11A704-317).
   */
  onboardingCompleted: boolean
  agreedAt: IsoDateTime
}

// ── 엔드포인트 ──

/**
 * MEM-02 약관 조회. 응답의 `items` 래퍼는 여기서 벗겨 배열로 돌려준다.
 * 정렬·필터(시행 중인 최신 버전만)는 서버 책임이다 — 프론트는 받은 순서를 그대로 쓴다.
 */
export async function getTerms(purpose: TermsPurpose = 'ONBOARDING'): Promise<TermsItem[]> {
  const { data } = await http.get<TermsListResponse>(TERMS_PATH.terms, { params: { purpose } })
  return data.items
}

/**
 * MEM-02 약관 동의 저장.
 * 같은 회원·같은 버전의 중복 동의는 서버가 1행으로 정리하고, 동의 저장과 온보딩 완료 처리는 한 트랜잭션이다.
 * 필수 약관을 빠뜨리면 409 `REQUIRED_TERMS_NOT_AGREED`가 온다 — 목도 같은 에러를 던진다.
 */
export async function agreeToTerms(
  agreements: TermsAgreementItem[],
): Promise<AgreeToTermsResponse> {
  const { data } = await http.post<AgreeToTermsResponse>(TERMS_PATH.agreements, {
    agreements,
  } satisfies AgreeToTermsRequest)
  return data
}
