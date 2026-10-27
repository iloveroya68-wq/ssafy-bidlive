import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import QuestionQueue from './QuestionQueue.vue'

// 「더 보기」는 **상한 뒤로 남은 질문이 있을 때만** 나와야 한다 (S15P11A704-371).
// 항상 띄우면 다 불러온 큐에서도 "더 있다"고 말하게 되고, 안 띄우면 예전처럼 조용히 잘린 채 끝난다.
// 둘 다 화면만 봐서는 구분이 안 된다 — 상한을 넘길 만큼 질문이 쌓인 상태를 만들기 어렵기 때문이다.

const items = [
  { questionId: '01A', nickname: '서연', content: '스위치 윤활 되어 있나요?' },
  { questionId: '01B', nickname: '준호', content: '키캡 흠집 있나요?' },
]

const render = (props: Record<string, unknown> = {}) =>
  mount(QuestionQueue, { props: { items, ...props } })

/**
 * 「더 보기」 버튼 — 항목마다 있는 「답변 완료」와 구분한다.
 * 불러오는 중에는 문구가 「불러오는 중…」으로 바뀌므로 둘 다 본다.
 */
function moreButton(wrapper: ReturnType<typeof render>) {
  return wrapper.findAll('button').find((b) => /더 보기|불러오는 중/.test(b.text()))
}

describe('QuestionQueue 「더 보기」', () => {
  it('남은 게 없으면 안 보인다', () => {
    expect(moreButton(render())).toBeUndefined()
    expect(moreButton(render({ hasMore: false }))).toBeUndefined()
  })

  it('남았으면 보이고 누르면 more를 올린다', async () => {
    const wrapper = render({ hasMore: true })

    await moreButton(wrapper)!.trigger('click')

    expect(wrapper.emitted('more')).toHaveLength(1)
  })

  it('불러오는 중에는 잠기고 문구가 바뀐다 — 연타로 같은 커서를 두 번 받지 않게', () => {
    const button = moreButton(render({ hasMore: true, loadingMore: true }))!

    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toContain('불러오는 중')
  })

  it('질문이 없어도 남은 게 있으면 보인다 — 앞쪽을 다 답한 상태다', () => {
    // 100건을 다 처리하면 items는 비지만 뒤에 남아 있을 수 있다. 그때 빈 문구만 뜨면
    // 판매자는 큐가 끝난 줄 안다.
    const wrapper = mount(QuestionQueue, { props: { items: [], hasMore: true } })

    expect(wrapper.text()).toContain('아직 들어온 질문이 없어요')
    expect(moreButton(wrapper)).toBeDefined()
  })
})
