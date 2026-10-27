// 금액 입력 필드 공용 헬퍼 — 입찰 dock과 예측 dock 두 곳에서 쓴다.
// 디자인 원본(`비드라이브 인터랙티브.html`)의 `commafy`와 같은 동작이다.

import { formatAmount } from './point'

/** "52,000" → 52000. 숫자가 아닌 문자는 전부 버린다. */
export function parseAmount(raw: string): number {
  return Number(raw.replace(/[^\d]/g, '')) || 0
}

/**
 * 입력 중 천 단위 콤마를 다시 찍는다.
 *
 * 값만 바꾸면 콤마가 끼는 순간 커서가 문자열 끝으로 튄다.
 * 길이 변화만큼 커서를 밀어 주면 "1,2|34"에서 타이핑을 이어갈 수 있다.
 * 반환값은 파싱된 숫자.
 */
export function commafyInput(el: HTMLInputElement): number {
  const cursor = el.selectionStart ?? el.value.length
  const lengthBefore = el.value.length
  const value = parseAmount(el.value)

  el.value = value > 0 ? formatAmount(value) : ''

  const next = Math.max(0, cursor + (el.value.length - lengthBefore))
  el.setSelectionRange(next, next)
  return value
}
