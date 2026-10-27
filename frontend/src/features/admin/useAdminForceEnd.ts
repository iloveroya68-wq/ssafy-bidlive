// DEAL-09 강제 종료의 2단계 확인 (S15P11A704-525).
//
// `useDealAction`(수령 확인)·`useRoomDelete`와 같은 규칙이다 — 되돌릴 수 없는 동작이라
// 「한 번 더 눌러」를 거치고, 타임아웃과 다른 행 무장으로 자동 해제된다.
import { onUnmounted, ref } from 'vue'
import { forceEndAuction } from '@/shared/api/admin'
import { extractApiErrorStatus } from '@/shared/lib/apiError'
import type { Id } from '@/shared/api/types'

/** 무장이 열려 있는 시간. `useDealAction`·`useRoomDelete`와 같은 값으로 맞춘다 */
const CONFIRM_TIMEOUT_MS = 5_000

/**
 * 서버로 보내는 종료 사유. **운영 고정값이고, 신고 사유를 넣지 않는다.**
 *
 * 이 값은 `AuctionForceEndService` → `AUCTION_CANCELED` 이벤트를 타고
 * `/topic/auctions/{auctionId}`로 나가고, 그 토픽은 **그 방에 있던 시청자 전원이 구독한다**
 * (`features/live/socket/liveSocket.ts`). `PROHIBITED_ITEM`·`FALSE_LISTING` 같은 신고 사유를
 * 실으면 판매자에 대한 혐의가 방 전체 브라우저에 도착한다 — 화면에 안 그려도 페이로드에는 있다.
 * AGENTS.md 절대 규칙 6(시청자에게 새는 값을 만들지 않는다)이 걸리는 자리다.
 *
 * 운영자가 보는 「사유: 금지 품목」은 `ReportedAuctionRow`가 자기 `reasonCounts`로 로컬 계산한다 —
 * 그건 이 화면 밖으로 나가지 않는다.
 */
const FORCE_END_REASON = 'ADMIN_FORCE_END'

/**
 * 이 상태면 그 방은 목록에 남아 있을 이유가 없다 — 눌러도 계속 같은 오류다.
 *
 * **둘을 한 문구로 합치지 않는다.** 운영자에게 「이미 끝났거나 삭제됐다」와
 * 「그런 경매가 없다」(목록이 낡았거나 id가 틀렸다)는 다음 행동이 다르다.
 * 디자인 목업 「오류 처리」 표가 정한 두 문구다.
 *
 * 409가 삭제까지 덮는 이유: BE가 `target.deleted() || !endable(status)`를 한 코드로 묶었다
 * (운영자용이라 존재를 숨길 이유가 없어 404 대신 409, MR !246). 「이미 종료된」만 쓰면
 * 삭제된 방을 눌렀을 때 문구가 사실과 어긋난다.
 */
const GONE_MESSAGES: Record<number, string> = {
  409: '이미 종료됐거나 삭제된 경매예요.',
  404: '경매를 찾을 수 없어요.',
}

/**
 * @param remove 종료됐거나 이미 끝난 것으로 확인된 경매를 목록에서 빼는 콜백.
 *   전체 재조회를 하지 않는 이유: 운영자가 보던 자리가 밀려 다음 행을 잘못 누른다.
 */
export function useAdminForceEnd(remove: (auctionId: Id) => void) {
  /** 「한 번 더 눌러」 단계에 들어간 경매. null이면 무장 없음 */
  const armedId = ref<Id | null>(null)
  /** 요청이 나가 있는 경매 */
  const pendingId = ref<Id | null>(null)
  const error = ref<string | null>(null)
  /** 실패한 경매 — 목록에서 **그 줄 밑에** 오류를 붙이려면 어느 건인지 알아야 한다 */
  const errorId = ref<Id | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null
  // 응답을 기다리는 동안 화면을 떠나면 뒤늦은 remove가 다음 화면을 건드린다
  let cancelled = false

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function disarm() {
    clearTimer()
    armedId.value = null
  }

  /**
   * 맥락이 바뀌면 화면이 부른다(탭·필터·페이지 전환).
   *
   * **오류를 남기면 다른 맥락의 다른 행에 잘못 붙는다** — `errorId`는 `auctionId`일 뿐이라,
   * 실패한 방과 같은 id가 새 목록에도 있으면 아무 조작도 안 한 행에 「종료하지 못했어요」가 그려진다.
   * 맥락 전환이 무장을 푸는 것과 같은 이유다.
   */
  function clearError() {
    error.value = null
    errorId.value = null
  }

  /**
   * 1단계. **다른 행이 무장돼 있으면 먼저 푼다** — 둘이 동시에 무장돼 있으면 각각 한 번씩
   * 눌러 둘 다 끝나고, 운영자는 2단계를 거쳤다고 느끼지 못한다.
   */
  function arm(auctionId: Id) {
    // 비행 중에는 무장 자체를 막는다. `confirm()`이 무장된 행에서만 통과하므로
    // 여기 한 줄이 「같은 행 재발신」과 「다른 행 동시 발신」을 둘 다 닫는다.
    //
    // **호출부에 맡기면 안 된다.** `finally`가 `pendingId`를 비우는 것은 마지막-쓰기-승리라,
    // 두 요청이 겹치면 먼저 온 응답이 아직 비행 중인 쪽의 잠금까지 풀어 버린다.
    // (`useDealAction`·`useRoomDelete`도 같은 가드를 composable 안에 둔다)
    if (pendingId.value !== null) return
    disarm()
    clearError()
    armedId.value = auctionId
    timer = setTimeout(() => {
      if (armedId.value === auctionId) disarm()
    }, CONFIRM_TIMEOUT_MS)
  }

  /** 2단계. 무장되지 않은 행은 아무것도 하지 않는다 */
  async function confirm(auctionId: Id) {
    if (armedId.value !== auctionId) return

    disarm()
    pendingId.value = auctionId
    clearError()
    try {
      await forceEndAuction(auctionId, { reason: FORCE_END_REASON })
      if (cancelled) return
      remove(auctionId)
    } catch (caught) {
      if (cancelled) return
      const status = extractApiErrorStatus(caught)
      const gone = status === null ? undefined : GONE_MESSAGES[status]
      if (gone !== undefined) {
        error.value = gone
        remove(auctionId)
        return
      }
      error.value = '종료하지 못했어요. 잠시 후 다시 시도해 주세요.'
      errorId.value = auctionId
    } finally {
      if (!cancelled) pendingId.value = null
    }
  }

  onUnmounted(() => {
    cancelled = true
    clearTimer()
  })

  return { armedId, pendingId, error, errorId, arm, disarm, clearError, confirm }
}
