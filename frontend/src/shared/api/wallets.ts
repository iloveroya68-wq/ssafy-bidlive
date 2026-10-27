// PNT 포인트 지갑·원장 API (S15P11A704-59)
//
// 명세 근거
// - 지갑 조회:   GET  /api/v1/members/me/wallets                (PNT-01, 거래+베팅 한 번에)
// - 원장 조회:   GET  /api/v1/members/me/point-ledgers          (PNT-02, 커서 페이지)
// - 충전:        POST /api/v1/members/me/wallets/trade/charge   (PNT-01, `Idempotency-Key`)
// - 환불(출금):  POST /api/v1/members/me/wallets/trade/refunds  (PNT-01, `Idempotency-Key`)
// - 정산 계좌:   GET·PUT /api/v1/members/me/settlement-account  (MEM-03)
//
// 출금 화면은 **둘을 같이 쓴다** — 계좌는 `settlement-account`가, 잔액 차감은 `refunds`가 담당한다.
// 이름이 갈리는 이유는 `refunds`가 실제 송금이 아니라 프로젝트 내 포인트 차감이기 때문이다.
//
// ⚠️ 스키마 메모(`docs/04` §3.5)와 어긋나는 지점이 둘 있다 — 명세를 따랐다.
//  1. 지갑 필드가 `balance`(총 보유) / `heldAmount` / `availableAmount`이고
//     `availableAmount = balance - heldAmount`다. 스토어의 `availableBalance`는 `availableAmount`에,
//     `tradeTotal` getter는 `balance`에 대응한다.
//  2. 원장이 **한 엔드포인트로 통합**돼 있다. 단 행 자체는 `docs/04`가 전제한 것과 같은
//     **2델타 구조**다(2026-07-27 개정) — 한때 단일 `amount`였던 것이 되돌아왔다.

import { http } from '@/shared/api/http'
import { idempotent } from '@/shared/api/idempotency'
import type { CursorPage, CursorPageQuery, Id, IsoDateTime } from '@/shared/api/types'

export const WALLETS_PATH = {
  wallets: '/members/me/wallets',
  ledgers: '/members/me/point-ledgers',
  charge: '/members/me/wallets/trade/charge',
  refunds: '/members/me/wallets/trade/refunds',
  /** MEM-03 정산 계좌 — 조회·등록·변경이 같은 경로다 (GET / PUT) */
  settlementAccount: '/members/me/settlement-account',
} as const

export type WalletType = 'TRADE' | 'BETTING'

/** 지갑 1개의 잔액 3종. 화면은 `availableAmount`와 `heldAmount`를 **따로** 보여준다 */
export interface WalletBalance {
  /** 총 보유액 */
  balance: number
  /** 입찰·베팅으로 사용이 제한된 금액 */
  heldAmount: number
  /** 지금 새 요청에 쓸 수 있는 금액 (= balance - heldAmount) */
  availableAmount: number
}

export interface MyWalletsResponse {
  /** 입찰·낙찰 결제용. 충전·환불 대상 */
  tradeWallet: WalletBalance
  /** 예측 전용 무료 재화. 충전·환불·상호 전환이 없다 */
  bettingWallet: WalletBalance
}

/** 원장 거래 유형 (명세 쿼리 파라미터가 열거한 8종. "등"이 붙어 있어 늘어날 수 있다) */
export type PointTransactionType =
  'CHARGE' | 'HOLD' | 'RELEASE' | 'SETTLEMENT' | 'REFUND' | 'GRANT' | 'BET' | 'PAYOUT'

/**
 * 증감 원인 도메인. 명세 「referenceType 전체 값」 표의 7값이 전부다 —
 * *"위 표에 없는 `referenceType`을 추가하려면 이 명세와 프론트 표시 정책을 함께 갱신한다"*.
 */
export type PointReferenceType =
  'BID' | 'AUCTION' | 'DEAL' | 'PREDICTION' | 'PAYMENT' | 'REWARD' | 'ADMIN'

/**
 * 원장 1행. **두 재화(가용·보류)가 각각 얼마나 움직였는지를 부호 있는 값으로** 준다.
 *
 * 명세 「조회·표시 규칙」이 홀드 표현을 못박았다:
 *   입찰 홀드 = `availableChange < 0`, `heldChange > 0` (같은 행)
 *   보류 해제 = `availableChange > 0`, `heldChange < 0`
 * 즉 홀드·해제 행은 **두 값의 합이 0**이라 "증감 없음"으로 보이면 안 된다 —
 * 화면은 합이 아니라 이동으로 그린다(`features/wallet/ledgerLabel.ts`).
 */
export interface PointLedgerEntry {
  ledgerId: Id
  walletType: WalletType
  transactionType: PointTransactionType
  /** 가용 포인트 증감. 증가는 양수, 감소는 음수, 변동 없음은 0 */
  availableChange: number
  /** 보류 포인트 증감. 같은 규칙 */
  heldChange: number
  /** 이 행 처리 **직후**의 가용 잔액 */
  availableBalanceAfter: number
  /** 이 행 처리 직후의 보류 잔액 */
  heldBalanceAfter: number
  /**
   * 행에 표시할 설명 문구. **서버가 `transactionType`과 `referenceType`으로 생성한다** —
   * 명세: *"프론트가 enum을 조합해 만들지 않는다"*. 화면은 이 문자열을 그대로 출력한다.
   */
  description: string
  referenceType: PointReferenceType
  /** 원인 도메인의 식별자. 직접 지급 등 연결 대상이 없으면 null */
  referenceId: Id | null
  createdAt: IsoDateTime
}

export interface PointLedgerQuery extends CursorPageQuery {
  /** 생략하면 두 지갑을 모두 조회한다 */
  walletType?: WalletType
  transactionType?: PointTransactionType
}

export interface TradeChargeResponse {
  walletType: 'TRADE'
  chargedAmount: number
  balance: number
  heldAmount: number
  availableAmount: number
  ledgerId: Id
  chargedAt: IsoDateTime
}

export interface TradeRefundResponse {
  walletType: 'TRADE'
  refundedAmount: number
  balance: number
  heldAmount: number
  availableAmount: number
  ledgerId: Id
  refundedAt: IsoDateTime
}

// ── 엔드포인트 ──

/** PNT-01 이중 지갑 조회 */
export async function getMyWallets(): Promise<MyWalletsResponse> {
  const { data } = await http.get<MyWalletsResponse>(WALLETS_PATH.wallets)
  return data
}

/** PNT-02 원장 조회 — `createdAt DESC, ledgerId DESC`. 기본 20건, 최대 100건 */
export async function getPointLedgers(
  query: PointLedgerQuery = {},
): Promise<CursorPage<PointLedgerEntry>> {
  const { data } = await http.get<CursorPage<PointLedgerEntry>>(WALLETS_PATH.ledgers, {
    params: query,
  })
  return data
}

/**
 * PNT-01 거래 포인트 충전 (P0 원클릭 — 실결제 없음).
 * @param idempotencyKey 재시도 시 같은 키를 넘긴다. 중복이면 409 `DUPLICATE_REQUEST`
 */
export async function chargeTradePoint(
  amount: number,
  idempotencyKey?: string,
): Promise<TradeChargeResponse> {
  const { data } = await http.post<TradeChargeResponse>(
    WALLETS_PATH.charge,
    { amount },
    idempotent(idempotencyKey),
  )
  return data
}

/**
 * PNT-01 거래 포인트 환불(= 화면상 "출금").
 * 홀드 금액은 대상이 아니라 `amount <= availableAmount`여야 한다.
 * 보낼 계좌는 이 API가 아니라 MEM-03 `settlement-account`가 들고 있다 — 둘을 한 화면이 함께 쓴다.
 *
 */
export async function refundTradePoint(
  amount: number,
  idempotencyKey?: string,
): Promise<TradeRefundResponse> {
  const { data } = await http.post<TradeRefundResponse>(
    WALLETS_PATH.refunds,
    { amount },
    idempotent(idempotencyKey),
  )
  return data
}

// ── 정산 계좌 (MEM-03) ──

/**
 * 선택 가능한 은행. 명세는 "서버가 지원하는 은행 코드"라고만 하고 **목록 API가 없다** —
 * 와이어프레임 6종에 표준 코드를 붙여 FE가 들고 있는다. 서버가 목록을 주면 이 표를 걷어낸다.
 * 지원하지 않는 코드면 `400 INVALID_BANK_CODE`로 돌아온다.
 */
export const SETTLEMENT_BANKS = [
  { code: '004', name: 'KB국민은행' },
  { code: '088', name: '신한은행' },
  { code: '020', name: '우리은행' },
  { code: '081', name: '하나은행' },
  { code: '090', name: '카카오뱅크' },
  { code: '092', name: '토스뱅크' },
] as const

export const bankNameByCode = (code: string): string =>
  SETTLEMENT_BANKS.find((b) => b.code === code)?.name ?? code

/** 등록·변경 요청. 계좌번호는 공백·구분 기호를 **화면에서 제거해** 보낸다 */
export interface SettlementAccountRequest {
  bankCode: string
  accountNumber: string
  accountHolderName: string
}

/**
 * 조회·등록·변경 공통 응답. **계좌번호 원문은 절대 오지 않는다** —
 * 명세 보안 원칙이 "응답·예외 메시지·로그에 원문을 기록하지 않는다"로 못박았다.
 */
export interface SettlementAccountResponse {
  bankCode: string
  /** 서버가 코드로 찾아 준 은행명 — 화면이 코드 표로 만들지 않는다 */
  bankName: string
  /** 예: `123-****-**789` */
  maskedAccountNumber: string
  accountHolderName: string
  /**
   * 실명·계좌 검증 통과 여부. 명세: *"검증 기능을 도입하기 전에는 `false`를 반환하고
   * 검증 완료로 표시하지 않는다"* — 화면은 이 값이 false면 **미검증**으로 그린다.
   */
  verified: boolean
  updatedAt: IsoDateTime
}

/**
 * ⚠️ **조회 응답만 한 겹 감싸여 있다** — `{ "account": {...} }` 또는 `{ "account": null }`.
 * 등록·변경(PUT)은 계좌 객체를 그대로 주는데 조회(GET)만 래퍼다. 명세가 그렇게 적혀 있다.
 * 미등록을 404가 아니라 **`account: null`인 200으로** 표현하기 위한 형태로 보인다.
 */
interface SettlementAccountEnvelope {
  account: SettlementAccountResponse | null
}

/**
 * MEM-03 정산 계좌 조회.
 * 등록된 계좌가 없으면 `null`이다 — 오류가 아니라 정상 200이므로 화면은 등록 UI를 연다.
 */
export async function getSettlementAccount(): Promise<SettlementAccountResponse | null> {
  const { data } = await http.get<SettlementAccountEnvelope>(WALLETS_PATH.settlementAccount)
  return data.account
}

/**
 * MEM-03 정산 계좌 등록·변경. `PUT`이라 **같은 값으로 다시 보내도 계좌 행이 늘지 않는다**(멱등).
 * 진행 중 정산이 있으면 409 `SETTLEMENT_ACCOUNT_CHANGE_BLOCKED`로 막힌다.
 */
export async function putSettlementAccount(
  request: SettlementAccountRequest,
): Promise<SettlementAccountResponse> {
  const { data } = await http.put<SettlementAccountResponse>(
    WALLETS_PATH.settlementAccount,
    request,
  )
  return data
}
