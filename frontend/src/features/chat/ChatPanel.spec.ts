import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import { CHAT_OLDER_MAX, useLiveStore, type ChatMessage } from '@/stores/live'
import { useAuthStore, type MemberProfile } from '@/stores/auth'
import ChatPanel from './ChatPanel.vue'

// ChatPanel이 setup에서 useRoute()를 부른다 — 비로그인 잠금의 로그인 링크가 복귀 경로를 실어 보내기 때문이다
// (S15P11A704-430). 라우터 없이 부르면 undefined라 AppHeader.spec과 같은 방식으로 최소 스텁만 준다.
const currentPath = ref('/rooms/1/live')
// 시연 스트립은 `?chatdemo=1` 옵트인이다(453, !161 리뷰) — 쿼리도 바꿀 수 있어야 한다.
// 파싱 변형(`?k`은 null이고 `?k=`은 '')은 shared/lib/useMockDemoFlag.spec.ts가 덮는다 — 여기선 배선만 본다.
const currentQuery = ref<Record<string, string>>({})
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get fullPath() {
        return currentPath.value
      },
      get query() {
        return currentQuery.value
      },
    }),
  }
})

const RouterLinkStub = { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' }

// 시연 트리거(453)는 isMockApi() 게이트 뒤에 있다. 기본은 목 모드(테스트 환경 기본값)이고,
// 실 모드 누출을 보는 테스트에서만 뒤집는다 — setup에서 한 번 읽으므로 mount 전에 세워야 한다.
const { mockApiFlag } = vi.hoisted(() => ({ mockApiFlag: { value: true } }))
vi.mock('@/shared/api/mock', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/mock')>('@/shared/api/mock')
  return { ...actual, isMockApi: () => mockApiFlag.value }
})

// CHAT-02 고정 공지 API(559)는 실 호출이다 — 스펙에선 모듈째 갈아 끼운다.
// vi.hoisted로 만들어 각 테스트가 직접 reset·해석을 지정한다(전역 afterEach의 restoreAllMocks는
// spyOn만 되돌리므로 여기 vi.fn에는 기대지 않는다).
// CHAT-01 메시지 삭제(681)도 같은 모듈이라 함께 갈아 끼운다.
const { pinNoticeMock, unpinNoticeMock, deleteChatMessageMock } = vi.hoisted(() => ({
  pinNoticeMock: vi.fn(),
  unpinNoticeMock: vi.fn(),
  deleteChatMessageMock: vi.fn(),
}))
vi.mock('@/shared/api/chat', () => ({
  pinNotice: pinNoticeMock,
  unpinNotice: unpinNoticeMock,
  deleteChatMessage: deleteChatMessageMock,
}))

function member(): MemberProfile {
  return { id: 25, nickname: '준호', role: 'USER', status: 'ACTIVE', withdrawnAt: null }
}

// older 보관 상한(406) 검증용 — 상한만큼의 older 행 배열.
const olderRowsAtCap = (): ChatMessage[] =>
  Array.from({ length: CHAT_OLDER_MAX }, (_, i) => ({
    ...msg('x'),
    id: `srv-${1000 + i}`,
    clientMessageId: null,
    older: true,
  }))

// 앞단 트림 스크롤 보정(S15P11A704-392) — 피처 감지 두 경로를 검증한다.
// jsdom은 레이아웃을 계산하지 않으므로 offsetTop·scrollHeight·clientHeight·scrollTop을 스텁한다.

function msg(key: string): ChatMessage {
  return {
    id: `opt-${key}`,
    nickname: '나',
    text: key,
    isQuestion: false,
    clientMessageId: key, // key로 식별
    maskedFields: [],
    createdAt: '2026-01-01T00:00:00.000Z',
  }
}

// 각 data-chat-row의 offsetTop을 형제 중 index*rowHeight로 동적 계산하는 프로토타입 스텁 — prepend로 행이
// 재정렬돼도 측정이 맞다(과거 조회 앵커링 검증용). afterEach에서 원복한다.
let restoreOffsets: (() => void) | null = null
function stubDynamicRowOffsets(rowHeight: number) {
  const desc = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetTop')
  Object.defineProperty(HTMLElement.prototype, 'offsetTop', {
    configurable: true,
    get(this: HTMLElement) {
      if (!this.hasAttribute?.('data-chat-row') || !this.parentElement) return 0
      return (
        Array.from(this.parentElement.querySelectorAll('[data-chat-row]')).indexOf(this) * rowHeight
      )
    },
  })
  restoreOffsets = () => {
    if (desc) Object.defineProperty(HTMLElement.prototype, 'offsetTop', desc)
    else delete (HTMLElement.prototype as unknown as Record<string, unknown>).offsetTop
    restoreOffsets = null
  }
}

// overflow-anchor 지원 여부 강제 — ChatPanel이 setup에서 CSS.supports를 읽으므로 mount 전에 stub한다.
function setAnchorSupport(supported: boolean) {
  vi.stubGlobal('CSS', { supports: () => supported })
}

// 각 행 offsetTop을 인덱스*rowHeight로, 컨테이너 geometry를 스텁한다. scrollTop은 backing 변수로 읽기/쓰기.
function stubGeometry(
  wrapper: VueWrapper,
  opts: { rowHeight: number; scrollTop: number; scrollHeight: number; clientHeight: number },
) {
  const rows = wrapper.findAll('[data-chat-row]')
  rows.forEach((r, i) => {
    Object.defineProperty(r.element, 'offsetTop', {
      get: () => i * opts.rowHeight,
      configurable: true,
    })
  })
  const listEl = rows[0].element.parentElement as HTMLElement
  const state = { scrollTop: opts.scrollTop }
  Object.defineProperty(listEl, 'scrollHeight', {
    get: () => opts.scrollHeight,
    configurable: true,
  })
  Object.defineProperty(listEl, 'clientHeight', {
    get: () => opts.clientHeight,
    configurable: true,
  })
  Object.defineProperty(listEl, 'scrollTop', {
    get: () => state.scrollTop,
    set: (v: number) => {
      state.scrollTop = v
    },
    configurable: true,
  })
  return state
}

// 위로 읽는 중(바닥 아님) geometry로 마운트하고, 앞(a) 잘리고 뒤(d) 추가되는 상한 트림을 재현한다.
async function mountAndTrim(supported: boolean, scrollTop: number) {
  setAnchorSupport(supported)
  const live = useLiveStore()
  live.chat = [msg('a'), msg('b'), msg('c')]
  const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
  await flushPromises()
  const geo = stubGeometry(wrapper, {
    rowHeight: 20,
    scrollTop,
    scrollHeight: 1000,
    clientHeight: 300,
  })
  live.chat = [msg('b'), msg('c'), msg('d')]
  await flushPromises()
  return geo
}

beforeEach(() => {
  setActivePinia(createPinia())
  // 기본 픽스처는 로그인 상태다 — 채팅의 정상 시나리오이고, 비로그인은 전송 자체가 잠긴다(S15P11A704-430).
  // 이걸 빼면 입력창을 만지는 모든 테스트가 잠금 안내만 보게 된다.
  useAuthStore().user = member()
})
afterEach(() => {
  mockApiFlag.value = true
  currentQuery.value = {}
  currentPath.value = '/rooms/1/live'
  restoreOffsets?.()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('ChatPanel — 앞단 트림 스크롤 보정 (S15P11A704-392)', () => {
  it('overflow-anchor 미지원(Safari): 위로 읽는 중 앞단 1건 트림 시 잘린 높이만큼 scrollTop을 줄인다', async () => {
    // 1000 - 500 - 300 = 200 ≥ 40 → 바닥 아님. 잘린 a 높이(b.offsetTop 20 - a.offsetTop 0)만큼 감소.
    const geo = await mountAndTrim(false, 500)
    expect(geo.scrollTop).toBe(480)
  })

  it('미지원 + 낙관 행이 맨 앞: 중간 확정 메시지가 사라져도(capChat 399) 그 높이만큼 보정한다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('f'), msg('c1'), msg('c2')] // f = 앞에 눌러앉은 낙관 행 자리(키만 중요)
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    const geo = stubGeometry(wrapper, {
      rowHeight: 20,
      scrollTop: 500,
      scrollHeight: 1000,
      clientHeight: 300,
    })
    // capChat이 f를 건너뛰고 중간 확정 c1(index 1)을 버린 결과 — next[0]은 여전히 f라 앞단 연속 판정으론 0이 나오는 케이스
    live.chat = [msg('f'), msg('c2'), msg('d')]
    await flushPromises()
    // c1이 차지하던 높이(row[2].offsetTop 40 - row[1] 20 = 20)만큼 감소 → 시야 고정
    expect(geo.scrollTop).toBe(480)
  })

  // 상한 트림은 항상 앞단(=시야 위)이라 위치를 안 따져도 맞았지만, CHAT_DELETED(S15P11A704-681)는
  // 아무 위치의 행이나 지운다 — 시야 안·아래가 사라지면 아래가 위로 당겨질 뿐이라 보정하면 그만큼 튄다.
  it('미지원 + 시야 아래 행이 사라지면 보정하지 않는다 (CHAT_DELETED)', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a'), msg('b'), msg('c'), msg('d'), msg('e')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    const geo = stubGeometry(wrapper, {
      rowHeight: 20,
      scrollTop: 30,
      scrollHeight: 1000,
      clientHeight: 300,
    })

    // c(index 2)의 아래 경계는 60 — scrollTop 30보다 아래라 시야 안이다(앞단 트림이 아니다)
    live.chat = [msg('a'), msg('b'), msg('d'), msg('e')]
    await flushPromises()

    expect(geo.scrollTop).toBe(30)
  })

  // 완전히 위(전체 보정)·시야 안(무보정) 사이에 **걸친** 행이 있다 — 전체를 세면 과보정, 건너뛰면 미보정이라
  // 어느 쪽이든 그 차이만큼 화면이 튄다(!319 리뷰). 시야 밖으로 나가 있던 위쪽 부분만 되돌린다.
  it('미지원 + 상단 경계에 걸친 행이 사라지면 시야 위 부분만 보정한다 (CHAT_DELETED)', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a'), msg('b'), msg('c'), msg('d'), msg('e')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    const geo = stubGeometry(wrapper, {
      rowHeight: 20,
      scrollTop: 30,
      scrollHeight: 1000,
      clientHeight: 300,
    })

    // b(index 1)는 20~40을 차지하고 시야는 30부터다 — 위 10px만 시야 밖이었다
    live.chat = [msg('a'), msg('c'), msg('d'), msg('e')]
    await flushPromises()

    expect(geo.scrollTop).toBe(20) // 30 - 10, 전체 높이(20)를 빼지 않는다
  })

  it('overflow-anchor 지원(Chrome·FF): 네이티브 앵커링에 맡기고 수동 보정하지 않는다', async () => {
    // 같은 상황이지만 지원 브라우저 → 수동 보정을 건너뛰어 scrollTop을 안 건드린다(멀쩡한 경로 무변경).
    const geo = await mountAndTrim(true, 500)
    expect(geo.scrollTop).toBe(500)
  })

  it('바닥 근처면 보정 대신 scrollToBottom (지원 여부 무관)', async () => {
    // 1000 - 990 - 300 < 40 → 바닥 근처. 보정(-20)이 아니라 scrollToBottom(=scrollHeight).
    const geo = await mountAndTrim(false, 990)
    expect(geo.scrollTop).toBe(1000)
  })
})

describe('ChatPanel — 과거 조회 (S15P11A704-379)', () => {
  it('상단 근처로 스크롤하면 loadOlderChat을 호출한다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a')]
    const spy = vi.spyOn(live, 'loadOlderChat').mockResolvedValue()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    // jsdom scrollTop 기본 0 ≤ 80 + hasMore 기본 true → 트리거
    await wrapper.get('.overflow-y-auto').trigger('scroll')

    expect(spy).toHaveBeenCalled()
  })

  it('과거 메시지를 앞에 붙이면 그 높이만큼 scrollTop을 늘려 읽던 위치를 고정한다(미지원 경로)', async () => {
    setAnchorSupport(false)
    stubDynamicRowOffsets(20)
    const live = useLiveStore()
    live.chat = [msg('a'), msg('b'), msg('c')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    // 위로 읽는 중: 1000 - 200 - 300 = 500 ≥ 40 → 바닥 아님
    const listEl = wrapper.get('.overflow-y-auto').element as HTMLElement
    const st = { v: 200 }
    Object.defineProperty(listEl, 'scrollTop', {
      get: () => st.v,
      set: (x: number) => {
        st.v = x
      },
      configurable: true,
    })
    Object.defineProperty(listEl, 'scrollHeight', { get: () => 1000, configurable: true })
    Object.defineProperty(listEl, 'clientHeight', { get: () => 300, configurable: true })

    // 과거 2건 prepend
    live.chat = [msg('o1'), msg('o2'), msg('a'), msg('b'), msg('c')]
    await flushPromises()

    // 앞에 붙은 2행 높이(40)만큼 scrollTop 증가 → 읽던 위치 고정
    expect(st.v).toBe(240)
  })
})

describe('ChatPanel — 전송 실패 행 조치 (S15P11A704-400)', () => {
  it('실패 행에 재시도·닫기 버튼을 렌더하고 클릭 시 store 액션을 부른다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true }]
    const retry = vi.spyOn(live, 'retryChat').mockImplementation(() => {})
    const dismiss = vi.spyOn(live, 'dismissChatFailed').mockImplementation(() => {})
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    const byText = (t: string) => wrapper.findAll('button').find((b) => b.text() === t)
    const retryBtn = byText('다시 시도')
    const dismissBtn = byText('닫기')
    if (!retryBtn || !dismissBtn) throw new Error('재시도/닫기 버튼을 찾지 못함')

    await retryBtn.trigger('click')
    expect(retry).toHaveBeenCalledWith('c1')

    await dismissBtn.trigger('click')
    expect(dismiss).toHaveBeenCalledWith('c1')
  })

  it('뮤트 중이면 다시 시도 버튼이 비활성화된다(리뷰 1)', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true }]
    live.muted = true
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    const retryBtn = wrapper.findAll('button').find((b) => b.text() === '다시 시도')
    if (!retryBtn) throw new Error('다시 시도 버튼을 찾지 못함')
    expect((retryBtn.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('닫기로 지운 프레임은 트림 스크롤 보정을 건너뛴다(리뷰 2)', async () => {
    setAnchorSupport(false)
    stubDynamicRowOffsets(20)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true }, msg('a'), msg('b')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    // 위로 읽는 중: 1000 - 100 - 300 = 600 ≥ 40 → 바닥 아님
    const listEl = wrapper.get('.overflow-y-auto').element as HTMLElement
    const st = { v: 100 }
    Object.defineProperty(listEl, 'scrollTop', {
      get: () => st.v,
      set: (x: number) => {
        st.v = x
      },
      configurable: true,
    })
    Object.defineProperty(listEl, 'scrollHeight', { get: () => 1000, configurable: true })
    Object.defineProperty(listEl, 'clientHeight', { get: () => 300, configurable: true })

    const dismissBtn = wrapper.findAll('button').find((b) => b.text() === '닫기')
    if (!dismissBtn) throw new Error('닫기 버튼을 찾지 못함')
    await dismissBtn.trigger('click')

    expect(live.chat.map((m) => m.clientMessageId)).toEqual(['a', 'b']) // 실제 제거됨
    expect(st.v).toBe(100) // 지운 행(20px) 보정 안 함 → scrollTop 그대로
  })
})

describe('ChatPanel — 검열 표시 (S15P11A704-413)', () => {
  it('maskedFields가 있으면 서버가 가린 content를 그대로 렌더하고 중립 「검열됨」 힌트를 붙인다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), text: '이런 **', maskedFields: ['content'] }]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('이런 **') // 서버본 그대로 노출(FE 재가공 없음)
    expect(wrapper.text()).toContain('검열됨')
  })

  it('maskedFields가 비면 검열 힌트를 붙이지 않는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), text: '평범한 인사', maskedFields: [] }]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('평범한 인사')
    expect(wrapper.text()).not.toContain('검열됨')
  })
})

describe('ChatPanel — 개인 검열 경고 토스트 (S15P11A704-414)', () => {
  it('MODERATION_WARNING.message를 그대로 토스트로 표시하고 reason 코드는 노출하지 않는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    live.moderationWarning = {
      id: 1,
      reason: 'OFF_PLATFORM_TRADE',
      message: '외부 거래 유도는 제한됩니다.',
      messageId: 'srv-1',
    }
    await flushPromises()

    expect(wrapper.text()).toContain('외부 거래 유도는 제한됩니다.') // 서버 표시 문구 그대로
    expect(wrapper.text()).not.toContain('OFF_PLATFORM_TRADE') // 공개 enum(코드)은 미노출
    wrapper.unmount()
  })

  it('message가 비면 안전한 폴백 문구로 대체한다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    live.moderationWarning = { id: 1, reason: 'x', message: '', messageId: null }
    await flushPromises()

    expect(wrapper.text()).toContain('검열 경고를 받았어요')
    wrapper.unmount()
  })
})

describe('ChatPanel — 과거 보관 상한 (S15P11A704-406)', () => {
  it('older 보관 상한 도달 시 「여기까지만」 안내를 띄운다(서버엔 더 있어 "처음이에요"와 구분)', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = olderRowsAtCap() // chatHasMore는 초기값 true → 서버엔 더 있음
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('여기까지만 불러왔어요')
    expect(wrapper.text()).not.toContain('대화의 처음이에요')
  })

  it('older 보관 상한이면 상단 스크롤에도 loadOlderChat을 안 부른다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = olderRowsAtCap()
    const spy = vi.spyOn(live, 'loadOlderChat').mockResolvedValue()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    await wrapper.get('.overflow-y-auto').trigger('scroll') // scrollTop 0 ≤ 80 + hasMore true지만 상한이라 안 부름

    expect(spy).not.toHaveBeenCalled()
  })
})

// 「대화의 처음이에요」는 **서버가 마지막 페이지를 준 경우만** 사실이다 (S15P11A704-679).
// 4xx로 끊긴 경우엔 hasMore가 같이 내려가는데, 그 원인은 401 비로그인·403·비공개 방 404라
// 같은 문구를 띄우면 "이 방엔 원래 대화가 없다"고 거짓말을 하게 된다.
describe('ChatPanel — 과거 조회 차단 표시 (S15P11A704-679)', () => {
  it('4xx로 끊겼으면(chatOlderBlocked) 「대화의 처음이에요」를 띄우지 않는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a')]
    live.chatHasMore = false
    live.chatOlderBlocked = true
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('대화의 처음이에요')
  })

  it('서버가 마지막 페이지를 준 경우엔 그대로 띄운다 — 차단 분리가 정상 경로를 죽이지 않게', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a')]
    live.chatHasMore = false
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('대화의 처음이에요')
  })
})

describe('ChatPanel — Enter 전송 · IME 조합 가드 (S15P11A704-420)', () => {
  function enterOn(wrapper: VueWrapper, opts: KeyboardEventInit = {}): KeyboardEvent {
    // Vue의 @keydown.enter.exact 리스너를 실제 KeyboardEvent로 친다 — isComposing은 생성자 옵션으로만
    // 설정 가능해(trigger 옵션 병합으로는 read-only라 안 먹음) native dispatch로 검증한다.
    // 이벤트를 반환해 defaultPrevented(= preventDefault 호출 여부)까지 단정한다.
    const evt = new KeyboardEvent('keydown', {
      key: 'Enter',
      bubbles: true,
      cancelable: true,
      ...opts,
    })
    wrapper.get('textarea').element.dispatchEvent(evt)
    return evt
  }

  it('조합이 아닌 Enter는 전송하고 기본 동작(줄바꿈)을 막는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const send = vi.spyOn(live, 'sendChat').mockImplementation(() => {})
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    await wrapper.get('textarea').setValue('안녕하세요')
    const evt = enterOn(wrapper)
    await flushPromises()

    expect(send).toHaveBeenCalledWith('안녕하세요', false)
    expect(evt.defaultPrevented).toBe(true) // 줄바꿈 방지 — .prevent 제거 후 핸들러가 직접 막는다
  })

  it('IME 조합 중 Enter(isComposing)는 전송하지 않고 기본 동작(조합 확정)을 막지 않는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const send = vi.spyOn(live, 'sendChat').mockImplementation(() => {})
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    await wrapper.get('textarea').setValue('안녕하세')
    const evt = enterOn(wrapper, { isComposing: true })
    await flushPromises()

    expect(send).not.toHaveBeenCalled() // 미완성 문장 전송 방지
    expect(evt.defaultPrevented).toBe(false) // 조합 확정을 막지 않는다(이 수정의 절반)
  })
})

// 비로그인 시청자의 채팅 (S15P11A704-430 → 461로 뒤집음).
//
// 430은 입력부를 통째로 잠갔다. 그러면 패널이 "채팅이 안 되는 방"으로 보이고, 참여 의사가 생기기 전에
// 차단해 전환 지점을 놓친다. 461에서 입력부를 열어 두고 **입력하려는 순간**에 안내하도록 바꿨다.
// 스토어 보호(닉네임 null 낙관 행 차단)는 그대로 유지된다 — send()가 sendChat 앞에서 가로챈다.
describe('ChatPanel — 비로그인 채팅 안내 (S15P11A704-430)', () => {
  const mountAnonymous = (props: Record<string, unknown> = {}) => {
    useAuthStore().user = null
    return mount(ChatPanel, {
      props: { roomId: 1, ...props },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
  }

  it('비로그인도 입력창이 정상처럼 보이고 readonly다 — 잠긴 박스로 바꾸지 않는다', async () => {
    const live = useLiveStore()
    live.chat = [msg('a')]
    const wrapper = mountAnonymous()
    await flushPromises()

    const ta = wrapper.get('textarea')
    expect(ta.attributes('readonly')).toBeDefined() // 못 치므로 잃을 초안이 없다
    expect(wrapper.text()).not.toContain('로그인하면 채팅에 참여할 수 있어요') // 430의 잠금 문구는 없다
    expect(wrapper.text()).toContain('a') // 구경 허용 — 메시지는 계속 보인다
  })

  it('상호작용 전에는 안내가 뜨지 않는다 — 참여 의사가 생기기 전에 차단하지 않는다', async () => {
    const wrapper = mountAnonymous()
    await flushPromises()
    expect(wrapper.text()).not.toContain('로그인이 필요해요')
  })

  it('입력창에 포커스하면 안내가 뜬다', async () => {
    const wrapper = mountAnonymous()
    await flushPromises()

    await wrapper.get('textarea').trigger('focus')

    expect(wrapper.text()).toContain('채팅을 보내려면 로그인이 필요해요')
  })

  it('로그인 링크가 지금 화면을 복귀 지점으로 실어 보낸다', async () => {
    currentPath.value = '/rooms/7/live'
    const wrapper = mountAnonymous()
    await flushPromises()
    await wrapper.get('textarea').trigger('focus')

    const link = wrapper.getComponent({ name: 'RouterLink' })
    expect(link.props('to')).toEqual({ name: 'login', query: { redirect: '/rooms/7/live' } })
  })

  it('전송을 눌러도 sendChat이 불리지 않고 안내만 뜬다 — 닉네임 null 낙관 행 차단은 유지된다', async () => {
    const live = useLiveStore()
    const send = vi.spyOn(live, 'sendChat').mockImplementation(() => {})
    const wrapper = mountAnonymous()
    await flushPromises()

    const btn = wrapper.findAll('button').find((b) => b.text() === '전송')
    if (!btn) throw new Error('전송 버튼을 찾지 못함')
    expect((btn.element as HTMLButtonElement).disabled).toBe(false) // 비어도 눌러 안내를 볼 수 있다
    await btn.trigger('click')

    expect(send).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('채팅을 보내려면 로그인이 필요해요')
  })

  it('안내를 스크린리더에도 알린다 (422 리전 연계)', async () => {
    const wrapper = mountAnonymous()
    await flushPromises()
    await wrapper.get('textarea').trigger('focus')

    expect(wrapper.get('.sr-only[aria-live="polite"]').text()).toContain('로그인이 필요해요')
  })

  it('컨테이너가 잠근 방이면 로그인을 권하지 않는다 — 로그인으로 풀리지 않을 수 있다', async () => {
    const wrapper = mountAnonymous({ disabled: true })
    await flushPromises()

    expect(wrapper.text()).toContain('종료된 방송이라 채팅을 보낼 수 없어요')
    expect(wrapper.text()).not.toContain('로그인하면')
    expect(wrapper.findComponent({ name: 'RouterLink' }).exists()).toBe(false)
  })

  // 컨테이너가 사유를 직접 지정하는 경로(대기방의 !permissions.chat)도 같은 우선순위를 탄다.
  // 이 값이 터미널 사유라는 보장은 컨테이너 몫이라는 계약을 여기서 고정한다 — 그 전제가 바뀌면
  // 사유를 구분해 넘기도록 계약을 넓혀야 하고, 그때 이 테스트가 먼저 걸린다(!140 리뷰 1).
  it('컨테이너가 사유 문구를 지정하면 그것만 보여준다 — 로그인 유도로 덮지 않는다', async () => {
    const wrapper = mountAnonymous({
      disabled: true,
      disabledText: '🔒 이 방에서는 채팅이 제한됐어요',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('이 방에서는 채팅이 제한됐어요')
    expect(wrapper.findComponent({ name: 'RouterLink' }).exists()).toBe(false)
  })

  it('로그인 상태에선 잠금도 링크도 없다', async () => {
    const wrapper = mount(ChatPanel, {
      props: { roomId: 1 },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'RouterLink' }).exists()).toBe(false)
  })
})

// 채팅 상태 문구·검열 경고 표시 정합 (S15P11A704-428, 표시 수명은 703에서 갱신).
// 경고는 6초 뒤 사라지고 한 줄로 잘려서, 왜 걸렸는지 못 읽은 채 같은 이유로 재차 뮤트될 수 있었다.
// 428은 말줄임과 자동 소멸을 함께 걷었고, 703이 자동 소멸만 **10초로** 되살렸다 — 못 읽던 원인은 6초였지
// 자동 소멸 자체가 아니었고, 상주하는 동안엔 안내가 메시지 목록을 덮는다. 말줄임 부재는 그대로 고정한다.
// 빈 목록은 잠금 여부와 무관하게 "먼저 인사를 건네보세요"를 권해, 막힌 입력창과 모순됐다.
describe('ChatPanel — 상태 문구·검열 경고 정합 (S15P11A704-428)', () => {
  const warn = (message: string, id = 1) => ({
    id,
    reason: 'OFF_PLATFORM_TRADE',
    message,
    messageId: 'srv-1',
  })

  it('경고는 10초 뒤 자동으로 사라진다 (S15P11A704-703)', async () => {
    vi.useFakeTimers()
    try {
      setAnchorSupport(false)
      const live = useLiveStore()
      const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
      await flushPromises()

      live.moderationWarning = warn('외부 거래 유도는 제한됩니다.')
      await flushPromises()
      expect(wrapper.text()).toContain('외부 거래 유도는 제한됩니다.')

      // 9초까지는 남아 있어야 한다 — 6초로 되돌아가면 여기서 걸린다
      vi.advanceTimersByTime(9_000)
      await flushPromises()
      expect(wrapper.text()).toContain('외부 거래 유도는 제한됩니다.')

      vi.advanceTimersByTime(1_000)
      await flushPromises()
      expect(wrapper.text()).not.toContain('외부 거래 유도는 제한됩니다.')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('새 경고가 오면 타이머를 리셋한다 — 옛 카운트다운으로 순식간에 사라지지 않는다', async () => {
    vi.useFakeTimers()
    try {
      setAnchorSupport(false)
      const live = useLiveStore()
      const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
      await flushPromises()

      live.moderationWarning = warn('첫 번째 경고입니다.')
      await flushPromises()
      vi.advanceTimersByTime(9_000)

      // 같은 사유가 다시 와도 id가 달라 새로 발화한다 — 수명도 처음부터 다시 센다
      live.moderationWarning = warn('두 번째 경고입니다.', 2)
      await flushPromises()

      vi.advanceTimersByTime(9_000) // 첫 경고 기준으론 18초 — 리셋이 없으면 이미 사라졌다
      await flushPromises()
      expect(wrapper.text()).toContain('두 번째 경고입니다.')

      vi.advanceTimersByTime(1_000)
      await flushPromises()
      expect(wrapper.text()).not.toContain('두 번째 경고입니다.')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('닫기 버튼으로 즉시 걷는다 — 자동 소멸을 기다리지 않는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    live.moderationWarning = warn('연락처 공유는 제한됩니다.')
    await flushPromises()

    await wrapper.get('[aria-label="검열 경고 닫기"]').trigger('click')

    expect(wrapper.text()).not.toContain('연락처 공유는 제한됩니다.')
    wrapper.unmount()
  })

  it('긴 사유를 말줄임으로 자르지 않는다 — 잘리면 읽을 수 없다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    live.moderationWarning = warn(
      '외부 메신저로 거래를 유도하는 내용이 감지되었어요. 반복되면 채팅이 제한될 수 있어요.',
    )
    await flushPromises()

    // jsdom엔 레이아웃이 없어 실제 잘림은 못 본다 — truncate 클래스 부재로 고정한다(이 티켓의 실제 결함).
    const alert = wrapper.get('[role="alert"]')
    expect(alert.html()).not.toContain('truncate')
    wrapper.unmount()
  })

  it('잠긴 방의 빈 목록은 인사를 권하지 않는다 — 입력창이 막혀 있다', async () => {
    setAnchorSupport(false)
    const wrapper = mount(ChatPanel, {
      props: { roomId: 1, disabled: true },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('아직 채팅이 없어요')
    expect(wrapper.text()).not.toContain('먼저 인사를 건네보세요')
  })

  // 461에서 비로그인은 잠금 사유가 아니게 됐다 — 입력부가 열려 있으므로 권유가 나오는 것이 맞다.
  // (로그인 안내는 입력하려는 순간에 별도로 뜬다.)
  it('비로그인은 잠금이 아니므로 인사를 권한다 — 이제 칠 수 있다', async () => {
    setAnchorSupport(false)
    useAuthStore().user = null
    const wrapper = mount(ChatPanel, {
      props: { roomId: 1 },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('먼저 인사를 건네보세요')
  })

  it('보낼 수 있으면 인사를 권한다 — 권유는 실행 가능할 때만', async () => {
    setAnchorSupport(false)
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).toContain('먼저 인사를 건네보세요')
  })
})

// 실패 표식 강화 (S15P11A704-424). 이전 표식은 text-dim 10px 인라인이라 "주의를 끈다"는 주석 의도와
// 실제 렌더가 어긋났다 — 빠르게 흐르는 라이브 채팅에서 내 메시지가 안 나갔다는 걸 놓친다.
describe('ChatPanel — 전송 실패 표식 강화 (S15P11A704-424)', () => {
  // 422에서 통지를 상주 sr-only 리전으로 모으면서 이 표식의 role="status"를 뗐다(중복 낭독 방지) —
  // 표식은 시각 사용자용 인라인 요소라 텍스트로 찾는다.
  const failedMarker = (wrapper: VueWrapper) =>
    wrapper.findAll('span').find((el) => el.text().trim() === '전송 실패')

  it('실패 표식에 경고 아이콘이 붙는다 — 색 없이 대비를 만든다(레드=기능색 배제)', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true }]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    const marker = failedMarker(wrapper)
    if (!marker) throw new Error('전송 실패 표식을 찾지 못함')
    expect(marker.find('svg').exists()).toBe(true)
  })

  it('실패 행 자체에 좌측 표시가 붙는다 — 인라인 문구만으로는 스캔이 안 된다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true }, msg('ok')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    const rows = wrapper.findAll('[data-chat-row]')
    // 클래스 이름 자체가 아니라 "실패 행만 좌측 보더를 갖는다"는 대비를 본다
    expect(rows[0].classes().some((c) => c.startsWith('border-l'))).toBe(true)
    expect(rows[1].classes().some((c) => c.startsWith('border-l'))).toBe(false)
  })

  // 서버 거절 사유 표시 (S15P11A704-558). 사유 없는 「전송 실패」는 왜 안 갔는지를 못 알려준다 —
  // 개인 에러 큐가 준 문구(publicMessage)가 있으면 덧붙인다. **대체가 아니라 병기다**: BE의
  // CHAT_CONTENT_INVALID("채팅 내용을 확인해 주세요.")·SLOW_MODE_LIMIT은 전송이 실패했다는 사실을
  // 말하지 않아서(ChatSendErrorCode.java), 사유로 갈아 끼우면 "안 나갔다"가 화면에서 사라진다.
  it('실패 사유는 「전송 실패」와 함께 보여준다 — 사유만 남기면 실패 사실을 잃는다', async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [{ ...msg('c1'), failed: true, failReason: '채팅 내용을 확인해 주세요.' }]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('전송 실패') // 사실이 남는다
    expect(text).toContain('채팅 내용을 확인해 주세요.') // 사유도 붙는다
    expect(text).toContain('다시 시도') // 조치 경로는 그대로
  })
})

// 실시간 연결 상태 표시 (S15P11A704-425).
// 스토어의 connectionStatus가 어디에도 렌더되지 않아, 끊긴 상태와 조용한 상태를 구분할 수 없었다.
describe('ChatPanel — 연결 상태 표시 (S15P11A704-425)', () => {
  const mountWith = async (status: ReturnType<typeof useLiveStore>['connectionStatus']) => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.connectionStatus = status
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    return wrapper
  }

  it('재연결 중이면 끊김을 알린다', async () => {
    const wrapper = await mountWith('reconnecting')
    expect(wrapper.text()).toContain('연결이 끊겼어요')
  })

  it('연결 중이면 진행 중임을 알린다 — 조용한 것과 구분된다', async () => {
    const wrapper = await mountWith('connecting')
    expect(wrapper.text()).toContain('실시간 연결 중이에요')
  })

  // 배너 문구로 좁혀 본다 — 전체 텍스트에서 '연결'을 찾으면 시연 버튼 라벨(453)까지 걸린다.
  const noBanner = (wrapper: VueWrapper) => {
    expect(wrapper.text()).not.toContain('연결이 끊겼어요')
    expect(wrapper.text()).not.toContain('실시간 연결 중이에요')
  }

  it('연결되면 안내를 띄우지 않는다', async () => {
    noBanner(await mountWith('connected'))
  })

  // 목 모드 대기방은 자체 드라이버로 돌아 connectionStatus가 계속 'idle'이다(connectWaitingRoom 미호출).
  // 여기에 배너가 뜨면 정상 화면에 상시 경고가 붙는다 — 이 티켓이 만들 수 있었던 최악의 회귀다.
  it('연결을 시작하지 않은 상태(idle)엔 배너를 띄우지 않는다 — 목 대기방 상시 경고 방지', async () => {
    noBanner(await mountWith('idle'))
  })

  it('의도적으로 끊은 상태(closed)엔 띄우지 않는다 — 퇴장은 사고가 아니다', async () => {
    noBanner(await mountWith('closed'))
  })
})

// 스크린리더 통지 (S15P11A704-422).
// 목록에 role도 라이브 리전도 없어 새 채팅을 전혀 통지하지 않았다. 통지를 상주 sr-only 리전 하나로 모으고,
// 꼬리 추가만 대상으로 한다 — 과거 조회 prepend까지 낭독되면 고치려던 소음보다 커진다.
describe('ChatPanel — 스크린리더 통지 (S15P11A704-422)', () => {
  const region = (wrapper: VueWrapper) => wrapper.get('.sr-only[aria-live="polite"]')
  /**
   * 통지 문구 — 낭독 유실 방지 토글 문자(U+200B, S15P11A704-516)는 벗겨서 본다.
   * 문구 자체를 단정하는 테스트들은 그 문자에 관심이 없고, **바뀌는지**는 아래 516 테스트가 따로 본다.
   */
  const announced = (wrapper: VueWrapper) =>
    region(wrapper)
      .text()
      .replace(/\u200B/g, '')

  const mounted = async () => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = [msg('a')]
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    return { live, wrapper }
  }

  // 남의 메시지는 서버 id(ULID)로 도착한다 — 내 낙관 행의 `opt-` 프리픽스와 구분되는 지점이고,
  // 통지가 자기 발신을 거르는 근거다(!150 리뷰 1). msg()는 opt- id라 "내 행" 픽스처로 쓴다.
  const from = (nickname: string, text: string, key: string): ChatMessage => ({
    ...msg(key),
    id: `srv-${key}`,
    nickname,
    text,
  })

  it('통지 리전은 내용이 없어도 DOM에 상주한다 — 삽입 시 낭독을 놓치지 않게', async () => {
    const { wrapper } = await mounted()
    // find로 본다 — 상주 여부가 이 테스트의 대상이라 없으면 throw가 아니라 false로 드러나야 한다
    expect(wrapper.find('.sr-only[aria-live="polite"]').exists()).toBe(true)
    expect(announced(wrapper)).toBe('')
  })

  it('메시지 목록은 role="log"이고 암시 라이브는 꺼 둔다 — prepend 낭독 방지', async () => {
    const { wrapper } = await mounted()
    const list = wrapper.get('[role="log"]')
    expect(list.attributes('aria-live')).toBe('off')
  })

  // 같은 문구 유실 (S15P11A704-516). 447에서 **리더 없이 확정된** 결함이다 —
  // 값이 같으면 Vue가 DOM을 갱신조차 하지 않으므로 리더에게 알릴 변경이 없다.
  it('같은 문구가 연속으로 와도 리전 텍스트가 바뀐다 — 안 바뀌면 두 번째가 유실된다', async () => {
    const { live, wrapper } = await mounted()

    live.chat = [msg('a'), from('준호', 'ㅋㅋㅋ', 'b')]
    await flushPromises()
    const first = region(wrapper).text() // 마커까지 포함한 원문을 본다
    expect(announced(wrapper)).toBe('준호: ㅋㅋㅋ')

    // 같은 사람이 같은 말을 다시 — 문구는 같지만 DOM은 달라야 한다
    live.chat = [...live.chat, from('준호', 'ㅋㅋㅋ', 'c')]
    await flushPromises()

    expect(announced(wrapper)).toBe('준호: ㅋㅋㅋ') // 사용자에게 읽히는 문구는 그대로
    expect(region(wrapper).text()).not.toBe(first) // 그런데 텍스트는 바뀌었다
  })

  it('토글 문자는 문구를 오염시키지 않는다 — 화면·낭독에 나타나지 않는 문자여야 한다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [msg('a'), from('준호', '안녕', 'b')]
    await flushPromises()

    // 마커가 붙은 쪽이든 아닌 쪽이든, 벗기면 문구가 온전해야 한다
    expect(
      region(wrapper)
        .text()
        .replace(/\u200B/g, ''),
    ).toBe('준호: 안녕')
    // 공백을 쓰면 정규화로 접혀 「바뀌지 않았다」로 판정될 여지가 남는다 — 공백이 아닌 문자여야 한다
    expect(region(wrapper).text()).not.toMatch(/ $/)
  })

  it('꼬리에 붙은 새 메시지를 발신자와 함께 통지한다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [msg('a'), from('준호', '안녕하세요', 'b')]
    await flushPromises()

    expect(announced(wrapper)).toBe('준호: 안녕하세요')
  })

  it('한 프레임에 여러 건이 들어오면 건수와 마지막만 통지한다 — 전부 읽으면 다음 묶음이 온다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [msg('a'), from('준호', '먼저', 'b'), from('서연', '마지막', 'c')]
    await flushPromises()

    expect(announced(wrapper)).toBe('새 메시지 2개. 마지막 서연: 마지막')
  })

  it('내가 보낸 메시지는 통지하지 않는다 — 직접 친 문장을 되읽는 소비다', async () => {
    const { live, wrapper } = await mounted()
    // sendChat이 올리는 낙관 행과 같은 모양(opt- id)
    live.chat = [msg('a'), msg('mine')]
    await flushPromises()

    expect(announced(wrapper)).toBe('')
  })

  it('실제 전송 경로(입력 → 전송)로도 통지하지 않는다 — 스토어가 올린 낙관 행 기준', async () => {
    const { wrapper } = await mounted()

    // 목이 아닌 실제 sendChat을 태운다 — 낙관 행 id가 opt-로 발급되는 경로를 그대로 지난다
    await wrapper.get('textarea').setValue('안녕하세요')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '전송')!
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('안녕하세요') // 목록엔 들어갔다
    expect(announced(wrapper)).toBe('') // 통지는 안 한다
  })

  it('내 전송과 남의 메시지가 같은 프레임이면 남의 것만 통지한다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [msg('a'), msg('mine'), from('서연', '안녕', 'other')]
    await flushPromises()

    // 내 행을 빼고 한 건이라 요약 없이 그대로 읽는다 — pinned 불리언으로 걸렀다면 둘 다 조용해진다
    expect(announced(wrapper)).toBe('서연: 안녕')
  })

  it('과거 조회 prepend는 새 메시지로 통지하지 않는다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [{ ...msg('old1'), older: true }, { ...msg('old2'), older: true }, msg('a')]
    await flushPromises()

    expect(announced(wrapper)).not.toContain('새 메시지')
  })

  it('사용자가 스크롤해 부른 과거 조회는 시작과 끝을 알린다 — 꼬리 통지 대상이 아니어서 침묵하지 않게', async () => {
    const { live, wrapper } = await mounted()
    vi.spyOn(live, 'loadOlderChat').mockResolvedValue()
    await wrapper.get('.overflow-y-auto').trigger('scroll') // 상단(scrollTop 0) → 이 로드는 사용자가 부른 것

    live.chatLoadingOlder = true
    await flushPromises()
    expect(announced(wrapper)).toBe('이전 메시지 불러오는 중')

    live.chatHasMore = false
    live.chatLoadingOlder = false
    await flushPromises()
    expect(announced(wrapper)).toBe('이전 메시지를 모두 불러왔어요')
  })

  // 방 입장 시드(679)는 사용자가 요청한 적 없는 로드다 — 낭독하면 입장 안내를 밀어낸다(422의 취지).
  it('스크롤 없이 도는 시드 로드는 통지하지 않는다', async () => {
    const { live, wrapper } = await mounted()

    live.chatLoadingOlder = true
    await flushPromises()
    live.chatLoadingOlder = false
    await flushPromises()

    expect(announced(wrapper)).toBe('')
  })

  // 4xx 차단(679)은 「모두 불러왔어요」가 아니다 — 못 읽은 것이지 처음에 닿은 게 아니다.
  it('4xx로 막혀 끊긴 조회는 「모두 불러왔어요」 대신 실패를 알린다', async () => {
    const { live, wrapper } = await mounted()
    vi.spyOn(live, 'loadOlderChat').mockResolvedValue()
    await wrapper.get('.overflow-y-auto').trigger('scroll')

    live.chatLoadingOlder = true
    await flushPromises()
    live.chatHasMore = false
    live.chatOlderBlocked = true
    live.chatLoadingOlder = false
    await flushPromises()

    expect(announced(wrapper)).toBe('이전 메시지를 불러오지 못했어요')
  })

  it('연결 상태 변화를 통지한다 — 시각 배너는 v-if라 통지를 못 맡는다', async () => {
    const { live, wrapper } = await mounted()
    live.connectionStatus = 'reconnecting'
    await flushPromises()

    expect(announced(wrapper)).toContain('연결이 끊겼어요')
  })

  it('전송 실패가 새로 생기면 조치 가능함을 함께 통지한다', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [{ ...msg('a'), failed: true }]
    await flushPromises()

    expect(announced(wrapper)).toContain('전송되지 않았어요')
    expect(announced(wrapper)).toContain('다시 시도')
  })

  it('실패 사유가 있어도 실패 사실을 먼저 알리고 사유를 덧붙인다 (S15P11A704-558)', async () => {
    const { live, wrapper } = await mounted()
    live.chat = [{ ...msg('a'), failed: true, failReason: '채팅 내용을 확인해 주세요.' }]
    await flushPromises()

    // BE publicMessage는 실패 사실을 말하지 않는 것이 있다(ChatSendErrorCode) — 사유로 대체하면 안 된다.
    expect(announced(wrapper)).toContain('전송되지 않았어요')
    expect(announced(wrapper)).toContain('채팅 내용을 확인해 주세요.')
    expect(announced(wrapper)).toContain('다시 시도')
  })
})

// 목 모드 시연·검증 트리거 (S15P11A704-453).
// 목 드라이버가 검열 경고·전송 실패·재연결을 쏘지 않아 447(스크린리더 실측)의 셋을 띄울 방법이 없었다.
// 실 모드에 새면 안 되는 코드라 게이트가 이 describe의 핵심이다.
describe('ChatPanel — 목 시연 트리거 (S15P11A704-453)', () => {
  const btn = (wrapper: VueWrapper, label: string) =>
    wrapper.findAll('button').find((b) => b.text() === label)

  const mounted = async () => {
    setAnchorSupport(false)
    currentQuery.value = { chatdemo: '1' } // 옵트인 — 없으면 스트립이 안 뜬다
    const live = useLiveStore()
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    return { live, wrapper }
  }

  it('검열 경고를 띄운다 — 428의 다중 라인·닫기도 함께 확인되는 긴 문구', async () => {
    const { wrapper } = await mounted()
    await btn(wrapper, '검열 경고')!.trigger('click')

    expect(wrapper.get('[role="alert"]').text()).toContain('외부 메신저로 거래를 유도하는')
    expect(wrapper.find('[aria-label="검열 경고 닫기"]').exists()).toBe(true)
  })

  it('전송 실패 행을 만든다 — 표식과 재시도·닫기 경로가 함께 뜬다', async () => {
    const { live, wrapper } = await mounted()
    await btn(wrapper, '전송 실패')!.trigger('click')
    await flushPromises()

    expect(live.chat.some((m) => m.failed === true)).toBe(true)
    // 첫 클릭은 사유 있는 서버 거절 케이스다(558) — 목 모드로 사유 표시까지 브라우저에서 확인할 수 있어야 한다.
    expect(live.chat.some((m) => m.failReason !== undefined)).toBe(true)
    expect(btn(wrapper, '다시 시도')).toBeTruthy()
  })

  it('재연결 상태를 토글한다 — 끊김 안내와 복구를 모두 들어봐야 순서를 본다', async () => {
    const { live, wrapper } = await mounted()

    await btn(wrapper, '재연결')!.trigger('click')
    expect(live.connectionStatus).toBe('reconnecting')
    expect(wrapper.text()).toContain('연결이 끊겼어요')

    await btn(wrapper, '재연결')!.trigger('click')
    expect(live.connectionStatus).toBe('connected')
  })

  it('같은 메시지를 연속 두 건 넣는다 — 통지 리전 텍스트가 동일해지는 경우', async () => {
    const { live, wrapper } = await mounted()
    await btn(wrapper, '같은 메시지 2연속')!.trigger('click')
    await flushPromises()

    const same = live.chat.filter((m) => m.text === '이거 실물 상태 어때요?')
    expect(same).toHaveLength(2)
    // messageId가 달라야 handleChatMessage의 중복 제거에 걸리지 않는다
    expect(same[0].id).not.toBe(same[1].id)
  })

  it('실 모드에선 트리거가 아예 없다 — 시연 코드가 새면 안 된다', async () => {
    setAnchorSupport(false)
    currentQuery.value = { chatdemo: '1' } // 쿼리를 켜도 실 모드면 안 나와야 한다
    mockApiFlag.value = false
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('시연')
    for (const label of ['검열 경고', '전송 실패', '재연결', '같은 메시지 2연속']) {
      expect(btn(wrapper, label)).toBeUndefined()
    }
  })
})

// 시연 스트립 옵트인 게이트 (S15P11A704-453, !161 리뷰).
// 목 모드만으로 켜지면 시연 중심 화면에 버튼이 상시 노출된다 — 쿼리를 붙일 때만 뜬다.
describe('ChatPanel — 시연 스트립 옵트인 게이트 (S15P11A704-453)', () => {
  const hasStrip = (wrapper: VueWrapper) => wrapper.text().includes('시연')

  it('목 모드라도 쿼리가 없으면 안 뜬다 — 시연 화면에 새지 않게', async () => {
    setAnchorSupport(false)
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    expect(hasStrip(wrapper)).toBe(false)
  })

  it('?chatdemo=1이면 뜬다', async () => {
    setAnchorSupport(false)
    currentQuery.value = { chatdemo: '1' }
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    expect(hasStrip(wrapper)).toBe(true)
  })

  it('?chatdemo=0은 끈 것으로 본다', async () => {
    setAnchorSupport(false)
    currentQuery.value = { chatdemo: '0' }
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    expect(hasStrip(wrapper)).toBe(false)
  })
})

// 안내와 입력창의 접근성 연결 (S15P11A704-461, !174 리뷰 1).
// readonly는 "읽기 전용"까지만 알려 주고 이유는 안내에 있다. 422의 상주 리전은 같은 문구가 연속으로
// 들어가면 재낭독이 생략돼 두 번째 포커스부터 침묵하므로, describedby로 잇는다.
describe('ChatPanel — 로그인 안내와 입력창 연결 (S15P11A704-461)', () => {
  const anonymous = async () => {
    setAnchorSupport(false)
    useAuthStore().user = null
    const wrapper = mount(ChatPanel, {
      props: { roomId: 1 },
      global: { stubs: { RouterLink: RouterLinkStub } },
    })
    await flushPromises()
    return wrapper
  }

  it('안내가 뜨면 입력창이 그것을 aria-describedby로 가리킨다', async () => {
    const wrapper = await anonymous()
    await wrapper.get('textarea').trigger('focus')

    const id = wrapper.get('textarea').attributes('aria-describedby')
    expect(id).toBeTruthy()
    expect(wrapper.get(`#${id}`).text()).toContain('로그인이 필요해요')
  })

  it('안내가 없으면 가리키지 않는다 — 없는 id를 참조하면 리더가 침묵한다', async () => {
    const wrapper = await anonymous()
    expect(wrapper.get('textarea').attributes('aria-describedby')).toBeUndefined()
  })

  it('로그인 상태에선 연결하지 않는다', async () => {
    setAnchorSupport(false)
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()
    await wrapper.get('textarea').trigger('focus')

    expect(wrapper.get('textarea').attributes('aria-describedby')).toBeUndefined()
  })
})

// CHAT-02 고정 공지 등록·해제 (S15P11A704-559).
// 판매자 전용 액션이라 노출 분기가 이 describe의 핵심이다 — 서버도 NOT_AUCTION_HOST로 막지만
// 구매자에게 버튼이 보이는 것 자체가 결함이고, 반대로 안 보이면 기능이 없는 것과 같다.
describe('ChatPanel — 고정 공지 액션 (S15P11A704-559)', () => {
  // 서버 확정 행 — id가 ULID다. msg()는 낙관 행(`opt-`)이라 고정 대상이 아니다.
  const confirmed = (key: string): ChatMessage => ({
    ...msg(key),
    id: `01J8W3P2N9KQ4RSTVWXYZ0123${key}`,
    clientMessageId: null,
  })

  const pinButtons = (wrapper: VueWrapper) =>
    wrapper.findAll('button').filter((b) => b.text() === '고정')

  const mounted = async (
    props: Record<string, unknown>,
    chat: ChatMessage[] = [confirmed('T')],
  ) => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = chat
    const wrapper = mount(ChatPanel, { props: { roomId: 7, ...props } })
    await flushPromises()
    return { live, wrapper }
  }

  beforeEach(() => {
    pinNoticeMock.mockReset()
    pinNoticeMock.mockResolvedValue(undefined)
    unpinNoticeMock.mockReset()
    unpinNoticeMock.mockResolvedValue(undefined)
  })

  it('판매자면 확정된 메시지 행에 고정 액션이 붙는다', async () => {
    const { wrapper } = await mounted({ canPin: true })
    expect(pinButtons(wrapper)).toHaveLength(1)
  })

  // 판별 기준은 pending·failed 플래그가 아니라 임시 id(`opt-`)다 — 목 모드 낙관 행은 두 플래그가 모두
  // 없는 채로 `opt-` id만 갖고 남는다(!296 리뷰). 플래그로 가리면 그 행에 버튼이 새고 임시 id가 서버로 나간다.
  it('아직 서버 id가 없는 행은 제외한다 — 플래그가 아니라 임시 id로 가린다', async () => {
    const { wrapper } = await mounted({ canPin: true }, [
      { ...msg('p'), pending: true },
      { ...msg('f'), failed: true },
      msg('o'), // 플래그 없는 낙관 행 — 목 모드에서 실제로 이 모양으로 남는다
      confirmed('T'),
    ])
    expect(pinButtons(wrapper)).toHaveLength(1)
  })

  it('고정을 누르면 그 행의 ULID로 API를 부른다 — 화면은 소켓 에코가 갱신한다', async () => {
    const { live, wrapper } = await mounted({ canPin: true })

    await pinButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(pinNoticeMock).toHaveBeenCalledWith(7, '01J8W3P2N9KQ4RSTVWXYZ0123T')
    expect(live.pinnedNotice).toBeNull() // 응답으로 스토어를 덮지 않는다(NOTICE_PINNED가 주인)
  })

  it('어느 메시지를 고정하는지 aria-label로 알린다 — 「고정」만으로는 행을 특정할 수 없다', async () => {
    const { wrapper } = await mounted({ canPin: true })
    expect(pinButtons(wrapper)[0].attributes('aria-label')).toBe('나의 메시지를 공지로 고정: T')
  })

  it('고정 공지 배너의 해제 버튼도 판매자에게만 보이고, 누르면 해제 API를 부른다', async () => {
    const { live, wrapper } = await mounted({ canPin: true })
    live.pinnedNotice = { messageId: '01J8W3P2N9KQ4RSTVWXYZ0123T', content: '택배비 포함가입니다' }
    await flushPromises()

    await wrapper.get('[aria-label="고정 공지 해제"]').trigger('click')
    await flushPromises()

    expect(unpinNoticeMock).toHaveBeenCalledWith(7)
  })

  // canPin 미지정(구매자·대기방)에서 두 액션이 **모두** 없어야 한다는 것을 한 번에 본다 — 배너 자체는 보인다.
  it('구매자에게는 배너만 보이고 고정·해제 액션이 없다 — 대기방 등 다른 화면도 무변경이다', async () => {
    const { live, wrapper } = await mounted({})
    live.pinnedNotice = { messageId: '01J8W3P2N9KQ4RSTVWXYZ0123T', content: '택배비 포함가입니다' }
    await flushPromises()

    expect(wrapper.text()).toContain('택배비 포함가입니다')
    expect(pinButtons(wrapper)).toHaveLength(0)
    expect(wrapper.find('[aria-label="고정 공지 해제"]').exists()).toBe(false)
  })

  it('고정이 실패하면 사유를 화면에 남긴다 — 조용한 무시는 「눌렀는데 안 바뀐다」가 된다', async () => {
    pinNoticeMock.mockRejectedValue(new Error('호스트가 아닙니다'))
    const { wrapper } = await mounted({ canPin: true })

    await pinButtons(wrapper)[0].trigger('click')
    await flushPromises()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('공지로 고정하지 못했어요')
    expect(alert.text()).toContain('호스트가 아닙니다')
  })

  it('해제가 실패해도 마찬가지다', async () => {
    unpinNoticeMock.mockRejectedValue(new Error('호스트가 아닙니다'))
    const { live, wrapper } = await mounted({ canPin: true })
    live.pinnedNotice = { messageId: '01J8W3P2N9KQ4RSTVWXYZ0123T', content: '공지' }
    await flushPromises()

    await wrapper.get('[aria-label="고정 공지 해제"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('공지를 해제하지 못했어요')
  })
})

// CHAT-01 메시지 삭제 (S15P11A704-681).
// 노출 분기가 고정(canPin)과 **다른 값**이라는 것이 이 describe의 핵심이다 — 삭제는 운영자도 되는데
// 한 값으로 합치면 운영자에게 고정 버튼까지 보이고, 그건 서버가 NOT_AUCTION_HOST로 되돌린다.
describe('ChatPanel — 메시지 삭제 액션 (S15P11A704-681)', () => {
  // id는 **26자 정규 ULID**여야 한다 — 서버 ChatMessageId가 길이를 검증해 짧으면 400 INVALID_MESSAGE_ID다
  // (S15P11A704-613). 픽스처가 14자면 실제로는 못 나갈 값으로 테스트가 통과한다.
  const confirmed = (key: string): ChatMessage => ({
    ...msg(key),
    id: `01J8W3P2N9KQ4RSTVWXYZ0123${key}`,
    clientMessageId: null,
  })

  const deleteButtons = (wrapper: VueWrapper) =>
    wrapper.findAll('button').filter((b) => b.text() === '삭제')

  const mounted = async (
    props: Record<string, unknown>,
    chat: ChatMessage[] = [confirmed('T')],
  ) => {
    setAnchorSupport(false)
    const live = useLiveStore()
    live.chat = chat
    const wrapper = mount(ChatPanel, { props: { roomId: 7, ...props } })
    await flushPromises()
    return { live, wrapper }
  }

  beforeEach(() => {
    deleteChatMessageMock.mockReset()
    deleteChatMessageMock.mockResolvedValue(undefined)
  })

  it('권한이 있으면 확정된 메시지 행에 삭제 액션이 붙는다', async () => {
    const { wrapper } = await mounted({ canDelete: true })
    expect(deleteButtons(wrapper)).toHaveLength(1)
  })

  // 고정과 같은 이유다(!296) — 임시 id(`opt-`)를 서버로 보내면 400 INVALID_MESSAGE_ID다.
  it('아직 서버 id가 없는 행은 제외한다', async () => {
    const { wrapper } = await mounted({ canDelete: true }, [msg('o'), confirmed('T')])
    expect(deleteButtons(wrapper)).toHaveLength(1)
  })

  it('삭제를 누르면 그 행의 ULID로 API를 부른다 — 확인 단계 없이 즉시, 화면은 CHAT_DELETED 에코가 지운다', async () => {
    const { live, wrapper } = await mounted({ canDelete: true })

    await deleteButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(deleteChatMessageMock).toHaveBeenCalledWith(7, '01J8W3P2N9KQ4RSTVWXYZ0123T')
    expect(live.chat).toHaveLength(1) // 낙관적으로 지우지 않는다 — 403·404면 되살릴 방법이 없다
  })

  it('어느 메시지를 지우는지 aria-label로 알린다', async () => {
    const { wrapper } = await mounted({ canDelete: true })
    expect(deleteButtons(wrapper)[0].attributes('aria-label')).toBe('나의 메시지를 삭제: T')
  })

  it('권한이 없으면 삭제 액션이 없다 — 대기방 등 기본값(false) 화면도 무변경이다', async () => {
    const { wrapper } = await mounted({})
    expect(deleteButtons(wrapper)).toHaveLength(0)
  })

  // canPin·canDelete가 한 값으로 합쳐지면 조용히 깨지는 자리 — 운영자(삭제만)를 그대로 재현한다.
  it('삭제 권한만 있으면 고정 버튼은 안 보인다 — 고정은 판매자 전용이다', async () => {
    const { wrapper } = await mounted({ canDelete: true })
    expect(wrapper.findAll('button').filter((b) => b.text() === '고정')).toHaveLength(0)
  })

  it('삭제가 실패하면 사유를 화면에 남긴다 — 고정 실패와 같은 스트립을 쓴다', async () => {
    deleteChatMessageMock.mockRejectedValue(new Error('권한이 없습니다'))
    const { wrapper } = await mounted({ canDelete: true })

    await deleteButtons(wrapper)[0].trigger('click')
    await flushPromises()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('메시지를 삭제하지 못했어요')
    expect(alert.text()).toContain('권한이 없습니다')
  })

  // 지운 메시지가 공지였다면 서버는 CHAT_DELETED와 함께 NOTICE_UNPINNED도 보낸다 — 스트립이 고정·삭제
  // 공용이라, 출처를 안 가리면 그 에코가 방금 띄운 삭제 실패 사유를 읽기도 전에 지운다.
  it('삭제 실패 문구는 공지 배너가 바뀌어도 남는다 — 관계없는 사건이다', async () => {
    deleteChatMessageMock.mockRejectedValue(new Error('권한이 없습니다'))
    const { live, wrapper } = await mounted({ canDelete: true })

    await deleteButtons(wrapper)[0].trigger('click')
    await flushPromises()

    live.pinnedNotice = { messageId: '01J8W3P2N9KQ4RSTVWXYZ0123T', content: '택배비 포함가입니다' }
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('메시지를 삭제하지 못했어요')
  })

  // 진행 문구의 출처를 안 가리면 삭제 한 번에 옆의 고정 버튼까지 「고정 중…」이 된다 — 누른 적 없는 요청이
  // 나간 것처럼 보인다(!319 리뷰). 잠금은 반대로 전부 걸어야 한다(두 요청이 겹치면 안 된다).
  it('삭제 중에는 삭제 버튼만 「삭제 중…」이고, 고정 버튼은 문구를 유지한 채 잠기기만 한다', async () => {
    let release = (): void => {}
    deleteChatMessageMock.mockReturnValue(
      new Promise<void>((resolve) => {
        release = resolve
      }),
    )
    const { wrapper } = await mounted({ canDelete: true, canPin: true })

    await deleteButtons(wrapper)[0].trigger('click')
    await flushPromises()

    const labels = wrapper.findAll('button').map((b) => b.text())
    expect(labels).toContain('삭제 중…')
    expect(labels).toContain('고정') // 「고정 중…」이 아니다
    expect(
      wrapper
        .findAll('button')
        .filter((b) => b.text() === '고정')
        .every((b) => (b.element as HTMLButtonElement).disabled),
    ).toBe(true)

    release()
    await flushPromises()
    expect(wrapper.findAll('button').map((b) => b.text())).toContain('삭제')
  })
})

describe('ChatPanel — 입력 길이 상한 (S15P11A704-556)', () => {
  it('입력창에 CHAT-01 확정 상한 250자가 걸려 있다 — 없으면 서버가 조용히 거절한다', async () => {
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    // 리터럴 '250'은 명세(CHAT-01) 값을 못 박은 것이다 — 상수와의 일치는 live.spec.ts가 확인한다.
    expect(wrapper.get('textarea').attributes('maxlength')).toBe('250')
  })

  it('상한이 코앞이면 잔여 글자 카운터가 나온다', async () => {
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    await wrapper.get('textarea').setValue('a'.repeat(249))

    expect(wrapper.text()).toContain('249/250')
  })

  it('여유가 있으면 카운터를 띄우지 않는다 — 상시 표시는 채팅에 소음이다', async () => {
    const wrapper = mount(ChatPanel, { props: { roomId: 1 } })
    await flushPromises()

    await wrapper.get('textarea').setValue('a'.repeat(200))

    expect(wrapper.text()).not.toContain('/250')
  })
})
