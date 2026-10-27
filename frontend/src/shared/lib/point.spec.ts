import { describe, expect, it } from 'vitest'
import { formatAmount, formatLedgerTime, formatPoint, formatSignedPoint } from './point'

// 금액 포맷은 지갑·원장·결과 화면이 전부 공유한다 — 여기가 어긋나면 자릿수·부호가 화면마다 갈린다.
describe('formatPoint', () => {
  it('세 자리마다 쉼표를 넣고 재화 단위를 붙인다', () => {
    expect(formatPoint(1_234_567)).toBe('1,234,567P')
    expect(formatPoint(1_000, 'BETTING')).toBe('1,000BP')
  })

  it('음수여도 단위 표기는 절댓값 기준이다 — 부호는 formatSignedPoint의 몫', () => {
    expect(formatPoint(-3_000)).toBe('3,000P')
  })
})

describe('formatSignedPoint', () => {
  it('양수는 +, 음수는 U+2212 마이너스를 쓴다', () => {
    expect(formatSignedPoint(12_000)).toBe('+12,000P')
    // 하이픈(-)이 아니라 −(U+2212)다. 폭이 달라 tabular numbers 정렬이 흔들리기 때문.
    expect(formatSignedPoint(-3_000)).toBe('−3,000P')
  })

  it('0에는 부호를 붙이지 않는다', () => {
    expect(formatSignedPoint(0)).toBe('0P')
  })
})

describe('formatAmount', () => {
  it('단위 없이 숫자만 돌려준다', () => {
    expect(formatAmount(-82_000)).toBe('82,000')
  })
})

describe('formatLedgerTime', () => {
  // now를 주입받으므로 실행 시각에 흔들리지 않는다.
  const now = new Date('2026-07-27T15:30:00').getTime()

  it('오늘은 시각까지 보여준다', () => {
    expect(formatLedgerTime(new Date('2026-07-27T09:05:00').getTime(), now)).toBe('오늘 09:05')
  })

  it('어제도 시각까지 보여준다', () => {
    expect(formatLedgerTime(new Date('2026-07-26T21:12:00').getTime(), now)).toBe('어제 21:12')
  })

  it('일주일 안쪽은 "N일 전"으로 줄인다', () => {
    expect(formatLedgerTime(new Date('2026-07-24T10:00:00').getTime(), now)).toBe('3일 전')
  })

  // 서버는 ISO 문자열을 준다(`PointLedgerEntry.createdAt`). 목은 epoch ms라 둘 다 받아야 한다.
  it('ISO 문자열도 epoch와 같게 읽는다', () => {
    const iso = new Date('2026-07-27T09:05:00').toISOString()
    expect(formatLedgerTime(iso, now)).toBe('오늘 09:05')
    expect(formatLedgerTime(iso, now)).toBe(
      formatLedgerTime(new Date('2026-07-27T09:05:00').getTime(), now),
    )
  })

  it('파싱할 수 없으면 빈 문자열 — `Invalid Date`가 렌더되지 않게', () => {
    expect(formatLedgerTime('어제', now)).toBe('')
    expect(formatLedgerTime(Number.NaN, now)).toBe('')
  })
})
