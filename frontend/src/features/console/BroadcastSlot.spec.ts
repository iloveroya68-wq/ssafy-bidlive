import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BroadcastSlot from './BroadcastSlot.vue'

// FE2 소유 프리뷰는 getUserMedia를 잡으므로 스텁으로 대체한다 (이 파일의 관심사는 액션 줄뿐).
const stubs = { PublisherPreview: true }

type SlotProps = InstanceType<typeof BroadcastSlot>['$props']

// 캐스트 없이 필수 prop을 다 채운다 — `as never`로 지우면 props 계약이 바뀌어도 vue-tsc가 여기서 안 멈춘다
function render(props: Partial<SlotProps> = {}) {
  const merged: SlotProps = { state: 'live', pending: false, ...props }
  return mount(BroadcastSlot, { props: merged, global: { stubs } })
}

function labels(wrapper: ReturnType<typeof render>) {
  return wrapper.findAll('button').map((b) => b.text())
}

describe('BroadcastSlot', () => {
  it('라이브 중에는 방송 종료만 내준다', () => {
    const wrapper = render({ state: 'live' })
    expect(labels(wrapper)).toEqual(['방송 종료'])
  })

  it('확인 대기 중에도 버튼은 종료 하나뿐이다', () => {
    const wrapper = render({ state: 'live', confirming: true })
    expect(labels(wrapper)).toEqual(['한 번 더 눌러 종료'])
  })

  /**
   * 대기 상태에는 이 카드가 그리는 버튼이 없다 (S15P11A704-691).
   * 예전에는 여기서 「방 삭제하기」를 그렸는데, 화면의 액션 줄에도 「방 삭제」가 있어서
   * 가로 배치로 바꾸자 같은 되돌릴 수 없는 동작의 버튼 둘이 한 화면에 나란히 보였다.
   * 방 관리 액션의 소유는 화면 쪽 한 곳으로 넘겼다.
   */
  it('대기 상태에서는 이 카드가 버튼을 그리지 않는다', () => {
    const wrapper = render({ state: 'wait' })
    expect(labels(wrapper)).toEqual([])
  })

  it('종료 경고는 종료 버튼 바로 다음 형제로 붙는다', async () => {
    const wrapper = render({ state: 'live', confirming: true })
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('되돌릴 수 없습니다')
    expect(alert.element.previousElementSibling).toBe(wrapper.get('button').element)
  })

  it('확인 전에는 경고를 띄우지 않는다', () => {
    expect(render({ state: 'live' }).find('[role="alert"]').exists()).toBe(false)
  })
})
