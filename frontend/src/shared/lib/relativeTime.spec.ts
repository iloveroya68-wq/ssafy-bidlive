import { describe, expect, it } from 'vitest'
import { formatRelativeDay, formatRelativeDayIso, formatUpcomingAt } from './relativeTime'

// 날짜 경계는 눈으로 안 잡힌다 — "어제 23:50"이 오늘로 보이거나, 6일/7일에서 표기가 안 바뀌어도
// 화면은 멀쩡해 보인다. 기준 시각을 고정해 경계마다 못을 박는다.

/** 2026-07-28 (화) 10:00 */
const NOW = new Date(2026, 6, 28, 10, 0, 0).getTime()
const at = (y: number, m: number, d: number, h = 12, min = 0) =>
  new Date(y, m - 1, d, h, min).getTime()

describe('formatRelativeDay', () => {
  it('같은 날이면 오늘', () => {
    expect(formatRelativeDay(at(2026, 7, 28, 0, 1), NOW)).toBe('오늘')
    expect(formatRelativeDay(at(2026, 7, 28, 23, 59), NOW)).toBe('오늘')
  })

  it('하루 전이면 어제 — 시각이 아니라 달력 날짜로 센다', () => {
    // 24시간 미만 차이지만 달력상 전날이다
    expect(formatRelativeDay(at(2026, 7, 27, 23, 50), NOW)).toBe('어제')
    expect(formatRelativeDay(at(2026, 7, 27, 0, 5), NOW)).toBe('어제')
  })

  it('2~6일 전은 N일 전', () => {
    expect(formatRelativeDay(at(2026, 7, 26), NOW)).toBe('2일 전')
    expect(formatRelativeDay(at(2026, 7, 22), NOW)).toBe('6일 전')
  })

  it('7일째부터 날짜로 넘어간다 — 경계', () => {
    expect(formatRelativeDay(at(2026, 7, 21), NOW)).toBe('7월 21일')
  })

  it('해를 넘겨도 월·일로 적는다', () => {
    expect(formatRelativeDay(at(2025, 12, 31), NOW)).toBe('12월 31일')
  })

  it('미래 시각은 오늘로 접는다 — 서버·클라이언트 시계 차 방어', () => {
    expect(formatRelativeDay(NOW + 5_000, NOW)).toBe('오늘')
    expect(formatRelativeDay(at(2026, 7, 30), NOW)).toBe('오늘')
  })

  it('숫자가 아니면 빈 문자열', () => {
    expect(formatRelativeDay(Number.NaN, NOW)).toBe('')
  })
})

describe('formatRelativeDayIso', () => {
  it('ISO 문자열을 받는다', () => {
    expect(formatRelativeDayIso(new Date(at(2026, 7, 27)).toISOString(), NOW)).toBe('어제')
  })

  it('null·파싱 불가는 빈 문자열 — 호출부가 그 자리를 비운다', () => {
    expect(formatRelativeDayIso(null, NOW)).toBe('')
    expect(formatRelativeDayIso('종료됨', NOW)).toBe('')
  })
})

// ROOM-08 「곧 시작해요」 스트립 카드 (S15P11A704-542).
// 상대 표기는 **서버 시각 기준**이라(규칙 5) 오프셋 유무로 결과가 갈린다 —
// 오프셋이 빠진 채 단말 시계로 세어도 화면은 멀쩡해 보인다.
describe('formatUpcomingAt', () => {
  /** 기준 시각의 21분 뒤 (같은 로컬 타임존) */
  const SOON = new Date(at(2026, 7, 28, 10, 21)).toISOString()

  it('오프셋이 있으면 절대 시각 + 남은 분', () => {
    expect(formatUpcomingAt(SOON, 0, NOW)).toBe('10:21 · 21분 후')
    // 서버가 단말보다 6분 앞서 있으면 그만큼 덜 남았다
    expect(formatUpcomingAt(SOON, 6 * 60_000, NOW)).toBe('10:21 · 15분 후')
  })

  it('오프셋이 null이면 절대 시각만 — 남은 시간을 지어내지 않는다', () => {
    expect(formatUpcomingAt(SOON, null, NOW)).toBe('10:21')
  })

  it('1분 미만은 "곧 시작" — 0분 후·음수를 보여줄 값이 아니다', () => {
    expect(formatUpcomingAt(new Date(at(2026, 7, 28, 10, 0)).toISOString(), 0, NOW)).toBe(
      '10:00 · 곧 시작',
    )
    expect(formatUpcomingAt(new Date(at(2026, 7, 28, 9, 58)).toISOString(), 0, NOW)).toBe(
      '09:58 · 곧 시작',
    )
  })

  it('파싱할 수 없으면 빈 문자열 — 호출부가 그 자리를 비운다', () => {
    expect(formatUpcomingAt('곧시작', 0, NOW)).toBe('')
  })
})
