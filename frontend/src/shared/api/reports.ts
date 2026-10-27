// DEAL-04 경매 신고 접수 (S15P11A704-74)
//
// 명세 근거: POST /api/v1/auctions/{auctionId}/reports (확정) → 201 Created
//
// **접수만 하는 API다.** 명세: *"신고 검토 결과나 강제 종료 여부는 응답에 포함하지 않습니다"* —
// 그래서 응답에 있는 건 접수 번호·상태·시각뿐이고, 화면도 "접수됐다"까지만 말할 수 있다.
// 강제 종료는 운영자 검토 후 DEAL-09가 처리한다.
//
// 신고 대상은 **거래가 아니라 경매**다. 대기방·라이브 단계에는 아직 구매자도 거래도 없어서
// 명세가 「신고 대상 거래 ID」를 「신고 대상 경매 ID」로 바꿨다.

import { http } from '@/shared/api/http'
import type { Id, IsoDateTime } from '@/shared/api/types'

export const REPORTS_PATH = {
  auctionReports: (auctionId: Id) => `/auctions/${auctionId}/reports`,
} as const

/**
 * 신고 사유. 명세 「신고 사유」 표의 4값이 전부다.
 *
 * `ITEM_NOT_AS_DESCRIBED`는 **없다** — 명세가 *"상품 수령 후 발생하는 거래 분쟁 사유이므로
 * 대기방·라이브 신고에서는 제외"*로 명시했다.
 */
export type ReportReasonCode =
  'FALSE_LISTING' | 'PROHIBITED_ITEM' | 'INAPPROPRIATE_BROADCAST' | 'OTHER'

export interface AuctionReportRequest {
  reasonCode: ReportReasonCode
  /** `OTHER`일 때만 필수. 나머지 사유에서는 비워 보낼 수 있다 */
  description?: string
}

export interface AuctionReportResponse {
  reportId: Id
  /** 접수 직후에는 항상 `RECEIVED`. 이후 상태는 신고자에게 공개되지 않는다 */
  status: string
  receivedAt: IsoDateTime
}

/**
 * DEAL-04 신고 접수.
 *
 * 명세 Request의 `evidenceUrls`는 보내지 않는다 — `report` 테이블에 증빙 컬럼이 없고
 * 와이어프레임 모달에도 업로드 칸이 없다(BE와 함께 별도 이슈로 끊었다).
 */
export async function reportAuction(
  auctionId: Id,
  body: AuctionReportRequest,
): Promise<AuctionReportResponse> {
  const { data } = await http.post<AuctionReportResponse>(
    REPORTS_PATH.auctionReports(auctionId),
    body,
  )
  return data
}
