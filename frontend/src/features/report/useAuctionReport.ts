// DEAL-04 신고 접수 상태 — 대기방과 라이브가 공유한다.
//
// 두 화면이 같은 모달을 열고 같은 실패를 다뤄야 해서 상태를 여기 모았다.
// (`useDealAction`과 같은 판단: 화면마다 ref 5개를 복사하면 한쪽만 고치는 사고가 난다.)
import { onUnmounted, ref } from 'vue'
import { toApiError } from '@/shared/api/errors'
import { reportAuction, type ReportReasonCode } from '@/shared/api/reports'
import type { Id } from '@/shared/api/types'

/** 접수 성공 안내가 떠 있는 시간 */
const NOTICE_TIMEOUT_MS = 4_000

export function useAuctionReport(auctionId: () => Id | null) {
  const open = ref(false)
  const pending = ref(false)
  const error = ref<string | null>(null)
  /** 접수 완료 안내. 처리 결과가 아니라 **접수됐다**는 사실만 알린다 */
  const notice = ref<string | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null
  // 응답을 기다리는 동안 화면을 떠나면 뒤늦은 상태 변경이 다음 화면을 건드린다
  let cancelled = false

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function openModal() {
    error.value = null
    open.value = true
  }

  function closeModal() {
    open.value = false
    error.value = null
  }

  async function submit(value: { reasonCode: ReportReasonCode; description: string }) {
    const id = auctionId()
    if (id === null || pending.value) return

    pending.value = true
    error.value = null
    try {
      await reportAuction(id, {
        reasonCode: value.reasonCode,
        // 빈 설명은 필드째 빼서 보낸다 — 서버도 빈 문자열을 "안 적음"으로 눕히지만
        // 굳이 빈 값을 실어 보낼 이유가 없다
        ...(value.description === '' ? {} : { description: value.description }),
      })
      if (cancelled) return
      open.value = false
      notice.value = '신고가 접수됐어요. 운영팀이 확인할게요.'
      clearTimer()
      timer = setTimeout(() => {
        notice.value = null
      }, NOTICE_TIMEOUT_MS)
    } catch (caught) {
      if (cancelled) return
      // 실패하면 모달을 열어 둔다 — 적어 둔 설명을 잃지 않게
      error.value = toApiError(caught).message
    } finally {
      if (!cancelled) pending.value = false
    }
  }

  onUnmounted(() => {
    cancelled = true
    clearTimer()
  })

  return { open, pending, error, notice, openModal, closeModal, submit }
}
