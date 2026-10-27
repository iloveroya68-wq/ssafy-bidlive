// DEAL 거래 API (S15P11A704-336)
//
// 명세 근거
// - 내 거래 할 일:   GET   /api/v1/members/me/deals/todos              (MEM-02, 확정, 쿼리 없음)
// - 배송지 입력:     PATCH /api/v1/deals/{dealId}/shipping-address     (DEAL-05, 확정)
// - 판매자 발송 확인: POST  /api/v1/deals/{dealId}/shipment-confirmation (DEAL-05, 확정, 본문 없음)
// - 구매자 수령 확인: POST  /api/v1/deals/{dealId}/receipt-confirmation  (DEAL-05, 확정, 본문 없음)
// - 배송 정보 조회:   GET   /api/v1/deals/{dealId}/shipping              (DEAL-05, 확정)
//
// 판정은 **서버가 한다.** 별도 할 일 테이블 없이 `deal.status`와 내 거래 역할로 계산해서
// "내가 지금 처리해야 하는 것"만 내려준다 — 화면이 role·dealStatus 조합을 다시 따지지 않는다.
//
// 정렬도 서버가 정한다: `updatedAt` 오름차순(오래 묵은 할 일이 위).
//
// 에스크로 흐름: WAITING_ADDRESS → WAITING_SHIPMENT → WAITING_RECEIPT → COMPLETED.
// 각 단계를 넘기는 주체가 다르다(구매자 → 판매자 → 구매자) — 그 판정도 서버 몫이다.

import { http } from '@/shared/api/http'
import type { Id, IsoDateTime } from '@/shared/api/types'

export const DEALS_PATH = {
  todos: '/members/me/deals/todos',
  shippingAddress: (dealId: Id) => `/deals/${dealId}/shipping-address`,
  shipmentConfirmation: (dealId: Id) => `/deals/${dealId}/shipment-confirmation`,
  receiptConfirmation: (dealId: Id) => `/deals/${dealId}/receipt-confirmation`,
  shipping: (dealId: Id) => `/deals/${dealId}/shipping`,
} as const

/** 이 거래에서 내 역할 */
export type DealRole = 'BUYER' | 'SELLER'

/**
 * 액션 코드 4종.
 *
 * ⚠️ **`AWAIT_RECEIPT`만 버튼이 없다** (S15P11A704-694). 판매자가 발송 확인을 누르면 그 건이
 * 목록에서 사라져 자기가 무엇을 어디로 보냈는지 다시 볼 데가 없었다 — 구매자가 수령을 확인할
 * 때까지 **표시 전용**으로 남는다. 화면은 이 코드로 버튼 유무를 가른다.
 */
export type DealActionCode =
  'SUBMIT_ADDRESS' | 'CONFIRM_SHIPMENT' | 'CONFIRM_RECEIPT' | 'AWAIT_RECEIPT'

/** 버튼을 그릴 항목인가 — `AWAIT_RECEIPT`만 false다 */
export function isActionableTodo(actionCode: DealActionCode): boolean {
  return actionCode !== 'AWAIT_RECEIPT'
}

// ── DEAL-05 배송 정보 조회 (S15P11A704-694) ──

export interface DealShipping {
  dealId: Id
  role: DealRole
  dealStatus: string
  /**
   * 배송지가 들어왔는가. **필드가 전부 null인 것과 「입력 전」을 이 값으로 가른다** —
   * 서버가 판정해 주므로 화면이 null 조합을 다시 해석하지 않는다.
   */
  addressSubmitted: boolean
  recipientName: string | null
  recipientPhone: string | null
  postalCode: string | null
  address: string | null
  addressDetail: string | null
  /** 발송 확인 전에는 null */
  carrier: string | null
  trackingNumber: string | null
  addressSubmittedAt: IsoDateTime | null
  shippedAt: IsoDateTime | null
  receivedConfirmedAt: IsoDateTime | null
  autoConfirmAt: IsoDateTime | null
}

/**
 * 그 거래의 배송지·송장. **당사자만** 조회된다 — 남의 거래는 404다(없는 거래와 같은 응답이라
 * 거래 번호를 훑어도 존재 여부를 알 수 없다).
 *
 * 수취인 실명·전화번호·주소가 마스킹 없이 온다. 물건을 보내려면 그대로 필요한 값이고 조회가
 * 이미 당사자로 좁혀져 있어서다 — 목록의 마스킹(`판**`)과 기준이 다르다.
 */
export async function getDealShipping(dealId: Id): Promise<DealShipping> {
  const { data } = await http.get<DealShipping>(DEALS_PATH.shipping(dealId))
  return data
}

export interface DealTodo {
  dealId: Id
  auctionId: Id
  productTitle: string
  imageUrl: string | null
  role: DealRole
  dealStatus: string
  finalPrice: number
  actionCode: DealActionCode
  /** 버튼 문구 — 화면이 코드로 문구를 짜지 않는다 */
  actionLabel: string
  actionDescription: string
  /**
   * 서버가 주는 이동 경로(예: `/deals/300/address`).
   * ⚠️ **쓰지 않는다**(S15P11A704-337). 와이어프레임에 거래 화면이 없어서 대응 라우트를 만들지 않았다 —
   *    배송지는 모달, 발송·수령은 확인 전이다. 분기는 `actionCode`로 한다. BE 조율 대상.
   */
  actionPath: string
  endedAt: IsoDateTime
  updatedAt: IsoDateTime
  /** 수령 대기일 때만 값이 있다. 그 시각이 지나면 자동 구매 확정 */
  autoConfirmAt: IsoDateTime | null
}

export interface DealTodosResponse {
  totalCount: number
  items: DealTodo[]
}

/** MEM-02 지금 할 일 — 마이페이지 탭 배지(`totalCount`)와 목록을 함께 쓴다 */
export async function getDealTodos(): Promise<DealTodosResponse> {
  const { data } = await http.get<DealTodosResponse>(DEALS_PATH.todos)
  return data
}

// ── DEAL-05 에스크로 전이 3종 ──
//
// 셋 다 성공하면 그 건은 **내 할 일에서 빠진다** — 다음 단계의 주체가 상대이기 때문이다.
// 그래서 호출부는 응답을 화면에 그리기보다 목록을 다시 받는 쪽이 맞다.

/** 배송지. 명세 Request에 `memo`가 있으나 와이어프레임 모달에 필드가 없어 넣지 않는다(S15P11A704-337) */
export interface ShippingAddressRequest {
  receiverName: string
  contact: string
  postalCode: string
  address: string
  detailAddress: string
}

export interface SubmitShippingAddressResponse {
  dealId: Id
  status: string
  updatedAt: IsoDateTime
}

export interface ConfirmShipmentResponse {
  dealId: Id
  status: string
  shippedAt: IsoDateTime
  /** 발송 확인 시점에 서버가 정하는 자동 구매 확정 예정 시각 */
  autoConfirmAt: IsoDateTime
}

export interface ConfirmReceiptResponse {
  dealId: Id
  status: string
  /** 에스크로에서 판매자에게 지급된 금액 */
  sellerPayoutAmount: number
  completedAt: IsoDateTime
}

/** DEAL-05 배송지 입력 — 구매자만, `WAITING_ADDRESS`에서만. 성공 시 발송 대기로 넘어간다 */
export async function submitShippingAddress(
  dealId: Id,
  body: ShippingAddressRequest,
): Promise<SubmitShippingAddressResponse> {
  const { data } = await http.patch<SubmitShippingAddressResponse>(
    DEALS_PATH.shippingAddress(dealId),
    body,
  )
  return data
}

/** DEAL-05 판매자 발송 확인 — 판매자만, `WAITING_SHIPMENT`에서만. 요청 본문 없음 */
export async function confirmShipment(dealId: Id): Promise<ConfirmShipmentResponse> {
  const { data } = await http.post<ConfirmShipmentResponse>(DEALS_PATH.shipmentConfirmation(dealId))
  return data
}

/**
 * DEAL-05 구매자 수령 확인 — 구매자만, `WAITING_RECEIPT`에서만. 요청 본문 없음.
 * **되돌릴 수 없다** — 에스크로 대금이 판매자 지갑으로 나가고 원장에 기록된다(명세: 동일 거래는 한 번만 지급).
 * 호출부에서 한 번 더 확인받는 이유다.
 */
export async function confirmReceipt(dealId: Id): Promise<ConfirmReceiptResponse> {
  const { data } = await http.post<ConfirmReceiptResponse>(DEALS_PATH.receiptConfirmation(dealId))
  return data
}
