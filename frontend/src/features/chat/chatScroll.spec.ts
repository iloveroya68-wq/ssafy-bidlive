import { describe, expect, it } from 'vitest'
import { appendedRowCount, prependedRowCount, removedRowIndices } from './chatScroll'

// 사라진 행 인덱스 판정(S15P11A704-392, 399) — scrollTop 보정량 산출의 근거라 경계 조건을 고정한다.
// capChat(399)이 낙관 행을 건너뛰고 중간 확정 메시지를 버릴 수 있어, 앞단 연속뿐 아니라 중간·불연속도 잡아야 한다.
describe('removedRowIndices', () => {
  it('상한 도달 후 1건 추가 — 앞에서 1개 제거(index 0)', () => {
    expect(removedRowIndices(['a', 'b', 'c'], ['b', 'c', 'd'])).toEqual([0])
  })

  it('앞에서 2개 제거', () => {
    expect(removedRowIndices(['a', 'b', 'c', 'd'], ['c', 'd', 'e'])).toEqual([0, 1])
  })

  it('낙관 행이 맨 앞이면 그 뒤 확정 메시지가 중간에서 제거된다(capChat 399)', () => {
    // prev [f, c1, c2] → next [f, c2, d]: f는 보호, c1(index 1)이 사라짐
    expect(removedRowIndices(['f', 'c1', 'c2'], ['f', 'c2', 'd'])).toEqual([1])
  })

  it('낙관 행이 사이사이 끼면 제거가 불연속일 수 있다', () => {
    // prev [f1, c1, f2, c2] → next [f1, f2, d]: c1(1)·c2(3) 제거
    expect(removedRowIndices(['f1', 'c1', 'f2', 'c2'], ['f1', 'f2', 'd'])).toEqual([1, 3])
  })

  it('상한 전 단순 추가 — 제거 없음', () => {
    expect(removedRowIndices(['a', 'b'], ['a', 'b', 'c'])).toEqual([])
  })

  it('전면 교체(prev 행이 하나도 안 살아남음)는 판정 불가로 빈 배열 — 맨 위로 튀지 않게', () => {
    expect(removedRowIndices(['a', 'b'], ['x', 'y'])).toEqual([])
  })

  it('제자리 교체(에코 승격 등) — 키 순서 그대로라 제거 없음', () => {
    expect(removedRowIndices(['a', 'b'], ['a', 'b'])).toEqual([])
  })

  it('빈 배열은 빈 결과', () => {
    expect(removedRowIndices([], ['a'])).toEqual([])
    expect(removedRowIndices(['a'], [])).toEqual([])
  })

  it('숫자 키(낙관 행 임시 id 등)도 동일하게 잡는다', () => {
    expect(removedRowIndices([-3, -2, -1], [-2, -1, 5])).toEqual([0])
  })
})

// 앞에 붙은(prepend) 행 수 판정(S15P11A704-379) — 과거 조회 prepend 높이 보정의 근거.
describe('prependedRowCount', () => {
  it('앞에 2건 prepend — 2', () => {
    expect(prependedRowCount(['c', 'd'], ['a', 'b', 'c', 'd'])).toBe(2)
  })

  it('꼬리 추가(next[0]이 prev에 있음)는 prepend 아님 — 0', () => {
    expect(prependedRowCount(['a', 'b'], ['a', 'b', 'c'])).toBe(0)
  })

  it('제자리 교체 — 0', () => {
    expect(prependedRowCount(['a', 'b'], ['a', 'b'])).toBe(0)
  })

  it('prev가 비어 있으면(초기 로드) prepend가 아니므로 0', () => {
    expect(prependedRowCount([], ['a', 'b'])).toBe(0)
  })
})

// 꼬리에 붙은(라이브 새 메시지) 행 수 판정(S15P11A704-375·379) — prependedRowCount의 대칭.
describe('appendedRowCount', () => {
  it('꼬리에 2건 추가 — 2', () => {
    expect(appendedRowCount(['a', 'b'], ['a', 'b', 'c', 'd'])).toBe(2)
  })

  it('상한 트림 + 꼬리 추가: 앞이 잘려도 꼬리 새 것만 센다', () => {
    expect(appendedRowCount(['a', 'b', 'c'], ['b', 'c', 'd'])).toBe(1) // d 추가(a 트림)
  })

  it('prepend(앞에만 추가)는 꼬리가 그대로라 0', () => {
    expect(appendedRowCount(['c', 'd'], ['a', 'b', 'c', 'd'])).toBe(0)
  })

  it('제자리 교체 — 0', () => {
    expect(appendedRowCount(['a', 'b'], ['a', 'b'])).toBe(0)
  })

  it('prev가 비면(초기 로드) 0 — 새 메시지로 안 센다', () => {
    expect(appendedRowCount([], ['a', 'b'])).toBe(0)
  })

  it('전면 교체(prev 비지 않았는데 전부 새 키)는 0 — 미읽음이 튀지 않게(removedRowIndices와 대칭)', () => {
    expect(appendedRowCount(['a', 'b'], ['x', 'y'])).toBe(0)
  })
})
