import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import * as dealsApi from '@/shared/api/deals'
import { useDealAction, type DealActionTarget } from './useDealAction'

// 수령 확인은 **되돌릴 수 없는 대금 지급**이라 2단계를 둔다. 그 2단계가 무너지는 방식이
// 눈에 안 띈다 — 화면은 똑같이 보이고 클릭 수도 똑같은데, 두 번째 클릭이 다른 자리에서 온다.

const target: DealActionTarget = {
  dealId: 300,
  actionCode: 'CONFIRM_RECEIPT',
  productTitle: '기계식 키보드',
  finalPrice: 82_000,
}

/** onUnmounted를 쓰므로 컴포넌트 안에서 부른다 */
function setup() {
  const reload = vi.fn()
  let api!: ReturnType<typeof useDealAction>
  const wrapper = mount(
    defineComponent({
      setup() {
        api = useDealAction(reload)
        return () => null
      },
    }),
  )
  return { api, reload, wrapper }
}

describe('useDealAction — 수령 확인 2단계', () => {
  let confirmReceipt: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.useFakeTimers()
    confirmReceipt = vi.spyOn(dealsApi, 'confirmReceipt').mockResolvedValue(undefined as never)
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('첫 클릭은 무장만 하고 대금이 나가지 않는다', async () => {
    const { api } = setup()

    await api.act(target)

    expect(confirmReceipt).not.toHaveBeenCalled()
    expect(api.confirmingDealId.value).toBe(300)
  })

  it('같은 거래를 두 번 누르면 지급된다', async () => {
    const { api, reload } = setup()

    await api.act(target)
    await api.act(target)

    expect(confirmReceipt).toHaveBeenCalledWith(300)
    expect(reload).toHaveBeenCalled()
  })

  // S15P11A704-382의 본체. 이 composable은 마이페이지에 하나뿐이고 두 패널이 공유하는데,
  // 같은 거래가 「지금 할 일」·「경매 목록」 양쪽에서 액션 버튼을 갖는다. 화면 맥락이 바뀔 때
  // 풀지 않으면 각 탭에서 한 번씩 누른 것이 지급이 된다.
  it('disarmConfirm 뒤의 클릭은 다시 1단계다 — 지급되지 않는다', async () => {
    const { api } = setup()
    await api.act(target)
    expect(api.confirmingDealId.value).toBe(300)

    api.disarmConfirm()
    expect(api.confirmingDealId.value).toBeNull()

    await api.act(target)

    expect(confirmReceipt).not.toHaveBeenCalled()
    expect(api.confirmingDealId.value).toBe(300)
  })

  it('무장을 푼 뒤에는 남은 타이머가 되살리지 않는다', async () => {
    // disarm이 타이머를 안 지우면, 푼 뒤 다시 무장한 것을 그 타이머가 5초 안에 꺼 버린다
    const { api } = setup()
    await api.act(target)
    api.disarmConfirm()
    await api.act(target)

    vi.advanceTimersByTime(4_000)

    expect(api.confirmingDealId.value).toBe(300)
  })

  it('5초가 지나면 저절로 풀린다 — 한참 뒤 무심코 누른 클릭이 지급이 되면 안 된다', async () => {
    const { api } = setup()
    await api.act(target)

    vi.advanceTimersByTime(5_000)

    expect(api.confirmingDealId.value).toBeNull()
  })

  it('다른 거래를 누르면 무장이 그쪽으로 옮겨간다 — 이전 건은 지급되지 않는다', async () => {
    const { api } = setup()
    await api.act(target)
    await api.act({ ...target, dealId: 301 })

    expect(confirmReceipt).not.toHaveBeenCalled()
    expect(api.confirmingDealId.value).toBe(301)
  })
})
