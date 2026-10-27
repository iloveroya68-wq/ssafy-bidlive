// AI-04 상품 등록 사전 검열 — `POST /api/v1/ai/products/inspect` (S15P11A704-560)
//
// 명세 근거: `backend/docs/api/product/ai-04-post-ai-products-inspect-*.md` (상태 `확정`).
// 요청은 등록하려는 원본 입력 그대로다(`productId`가 아니다). `title`·`description`·`imageUrls` 중
// **최소 하나에 내용이 있어야** 하고, 아니면 400 `INVALID_INSPECTION_REQUEST`다.
//
// ⚠️ **목 분기가 없다.** 서버에 `ProductInspectionController`가 있으므로 목 모드에서도 실 호출로 나간다
//    (`mock.ts` 머리말: 목 스위치가 덮는 범위는 "아직 서버에 없는 것"뿐이다).
//    서버·GMS가 없어도 아래 fail-open이 흐름을 죽이지 않는다.

import { http } from '@/shared/api/http'
import { toApiError } from '@/shared/api/errors'
import type { IsoDateTime } from '@/shared/api/types'

const INSPECT_PATH = '/ai/products/inspect'

type InspectionVerdict = 'PASSED' | 'REJECTED' | 'NEEDS_REVIEW'

/** 텍스트 1건 또는 이미지 1장의 단위 판정 */
interface InspectionUnitResult {
  verdict: InspectionVerdict
  /** 이미지 결과에서만 채워진다 */
  imageUrl: string | null
  /** `ILLEGAL_GOODS`·`COUNTERFEIT`·… 서버 상수. 화면이 분기하지 않아 문자열로 둔다 */
  flaggedCategories: string[]
  maxScore: number
  reason: string | null
  /**
   * AI 판정을 얻지 못해 보류된 사유(`TIMEOUT`·`TRANSPORT`·`INVALID_RESPONSE`).
   * ⚠️ **GMS 장애는 에러가 아니라 200 + `NEEDS_REVIEW` + 이 필드로 온다.**
   */
  failureReason: string | null
}

interface ProductInspection {
  verdict: InspectionVerdict
  /** 검열할 텍스트가 없으면 null */
  textResult: InspectionUnitResult | null
  imageResults: InspectionUnitResult[]
  inspectedAt: IsoDateTime
}

export interface ProductInspectionInput {
  /** ≤100자 */
  title?: string
  /** ≤5000자 */
  description?: string
  /** **서버가 발급한 URL**이어야 한다 — `blob:`을 보내면 GMS가 못 읽는다 */
  imageUrls?: string[]
}

// 폼의 다른 문구와 같은 해요체다 — 사유 뒤에 그대로 이어 붙어 한 문장을 이룬다.
// "이미지"가 아니라 "사진"인 것도 1단계 라벨(「상품 사진」)에 맞춘 것이다.
const REJECTION_HINT = '다른 사진·문구로 수정해 주세요.'

/**
 * 상품을 등록 전에 검열하고, **차단해야 하면** 사용자에게 보여줄 한 줄을 돌려준다. 통과면 null.
 *
 * 차단은 **`REJECTED` 하나뿐이다.**
 * - `NEEDS_REVIEW`는 "위험도가 애매하다"와 "AI 판정을 아예 못 얻었다"를 **같은 값으로 뭉뚱그린다**
 *   (명세 §실패 정책: 타임아웃·전송 오류·스키마 위반이 전부 보류다). 이걸 막으면 GMS가 흔들릴 때마다
 *   방 개설이 통째로 멈춘다.
 * - 호출 자체가 실패해도(502 `AI_CREDIT_EXHAUSTED`·네트워크·400) 통과시킨다 — **fail-open**.
 *   명세와 `ProductInspectionController` javadoc이 "검열 판정은 상품 등록을 직접 막지 않는다"고
 *   못박았고, 차단으로 다루면 GMS 크레딧이 떨어진 순간 방 개설이 전면 중단된다.
 *   대신 흔적은 남긴다 — 조용히 통과하면 검열이 죽은 걸 아무도 모른다.
 */
export async function findProductRejection(input: ProductInspectionInput): Promise<string | null> {
  try {
    const { data } = await http.post<{ type: string; data: ProductInspection }>(INSPECT_PATH, input)
    const inspection = data.data
    if (inspection.verdict !== 'REJECTED') return null
    // 대표 사유 한 줄만 보여준다 — 카테고리·점수까지 펼치는 패널은 와이어프레임에 근거가 없다.
    // **텍스트가 우선이다**(제목·설명이 걸렸으면 그걸 먼저 고치게 한다).
    const rejected = [inspection.textResult, ...inspection.imageResults].find(
      (unit) => unit?.verdict === 'REJECTED',
    )
    // 사진이 걸렸으면 몇 번째인지 붙인다 — 어느 장을 바꿔야 하는지 알려주는 한 줄이면 충분하다.
    // ⚠️ 순번을 `imageResults` 인덱스로 세면 안 된다. BE `InspectionContent.normalizeUrls()`가
    //    `distinct().sorted()`로 갈아엎어 **응답은 보낸 순서가 아니라 URL 사전순**이고,
    //    중복이 있었으면 길이도 달라진다. 그래서 1단계 사진 목록의 순번은 내가 보낸 `imageUrls`에서 되찾는다.
    //    거기 없으면(정규화로 걸러진 URL) 접두 없이 — 텍스트 판정과 같은 폴백이다.
    const imageIndex = rejected?.imageUrl ? (input.imageUrls ?? []).indexOf(rejected.imageUrl) : -1
    const where = imageIndex >= 0 ? `${imageIndex + 1}번째 사진: ` : ''
    const reason = rejected?.reason?.trim()
    return `${where}${reason || '등록할 수 없는 상품으로 판정됐어요.'} ${REJECTION_HINT}`
  } catch (caught) {
    console.warn('[AI-04] 상품 사전 검열을 건너뛴다 —', toApiError(caught).message)
    return null
  }
}
