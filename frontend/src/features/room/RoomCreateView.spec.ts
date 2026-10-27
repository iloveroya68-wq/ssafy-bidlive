import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useRoomStore } from '@/stores/room'
import RoomCreateView from './RoomCreateView.vue'
import RoomCreateStep1 from './RoomCreateStep1.vue'
import RoomCreateStep2 from './RoomCreateStep2.vue'

// 에러 문구 **두 개가 각자 다른 자리**에 뜨는지 잠근다 (S15P11A704-712).
// 예전에는 ref 하나를 공유해서 둘 다 화면 맨 아래 버튼 위에 떴다 — AI-04 사유는 「n번째 사진」을
// 가리키는데 정작 사진 카드는 스크롤 위에 있어서, 무엇을 고치라는 문구인지 보이지 않았다.
// 자리가 요점이므로 문자열 존재가 아니라 **DOM 위치**로 검사한다.

const { route, router, rejectionMock } = await vi.hoisted(async () => {
  const { reactive } = await import('vue')
  // 단계 전환은 오직 `?step=`을 통해서만 일어난다(화면이 쿼리를 watch한다) —
  // 라우터 목이 쿼리를 실제로 바꿔 줘야 2단계가 그려진다.
  const route = reactive({ query: {} as Record<string, string> })
  const write = (to: unknown) => {
    if (typeof to === 'object' && to !== null && 'query' in to) {
      route.query = { ...(to as { query: Record<string, string> }).query }
    }
  }
  return {
    route,
    router: { push: vi.fn(write), replace: vi.fn(write), back: vi.fn() },
    rejectionMock: vi.fn<() => Promise<string | null>>(),
  }
})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return { ...actual, useRoute: () => route, useRouter: () => router }
})
vi.mock('@/shared/api/ai', () => ({ findProductRejection: rejectionMock }))

const mountView = () => mount(RoomCreateView, { global: { stubs: { RouterLink: true } } })

/** 1단계를 통과 가능한 상태로 채운다. 사진은 이미 업로드된 서버 URL이어야 검열까지 간다 */
async function fillStep1(wrapper: ReturnType<typeof mountView>) {
  const step1 = wrapper.findComponent(RoomCreateStep1)
  await wrapper.find('#cr-title').setValue('기계식 키보드')
  await wrapper.find('#cr-desc').setValue('풀빌드 저소음 적축')
  step1.vm.$emit('update:categoryCode', 'ELECTRONICS')
  step1.vm.$emit('update:images', [
    { id: 'i1', imageUrl: 'https://cdn.test/1.png', sortOrder: 1, isThumbnail: true },
  ])
  await flushPromises()
  return step1
}

const buttonByText = (wrapper: ReturnType<typeof mountView>, text: string) =>
  wrapper.findAll('button').find((b) => b.text().includes(text))!

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  route.query = {}
  // 둘 다 jsdom에 없다. 없으면 문구가 뜨는 순간 TypeError로 죽어 검사하려던 것과 무관하게 실패한다
  Element.prototype.scrollIntoView = vi.fn()
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
})

describe('RoomCreateView 에러 문구 자리 (S15P11A704-712)', () => {
  it('AI-04 검열이 막으면 사유가 1단계 사진 카드 바로 아래에 뜬다', async () => {
    rejectionMock.mockResolvedValue(
      '1번째 사진: 위조품으로 판정됐어요. 다른 사진·문구로 수정해 주세요.',
    )
    const wrapper = mountView()
    const step1 = await fillStep1(wrapper)

    await buttonByText(wrapper, '다음').trigger('click')
    await flushPromises()

    const alert = step1.findAll('p[role="alert"]').at(-1)!
    expect(alert.text()).toContain('1번째 사진')
    // 「상품 사진」 카드 **직후**여야 한다 — 버튼 위로 돌아가면 이 티켓이 되살아난다
    expect(step1.find('section').element.nextElementSibling).toBe(alert.element)
    // 「다음」을 누른 직후라 문구가 화면 밖(위쪽)일 수 있다
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith(
      expect.objectContaining({ block: 'center' }),
    )
    expect(route.query.step).toBeUndefined()
  })

  it('2단계 개설 실패 문구는 그대로 버튼 바로 위에 뜬다', async () => {
    rejectionMock.mockResolvedValue(null)
    const roomStore = useRoomStore()
    vi.spyOn(roomStore, 'createRoom').mockRejectedValue(new Error('boom'))

    const wrapper = mountView()
    await fillStep1(wrapper)
    await buttonByText(wrapper, '다음').trigger('click')
    await flushPromises()
    expect(route.query.step).toBe('2')

    wrapper.findComponent(RoomCreateStep2).vm.$emit('update:reservePrice', 10_000)
    await flushPromises()
    await buttonByText(wrapper, '개설하기').trigger('click')
    await flushPromises()

    const alert = wrapper.findAll('p[role="alert"]').at(-1)!
    expect(alert.text()).toContain('방 개설에 실패')
    // 다음 형제가 「← 이전」·「방송 개설하기」를 담은 버튼 줄이다
    expect(alert.element.nextElementSibling?.querySelector('button')).not.toBeNull()
  })
})
