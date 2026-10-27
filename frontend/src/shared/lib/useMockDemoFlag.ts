// 목 전용 시연·검증 트리거 쿼리 (S15P11A704-470, !184 리뷰 1)
//
// `?chatdemo=1`(453)과 `?audiodemo=1`(470)이 **로직이 완전히 같아** 키만 달랐다. 두 번째 등장이라
// 여기로 뽑았다(AGENTS 컨벤션: 공통은 두 번째로 등장할 때 추출). 넷째 트리거는 호출 한 줄이면 된다.
// 값 기반인 `?reveal=sold|nosale|canceled`는 성격이 달라 포함하지 않는다 — 켬/끔이 아니라 어느 시나리오냐다.
//
// 셋의 공통 근거는 `docs/02-협업-가이드.md`에 한 문단으로 있다 — **시연도 목 모드로 돈다.**
// 그래서 목 모드만으로 켜지지 않고 쿼리 옵트인이어야 한다. 상시로 켜면 심사위원 화면에 시연용 버튼이
// 보이고, 잊었을 때의 비용이 한쪽으로만 쏠려 있다.
import { computed, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'
import { isMockApi } from '@/shared/api/mock'

/**
 * 목 모드에서만 켜지는 쿼리 스위치. 실 모드에서는 쿼리를 붙여도 항상 `false`다 —
 * `isMockApi()` 가드가 **먼저** 와서 쿼리가 아무 힘도 갖지 못한다.
 *
 * 손으로 치는 쿼리라 켜는 쪽을 관대하게 본다. vue-router가 파싱한 값이 셋으로 갈린다:
 * - `?k` → `null` (등호 없음)
 * - `?k=` → `''`
 * - `?k=1` → `'1'`
 *
 * 셋 다 켬으로 보고, 끄는 표기는 `?k=0` 하나뿐이다(쿼리를 지우는 것과 같게).
 * 같은 키를 반복하면(`?k=1&k=0`) 배열이 와서 켬이 되는데, 켜고 끄는 스위치에 그렇게 칠 이유가 없어 둔다.
 *
 * `defaultOn`은 **쿼리가 없을 때의 값**이다(S15P11A704-475). 둘이 갈린다:
 * - `?chatdemo` (기본 끔) — 시연 스트립은 켤 때만 필요하다
 * - `?audiodemo` (기본 켬) — 목 모드에 오디오 컨트롤이 원래 안 뜨는데, 쿼리를 붙여야 보이면
 *   확인 비용이 너무 크다. 그래서 기본으로 켜고 `?audiodemo=0`으로 감춘다.
 *
 * 어느 쪽이든 `isMockApi()` 가드가 먼저다 — 실 모드에서는 `defaultOn`도 무시된다.
 */
export function useMockDemoFlag(key: string, defaultOn = false): ComputedRef<boolean> {
  const route = useRoute()
  return computed(() => {
    if (!isMockApi()) return false
    const flag = route.query[key]
    if (flag === undefined) return defaultOn
    return flag !== '0'
  })
}
