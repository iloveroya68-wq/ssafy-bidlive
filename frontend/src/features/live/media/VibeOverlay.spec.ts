import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import VibeOverlay from './VibeOverlay.vue'
import { useLiveStore } from '@/stores/live'

// BID-04 Vibe 오버레이 (S15P11A704-357). 칩이 떠오르던 연출을 경매봉 타격으로 바꾸면서 함께 고쳤다 —
// 타이밍 규칙 자체는 useBidPulse.spec.ts가 잠그고, 여기서는 **그리는 쪽**만 본다.
//
// 이 컴포넌트가 조용히 깨질 수 있는 지점:
//  1. vibeFeed는 **새 배열로 교체**되므로(handlers.handleBidAccepted) 길이가 아니라 참조 변화를 봐야 한다.
//     길이만 보면 상한(VIBE_KEEP=8)에 도달한 뒤로 망치가 영구히 안 뜬다.
//  2. 자리가 연속으로 겹치면 "여기 뜨는구나"로 학습돼 랜덤 슬롯의 의미가 사라진다.
//  3. 모션 최소화 선호에서 움직임과 **함께 정보까지** 사라지면 그건 접근성 회귀다.

function mountOverlay() {
  return mount(VibeOverlay)
}

/** 서버 이벤트처럼 **새 배열로** 교체한다 — push로 밀면 참조가 그대로여서 실제 흐름과 달라진다. */
async function pushVibe(store: ReturnType<typeof useLiveStore>, text = '누군가 베팅했어요') {
  store.vibeFeed = [...store.vibeFeed, text]
  await nextTick()
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('VibeOverlay', () => {
  it('vibeFeed가 새 배열로 교체되면 경매봉이 내려친다', async () => {
    const store = useLiveStore()
    const wrapper = mountOverlay()

    await pushVibe(store)

    expect(wrapper.findAll('.gavel-strike')).toHaveLength(1)
    expect(wrapper.text()).toContain('땅!')
    wrapper.unmount()
  })

  it('피드가 상한에 찬 뒤에도 뜬다 — 길이가 아니라 참조로 판정해야 한다', async () => {
    // vibeFeed는 상한(VIBE_KEEP=8)에서 길이가 고정된다. 그 뒤로는 **새 Vibe가 와도 길이가 그대로**여서,
    // 길이 변화로 판정하면 9번째부터 영구히 안 뜬다(!46 리뷰 1이 잡은 것). 화면은 조용히 죽는다.
    const store = useLiveStore()
    store.vibeFeed = Array.from({ length: 8 }, (_, i) => `이전 ${i}`)
    const wrapper = mountOverlay()

    // 상한 상태에서 새 Vibe — 서버는 오래된 것을 떨어뜨리고 같은 길이로 교체한다
    store.vibeFeed = [...store.vibeFeed.slice(1), '새 입찰']
    await nextTick()

    expect(wrapper.findAll('.gavel-strike')).toHaveLength(1)
    wrapper.unmount()
  })

  it('스크린리더에 잡히지 않는다 — 집계·상태는 #action이 알린다', () => {
    // 초당 한두 번 울리는 장식이다. 리더에 흘리면 낭독을 도배한다.
    const wrapper = mountOverlay()
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    wrapper.unmount()
  })

  it('연속으로 같은 자리에 서지 않는다', async () => {
    // 같은 자리가 두 번 이어지면 시선이 그 자리를 학습하고, 그다음부터는 다시 "못 봤다"가 된다.
    // 랜덤이라 값 자체는 단정할 수 없지만 **연속 금지는 구조적으로 보장**돼야 한다.
    const store = useLiveStore()
    const wrapper = mountOverlay()

    const slots: (string | undefined)[] = []
    for (let i = 0; i < 8; i++) {
      await pushVibe(store)
      slots.push(wrapper.get('[data-slot]').attributes('data-slot'))
      await vi.advanceTimersByTimeAsync(700) // 쿨다운을 비워 다음 타격이 즉시 나가게 한다
    }

    expect(slots).toHaveLength(8)
    for (let i = 1; i < slots.length; i++) expect(slots[i]).not.toBe(slots[i - 1])
    wrapper.unmount()
  })

  it('합쳐진 입찰은 +N으로 뜬다 — 한 번에 접히지만 건수는 안 사라진다', async () => {
    const store = useLiveStore()
    const wrapper = mountOverlay()

    await pushVibe(store) // 첫 건은 즉시 타격 (쿨다운 시작)
    expect(wrapper.text()).not.toContain('+') // 1건짜리에 「+1」은 노이즈다

    await pushVibe(store)
    await pushVibe(store)
    await pushVibe(store)
    await vi.advanceTimersByTimeAsync(700)

    expect(wrapper.text()).toContain('+3')
    expect(wrapper.text()).toContain('연속') // 3건이 뭉쳤으면 과열 배지도 함께
    wrapper.unmount()
  })

  it('모션 최소화 선호: 망치도 흔들림도 없지만 건수는 그대로 남는다', async () => {
    // 정보를 지우지 않고 움직임만 지운다. 망치를 안 그리는 이유는 **안 움직이는 망치는
    // 화면에 박힌 그림**이라서다 — 남겨 두면 무엇이 일어났는지가 아니라 잔상이 된다.
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    const store = useLiveStore()
    const wrapper = mountOverlay()

    await pushVibe(store)
    await pushVibe(store)
    await pushVibe(store)
    await pushVibe(store)
    await vi.advanceTimersByTimeAsync(700)

    expect(wrapper.find('.gavel-strike').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('gavel-shake')
    expect(wrapper.classes()).not.toContain('gavel-shake-hot')
    expect(wrapper.text()).toContain('땅!')
    expect(wrapper.text()).toContain('+3')
    wrapper.unmount()
  })

  it('언마운트하면 예약된 타이머가 남지 않는다', async () => {
    const store = useLiveStore()
    const wrapper = mountOverlay()
    await pushVibe(store)
    expect(vi.getTimerCount()).toBeGreaterThan(0)

    wrapper.unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
