import { describe, expect, it } from 'vitest'
import type { PointLedgerEntry as LedgerEntry } from '@/shared/api/wallets'
import {
  holdDirectionLabel,
  isHoldTransfer,
  ledgerKindLabel,
  ledgerNetDelta,
  ledgerTone,
} from './ledgerLabel'

// 원장 행 표기는 **두 델타의 부호 조합**으로 갈린다. 조합을 잘못 읽으면 홀드 행이
// "증감 0"으로 보이거나 이동 방향이 반대로 뜨는데, 둘 다 렌더는 멀쩡해서 조용히 지나간다.
// 명세 「조회·표시 규칙」의 두 문장이 판정의 단일 소스다:
//   입찰 홀드 = availableChange < 0, heldChange > 0
//   보류 해제 = availableChange > 0, heldChange < 0

function entry(overrides: Partial<LedgerEntry>): LedgerEntry {
  return {
    ledgerId: 1,
    walletType: 'TRADE',
    transactionType: 'CHARGE',
    availableChange: 0,
    heldChange: 0,
    availableBalanceAfter: 0,
    heldBalanceAfter: 0,
    createdAt: '2026-07-28T00:00:00+09:00',
    description: '',
    referenceType: 'PAYMENT',
    referenceId: null,
    ...overrides,
  }
}

describe('ledgerNetDelta / isHoldTransfer', () => {
  it('입찰 홀드는 두 델타의 합이 0이라 이동 행으로 판정한다', () => {
    const hold = entry({ transactionType: 'HOLD', availableChange: -82_000, heldChange: 82_000 })
    expect(ledgerNetDelta(hold)).toBe(0)
    expect(isHoldTransfer(hold)).toBe(true)
  })

  it('보류 해제도 이동 행이다', () => {
    const release = entry({
      transactionType: 'RELEASE',
      availableChange: 61_000,
      heldChange: -61_000,
    })
    expect(ledgerNetDelta(release)).toBe(0)
    expect(isHoldTransfer(release)).toBe(true)
  })

  it('충전은 가용만 늘어 이동 행이 아니다', () => {
    const charge = entry({ transactionType: 'CHARGE', availableChange: 100_000 })
    expect(ledgerNetDelta(charge)).toBe(100_000)
    expect(isHoldTransfer(charge)).toBe(false)
  })

  it('보류분이 그대로 빠지는 정산은 총 보유가 줄어든다 — 이동이 아니다', () => {
    // 낙찰 결제: 가용은 그대로고 보류만 사라진다. 합이 0이 아니므로 감소 행이다.
    const settle = entry({ transactionType: 'SETTLEMENT', availableChange: 0, heldChange: -74_000 })
    expect(ledgerNetDelta(settle)).toBe(-74_000)
    expect(isHoldTransfer(settle)).toBe(false)
  })

  it('배당은 보류 해제와 지급이 한 행에 겹쳐도 순증분만 남는다', () => {
    // 스테이크 300 보류 해제 + 배당 900 지급 → 총 보유 +600
    const payout = entry({ transactionType: 'PAYOUT', availableChange: 900, heldChange: -300 })
    expect(ledgerNetDelta(payout)).toBe(600)
    expect(isHoldTransfer(payout)).toBe(false)
  })
})

describe('ledgerTone', () => {
  it.each([
    ['충전', { availableChange: 50_000, heldChange: 0 }, 'up'],
    ['환불(출금)', { availableChange: -50_000, heldChange: 0 }, 'dn'],
    ['입찰 홀드', { availableChange: -82_000, heldChange: 82_000 }, 'hold'],
    ['보류 해제', { availableChange: 61_000, heldChange: -61_000 }, 'hold'],
  ])('%s → %s', (_label, changes, expected) => {
    expect(ledgerTone(entry(changes))).toBe(expected)
  })
})

describe('holdDirectionLabel', () => {
  it('보류가 늘면 가용 → 보류', () => {
    expect(holdDirectionLabel(entry({ availableChange: -300, heldChange: 300 }))).toBe(
      '사용 가능 → 보류',
    )
  })

  it('보류가 줄면 보류 → 가용', () => {
    expect(holdDirectionLabel(entry({ availableChange: 300, heldChange: -300 }))).toBe(
      '보류 → 사용 가능',
    )
  })
})

describe('ledgerKindLabel', () => {
  it('명세 8종을 전부 한글 배지로 옮긴다', () => {
    const types = [
      'CHARGE',
      'HOLD',
      'RELEASE',
      'SETTLEMENT',
      'REFUND',
      'GRANT',
      'BET',
      'PAYOUT',
    ] as const
    const labels = types.map((t) => ledgerKindLabel(entry({ transactionType: t })))
    expect(labels).toEqual(['충전', '보류', '해제', '정산', '환불', '지급', '베팅', '배당'])
  })

  it('명세에 "등"이 붙어 있어 값이 늘 수 있다 — 모르는 값은 기타로 떨어진다', () => {
    // 서버가 새 유형을 추가해도 배지가 undefined로 비지 않아야 한다
    const unknown = entry({ transactionType: 'CASHBACK' as never })
    expect(ledgerKindLabel(unknown)).toBe('기타')
  })
})
