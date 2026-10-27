import { describe, expect, it } from 'vitest'
import { planReplay, type BufferedEvent } from './snapshotReplay'

// WS-03 재생 계획(3·4·5단계)의 경계 분기 — 버전 폐기/오름차순/불연속·역전/무버전 혼재(S15P11A704-228, 374).
// 순수 함수라 스토어 없이 검증한다.
const ev = (type: string, version?: number): BufferedEvent => ({
  type,
  data: version === undefined ? {} : { stateVersion: version },
})

describe('planReplay', () => {
  it('버전이 하나도 없으면 도착 순서 그대로 재생한다(현행 명세 — 이벤트에 stateVersion 없음)', () => {
    const buffered = [ev('A'), ev('B'), ev('C')]
    const plan = planReplay(buffered, 10)
    expect(plan.apply).toEqual(buffered)
    expect(plan.needsResync).toBe(false)
    expect(plan.discarded).toBe(0)
  })

  it('빈 버퍼는 빈 계획', () => {
    const plan = planReplay([], 5)
    expect(plan.apply).toEqual([])
    expect(plan.needsResync).toBe(false)
    expect(plan.discarded).toBe(0)
  })

  it('스냅샷 이하 버전은 폐기하고, 큰 것만 오름차순으로 적용한다(도착이 뒤섞여도 정렬)', () => {
    const buffered = [ev('e12', 12), ev('e10', 10), ev('e9', 9), ev('e11', 11), ev('e13', 13)]
    const plan = planReplay(buffered, 10)
    expect(plan.discarded).toBe(2) // 9, 10 폐기
    expect(plan.apply.map((e) => e.type)).toEqual(['e11', 'e12', 'e13'])
    expect(plan.needsResync).toBe(false)
  })

  it('버전 공백(불연속)이면 추측 적용하지 않고 재조회를 요구한다', () => {
    const plan = planReplay([ev('e11', 11), ev('e13', 13)], 10) // 12 누락
    expect(plan.needsResync).toBe(true)
  })

  it('선두 공백(스냅샷+1이 아님)도 재조회를 요구한다', () => {
    const plan = planReplay([ev('e12', 12), ev('e13', 13)], 10) // 11부터여야 하는데 12부터
    expect(plan.needsResync).toBe(true)
  })

  it('버전 중복·역전이면 재조회를 요구한다', () => {
    const plan = planReplay([ev('e11', 11), ev('e11b', 11)], 10) // 11 다음 기대는 12
    expect(plan.needsResync).toBe(true)
  })

  it('버전·무버전이 섞이면 상대 순서를 확신할 수 없어 재조회를 요구한다', () => {
    const plan = planReplay([ev('vers', 11), ev('unversioned')], 10)
    expect(plan.needsResync).toBe(true)
  })

  it('stateVersion이 숫자가 아니면(문자열 등) 무버전 취급 — 신호 없이 조용히 best-effort로 떨어진다', () => {
    // BE 전환 과도기에 stateVersion이 "12" 문자열로 오면 versionOf가 null → 전부 무버전 → 조기 반환.
    // 혼재(위 테스트)와 달리 needsResync조차 안 뜨는 케이스라 그 성질을 고정한다.
    const buffered: BufferedEvent[] = [{ type: 'e12', data: { stateVersion: '12' } }]
    const plan = planReplay(buffered, 10)
    expect(plan.apply).toEqual(buffered)
    expect(plan.needsResync).toBe(false)
    expect(plan.discarded).toBe(0)
  })
})
