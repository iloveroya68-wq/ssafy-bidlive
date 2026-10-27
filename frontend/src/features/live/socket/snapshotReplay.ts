// WS-03 스냅샷 재생 규칙 (S15P11A704-228) — docs/03 §6-5의 3·4·5단계를 순수 함수로 뺀다.
//
// 절차(전체):
//   1. SUBSCRIBE에 receipt를 넣고 RECEIPT 수신 전 이벤트는 버퍼링한다.   ← liveSocket.ts
//   2. 스냅샷을 조회해 스토어에 반영한다(applySnapshot).                  ← liveSocket.ts
//   3. 버퍼에서 event.stateVersion <= snapshot.stateVersion인 것은 폐기한다.   ┐
//   4. 더 큰 버전만 오름차순 적용하고 이후 실시간 이벤트를 처리한다.          ├ 이 파일(planReplay)
//   5. 버전이 불연속·역전이면 추측 적용하지 말고 스냅샷을 다시 조회한다.       ┘
//
// ⚠️ 현행 명세: 상태 변경 이벤트 data에 **stateVersion이 아직 없다**(§6-8 → S15P11A704-221 BE 확정 대기).
//    그래서 지금은 3~5단계가 no-op에 수렴한다(버전이 없어 폐기·정렬·불연속 판정을 못 함) — 버퍼는
//    도착 순서대로 재생된다(best-effort). BE가 이벤트에 stateVersion을 실으면 이 함수가 자동으로 켜진다.
//    핵심 이득인 1·2단계(버퍼링으로 '스냅샷이 더 신선한 라이브 이벤트를 덮어쓰는' 레이스 차단)는 지금도 동작한다.

/** 버퍼에 쌓인 원본 이벤트. stateVersion은 있으면 data 안에 온다(현행은 없음). */
export interface BufferedEvent {
  type: string
  data: unknown
}

export interface ReplayPlan {
  /** 재생할 이벤트(버전 오름차순 또는 도착 순서). 스냅샷 이하 버전은 제외됨. needsResync면 무시한다. */
  apply: BufferedEvent[]
  /** 버전 불연속·역전 감지 — 호출부가 스냅샷을 재조회해야 한다(추측 적용 금지, 5단계). */
  needsResync: boolean
  /** 관측용 — 스냅샷 이하 버전이라 폐기한 이벤트 수. */
  discarded: number
}

/** 이벤트가 실어온 stateVersion(숫자)만 뽑는다. 없거나 숫자가 아니면 null. */
function versionOf(event: BufferedEvent): number | null {
  const raw = (event.data as { stateVersion?: unknown } | null)?.stateVersion
  return typeof raw === 'number' ? raw : null
}

/**
 * 버퍼된 이벤트의 재생 계획을 세운다(3·4·5단계). 순수 함수 — 스토어를 건드리지 않는다.
 *
 * @param buffered        RECEIPT/스냅샷 사이에 버퍼링된 이벤트(도착 순서)
 * @param snapshotVersion 방금 반영한 스냅샷의 stateVersion
 */
export function planReplay(buffered: BufferedEvent[], snapshotVersion: number): ReplayPlan {
  const versioned: { event: BufferedEvent; version: number }[] = []
  let unversionedCount = 0
  for (const event of buffered) {
    const version = versionOf(event)
    if (version === null) unversionedCount += 1
    else versioned.push({ event, version })
  }

  // 버전이 붙은 이벤트가 하나도 없으면(현행 명세) 폐기·정렬 판정이 불가능하다 —
  // 도착 순서대로 그대로 재생한다(best-effort). 버퍼링 이득(1·2단계)은 이미 얻은 상태.
  if (versioned.length === 0) {
    return { apply: buffered, needsResync: false, discarded: 0 }
  }

  // 3단계: 스냅샷 이하 폐기. 4단계: 큰 것만 오름차순.
  const kept = versioned
    .filter(({ version }) => version > snapshotVersion)
    .sort((a, b) => a.version - b.version)
  const discarded = versioned.length - kept.length

  // 5단계: 스냅샷 다음 버전부터 1씩 연속이어야 한다. 공백·중복(역전)이면 재조회.
  let needsResync = false
  let expected = snapshotVersion + 1
  for (const { version } of kept) {
    if (version !== expected) {
      needsResync = true
      break
    }
    expected += 1
  }

  // 버전 이벤트와 무버전 이벤트가 섞이면 상대 순서를 확신할 수 없다 — 추측 적용 대신 재조회가 안전하다.
  // (전부 무버전이면 위에서 이미 반환했으므로, 여기 오는 혼재는 BE 전환 과도기에만 생긴다.)
  if (unversionedCount > 0) needsResync = true

  return { apply: kept.map(({ event }) => event), needsResync, discarded }
}
