// 원장 화면(`/wallet/ledger`)의 `?cur=` 쿼리 계약 (S15P11A704-340).
//
// 통화 탭은 "여기 이 탭을 보라"고 링크를 보내는 게 자연스러운 상태라 쿼리에 노출한다(docs/02 「화면 내 상태」).
// **쿼리가 정본이고 화면 상태는 그 사본이다.**
//
// 매핑을 여기 둔 이유: 보내는 쪽(마이페이지 지갑 카드)과 읽는 쪽(원장 화면)이 서로 다른 feature에 있다.
// 한쪽에 문자열을 박아두면 다른 쪽과 조용히 갈라진다 — 실제로 `?cur=bet`을 안 달고 스토어만 세팅하던
// 코드가 있었고, 원장이 쿼리를 정본으로 읽기 시작한 순간 "베팅 내역 보기"가 거래 탭으로 가게 됐다.
import type { WalletType } from '@/stores/wallet'

/** URL에 노출되는 값 — 스키마의 `TRADE`/`BETTING`을 그대로 쓰지 않는다(주소가 길고 시끄럽다) */
export type LedgerCurrency = 'trade' | 'bet'

const CUR_BY_WALLET: Record<WalletType, LedgerCurrency> = { TRADE: 'trade', BETTING: 'bet' }
const WALLET_BY_CUR: Record<LedgerCurrency, WalletType> = { trade: 'TRADE', bet: 'BETTING' }

export const DEFAULT_WALLET_TYPE: WalletType = 'TRADE'

export function curOf(walletType: WalletType): LedgerCurrency {
  return CUR_BY_WALLET[walletType]
}

export function isLedgerCurrency(value: unknown): value is LedgerCurrency {
  return value === 'trade' || value === 'bet'
}

/** 알아볼 수 없는 값이면 기본 지갑으로 떨어뜨린다 */
export function walletTypeOfCur(value: unknown): WalletType {
  return isLedgerCurrency(value) ? WALLET_BY_CUR[value] : DEFAULT_WALLET_TYPE
}
