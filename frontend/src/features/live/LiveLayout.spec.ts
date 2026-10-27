import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import LiveLayout from './LiveLayout.vue'
import { useLiveStore } from '@/stores/live'
import type { ConnectionStatus } from '@/stores/live'

// LIVE 배지의 점은 "실시간이 살아 있다"는 유일한 시각 신호다(S15P11A704-438).
// 끊긴 상태에서도 뛰면 425가 넣은 "연결이 끊겼어요" 배너와 한 화면에서 서로를 부정한다.
// 클래스 하나라 지우기 쉽고, 지워도 화면은 멀쩡해 보인다 — 그래서 테스트로 잠근다.

function render(status: ConnectionStatus) {
  const live = useLiveStore()
  live.connectionStatus = status
  return mount(LiveLayout, { props: { title: '기계식 키보드' } })
}

/** LIVE 배지 안의 점 */
function dot(wrapper: ReturnType<typeof render>) {
  return wrapper.get('.bg-red > span:first-child')
}

describe('LiveLayout LIVE 배지', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('연결됐을 때만 점이 뛴다', () => {
    expect(dot(render('connected')).classes()).toContain('animate-pulse')
  })

  it.each(['idle', 'connecting', 'reconnecting', 'closed'] as const)(
    '%s이면 점이 멈춘다',
    (status) => {
      expect(dot(render(status)).classes()).not.toContain('animate-pulse')
    },
  )

  it('라벨은 LIVE 그대로다 — 끊긴 건 내 연결이지 경매가 아니다', () => {
    expect(render('reconnecting').get('.bg-red').text()).toContain('LIVE')
  })

  it('끊긴 상태는 스크린리더에도 전달된다 — 점의 움직임은 안 잡힌다', () => {
    expect(render('reconnecting').get('.sr-only').text()).toContain('연결이 끊겨')
    expect(render('connected').find('.bg-red .sr-only').exists()).toBe(false)
  })
})

// BID-05 마감 5초 강조. 60초(색)와 5초(크기)는 **다른 겹**이고, 둘을 한 조건으로 묶는 "정리"가
// 이걸 조용히 깨뜨린다 — 60초 내내 칩이 커져 있어도 화면은 고장 나 보이지 않는다.
// 시간에만 반응하는 분기라 손으로 재현하려면 마감 5초 전 방이 필요하다.
describe('LiveLayout 타이머 칩', () => {
  beforeEach(() => setActivePinia(createPinia()))

  /**
   * 남은 시간을 초 단위로 세팅한다. 규칙 5대로 endsAt + serverOffset만 쓴다.
   * ⚠️ 칩이 읽는 `deadlineAt`·`clockOffset`은 **게터라 대입이 안 먹는다** — 실제 소스인
   * state 필드를 채워야 한다(게터가 룸 스토어 폴백까지 합치는 구조라 그렇다).
   */
  function mountWithRemaining(seconds: number) {
    const live = useLiveStore()
    live.endsAt = Date.now() + seconds * 1000
    live.serverOffset = 0
    return mount(LiveLayout, { props: { title: '기계식 키보드' } })
  }

  function timer(wrapper: ReturnType<typeof mountWithRemaining>) {
    return wrapper.get('.tabular-nums')
  }

  it('5초 이하면 숫자가 커지고 라벨이 빠진다', () => {
    const wrapper = mountWithRemaining(4)
    expect(timer(wrapper).classes()).toContain('text-[34px]')
    expect(wrapper.text()).not.toContain('남은 시간')
  })

  it('6초면 아직 평소 크기다 — 5초는 60초 임박과 다른 겹이다', () => {
    const wrapper = mountWithRemaining(6)
    expect(timer(wrapper).classes()).toContain('text-[17px]')
    // 60초 임박(색)은 이미 걸려 있다. 크기만 아직 안 커진 상태다.
    expect(timer(wrapper).classes()).toContain('text-red')
    expect(wrapper.text()).toContain('남은 시간')
  })

  it('마감 시각이 없으면 커지지 않는다 — 0을 임박으로 착각하지 않는다', () => {
    const live = useLiveStore()
    live.endsAt = null
    const wrapper = mount(LiveLayout, { props: { title: '기계식 키보드' } })
    expect(timer(wrapper).classes()).toContain('text-[17px]')
    expect(wrapper.text()).toContain('--:--')
  })
})

// 전체화면 대상 훅 (S15P11A704-433). #video 슬롯에 **어떤 엘리먼트를 넘기느냐**가 계약의 전부다.
// 잘못된 엘리먼트를 넘겨도(예: 슬롯을 감싼 absolute 래퍼) 화면은 똑같이 보이고,
// 전체화면에 들어가야만 타이머 칩이 빠진 것을 알 수 있다 — 눈으로는 안 잡히는 종류다.
describe('LiveLayout #video 슬롯의 fullscreen-target', () => {
  beforeEach(() => setActivePinia(createPinia()))

  /**
   * ⚠️ 첫 렌더에는 `null`이 간다 — 템플릿 ref는 렌더가 끝나야 채워지기 때문이다.
   * ref 대입이 반응을 일으켜 **다음 틱에 실제 엘리먼트로 다시 내려간다.**
   * 그래서 받는 쪽(LiveStage)은 이 값을 마운트 시점에 붙잡아 두면 안 되고 computed로 읽어야 하며,
   * null일 때의 폴백(자기 루트)도 있어야 한다. 여기서 굳이 `await nextTick()`을 넣는 이유가 그것이다.
   */
  async function mountWithProbe() {
    let received: HTMLElement | null | undefined
    const wrapper = mount(LiveLayout, {
      props: { title: '기계식 키보드' },
      slots: {
        video: (props: { fullscreenTarget: HTMLElement | null }) => {
          received = props.fullscreenTarget
          return 'stage'
        },
      },
    })
    const first = received
    await nextTick()
    return { wrapper, first, received: () => received }
  }

  it('첫 렌더에는 null이고 다음 틱에 실제 엘리먼트가 간다', async () => {
    const { first, received } = await mountWithProbe()
    expect(first).toBeNull()
    expect(received()).not.toBeNull()
  })

  it('스테이지 컨테이너를 넘긴다 — 슬롯을 감싼 래퍼가 아니다', async () => {
    const { wrapper, received } = await mountWithProbe()
    expect(received()).toBe(wrapper.get('.stage-frame').element)
  })

  it('넘긴 엘리먼트 안에 타이머 칩과 제품 바가 들어 있다', async () => {
    // 전체화면에 함께 들어가야 하는 것들. 이게 이 티켓의 이유다 —
    // 남은 시간(BID-05)이 전체화면에서 사라지면 경매 화면으로서 못 쓴다.
    const { wrapper, received } = await mountWithProbe()
    const target = received() as HTMLElement
    expect(target.contains(wrapper.get('.tabular-nums').element)).toBe(true)
    expect(target.textContent).toContain('남은 시간')
    expect(target.textContent).toContain('기계식 키보드')
  })

  // 슬롯 프롭 말고 **클래스로 오가는 계약이 하나 더 있다**(S15P11A704-464).
  // #video 슬롯(FE2)의 시청 컨트롤이 `group-hover/stage:`로 이 hover를 읽는다 —
  // (`lg:`가 붙지 않는 것이 중요하다 — 숨김을 뷰포트 폭으로 가르면 iPad 가로에서 영구히 숨는다. 464·467)
  // 지우면 데스크톱에서 음소거·전체화면이 영구히 숨는데, 이 프레임만 보면 아무 일도 안 일어난 것처럼 보인다.
  it('스테이지 컨테이너가 group/stage를 유지한다 — #video 슬롯의 hover 컨트롤이 이걸 읽는다', async () => {
    const { wrapper } = await mountWithProbe()
    expect(wrapper.get('.stage-frame').classes()).toContain('group/stage')
  })

  // 클래스로 오가는 계약 둘째(S15P11A704-475). #video 슬롯의 전체화면 버튼이 우하단 코너(하단 16~44px)로
  // 내려와 이 정보 바의 본문과 같은 줄에 앉는다. 타이틀이 flex-1 + truncate라 우측 예약이 없으면
  // 긴 상품명이 버튼 아래로 파고든다(버튼이 z-[7]이라 글자 위에 덮인다).
  // px-5로 되돌리면 pr 예약이 사라지는데 화면은 「타이틀이 조금 더 긴」 정도로만 보여 눈에 안 걸린다.
  it('제품 정보 바가 우측에 시청 컨트롤 자리를 예약한다 — 폭에 따라 두 값', async () => {
    const { wrapper } = await mountWithProbe()
    // 타이틀(.truncate)에서 두 칸 올라가면 정보 바다 — z-[6] 같은 클래스는 CSS 셀렉터 이스케이프가 필요해 피한다
    const title = wrapper.get('.truncate').element
    const prodbar = title.parentElement?.parentElement as HTMLElement
    const cls = [...prodbar.classList]
    expect(cls).toContain('bottom-0') // 정보 바를 제대로 잡았는지 확인
    expect(cls).toContain('pr-14') // 좁은 화면: 세로 스택이라 전체화면(28px) 몫만
    expect(cls).toContain('lg:pr-48') // lg: 가로 한 줄이라 클러스터 156px + 오프셋 16px
    // px-5와 섞으면 어느 쪽이 이기는지 정렬 순서에 달린다 — 갈라 쓴 상태를 고정한다
    expect(cls).toContain('pl-5')
    expect(cls).not.toContain('px-5')
  })
})

// 입찰 폭주 배너. 5초 창의 증가분만 보는 판정이라 손으로 재현하려면 실제로 5초 동안 입찰이 몰려야 한다.
// 임계값·창 길이는 물론 "끊긴 동안은 세지 않는다"까지 조용히 깨질 수 있는 분기다.
describe('LiveLayout 입찰 폭주 배너', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })
  afterEach(() => vi.useRealTimers())

  /** 창 하나를 흘려보낸다 — bidCount를 세팅한 뒤 5초를 넘긴다 */
  async function advanceWindow(live: ReturnType<typeof useLiveStore>, bidCount: number) {
    live.bidCount = bidCount
    vi.advanceTimersByTime(5_000)
    await nextTick()
  }

  function mountLive() {
    const live = useLiveStore()
    live.connectionStatus = 'connected'
    const wrapper = mount(LiveLayout, { props: { title: '기계식 키보드' } })
    return { live, wrapper }
  }

  it('한 창에 3건 이상 늘면 배너가 뜨고, 잠잠해지면 사라진다', async () => {
    const { live, wrapper } = mountLive()
    await advanceWindow(live, 1) // 기준선
    expect(wrapper.text()).not.toContain('입찰 폭주 중')

    await advanceWindow(live, 4) // +3
    expect(wrapper.text()).toContain('입찰 폭주 중')

    await advanceWindow(live, 5) // +1
    expect(wrapper.text()).not.toContain('입찰 폭주 중')
  })

  it('연결이 끊긴 동안의 증가는 폭주로 치지 않는다 — 재연결 후 따라잡은 값이다', async () => {
    const { live, wrapper } = mountLive()
    await advanceWindow(live, 1)
    live.connectionStatus = 'reconnecting'
    await advanceWindow(live, 20)
    expect(wrapper.text()).not.toContain('입찰 폭주 중')
  })

  // 쿨다운이 없으면 붐비는 방에서는 매 창이 조건을 만족해 배너가 상시 노출된다 — 뜨는 것 자체가
  // 신호인 연출이라 그러면 벽지가 된다. 화면상으로는 "잘 뜬다"로 보여 눈에 안 걸린다.
  it('연속으로 몰려도 쿨다운(30초) 동안은 다시 뜨지 않는다', async () => {
    const { live, wrapper } = mountLive()
    await advanceWindow(live, 1)
    await advanceWindow(live, 4) // +3 → 뜬다
    expect(wrapper.text()).toContain('입찰 폭주 중')

    // 계속 몰리는 6개 창(30초)은 침묵
    let bids = 4
    for (let i = 0; i < 6; i++) {
      bids += 3
      await advanceWindow(live, bids)
      expect(wrapper.text()).not.toContain('입찰 폭주 중')
    }

    // 쿨다운이 끝난 다음 창에서 다시 뜬다
    await advanceWindow(live, bids + 3)
    expect(wrapper.text()).toContain('입찰 폭주 중')
  })
})
