import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import WaitingRoomView from './WaitingRoomView.vue'

// 목 대기방 서버 시계 시드가 **실제로 돌아가는지** (S15P11A704-448).
//
// seedMockServerClock 자체는 mocks/waitingRoom.spec.ts가 단위로 검증한다. 하지만 그것만으로는
// 호출부가 사라져도 통과한다 — 이 티켓의 요점은 "대기방 진입 시 오프셋이 세워진다"이므로 진입 경로를 잠근다.
//
// 자식 컴포넌트는 스텁한다. 여기서 보는 것은 enter()가 남기는 스토어 상태뿐이고, 스테이지·채팅 렌더는
// 각자의 스펙이 본다.

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({ params: { id: '7' }, fullPath: '/rooms/7/waiting' }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  }
})

// `detailMock`에 반환 타입을 박아 둔다. 타입이 없으면 `mockResolvedValue`가 무엇이든 받아,
// **계약상 필수인 필드를 빠뜨린 응답**을 만들어 낼 수 있다 — 실제로 그래서 product·seller가 빠진 채
// 화면이 `detail.value?.seller.nickname`에서 터졌고, 테스트는 통과하는데 unhandled rejection만 남았다.
// (vitest가 "false positive를 만들 수 있다"고 경고하는 그 상태다.)
const { joinMock, detailMock } = vi.hoisted(() => ({
  joinMock: vi.fn(),
  detailMock: vi.fn<() => Promise<import('@/shared/api/rooms').AuctionRoomDetail>>(),
}))
vi.mock('@/shared/api/rooms', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/rooms')>('@/shared/api/rooms')
  return { ...actual, joinAuctionRoom: joinMock, getAuctionRoom: detailMock }
})

const mountView = () =>
  mount(WaitingRoomView, {
    global: {
      stubs: { WaitingStage: true, WaitingLockedDock: true, ChatPanel: true, RouterLink: true },
    },
  })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  joinMock.mockResolvedValue({
    auctionId: 7,
    roomStatus: 'WAITING',
    permissions: { chat: true, bid: false, prediction: false },
    destinations: { room: '/topic/auctions/7', chat: '/topic/auctions/7/chat' },
  })
  // ROOM-03 상세 계약을 그대로 채운다. 이 스펙이 보는 것은 serverOffset뿐이지만,
  // 빠진 필드가 있으면 화면이 렌더 중에 터져 검사하려던 것과 무관한 실패가 섞인다.
  detailMock.mockResolvedValue({
    auctionId: 7,
    title: '키보드',
    status: 'WAITING',
    reservePrice: 10_000,
    scheduledAt: null,
    durationMinutes: 15,
    viewerCount: 3,
    isSeller: false,
    notificationCount: null,
    product: { productId: 7, title: '키보드', description: '목 설명', imageUrl: '' },
    seller: { nickname: '판**', rating: 4.8 },
    canEnter: true,
  })
})
afterEach(() => vi.restoreAllMocks())

describe('WaitingRoomView 목 서버 시계 (S15P11A704-448)', () => {
  it('대기방 진입 시 serverOffset을 세운다 — 없으면 카운트다운이 계산되지 않는다', async () => {
    const live = useLiveStore()
    expect(live.serverOffset).toBeNull()

    const wrapper = mountView()
    await flushPromises()

    // 목 모드엔 TIME_SYNC가 없다. 시드가 없으면 이 값이 null로 남아 '시작까지'가 빈 값이 된다.
    expect(live.serverOffset).toBe(0)
    wrapper.unmount()
  })

  it('진입 전에는 세워지지 않는다 — 시드가 enter() 경로에 있다는 확인', () => {
    const live = useLiveStore()
    expect(live.serverOffset).toBeNull()
  })
})
