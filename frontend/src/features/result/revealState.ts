// 마감 → 집계 → 공개 오버레이의 상태 기계 (S15P11A704-66).
//
// WS 이벤트 봉투는 백엔드 공통 규격 `{ type, data }`다 (AGENTS.md 규칙 9).
// 필드는 「WebSocket 이벤트 명세」의 Payload 열을 그대로 옮겼다.
//
// ⚠️ `stores/live.ts`(FE2 소유)에는 아직 `auctionStatus`가 없다. 계약 개정은 별도 이슈라
// 지금은 이 composable을 화면 로컬로 들고 있는다 — 스토어에 자리가 생기면 그대로 옮긴다.

import { computed, ref, type ComputedRef, type Ref } from 'vue'
import type { AuctionEndEvent } from '@/features/live/socket/types'

/**
 * 마감·공개를 일으키는 4종(AUCTION_ENDED·SETTLED·NO_SALE·CANCELED).
 *
 * 이 파일이 같은 4종을 따로 선언하고 있었는데 `socket/types.ts`의 사본이라 지웠다 —
 * 실소켓 핸들러가 담아 주는 값(`live.endEvent`)을 화면이 그대로 `apply`에 넘길 수 있어야 한다.
 * (사본 쪽 `AUCTION_NO_SALE.reason`은 `NoSaleReason` 3값으로 좁혀 있었지만, 소켓 명세는
 *  값 목록 미확인이라 string이 맞다. 모르는 값 처리는 RevealOverlay가 이미 한다.)
 */
export type RevealSocketEvent = AuctionEndEvent

export const REVEAL_SETTLING_DURATION_MS = 1760

type RevealTerminalEvent = Exclude<RevealSocketEvent, { type: 'AUCTION_ENDED' }>

/** `hidden`을 뺀 나머지가 오버레이가 실제로 그리는 국면이다 */
export type RevealPhase = 'hidden' | 'settling' | 'sold' | 'nosale' | 'canceled'

export interface RevealState {
  phase: Ref<RevealPhase>
  winnerLabel: Ref<string>
  finalPrice: Ref<number>
  highestBidAmount: Ref<number | null>
  reasonText: Ref<string>
  /** 마감 이후인지 — 입찰·예측 입력을 막는 기준 */
  isEnded: ComputedRef<boolean>
  /** "자세히" 버튼이 갈 라우트 이름 */
  detailRouteName: ComputedRef<'result' | 'nosale'>
  apply: (event: RevealSocketEvent) => void
  /** 오버레이만 걷는다 — 마감 사실(`isEnded`)은 되돌리지 않는다 */
  dismiss: () => void
}

export function useRevealState(): RevealState {
  const phase = ref<RevealPhase>('hidden')
  /** 오버레이를 닫아도 "이미 끝났다"는 사실은 남아야 입력이 다시 열리지 않는다 */
  const ended = ref(false)

  const winnerLabel = ref('')
  const finalPrice = ref(0)
  const highestBidAmount = ref<number | null>(null)
  const reasonText = ref('')
  const settledPhase = ref<Exclude<RevealPhase, 'hidden' | 'settling'>>('sold')
  let settlingTimer: ReturnType<typeof setTimeout> | null = null
  let pendingTerminal: RevealTerminalEvent | null = null

  function clearSettlingTimer() {
    if (settlingTimer !== null) {
      clearTimeout(settlingTimer)
      settlingTimer = null
    }
    pendingTerminal = null
  }

  function applyTerminal(event: RevealTerminalEvent) {
    switch (event.type) {
      case 'AUCTION_SETTLED':
        winnerLabel.value = event.data.winner
        finalPrice.value = event.data.finalPrice
        highestBidAmount.value = event.data.highestBidAmount
        settledPhase.value = 'sold'
        phase.value = 'sold'
        break
      case 'AUCTION_NO_SALE':
        reasonText.value = event.data.reason
        settledPhase.value = 'nosale'
        phase.value = 'nosale'
        break
      case 'AUCTION_CANCELED':
        reasonText.value = event.data.reason
        settledPhase.value = 'canceled'
        phase.value = 'canceled'
        break
    }
  }

  function finishSettling() {
    settlingTimer = null
    const event = pendingTerminal
    pendingTerminal = null
    if (event !== null) applyTerminal(event)
  }

  function apply(event: RevealSocketEvent) {
    if (event.type === 'AUCTION_ENDED') {
      clearSettlingTimer()
      ended.value = true
      phase.value = 'settling'
      settlingTimer = setTimeout(finishSettling, REVEAL_SETTLING_DURATION_MS)
      return
    }

    ended.value = true
    if (settlingTimer !== null) {
      pendingTerminal = event
      return
    }
    applyTerminal(event)
  }

  function dismiss() {
    clearSettlingTimer()
    phase.value = 'hidden'
  }

  return {
    phase,
    winnerLabel,
    finalPrice,
    highestBidAmount,
    reasonText,
    isEnded: computed(() => ended.value),
    detailRouteName: computed(() => (settledPhase.value === 'sold' ? 'result' : 'nosale')),
    apply,
    dismiss,
  }
}
