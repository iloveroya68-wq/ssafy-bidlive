import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import RoomListView from './RoomListView.vue'
import { useRoomStore } from '@/stores/room'

// ROOM-02 방 목록 검색 (S15P11A704-370).
//
// 목록은 **서버가 거른다**(396에서 서버 필터 + 무한 스크롤로 갔다). 그래서 검색도 서버 몫이고,
// 화면이 `roomStore.rooms`를 다시 거르면 **지금 받은 한 페이지 안에서만** 맞는 결과가 된다.
// 여기서 잡는 건 그 계약과, 디바운스·뒤로가기처럼 깨져도 조용한 것들이다.

const DEBOUNCE_MS = 300

/** 라우터 흉내 — 쿼리가 정본이고 화면은 그 사본이라는 규약을 그대로 재현한다 */
const currentQuery = ref<Record<string, string>>({})
const replaced: Record<string, string>[] = []

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get query() {
        return currentQuery.value
      },
      fullPath: '/rooms',
    }),
    useRouter: () => ({
      replace: (to: { query: Record<string, string> }) => {
        replaced.push(to.query)
        currentQuery.value = to.query
        return Promise.resolve()
      },
      push: () => Promise.resolve(),
    }),
  }
})

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }

function mountList() {
  return mount(RoomListView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

/**
 * **테스트마다 화면을 내린다.** 안 내리면 앞 테스트의 화면이 계속 살아서 `currentQuery`를 감시한다 —
 * 다음 테스트의 `beforeEach`가 쿼리를 되돌리는 순간 그 화면들이 일제히 깨어나고,
 * 같은 `beforeEach`의 `vi.restoreAllMocks()`가 목을 이미 걷어낸 뒤라 **실제 `loadUpcoming`이 나간다.**
 *
 * 그게 두 가지를 망가뜨린다.
 * 1. 피니아 액션은 호출될 때마다 `setActivePinia`를 자기 피니아로 돌려놓는다 —
 *    그래서 뒤 테스트의 `useRoomStore()`가 **앞 테스트의 스토어**를 집어 온다.
 * 2. 그 실요청은 서버가 없어 거절되고, 거절이 뒤 테스트의 `flushPromises()` 안에서 처리되면서
 *    `catch`가 `upcoming`·`upcomingHidden`을 0으로 비운다 — 방금 세팅한 값이 사라진다.
 */
enableAutoUnmount(afterEach)

/** 마지막으로 서버에 나간 목록 조건 */
function lastQuery(spy: ReturnType<typeof vi.spyOn>) {
  const calls = spy.mock.calls
  return calls.length === 0 ? undefined : calls[calls.length - 1][0]
}

describe('RoomListView 검색', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    vi.useFakeTimers()
    currentQuery.value = {}
    replaced.length = 0
  })

  afterEach(() => vi.useRealTimers())

  it('타이핑 즉시 조회하지 않는다 — 한 글자마다 요청·이력이 쌓인다', async () => {
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()
    spy.mockClear()

    await wrapper.get('input[type="search"]').setValue('카메라')

    expect(spy).not.toHaveBeenCalled()
  })

  it('입력이 멎으면 keyword를 서버로 보낸다 — 화면에서 거르지 않는다', async () => {
    // 서버가 한 페이지만 준다. 화면에서 거르면 "3페이지에 있는 방"이 영영 안 보인다.
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()
    spy.mockClear()

    await wrapper.get('input[type="search"]').setValue('카메라')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(lastQuery(spy)).toMatchObject({ keyword: '카메라' })
  })

  it('검색어를 `?q=`에 싣는다 — 필터라 replace다', async () => {
    const store = useRoomStore()
    vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('카메라')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(currentQuery.value.q).toBe('카메라')
  })

  it('비우면 쿼리에서 뺀다 — 빈 값이 주소에 남으면 공유 링크가 지저분해진다', async () => {
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('카메라')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await wrapper.get('input[type="search"]').setValue('')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(currentQuery.value.q).toBeUndefined()
    expect(lastQuery(spy)).not.toHaveProperty('keyword')
  })

  it('공백만 친 검색어는 검색하지 않는다', async () => {
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('   ')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)

    expect(currentQuery.value.q).toBeUndefined()
    expect(lastQuery(spy)).not.toHaveProperty('keyword')
  })

  it('`?q=`로 바로 들어오면 입력창과 조회에 반영된다', async () => {
    currentQuery.value = { q: '카메라' }
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()

    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('카메라')
    expect(lastQuery(spy)).toMatchObject({ keyword: '카메라' })
  })

  it('뒤로가기로 쿼리가 바뀌면 입력 중이던 디바운스를 버린다', async () => {
    // 안 버리면 방금 되돌린 주소를 옛 입력값이 다시 덮어쓴다 — 뒤로가기가 안 먹는 것처럼 보인다
    const store = useRoomStore()
    vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()

    await wrapper.get('input[type="search"]').setValue('카메라')
    // 디바운스가 아직 안 끝난 사이 뒤로가기가 들어온다
    currentQuery.value = { q: '키보드' }
    await flushPromises()
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS * 2)

    expect(currentQuery.value.q).toBe('키보드')
    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('키보드')
  })

  it('디바운스가 돌아와도 입력창의 앞뒤 공백을 지우지 않는다', async () => {
    // 우리 자신의 syncQuery()가 만든 주소 변경도 쿼리 감시자로 되돌아온다.
    // 거기서 입력창을 무조건 덮어쓰면 방금 친 공백이 사라지고 다음 글자가 앞말에 붙는다.
    const store = useRoomStore()
    vi.spyOn(store, 'loadRooms').mockResolvedValue()
    const wrapper = mountList()
    await flushPromises()
    const input = wrapper.get('input[type="search"]')

    await input.setValue('카메라 렌즈 ')
    await vi.advanceTimersByTimeAsync(DEBOUNCE_MS)
    await flushPromises()

    expect(currentQuery.value.q).toBe('카메라 렌즈')
    expect((input.element as HTMLInputElement).value).toBe('카메라 렌즈 ')
  })

  it('상태 탭·카테고리와 조합된다 — 셋이 한 조건으로 나간다', async () => {
    currentQuery.value = { state: 'ended', q: '카메라' }
    const store = useRoomStore()
    const spy = vi.spyOn(store, 'loadRooms').mockResolvedValue()
    mountList()
    await flushPromises()

    expect(lastQuery(spy)).toMatchObject({ status: 'ENDED', keyword: '카메라' })
  })
})

// ROOM-08 「곧 시작해요」 스트립 (S15P11A704-542).
//
// 스트립은 목록 위에 얹힌 보조 정보라 **안 보이는 게 기본**이고, 잘못 보이는 쪽이 잘못 숨는 쪽보다
// 나쁘다(다른 탭·다른 필터의 방이 섞여 보인다). 여기서 잡는 건 그 노출 판정뿐이다.
describe('RoomListView 곧 시작해요 스트립', () => {
  const SOON = [
    {
      auctionId: 1,
      productTitle: '키크론 Q1 Pro',
      imageUrl: null,
      scheduledAt: '2026-08-01T21:30:00+09:00',
      waitingCount: 18,
      status: 'WAITING' as const,
    },
    {
      auctionId: 2,
      productTitle: '캐논 카메라 렌즈',
      imageUrl: null,
      scheduledAt: '2026-08-01T21:40:00+09:00',
      waitingCount: 4,
      status: 'WAITING' as const,
    },
  ]

  /** 스트립이 그린 카드들 (마지막 「더보기」 카드는 뺀다) */
  function stripOf(wrapper: ReturnType<typeof mountList>) {
    const section = wrapper.find('section[aria-labelledby="upcoming-heading"]')
    if (!section.exists()) return null
    const buttons = section.findAll('button')
    return {
      titles: buttons.filter((b) => !b.text().includes('더보기')).map((b) => b.text()),
      more: buttons.find((b) => b.text().includes('더보기')),
    }
  }

  let upcomingSpy: ReturnType<typeof vi.spyOn>

  async function mountWith(state: { upcoming?: typeof SOON; hidden?: number } = {}) {
    const store = useRoomStore()
    vi.spyOn(store, 'loadRooms').mockResolvedValue()
    // 스트립 조회는 이 화면이 건다 — 실제로 나가면 준비해 둔 항목을 덮어쓴다
    upcomingSpy = vi.spyOn(store, 'loadUpcoming').mockResolvedValue()
    store.upcoming = state.upcoming ?? SOON
    store.upcomingHidden = state.hidden ?? 3
    const wrapper = mountList()
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    currentQuery.value = {}
    replaced.length = 0
  })

  it('진행중 탭에서 서버가 준 순서 그대로 그린다', async () => {
    const strip = stripOf(await mountWith())
    expect(strip?.titles).toHaveLength(2)
    expect(strip?.titles[0]).toContain('키크론 Q1 Pro')
  })

  it('대기·종료 탭에서는 숨긴다 — 같은 방이 그리드에 이미 있다', async () => {
    currentQuery.value = { state: 'waiting' }
    expect(stripOf(await mountWith())).toBeNull()
  })

  it('곧 시작하는 방이 0건이면 섹션 제목까지 통째로 숨긴다', async () => {
    expect(stripOf(await mountWith({ upcoming: [], hidden: 0 }))).toBeNull()
  })

  it('검색어를 스트립에도 적용한다', async () => {
    currentQuery.value = { q: '렌즈' }
    const strip = stripOf(await mountWith())
    expect(strip?.titles).toHaveLength(1)
    expect(strip?.titles[0]).toContain('캐논 카메라 렌즈')
  })

  it('검색어로 전부 걸러지면 숨긴다', async () => {
    currentQuery.value = { q: '없는상품' }
    expect(stripOf(await mountWith())).toBeNull()
  })

  it('카테고리 칩이 켜져 있으면 숨긴다 — 응답에 카테고리가 없어 항목별 판정이 불가능하다', async () => {
    currentQuery.value = { cat: 'ELECTRONICS' }
    expect(stripOf(await mountWith())).toBeNull()
  })

  it('잘린 방이 없으면 더보기 카드를 띄우지 않는다 — 빈 대기 탭으로 보내게 된다', async () => {
    expect(stripOf(await mountWith({ hidden: 0 }))?.more).toBeUndefined()
  })

  it('진행중 탭에서만 조회한다 — 스트립이 없는 화면에서 요청이 나가면 안 된다', async () => {
    currentQuery.value = { state: 'ended' }
    await mountWith()
    expect(upcomingSpy).not.toHaveBeenCalled()
  })

  it('칩·검색어가 바뀌어도 다시 묻지 않는다 — ROOM-08은 조건을 받지 않는다', async () => {
    await mountWith()
    expect(upcomingSpy).toHaveBeenCalledTimes(1)

    currentQuery.value = { cat: 'ELECTRONICS', q: '렌즈' }
    await flushPromises()

    expect(upcomingSpy).toHaveBeenCalledTimes(1)
  })

  it('더보기 카드는 잘린 개수와 함께 대기 탭으로 보낸다', async () => {
    const wrapper = await mountWith({ hidden: 3 })
    const more = stripOf(wrapper)?.more
    expect(more?.text()).toContain('+3개')

    await more?.trigger('click')
    expect(currentQuery.value.state).toBe('waiting')
  })
})
