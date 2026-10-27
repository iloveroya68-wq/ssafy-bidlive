import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'

// 운영자 콘솔 화면 (S15P11A704-525).
//
// 렌더 스냅샷을 늘리지 않는다 — 여기서 잡는 건 **깨져도 조용한 것**뿐이다:
// 행이 사라진 뒤의 종료 오류, 맥락 전환 시 무장·오류 해제, 403의 정상 안내,
// 이어받기 실패가 목록을 지우지 않는 것, 응답 역전, 비행 중 다른 행 잠금.

vi.mock('@/shared/api/admin', () => ({
  getAdminReports: vi.fn(),
  forceEndAuction: vi.fn(),
}))
vi.mock('@/shared/api/rooms', () => ({ getAuctionRooms: vi.fn() }))

/** 라우터 흉내 — 쿼리가 정본이고 화면은 그 사본이라는 규약을 그대로 재현한다 */
const currentQuery = ref<Record<string, string>>({})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get query() {
        return currentQuery.value
      },
      fullPath: '/admin',
    }),
    useRouter: () => ({
      replace: (to: { query: Record<string, string> }) => {
        currentQuery.value = to.query
        return Promise.resolve()
      },
      push: () => Promise.resolve(),
    }),
  }
})

import { forceEndAuction, getAdminReports } from '@/shared/api/admin'
import { getAuctionRooms } from '@/shared/api/rooms'
import { useAuthStore } from '@/stores/auth'
import AdminView from './AdminView.vue'

const reportsMock = vi.mocked(getAdminReports)
const roomsMock = vi.mocked(getAuctionRooms)
const forceEndMock = vi.mocked(forceEndAuction)

function reported(auctionId: number, title: string) {
  return {
    auctionId,
    title,
    thumbnailUrl: null,
    sellerNickname: '서연***',
    status: 'LIVE',
    reportCount: 7,
    reasonCounts: [{ reasonCode: 'PROHIBITED_ITEM', count: 5 }],
    latestReportedAt: '2026-07-31T10:31:00+09:00',
  }
}

/** 「전체 방」 응답 1건. 화면이 읽는 필드만 채운다 */
function roomPage(items: { auctionId: number; title: string }[]) {
  return {
    items: items.map((item) => ({
      ...item,
      thumbnailUrl: null,
      sellerNickname: '서연***',
      status: 'LIVE',
    })),
    nextCursor: null,
    hasNext: false,
  } as unknown as Awaited<ReturnType<typeof getAuctionRooms>>
}

/** 인터셉터를 거치지 않은 raw axios 모양 — `extractApiErrorStatus`가 이것도 읽는다 */
function apiError(status: number) {
  return { response: { status } }
}

/** 응답을 붙잡아 둔다. 응답 역전·비행 중 상태를 검사하려면 필요하다 */
function defer<T>(mock: { mockReturnValue: (value: Promise<T>) => unknown }) {
  let settle!: (value: T) => void
  mock.mockReturnValue(
    new Promise<T>((resolve) => {
      settle = resolve
    }),
  )
  return (value: T) => settle(value)
}

/** 거부를 붙잡아 둔다 — 늦게 온 옛 **실패**가 새 목록을 건드리는지 보려면 필요하다 */
function deferReject(mock: { mockReturnValue: (value: Promise<never>) => unknown }) {
  let fail!: (reason: unknown) => void
  mock.mockReturnValue(
    new Promise<never>((_resolve, reject) => {
      fail = reject
    }),
  )
  return fail
}

/** 로그인한 회원을 세운다 — 화면이 `role`로 노출을 가른다 (S15P11A704-714) */
function signIn(role: 'USER' | 'ADMIN') {
  useAuthStore().user = {
    id: 1,
    nickname: '운영자',
    role,
    status: 'ACTIVE',
    withdrawnAt: null,
  }
}

async function mountAdmin() {
  const wrapper = mount(AdminView)
  await flushPromises()
  return wrapper
}

/** 강제 종료 버튼(1단계·2단계 겸용). 사유 안내문에는 같은 문구가 없어 버튼만 걸린다 */
function forceEndButtons(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
  return wrapper.findAll('button').filter((button) => button.text().includes('종료'))
}

describe('AdminView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    signIn('ADMIN')
    currentQuery.value = {}
    reportsMock.mockResolvedValue({
      items: [reported(1, '빈티지 카메라'), reported(2, '기계식 키보드')],
      nextCursor: null,
      hasNext: false,
    })
    roomsMock.mockResolvedValue(roomPage([]))
  })

  // 「전체 방」 탭이 부르는 방 목록은 운영자 API가 아니라 **공개 API**라 비운영자에게도 200이 온다
  // (S15P11A704-714). 403에만 기대면 이 탭은 안 막히고, 진행 중인 방 전체가 강제 종료 버튼과
  // 함께 그려진다. `?tab=rooms`로 바로 들어오는 경로가 실제 사고 경로였다.
  it('운영자가 아니면 「전체 방」 탭으로 바로 들어와도 목록을 부르지 않는다', async () => {
    signIn('USER')
    currentQuery.value = { tab: 'rooms' }
    const wrapper = await mountAdmin()

    expect(roomsMock).not.toHaveBeenCalled()
    expect(reportsMock).not.toHaveBeenCalled()
    expect(forceEndButtons(wrapper)).toHaveLength(0)
    expect(wrapper.text()).toContain('권한이 없어요')
  })

  it('비로그인도 같다 — 탭 자체가 없어야 눌러서 목록을 띄우지 못한다', async () => {
    useAuthStore().user = null
    const wrapper = await mountAdmin()

    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
    expect(roomsMock).not.toHaveBeenCalled()
  })

  it('신고 사유가 보이는 행에서도 서버로는 고정값만 나간다', async () => {
    // 행은 `reasonCounts`로 「사유: 금지 품목」을 그리지만 그건 운영자 화면 안에서 끝난다.
    // 요청의 `reason`은 `AUCTION_CANCELED`로 그 방 시청자 전원에게 브로드캐스트되므로
    // 판매자 혐의(`PROHIBITED_ITEM`)가 실리면 안 된다.
    forceEndMock.mockResolvedValue({} as Awaited<ReturnType<typeof forceEndAuction>>)
    const wrapper = await mountAdmin()

    await forceEndButtons(wrapper)[0].trigger('click')
    expect(wrapper.text()).toContain('사유: 금지 품목')
    await forceEndButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(forceEndMock).toHaveBeenCalledWith(1, { reason: 'ADMIN_FORCE_END' })
  })

  it('행이 사라지는 409 오류를 목록 바깥에 그린다', async () => {
    // 409는 그 행을 목록에서 지우므로 `errorId`가 null이다. 행 밑에만 그리면
    // 「이미 종료됐거나 삭제된 경매예요」가 **영영 렌더되지 않는다**.
    forceEndMock.mockRejectedValue(apiError(409))
    const wrapper = await mountAdmin()

    await forceEndButtons(wrapper)[0].trigger('click')
    await forceEndButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('이미 종료됐거나 삭제된 경매예요')
    expect(wrapper.text()).not.toContain('빈티지 카메라')
  })

  it('탭을 바꾸면 2단계 무장이 풀린다', async () => {
    // 안 풀면 한 탭에서 1회 + 다른 탭에서 1회로 두 번째 클릭이 곧바로 강제 종료가 된다
    // (`useDealAction`이 S15P11A704-382에서 겪은 사고).
    roomsMock.mockResolvedValue(roomPage([{ auctionId: 1, title: '빈티지 카메라' }]))
    const wrapper = await mountAdmin()

    await forceEndButtons(wrapper)[0].trigger('click')
    expect(wrapper.text()).toContain('한 번 더 눌러 종료')

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('한 번 더 눌러 종료')
    expect(forceEndMock).not.toHaveBeenCalled()
  })

  it('403이면 「권한이 없어요」를 그린다 — 비ADMIN 진입의 정상 경로다', async () => {
    reportsMock.mockRejectedValue(apiError(403))
    const wrapper = await mountAdmin()

    expect(wrapper.text()).toContain('권한이 없어요')
    expect(wrapper.text()).toContain('운영자 계정으로 로그인해 주세요')
    // 다시 눌러도 같은 결과라 재시도 버튼을 두지 않는다
    expect(wrapper.text()).not.toContain('다시 시도')
  })

  it('「더 보기」가 실패해도 보던 목록이 남는다', async () => {
    // 전면 오류로 갈아치우면 보던 20건이 사라지고, 「다시 시도」는 커서를 버려 1페이지부터
    // 다시 받는다 — 보던 자리와 이어받은 페이지를 둘 다 잃는다.
    reportsMock.mockResolvedValueOnce({
      items: [reported(1, '빈티지 카메라')],
      nextCursor: 'c1',
      hasNext: true,
    })
    const wrapper = await mountAdmin()
    reportsMock.mockRejectedValueOnce(apiError(500))

    await wrapper
      .findAll('button')
      .filter((b) => b.text().includes('더 보기'))[0]
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('빈티지 카메라')
    expect(wrapper.text()).toContain('불러오지 못했어요')
    // 재시도 자리는 「더 보기」 그대로다 — 커서를 버리지 않는다
    expect(wrapper.text()).toContain('더 보기')
  })

  it('「더 보기」의 401에는 재시도 안내를 붙이지 않는다', async () => {
    // 401은 인터셉터가 refresh까지 실패한 **죽은 세션**이라 몇 번을 눌러도 401이다.
    // 전면 오류는 같은 상황에서 재시도 버튼을 감추는데 이 배너만 반대 말을 하면 안 된다.
    reportsMock.mockResolvedValueOnce({
      items: [reported(1, '빈티지 카메라')],
      nextCursor: 'c1',
      hasNext: true,
    })
    const wrapper = await mountAdmin()
    reportsMock.mockRejectedValueOnce(apiError(401))

    await wrapper
      .findAll('button')
      .filter((b) => b.text().includes('더 보기'))[0]
      .trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('로그인이 필요해요')
    expect(wrapper.text()).not.toContain('다시 시도할 수 있어요')
  })

  it('`hasNext`가 true여도 커서가 null이면 「더 보기」가 안 나간다', async () => {
    // 커서 없이 나가면 1페이지를 다시 받아 뒤에 붙인다 — 같은 id가 중복 `:key`로 들어가고
    // `hasNext`가 계속 true라 무한 반복이다.
    reportsMock.mockResolvedValue({
      items: [reported(1, '빈티지 카메라')],
      nextCursor: null,
      hasNext: true,
    })
    const wrapper = await mountAdmin()
    expect(reportsMock).toHaveBeenCalledTimes(1)

    await wrapper
      .findAll('button')
      .filter((b) => b.text().includes('더 보기'))[0]
      .trigger('click')
    await flushPromises()

    expect(reportsMock).toHaveBeenCalledTimes(1)
    expect(forceEndButtons(wrapper)).toHaveLength(1)
  })

  it('늦게 온 옛 탭 응답이 새 탭 목록을 덮지 않는다', async () => {
    // 덮으면 탭은 「전체 방」인데 목록엔 신고 방이 그려진다. 되돌릴 수 없는 동작 화면에서
    // 목록 라벨이 거짓말하면 운영자는 다른 방을 끝낸다고 믿으며 이 방을 끝낸다.
    const releaseReports = defer<Awaited<ReturnType<typeof getAdminReports>>>(reportsMock)
    roomsMock.mockResolvedValue(roomPage([{ auctionId: 9, title: '전체 방 항목' }]))

    const wrapper = mount(AdminView)
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('전체 방 항목')

    releaseReports({ items: [reported(1, '신고 방 항목')], nextCursor: null, hasNext: false })
    await flushPromises()

    expect(wrapper.text()).toContain('전체 방 항목')
    expect(wrapper.text()).not.toContain('신고 방 항목')
  })

  it('탭을 바꾸는 즉시 옛 목록이 사라진다', async () => {
    // 안 지우면 탭 라벨·`aria-selected`만 먼저 바뀌고 응답이 올 때까지(axios timeout 15초까지)
    // 옛 목록이 새 라벨 밑에 남는다. `hasNext`가 false면 「더 보기」조차 없어 화면 어디에도
    // 「불러오는 중」이 없고, 그 창에 행을 두 번 누르면 **다른 탭 소속 방이 강제 종료된다.**
    defer<Awaited<ReturnType<typeof getAuctionRooms>>>(roomsMock)
    const wrapper = await mountAdmin()
    expect(wrapper.text()).toContain('빈티지 카메라')

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('빈티지 카메라')
    expect(wrapper.text()).toContain('불러오는 중이에요')
  })

  it('늦게 온 옛 탭 실패가 새 탭 목록을 건드리지 않는다', async () => {
    // `catch`의 세대 가드. 없으면 옛 요청의 500이 새 탭의 목록을 지우고 오류를 씌운다 —
    // 운영자가 보는 목록과 라벨이 또 어긋난다.
    const failReports = deferReject(reportsMock)
    roomsMock.mockResolvedValue(roomPage([{ auctionId: 9, title: '전체 방 항목' }]))

    const wrapper = mount(AdminView)
    await flushPromises()

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('전체 방 항목')

    failReports(apiError(500))
    await flushPromises()

    expect(wrapper.text()).toContain('전체 방 항목')
    expect(wrapper.text()).not.toContain('불러오지 못했어요')
  })

  it('늦게 온 옛 응답이 비행 중인 조회의 잠금을 풀지 않는다', async () => {
    // `finally`의 세대 가드. 없으면 옛 응답이 새 조회의 `pending`을 풀어 **「더 보기」가 이중 발사**되고,
    // 아직 아무것도 안 받았는데 「해당 상태의 방이 없어요」라는 거짓 빈 상태가 뜬다.
    const wrapper = await mountAdmin()

    const releaseLive = defer<Awaited<ReturnType<typeof getAuctionRooms>>>(roomsMock)
    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()

    // 「대기」 필터로 갈아타 더 새로운 조회를 띄운다 (이쪽은 응답이 안 온다)
    defer<Awaited<ReturnType<typeof getAuctionRooms>>>(roomsMock)
    await wrapper
      .findAll('button')
      .filter((button) => button.text() === '대기')[0]
      .trigger('click')
    await flushPromises()

    releaseLive(roomPage([]))
    await flushPromises()

    expect(wrapper.text()).toContain('불러오는 중이에요')
    expect(wrapper.text()).not.toContain('해당 상태의 방이 없어요')
  })

  it('탭을 바꾸면 종료 오류도 지운다', async () => {
    // `errorId`는 `auctionId`일 뿐이라, 남겨 두면 새 탭의 같은 id 행에
    // 아무 조작도 안 했는데 「종료하지 못했어요」가 붙는다.
    forceEndMock.mockRejectedValue(apiError(500))
    roomsMock.mockResolvedValue(roomPage([{ auctionId: 1, title: '빈티지 카메라' }]))
    const wrapper = await mountAdmin()

    await forceEndButtons(wrapper)[0].trigger('click')
    await forceEndButtons(wrapper)[0].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('종료하지 못했어요')

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('종료하지 못했어요')
  })

  it('종료 요청이 나가 있으면 다른 행 버튼도 비활성이다', async () => {
    // 안전은 composable이 지키지만 피드백이 없다 — 눌러도 아무 일이 없는 버튼은 고장처럼 보인다
    const releaseForceEnd = defer<Awaited<ReturnType<typeof forceEndAuction>>>(forceEndMock)
    const wrapper = await mountAdmin()

    await forceEndButtons(wrapper)[0].trigger('click')
    await forceEndButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(forceEndButtons(wrapper)[1].attributes('disabled')).toBeDefined()

    releaseForceEnd({
      auctionId: 1,
      status: 'ENDED',
      resultType: 'FORCED',
      endedAt: '2026-07-31T10:40:00Z',
    })
    await flushPromises()

    expect(forceEndButtons(wrapper)[0].attributes('disabled')).toBeUndefined()
  })
})
