// PNT 지갑·원장 API의 **테스트 전용 가짜 서버**.
//
// 지갑 조회(494)·원장(495)·환불(497)이 dev에 들어오면서 `shared/api/wallets.ts`의 목 분기를
// 지웠다(S15P11A704-504). 그런데 지갑 스펙이 잡는 것들 — 커서 이어 붙이기, 지갑별 커서 분리,
// 충전·출금 뒤 잔액이 **서버 응답 기준**으로 갱신되는지 — 은 응답이 상태를 들고 있어야 성립한다.
// 정적 응답을 하나 고정하면 "충전했는데 다음 조회에서 되돌아간다"를 잡던 검사가 통째로 죽는다.
//
// ⚠️ **화면 코드는 이 파일을 임포트하지 않는다.** 스펙에서 `http`를 스텁할 때만 쓴다.
//    API 함수가 아니라 `http`를 스텁하는 이유는 `mocks/roomServer.ts`와 같다 —
//    스펙이 `getMyWallets`·`getPointLedgers` 호출 횟수를 직접 센다.

import { mockCursorPage } from '@/shared/api/mock'
import { mockBettingLedger, mockTradeLedger, mockWallets } from '@/mocks/wallet'
import { toIsoDateTime } from '@/shared/api/types'
import type {
  MyWalletsResponse,
  PointLedgerEntry,
  PointLedgerQuery,
  TradeChargeResponse,
  TradeRefundResponse,
  PointTransactionType,
  WalletBalance,
  WalletType,
} from '@/shared/api/wallets'
import type { CursorPage } from '@/shared/api/types'

/**
 * **쓰기가 남는 상태.** 정적 배열을 그대로 읽으면 충전·출금이 응답에만 반영되고 다음 조회에서
 * 되돌아간다 — 실제 서버라면 있을 수 없는 일이고, 그 차이를 잡는 테스트가 있다.
 * 테스트 간 격리는 `resetWalletServer()`로 한다.
 */
let walletState = mockWallets.map((w) => ({ ...w }))
let ledgerState = [...mockTradeLedger, ...mockBettingLedger].map((e) => ({ ...e }))

/** 스펙의 `beforeEach`에서 부른다 — 앞 테스트의 충전·출금이 다음 테스트로 새면 안 된다 */
export function resetWalletServer(): void {
  walletState = mockWallets.map((w) => ({ ...w }))
  ledgerState = [...mockTradeLedger, ...mockBettingLedger].map((e) => ({ ...e }))
}

function balanceOf(walletType: WalletType): WalletBalance {
  const wallet = walletState.find((w) => w.walletType === walletType)
  const availableAmount = wallet?.availableBalance ?? 0
  const heldAmount = wallet?.heldBalance ?? 0
  return { balance: availableAmount + heldAmount, heldAmount, availableAmount }
}

/** 가용 잔액 증감을 적용하고 원장 행을 앞에 남긴다 */
function applyTradeChange(
  availableChange: number,
  transactionType: PointTransactionType,
  description: string,
): WalletBalance {
  const wallet = walletState.find((w) => w.walletType === 'TRADE')
  if (wallet) wallet.availableBalance += availableChange
  const balance = balanceOf('TRADE')
  ledgerState.unshift({
    id: Date.now(),
    walletType: 'TRADE',
    transactionType,
    availableChange,
    heldChange: 0,
    availableBalanceAfter: balance.availableAmount,
    heldBalanceAfter: balance.heldAmount,
    createdAt: Date.now(),
    description,
    referenceType: 'PAYMENT',
    referenceId: null,
  })
  return balance
}

/** PNT-01 이중 지갑 조회 */
function walletsResponse(): MyWalletsResponse {
  return { tradeWallet: balanceOf('TRADE'), bettingWallet: balanceOf('BETTING') }
}

/** PNT-02 원장 조회 — `createdAt DESC, ledgerId DESC` (명세 조회 순서) */
function ledgersResponse(query: PointLedgerQuery = {}): CursorPage<PointLedgerEntry> {
  const all = [...ledgerState]
    .sort((a, b) => b.createdAt - a.createdAt || b.id - a.id)
    .map<PointLedgerEntry>(({ id, createdAt, ...rest }) => ({
      ...rest,
      ledgerId: id,
      createdAt: toIsoDateTime(createdAt),
    }))
    .filter(
      (entry) =>
        (query.walletType === undefined || entry.walletType === query.walletType) &&
        (query.transactionType === undefined || entry.transactionType === query.transactionType),
    )
  return mockCursorPage(all, query, 20)
}

/** PNT-01 거래 포인트 충전 */
function chargeResponse(amount: number): TradeChargeResponse {
  const balance = applyTradeChange(amount, 'CHARGE', '포인트 충전 · 테스트 결제')
  return {
    walletType: 'TRADE',
    chargedAmount: amount,
    balance: balance.balance,
    heldAmount: balance.heldAmount,
    availableAmount: balance.availableAmount,
    ledgerId: 1,
    chargedAt: toIsoDateTime(Date.now()),
  }
}

/** PNT-01 거래 포인트 환불(= 화면상 "출금"). 보류분은 대상이 아니다 — 서버도 같은 제약 */
function refundResponse(amount: number): TradeRefundResponse {
  const available = balanceOf('TRADE').availableAmount
  const balance = applyTradeChange(
    -Math.min(amount, available),
    'REFUND',
    '등록 계좌로 출금 · 테스트 송금',
  )
  return {
    walletType: 'TRADE',
    refundedAmount: amount,
    balance: balance.balance,
    heldAmount: balance.heldAmount,
    availableAmount: balance.availableAmount,
    ledgerId: 2,
    refundedAt: toIsoDateTime(Date.now()),
  }
}

/**
 * 스펙에서 `http.get`·`http.post`에 그대로 물리는 디스패처.
 * 경로가 겹치지 않아 GET·POST 한 함수로 처리한다.
 *
 * ```ts
 * vi.spyOn(http, 'get').mockImplementation(walletHttpStub as never)
 * vi.spyOn(http, 'post').mockImplementation(walletHttpStub as never)
 * ```
 */
export async function walletHttpStub(
  url: string,
  bodyOrConfig?: unknown,
): Promise<{ data: unknown }> {
  if (url === '/members/me/wallets') return { data: walletsResponse() }
  if (url === '/members/me/point-ledgers') {
    const config = bodyOrConfig as { params?: PointLedgerQuery } | undefined
    return { data: ledgersResponse(config?.params ?? {}) }
  }
  const amount = (bodyOrConfig as { amount?: number } | undefined)?.amount ?? 0
  if (url === '/members/me/wallets/trade/charge') return { data: chargeResponse(amount) }
  if (url === '/members/me/wallets/trade/refunds') return { data: refundResponse(amount) }
  throw new Error(`가짜 서버에 없는 경로: ${url}`)
}
