// 채팅 스크롤 보정 헬퍼 (S15P11A704-392, 399).
//
// 채팅 상한(capChat)이 배열에서 오래된 행을 버릴 때, 위로 과거를 읽는 중이면 사라진 만큼 시야가 위로 밀린다.
// 그만큼 scrollTop을 줄여 읽던 위치를 고정하려면 "어느 행들이 사라졌는가"를 알아야 한다. 그 판정만 순수 함수로 뺀다
// (픽셀 측정·scrollTop 적용은 레이아웃이 필요해 ChatPanel의 브라우저 경로에 둔다).
//
// ⚠️ 초기엔 트림이 앞에서 연속으로만 일어난다고 봤지만(countLeadingRemoved), capChat(399)이 낙관 행(pending/failed)을
// 건너뛰고 **그 뒤의 확정 메시지**를 버리면서 제거가 중간에서·불연속으로 일어날 수 있게 됐다 — 그래서 집합 차로 일반화한다.

/**
 * prev 대비 next에서 사라진 행들의 **prev 기준 인덱스**(오름차순)를 준다. 트림이 앞단 연속이 아니어도
 * (capChat이 낙관 행을 건너뛰고 중간 확정 메시지를 버려도) 정확히 잡는다.
 * prev 행이 하나도 안 살아남으면(전면 교체·재시드) 앵커 기준이 없어 판정 불가로 보고 빈 배열을 준다
 * — 그러지 않으면 전체 높이만큼 빼 맨 위로 튄다(리뷰 nit). 현행 채팅 흐름엔 그런 경로가 없지만 안전망으로 둔다.
 */
export function removedRowIndices(
  prevKeys: readonly (string | number)[],
  nextKeys: readonly (string | number)[],
): number[] {
  if (prevKeys.length === 0 || nextKeys.length === 0) return []
  const surviving = new Set(nextKeys)
  const indices: number[] = []
  prevKeys.forEach((key, i) => {
    if (!surviving.has(key)) indices.push(i)
  })
  if (indices.length === prevKeys.length) return [] // 하나도 안 살아남음 = 전면 교체 → 보정 안 함
  return indices
}

/**
 * next 앞쪽에 새로 붙은(prev에 없던) 행 수 — 과거 조회(S15P11A704-379)가 앞에 이전 메시지를 prepend할 때 그 개수다.
 * prev가 비어 있으면(초기 로드) prepend가 아니므로 0. 꼬리 추가(next[0]이 prev에 있음)면 0.
 */
export function prependedRowCount(
  prevKeys: readonly (string | number)[],
  nextKeys: readonly (string | number)[],
): number {
  if (prevKeys.length === 0) return 0
  const prevSet = new Set(prevKeys)
  let n = 0
  while (n < nextKeys.length && !prevSet.has(nextKeys[n])) n += 1
  return n
}

/**
 * next 뒤쪽(꼬리)에 새로 붙은(prev에 없던) 행 수 — 라이브 새 메시지 수다(미읽음 pill 카운트, S15P11A704-375·379).
 * prepend(앞에만 추가)면 꼬리가 prev에 있어 0. prev가 비면(초기 로드) 0 — 새 메시지로 안 센다.
 * prependedRowCount의 대칭 — 뒤에서부터 prev에 없는 키를 센다(리뷰 2). 한 tick에 여러 건 붙어도 정확.
 */
export function appendedRowCount(
  prevKeys: readonly (string | number)[],
  nextKeys: readonly (string | number)[],
): number {
  if (prevKeys.length === 0) return 0
  const prevSet = new Set(prevKeys)
  let n = 0
  let i = nextKeys.length - 1
  while (i >= 0 && !prevSet.has(nextKeys[i])) {
    n += 1
    i -= 1
  }
  // 전부 새 키면(prev가 비지 않았는데) 전면 교체다 — 새 메시지로 세면 미읽음이 튄다(removedRowIndices와 대칭 가드, 리뷰 A).
  if (n === nextKeys.length) return 0
  return n
}
