// PNT-02 원장 행 표기 규칙 — 두 델타(`availableChange`/`heldChange`)를 화면 문구로 옮긴다.
//
// **행 설명은 서버가 준 `description`을 그대로 쓴다.** 명세 「조회·표시 규칙」:
//   "description은 서버가 transactionType과 referenceType을 기준으로 생성하며
//    프론트가 enum을 조합해 만들지 않는다"
// 그래서 여기 있는 건 배지 한 단어와 금액 표기 규칙뿐이다.
import type { PointLedgerEntry, PointTransactionType } from '@/shared/api/wallets'

/** 표기 함수들은 응답 행을 그대로 받는다 — 목 전용 타입에 묶지 않는다 */
type LedgerEntry = PointLedgerEntry

/** 배지 톤 — up: 사용 가능 증가, dn: 감소, hold: 보류/해제(총 보유는 그대로) */
export type LedgerTone = 'up' | 'dn' | 'hold'

/**
 * `transactionType` → 한글 배지.
 *
 * 명세 8종이 기획 항목보다 거칠다 — 가입 축하·출석·시청 보상이 전부 `GRANT`로 뭉치고
 * 입찰 홀드와 예측 스테이크가 `HOLD`/`BET`으로만 갈린다. **그 구분은 배지가 아니라
 * `description`이 한다** — 그래서 여기서 억지로 세분화하지 않는다.
 *
 * 쿼리 파라미터 설명에 "등"이 붙어 있어 값이 늘어날 수 있다 → 모르는 값은 '기타'로 떨어뜨린다.
 */
const KIND_LABEL: Record<PointTransactionType, string> = {
  CHARGE: '충전',
  HOLD: '보류',
  RELEASE: '해제',
  SETTLEMENT: '정산',
  REFUND: '환불',
  GRANT: '지급',
  BET: '베팅',
  PAYOUT: '배당',
}

export function ledgerKindLabel(entry: LedgerEntry): string {
  return KIND_LABEL[entry.transactionType] ?? '기타'
}

/**
 * 총 보유(= 가용 + 보류) 증감.
 * 보류/해제 행은 두 델타의 합이 0이라 이 값이 0이 된다 — 금액 대신 이동으로 표기해야 하는 케이스다.
 */
export function ledgerNetDelta(entry: LedgerEntry): number {
  return entry.availableChange + entry.heldChange
}

/** 총 보유가 변하지 않는 행(보류 ↔ 가용 이동)인지 */
export function isHoldTransfer(entry: LedgerEntry): boolean {
  return ledgerNetDelta(entry) === 0
}

export function ledgerTone(entry: LedgerEntry): LedgerTone {
  const net = ledgerNetDelta(entry)
  if (net === 0) return 'hold'
  return net > 0 ? 'up' : 'dn'
}

/**
 * 보류 이동 행의 금액 문구용 부가 설명.
 * `heldChange`가 +면 가용 → 보류, −면 보류 → 가용. (명세 §조회·표시 규칙의 두 문장과 같은 판정)
 */
export function holdDirectionLabel(entry: LedgerEntry): string {
  return entry.heldChange > 0 ? '사용 가능 → 보류' : '보류 → 사용 가능'
}
