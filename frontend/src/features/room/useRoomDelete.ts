// ROOM-04 방 삭제 — 콘솔의 두 진입점(액션 줄 "방 삭제", 사이드 "방 삭제하기")이 공유한다.
// 와이어프레임도 cDropRoom·cDropRoom2가 같은 핸들러를 쓴다.
import { onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteAuctionRoom } from '@/shared/api/rooms'
import { API_ERROR_CODE, hasErrorCode, toApiError } from '@/shared/api/errors'

const CONFIRM_TIMEOUT_MS = 5_000

export function useRoomDelete(auctionId: () => number) {
  const router = useRouter()
  const confirming = ref(false)
  const pending = ref(false)
  const error = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null
  // I-2: DELETE 응답을 기다리는 동안 사용자가 다른 화면으로 나가면 뒤늦은 router.replace('/')가
  // 그 화면을 홈으로 덮어쓴다. RoomEditView.vue의 cancelled 가드와 같은 패턴이다.
  let cancelled = false

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function cancel() {
    clearTimer()
    confirming.value = false
  }

  /** 첫 호출은 확인 단계로 들어가고, 확인 중 재호출이면 실제로 지운다 */
  async function requestDelete(): Promise<void> {
    if (pending.value) return
    if (!confirming.value) {
      confirming.value = true
      error.value = null
      clearTimer()
      timer = setTimeout(() => {
        confirming.value = false
        timer = null
      }, CONFIRM_TIMEOUT_MS)
      return
    }

    cancel()
    pending.value = true
    error.value = null
    try {
      await deleteAuctionRoom(auctionId())
      // 응답을 기다리는 동안 화면을 떠났으면 뒤늦은 replace로 다른 화면을 덮지 않는다 (I-2)
      if (cancelled) return
      // 와이어프레임 dropRoom이 go('main') — 방을 지운 판매자는 홈으로 돌아간다
      await router.replace('/')
    } catch (caught) {
      if (cancelled) return
      const apiError = toApiError(caught)
      if (hasErrorCode(apiError, API_ERROR_CODE.AUCTION_ALREADY_STARTED)) {
        error.value = '이미 시작된 방은 삭제할 수 없어요.'
      } else if (hasErrorCode(apiError, API_ERROR_CODE.AUCTION_HAS_PARTICIPATION)) {
        error.value = '참여자가 있어 삭제할 수 없어요.'
      } else if (hasErrorCode(apiError, API_ERROR_CODE.NOT_AUCTION_SELLER)) {
        error.value = '이 방의 판매자만 삭제할 수 있어요.'
      } else {
        error.value = apiError.message
      }
    } finally {
      if (!cancelled) pending.value = false
    }
  }

  onUnmounted(() => {
    cancelled = true
    clearTimer()
  })

  return { confirming, pending, error, requestDelete, cancel }
}
