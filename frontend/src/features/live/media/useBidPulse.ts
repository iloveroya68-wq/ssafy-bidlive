// BID-04 입찰 펄스 — 경매봉 연출(VibeOverlay)의 타이밍 규칙만 담는다. DOM도, 자리 선택도 여기 없다.
//
// 화면에서 분리한 이유는 취향이 아니라 **이 파일의 버그는 눈으로 못 잡기 때문**이다. 아래 규칙은 전부
// "초당 몇 건이 몰아칠 때"만 드러나는데, 그 상황은 손으로 재현하기 어렵고 실서버에서는 마감 직전
// 30초 동안만 온다. 가짜 타이머로 돌릴 수 있어야 회귀를 잡는다.

import { onScopeDispose, ref, watch, type Ref } from 'vue'

/** 타격 한 번. `count`는 이 타격이 대표하는 입찰 건수(합쳐진 수), `hot`은 **발화 시점의** 과열 여부. */
export interface BidPulse {
  id: number
  count: number
  hot: boolean
}

/**
 * 다음 타격까지의 최소 간격. 타격 애니메이션(~900ms)보다 짧지만, **짧은 것이 목적이다** —
 * 애니메이션 길이에 맞추면 초당 1회를 겨우 넘겨 몰아칠 때 반응이 느리게 느껴진다.
 * 대신 이보다 짧게 잡으면 안 된다: 200ms 간격 입찰이 오면 망치가 매번 처음으로 되돌아가
 * 내려치지 못하고 제자리에서 떨기만 한다.
 */
const COOLDOWN_MS = 700

/** 한 번의 타격에 이만큼 합쳐졌으면 과열 */
const HOT_COALESCED = 3
/** 이 창 안에 타격이 HOT_FIRES번이면 과열. 마지막 **입찰**로부터 이만큼 지나면 식는다. */
const HOT_WINDOW_MS = 5000
const HOT_FIRES = 3

export interface BidPulseState {
  /** 새 타격마다 **새 객체로 교체**된다. 소비자는 이 ref의 변화를 보고 그린다. */
  pulse: Ref<BidPulse | null>
  /** 과열 상태(강한 흔들림·레드). 마지막 입찰 5초 뒤 스스로 식는다. */
  hot: Ref<boolean>
  /** 과열 창 안에 쌓인 입찰 건수 — 「🔥 입찰 N연속!」의 N */
  streak: Ref<number>
}

/**
 * `source()`가 **새 배열로 교체될 때마다 입찰 1건**으로 센다.
 *
 * 길이가 아니라 참조를 보는 이유는 원래 주석 그대로다 — `vibeFeed`는 상한(VIBE_KEEP=8)에서 길이가
 * 고정되므로 길이를 보면 9번째부터 화면이 조용히 죽는다.
 *
 * **`bidCount`를 트리거로 쓰면 안 된다.** 재접속 스냅샷(WS-03)은 건수를 한 번에 0 → 37로 올린다.
 * 그 차이를 입찰로 세면 돌아오자마자 「+37」이 터지는데, 그 37건은 **이미 지나간 일**이다.
 * `vibeFeed`는 BID_ACCEPTED 한 건마다 하나씩 늘어나므로 1 = 1이 보장된다.
 */
export function useBidPulse(source: () => readonly unknown[]): BidPulseState {
  const pulse = ref<BidPulse | null>(null)
  const hot = ref(false)
  const streak = ref(0)

  let seq = 0
  /** 아직 화면에 못 보여준 입찰 건수 */
  let pending = 0
  /** null이 아니면 쿨다운 중 = 지금 타격 중이다 */
  let cooldown: ReturnType<typeof setTimeout> | null = null
  let decay: ReturnType<typeof setTimeout> | null = null
  /** 과열 판정 창 — 최근 HOT_WINDOW_MS 안의 타격들 */
  let recent: { at: number; count: number }[] = []

  function fire(): void {
    const count = pending
    pending = 0

    const now = Date.now()
    recent = recent.filter((f) => now - f.at < HOT_WINDOW_MS)
    recent.push({ at: now, count })
    streak.value = recent.reduce((sum, f) => sum + f.count, 0)
    // 과열은 두 길로 온다: 한 번에 왕창 뭉쳤거나(순간 폭주), 잔잔하지만 계속 오거나(지속 경합).
    if (count >= HOT_COALESCED || recent.length >= HOT_FIRES) hot.value = true

    pulse.value = { id: ++seq, count, hot: hot.value }

    cooldown = setTimeout(() => {
      cooldown = null
      // 쿨다운 동안 쌓인 것을 **한 번에** 친다. 여기서 건별로 다시 예약하면 큐가 밀려
      // 입찰이 끝난 뒤에도 망치가 한참 더 두들긴다 — 화면이 서버보다 늦게 흥분한다.
      if (pending > 0) fire()
    }, COOLDOWN_MS)
  }

  function bump(): void {
    // 안 보이는 탭에서는 세지도 않는다. 돌아온 순간 밀린 건수를 몰아 보여주면 지나간 일을
    // 지금 벌어지는 일처럼 그리게 된다 — 「+37」이 터지는데 정작 그 입찰들은 몇 분 전 것이다.
    // 다음 진짜 입찰이 평소대로 치면 그만이다.
    if (document.hidden) return

    pending += 1

    // 식는 기준은 마지막 **입찰**이다. 마지막 타격으로 재면 쿨다운에 갇힌 입찰이 창을 못 늘려
    // 몰아치는 도중에 과열이 풀린다.
    if (decay !== null) clearTimeout(decay)
    decay = setTimeout(() => {
      decay = null
      hot.value = false
      streak.value = 0
      recent = []
    }, HOT_WINDOW_MS)

    if (cooldown === null) fire()
  }

  watch(source, (next, prev) => {
    // $reset이 만드는 빈 배열도 참조 교체다 — 방을 나갈 때 망치가 한 번 더 울리지 않게 막는다.
    if (next === prev || next.length === 0) return
    bump()
  })

  onScopeDispose(() => {
    // 언마운트 뒤에 fire되면 이미 사라진 화면 상태를 건드린다. 예약을 남기지 않는다.
    if (cooldown !== null) clearTimeout(cooldown)
    if (decay !== null) clearTimeout(decay)
    cooldown = null
    decay = null
  })

  return { pulse, hot, streak }
}
