import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import PredictionChips from './PredictionChips.vue'
import { useLiveStore } from '@/stores/live'

// PRED-01 예측 참여 연출. 이 컴포넌트가 **조용히** 깨질 수 있는 지점만 잠근다:
//  1. predictionFeed는 새 배열로 교체되므로(handlers.handlePredictionAccepted) 길이가 아니라
//     참조 변화를 봐야 한다. 길이만 보면 상한(VIBE_KEEP=8)에 도달한 뒤로 영구히 안 뜬다.
//  2. 장수가 스테이크·건수에 따라 흔들리기 시작하면 칩을 세어 남의 베팅 규모가 읽힌다(규칙 7).
//  3. 모션 최소화 선호에서 움직임과 **함께 정보까지** 사라지면 접근성 회귀다.

/** 서버 이벤트처럼 새 배열로 교체한다 — push로 밀면 참조가 그대로여서 실제 흐름과 달라진다. */
async function pushPrediction(
  store: ReturnType<typeof useLiveStore>,
  text = '누군가 예측에 참여했습니다!',
) {
  store.predictionFeed = [...store.predictionFeed, text]
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

describe('PredictionChips', () => {
  it('예측이 들어오면 칩을 던지고 문구를 띄운다', async () => {
    const store = useLiveStore()
    const wrapper = mount(PredictionChips)

    expect(wrapper.findAll('svg')).toHaveLength(0)

    await pushPrediction(store)

    expect(wrapper.findAll('svg')).toHaveLength(5)
    expect(wrapper.text()).toContain('누군가 예측에 참여했습니다!')
  })

  /**
   * 장수는 어떤 입력에도 반응하지 않아야 한다. 여기가 흔들리면 칩을 세는 것만으로
   * 남의 스테이크·참여 규모가 읽혀 규칙 7(베팅 분포 라이브 중 노출 금지)에 걸린다.
   */
  it('건수가 몇이든 항상 5장이다', async () => {
    const store = useLiveStore()
    const wrapper = mount(PredictionChips)

    await pushPrediction(store, '첫 번째')
    await pushPrediction(store, '두 번째')
    await pushPrediction(store, '세 번째')

    expect(wrapper.findAll('svg')).toHaveLength(5)
  })

  /** 상한에 걸려 길이가 고정된 뒤에도 계속 떠야 한다 — 참조를 보는 이유가 이것이다 */
  it('피드가 상한에 걸려 길이가 고정돼도 계속 뜬다', async () => {
    const store = useLiveStore()
    store.predictionFeed = Array.from({ length: 8 }, (_, i) => `이전 ${i}`)
    const wrapper = mount(PredictionChips)

    // 길이는 8 그대로지만 참조는 바뀐다 (핸들러의 slice(-VIBE_KEEP)와 같은 모양)
    store.predictionFeed = [...store.predictionFeed.slice(1), '새 예측']
    await nextTick()

    expect(store.predictionFeed).toHaveLength(8)
    expect(wrapper.findAll('svg')).toHaveLength(5)
  })

  it('수명이 다하면 칩과 문구가 함께 사라진다', async () => {
    const store = useLiveStore()
    const wrapper = mount(PredictionChips)

    await pushPrediction(store)
    expect(wrapper.findAll('svg')).toHaveLength(5)

    vi.advanceTimersByTime(1800)
    await nextTick()

    expect(wrapper.findAll('svg')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('누군가 예측에 참여했습니다!')
  })

  /** 움직임만 빠지고 **정보는 남아야** 한다. 문구까지 사라지면 접근성 회귀다. */
  it('모션 최소화 선호면 칩은 안 던지되 문구는 남긴다', async () => {
    vi.stubGlobal('matchMedia', (q: string) => ({
      matches: q.includes('reduce'),
      media: q,
      addEventListener: () => {},
      removeEventListener: () => {},
    }))

    const store = useLiveStore()
    const wrapper = mount(PredictionChips)

    await pushPrediction(store)

    expect(wrapper.findAll('svg')).toHaveLength(0)
    expect(wrapper.text()).toContain('누군가 예측에 참여했습니다!')
  })
})
