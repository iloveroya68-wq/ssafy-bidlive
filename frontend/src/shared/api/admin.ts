// 운영자 콘솔 API (S15P11A704-525)
//
// 명세 근거
// - 신고 목록:   GET  /api/v1/admin/reports                        (ADMIN-01, 로컬 신설 계약)
// - 강제 종료:   POST /api/v1/admin/auctions/{auctionId}/force-end (DEAL-09)
//
// **목 분기가 없다.** 둘 다 서버에 붙어 있다 — 목이 남아 있으면 연동이 깨져도 화면이 멀쩡해서
// 어긋난 걸 아무도 못 잡는다(`shared/api/mock.ts` 머리말).
//
// 둘 다 `ADMIN`만 열린다. 아닌 회원은 403, 비로그인은 401이며 화면이 그걸 구분해 그린다.

import { http } from '@/shared/api/http'
import type { Id, IsoDateTime } from '@/shared/api/types'

/** 신고 사유 코드. 접수 API(`shared/api/reports.ts`)의 `ReportReasonCode`와 같은 값 집합이다 */
export interface ReportReasonCount {
  reasonCode: string
  count: number
}

export interface ReportedAuctionItem {
  auctionId: Id
  title: string
  /** 이미지가 없는 상품은 null이다 */
  thumbnailUrl: string | null
  /** 서버가 마스킹한 판매자 닉네임 */
  sellerNickname: string
  /** `WAITING` | `LIVE` | `ENDED` */
  status: string
  reportCount: number
  /** 건수 내림차순, 동수면 코드 사전순 — 서버가 고정해 보낸다 */
  reasonCounts: ReportReasonCount[]
  latestReportedAt: IsoDateTime
}

export interface AdminReportPage {
  items: ReportedAuctionItem[]
  nextCursor: string | null
  hasNext: boolean
}

export interface AdminReportQuery {
  /** 기본 false — 종료된 방은 빼고 준다 */
  includeEnded?: boolean
  cursor?: string
  /** 기본 20, 최대 100 */
  size?: number
}

/**
 * ADMIN-01 신고 접수 경매 목록.
 *
 * **집계는 전부 서버 값이다.** `reportCount`와 `reasonCounts` 합이 같은 경매 집합을 세므로
 * 프론트에서 다시 세지 않는다.
 */
export async function getAdminReports(query: AdminReportQuery = {}): Promise<AdminReportPage> {
  // 준 값만 싣는다. `undefined`를 그대로 넘기면 axios가 파라미터를 빼주긴 하지만,
  // 서버 기본값을 쓰는 것과 빈 문자열을 보내는 것이 눈으로 구분되지 않는다.
  const params: Record<string, string | number | boolean> = {}
  if (query.includeEnded !== undefined) params.includeEnded = query.includeEnded
  if (query.cursor !== undefined) params.cursor = query.cursor
  if (query.size !== undefined) params.size = query.size

  // 경로는 baseURL(`/api/v1`) 기준 상대경로다 — `/v1`을 또 붙이면 `/api/v1/v1/...`가 된다(394).
  const { data } = await http.get<AdminReportPage>('/admin/reports', { params })
  return data
}

export interface ForceEndRequest {
  /** 비어 있으면 서버가 400으로 거절한다 */
  reason: string
  /** 명세에 있지만 서버가 저장하지 않는다 — 감사 기록으로 믿으면 안 된다 */
  adminNote?: string
}

export interface ForceEndResponse {
  auctionId: Id
  /** 항상 `ENDED` */
  status: string
  /** 항상 `FORCED` */
  resultType: string
  endedAt: IsoDateTime
}

/**
 * DEAL-09 운영 강제 종료. **되돌릴 수 없다** — 입찰 보류금과 예측 포인트가 환급되고
 * 방이 `ENDED`가 된다.
 *
 * 이미 종료된 방은 409 `AUCTION_NOT_ENDABLE`이다. 다른 운영자가 먼저 눌렀거나 정상 종료된
 * 경우라 화면은 그 행을 목록에서 지운다.
 */
export async function forceEndAuction(
  auctionId: Id,
  request: ForceEndRequest,
): Promise<ForceEndResponse> {
  const { data } = await http.post<ForceEndResponse>(
    `/admin/auctions/${auctionId}/force-end`,
    request,
  )
  return data
}
