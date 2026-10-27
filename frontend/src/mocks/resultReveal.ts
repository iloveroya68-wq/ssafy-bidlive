// 마감 → 집계 → 공개 오버레이(-66)를 굴리는 목 소켓 드라이버.
//
// **백엔드 연동 전 임시 파일이다.** 실제로는 FE2의 STOMP 구독(`features/live/socket/`)이
// `/topic/auctions/{auctionId}`에서 같은 이벤트를 받아 넘겨준다. 붙일 때는 이 파일을 지우고
// `startMockRevealFeed`를 실제 구독으로 바꾸면 된다 — 핸들러 시그니처는 그대로 쓴다.
//
// 이벤트 타입 정의는 `features/result/revealState.ts`(WS 계약)에 있고 여기서는 값만 만든다.
// 목이 만든 값이라도 **명세에 없는 필드를 지어내지 않는다**.

import type { RevealSocketEvent } from '@/features/result/revealState'

/** 데모 시나리오 — 어떤 종결 이벤트로 끝날지 */
export type RevealScenario = 'sold' | 'nosale' | 'canceled'

/**
 * 쿼리 문자열이 지원하는 시나리오인지.
 *
 * ⚠️ 이건 **목 전용 데모 트리거**다. 실제 마감은 서버의 `AUCTION_ENDED`가 유일한 신호이고
 * 클라이언트가 남은 시간으로 마감을 자체 판단하지 않는다 (AGENTS.md 규칙 5).
 * 결과 화면의 역할·유찰 사유는 여전히 쿼리로 지정할 수 없다 — 여기서 고르는 것은
 * "목 소켓이 어떤 이벤트를 흘릴지"뿐이다.
 */
export function asRevealScenario(value: unknown): RevealScenario | null {
  return value === 'sold' || value === 'nosale' || value === 'canceled' ? value : null
}

/** 집계 중(SETTLING) 구간의 길이. 서버가 2등가를 판정하는 실제 대기를 화면으로 채운다 */
export const TALLY_MS = 2200

/**
 * `AUCTION_ENDED` → (집계) → 종결 이벤트 순으로 흘린다.
 * @returns 구독 해제 함수. 화면을 떠날 때 반드시 호출한다.
 */
export function startMockRevealFeed(
  auctionId: number,
  scenario: RevealScenario,
  onEvent: (event: RevealSocketEvent) => void,
): () => void {
  const endedAt = new Date().toISOString()
  const timers: Array<ReturnType<typeof setTimeout>> = []

  timers.push(
    setTimeout(() => {
      onEvent({ type: 'AUCTION_ENDED', data: { endedAt, status: 'SETTLING' } })
    }, 0),
  )

  timers.push(
    setTimeout(() => {
      if (scenario === 'sold') {
        onEvent({
          type: 'AUCTION_SETTLED',
          data: {
            winner: '낙찰자 ●●3',
            highestBidAmount: 82_000,
            secondHighestBidAmount: 74_000,
            finalPrice: 74_000,
            resultType: 'SOLD',
          },
        })
        return
      }
      if (scenario === 'nosale') {
        onEvent({ type: 'AUCTION_NO_SALE', data: { reason: 'NO_BID', endedAt } })
        return
      }
      onEvent({
        type: 'AUCTION_CANCELED',
        data: { reason: 'FORCED', endedAt, resultPath: `/rooms/${auctionId}/nosale` },
      })
    }, TALLY_MS),
  )

  return () => {
    timers.forEach(clearTimeout)
    timers.length = 0
  }
}
