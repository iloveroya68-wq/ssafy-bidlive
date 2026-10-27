// DEAL-05 거래 액션 — 마이페이지 세 자리(지금 할 일 · 낙찰 · 개최)와 결과 화면이 공유한다.
//
// 와이어프레임 `dealRow()`가 정한 분기를 그대로 옮긴 것이다(2614행):
//   배송지 입력만 모달을 거치고, 나머지는 확인 한 번으로 끝나는 단순 전이.
//
// 분기 기준은 서버가 주는 `actionCode`다. `actionPath`(`/deals/300/address`)는 쓰지 않는다 —
// 와이어프레임에 거래 화면이 없어 대응 라우트를 만들지 않았다(S15P11A704-337).
import { onUnmounted, ref } from 'vue'
import {
  confirmReceipt,
  confirmShipment,
  submitShippingAddress,
  type ShippingAddressRequest,
} from '@/shared/api/deals'
import { toApiError } from '@/shared/api/errors'
import type { Id } from '@/shared/api/types'

/** 수령 확인 2단계가 열려 있는 시간. `useRoomDelete`와 같은 값으로 맞춘다 */
const CONFIRM_TIMEOUT_MS = 5_000

/** 액션을 걸 대상. 목록 항목마다 모양이 조금씩 달라 필요한 것만 추린다 */
export interface DealActionTarget {
  dealId: Id
  actionCode: string
  productTitle: string
  /** 모달 머리의 "낙찰 · N P" 표시용. 개최 목록은 낙찰 전이면 null이다 */
  finalPrice: number | null
}

export function useDealAction(reload: () => void | Promise<void>) {
  /** 배송지 모달이 열려 있는 대상. null이면 닫힘 */
  const addressTarget = ref<DealActionTarget | null>(null)
  /** 수령 확인에서 "한 번 더 눌러" 단계에 들어간 거래 */
  const confirmingDealId = ref<Id | null>(null)
  /** 요청이 나가 있는 거래. 거래끼리 독립이라 하나씩 잠근다 */
  const pendingDealId = ref<Id | null>(null)
  const error = ref<string | null>(null)
  /** 실패한 거래 — 목록에서 **그 줄 밑에** 오류를 붙이려면 어느 건인지 알아야 한다 */
  const errorDealId = ref<Id | null>(null)

  let timer: ReturnType<typeof setTimeout> | null = null
  // 응답을 기다리는 동안 화면을 떠나면 뒤늦은 reload가 다음 화면을 건드린다 (useRoomDelete I-2와 같은 가드)
  let cancelled = false

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  /**
   * 수령 확인 2단계를 푼다.
   *
   * **화면 맥락이 바뀌면 호출부가 반드시 불러야 한다** (S15P11A704-382).
   * 이 composable은 마이페이지에 **하나**만 있고 「지금 할 일」·「경매 목록」 두 패널이 공유한다.
   * 그래서 무장 상태(`confirmingDealId`)도 공유되는데, `myAuctions`의 `actionCode` 때문에
   * **같은 거래가 두 탭 모두에서 액션 버튼을 갖는다.** 풀지 않으면 한 탭에서 1회 + 다른 탭에서 1회로
   * 대금이 나간다 — 사용자는 각각 한 번씩 눌렀을 뿐이라 2단계를 거쳤다고 느끼지 못한다.
   *
   * "5초 뒤 자동 해제"는 이걸 못 막는다. 탭 전환은 클릭 한 번이라 그 창 안에 들어간다.
   */
  function disarmConfirm() {
    clearTimer()
    confirmingDealId.value = null
  }

  async function run(dealId: Id, call: () => Promise<unknown>) {
    pendingDealId.value = dealId
    error.value = null
    errorDealId.value = null
    try {
      await call()
      if (cancelled) return
      await reload()
    } catch (caught) {
      if (cancelled) return
      error.value = toApiError(caught).message
      errorDealId.value = dealId
    } finally {
      if (!cancelled) pendingDealId.value = null
    }
  }

  /**
   * 액션 버튼 클릭 진입점. `actionCode`로 갈린다.
   * - `SUBMIT_ADDRESS`  → 모달을 연다 (제출은 `submitAddress`)
   * - `CONFIRM_SHIPMENT` → 곧바로 전이 (와이어프레임대로 클릭 한 번)
   * - `CONFIRM_RECEIPT`  → 첫 클릭은 확인 단계, 두 번째 클릭에 전이
   */
  async function act(target: DealActionTarget): Promise<void> {
    if (pendingDealId.value !== null) return
    error.value = null
    errorDealId.value = null

    switch (target.actionCode) {
      case 'SUBMIT_ADDRESS':
        disarmConfirm()
        addressTarget.value = target
        return

      case 'CONFIRM_SHIPMENT':
        disarmConfirm()
        await run(target.dealId, () => confirmShipment(target.dealId))
        return

      case 'CONFIRM_RECEIPT':
        // 대금이 나가는 되돌릴 수 없는 동작이라 한 번 더 받는다.
        // 확인 상태를 무기한 열어 두면 한참 뒤 무심코 누른 클릭이 곧바로 지급이 되므로 5초 뒤 닫는다.
        if (confirmingDealId.value !== target.dealId) {
          confirmingDealId.value = target.dealId
          clearTimer()
          timer = setTimeout(disarmConfirm, CONFIRM_TIMEOUT_MS)
          return
        }
        disarmConfirm()
        await run(target.dealId, () => confirmReceipt(target.dealId))
        return

      default:
        // 모르는 코드는 조용히 넘긴다 — 서버가 액션을 늘려도 화면이 깨지지 않게.
        // (`myAuctions`의 `actionCode`가 `string | null`이라 타입으로 막히지 않는다)
        console.warn('[deal] 모르는 actionCode라 무시했다:', target.actionCode)
    }
  }

  function closeAddress(): void {
    addressTarget.value = null
  }

  /** 배송지 모달 제출 */
  async function submitAddress(body: ShippingAddressRequest): Promise<void> {
    const target = addressTarget.value
    if (target === null || pendingDealId.value !== null) return
    await run(target.dealId, () => submitShippingAddress(target.dealId, body))
    // 실패했으면 입력한 내용을 잃지 않게 모달을 열어 둔다
    if (!cancelled && error.value === null) closeAddress()
  }

  onUnmounted(() => {
    cancelled = true
    clearTimer()
  })

  return {
    addressTarget,
    confirmingDealId,
    pendingDealId,
    error,
    errorDealId,
    act,
    closeAddress,
    submitAddress,
    /** 화면 맥락(탭 등)이 바뀔 때 호출한다 — 위 정의의 주석 참조 */
    disarmConfirm,
  }
}
