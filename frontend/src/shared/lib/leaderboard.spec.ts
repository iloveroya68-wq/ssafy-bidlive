import { describe, expect, it } from 'vitest'
import { formatPayoutRate, rankMark } from './leaderboard'

describe('rankMark', () => {
  it('1~3위는 메달', () => {
    expect(rankMark(1)).toBe('🥇')
    expect(rankMark(2)).toBe('🥈')
    expect(rankMark(3)).toBe('🥉')
  })

  it('4위부터는 숫자 — 서버가 주는 rank는 1부터다', () => {
    // 티커는 0-based 인덱스로 메달을 붙이고 있었다. 그대로 rank를 넘기면 한 칸씩 밀린다.
    expect(rankMark(4)).toBe('4위')
    expect(rankMark(10)).toBe('10위')
  })
})

describe('formatPayoutRate', () => {
  it('이미 100을 곱해 온 값이라 그대로 붙인다', () => {
    expect(formatPayoutRate(312)).toBe('312%')
  })

  it('소수는 반올림한다', () => {
    expect(formatPayoutRate(187.6)).toBe('188%')
  })

  it('부호는 본전(100%)을 넘을 때만 — 50%에 +를 붙이면 거짓말이다', () => {
    expect(formatPayoutRate(312, { withSign: true })).toBe('+312%')
    expect(formatPayoutRate(50, { withSign: true })).toBe('50%')
    expect(formatPayoutRate(100, { withSign: true })).toBe('100%')
  })
})
