import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import { ApiError } from '@/shared/api/errors'
import { endLiveStream } from '@/features/live/media/api'
import { endAuctionAsSeller, getAuctionRoom, type AuctionRoomDetail } from '@/shared/api/rooms'
import ConsoleView from './ConsoleView.vue'

// 판매자 판정 전에 콘솔을 그리지 않는 것 (S15P11A704-714).
//
// **조용히 깨지는 쪽이다.** 판매자 계정으로 열면 상세가 곧바로 오므로 화면은 늘 멀쩡해 보이고,
// 비판매자에게만 「방송 종료」가 달린 콘솔이 한 구간 노출된다. 상세 조회가 실패하면 그 구간이
// 끝나지 않는다 — `room`이 계속 null이라 「판매자만」 판정 자체가 안 선다.
//
// 잠그는 것은 **모르는 동안 조작 버튼이 없다**는 것 하나다. 실제 종료는 서버가 403으로 막으므로
// 여기 관심사는 인가가 아니라 노출이다.

// 라우터는 **한 벌을 돌려쓴다** — 호출마다 새 `vi.fn()`을 만들면 화면이 쥔 것과 스펙이 보는 것이
// 다른 객체라, 홈으로 나갔는지 안 나갔는지를 확인할 수 없다.
const routerMock = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({ params: { id: '7' }, query: {}, fullPath: '/rooms/7/console' }),
    useRouter: () => routerMock,
  }
})

vi.mock('@/shared/api/rooms', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/rooms')>('@/shared/api/rooms')
  return { ...actual, getAuctionRoom: vi.fn(), endAuctionAsSeller: vi.fn() }
})
vi.mock('@/features/live/media/api', async () => {
  const actual = await vi.importActual<typeof import('@/features/live/media/api')>(
    '@/features/live/media/api',
  )
  return { ...actual, endLiveStream: vi.fn() }
})
vi.mock('@/shared/api/mock', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/mock')>('@/shared/api/mock')
  return { ...actual, isMockApi: () => false }
})
// 질문 큐는 이 스펙 밖이다 — 마운트가 네트워크로 새지 않게만 막는다(화면이 실패를 문구로 삼킨다).
vi.mock('@/shared/api/questions', async () => {
  const actual =
    await vi.importActual<typeof import('@/shared/api/questions')>('@/shared/api/questions')
  return {
    ...actual,
    getQuestionQueue: vi.fn().mockRejectedValue(new Error('질문 큐는 이 스펙 밖')),
  }
})

const roomMock = vi.mocked(getAuctionRoom)
const endAuctionMock = vi.mocked(endAuctionAsSeller)
const endLiveMock = vi.mocked(endLiveStream)

/** 화면이 읽는 필드만 채운다 */
function detail(isSeller: boolean): AuctionRoomDetail {
  return {
    auctionId: 7,
    title: '빈티지 카메라',
    status: 'LIVE',
    isSeller,
    serverTime: '2026-08-06T09:00:00Z',
    scheduledAt: null,
    viewerCount: 0,
    notificationCount: null,
    reservePrice: null,
  } as unknown as AuctionRoomDetail
}

const mountConsole = () =>
  mount(ConsoleView, {
    global: {
      stubs: {
        // 라우터를 통째로 목으로 갈아 끼워서 실물 `RouterLink`는 주입을 못 찾고 렌더가 통째로 죽는다
        RouterLink: { template: '<a><slot /></a>' },
        // 「방송 종료」는 BroadcastSlot이 그리고 이벤트만 올려 준다 — 스텁이 그 버튼 자리를 대신한다.
        BroadcastSlot: {
          emits: ['end'],
          template: `<div><slot name="status" /><button class="end" @click="$emit('end')">종료</button></div>`,
        },
        ConsoleMetrics: true,
        QuestionQueue: true,
        ChatPanel: true,
        RevealOverlay: true,
      },
    },
  })

/** 판매자만 누를 수 있어야 하는 것들 — 대기(시작·삭제)와 라이브(연장) 양쪽 */
function sellerActions(wrapper: ReturnType<typeof mountConsole>) {
  return wrapper.findAll('button').filter((button) => {
    const text = button.text()
    return text.includes('시작') || text.includes('연장') || text.includes('삭제')
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  const live = useLiveStore()
  vi.spyOn(live, 'connect').mockResolvedValue()
  vi.spyOn(live, 'disconnect').mockResolvedValue()
})

describe('ConsoleView 판매자 판정 전 노출', () => {
  it('상세 응답 전에는 콘솔을 그리지 않는다', async () => {
    roomMock.mockReturnValue(new Promise(() => {}))

    const wrapper = mountConsole()
    await flushPromises()

    expect(wrapper.text()).not.toContain('진행 콘솔')
    expect(sellerActions(wrapper)).toHaveLength(0)
  })

  it('상세 조회가 실패하면 콘솔 대신 오류만 남는다', async () => {
    // 여기가 원래 사고 경로다 — `room`이 null로 굳어 「판매자만」 판정도 서지 않고
    // 오류 문구를 단 콘솔이 그대로 남았다.
    roomMock.mockRejectedValue(new Error('방을 불러오지 못했어요'))

    const wrapper = mountConsole()
    await flushPromises()

    expect(wrapper.text()).toContain('방 정보를 불러오지 못했어요')
    expect(wrapper.text()).not.toContain('진행 콘솔')
    expect(sellerActions(wrapper)).toHaveLength(0)
  })

  it('비판매자에게는 안내 카드만 보인다', async () => {
    roomMock.mockResolvedValue(detail(false))

    const wrapper = mountConsole()
    await flushPromises()

    expect(wrapper.text()).toContain('이 방의 판매자만 진행 콘솔을 쓸 수 있어요')
    expect(sellerActions(wrapper)).toHaveLength(0)
  })

  it('판매자에게는 콘솔이 그대로 뜬다 — 안내 카드가 끼어들지 않는다', async () => {
    roomMock.mockResolvedValue(detail(true))

    const wrapper = mountConsole()
    await flushPromises()

    expect(wrapper.text()).toContain('진행 콘솔')
    expect(wrapper.text()).not.toContain('이 방의 판매자만')
  })
})

// 「방송 종료」가 경매를 못 닫고 멈추는 경로 (S15P11A704-714 후속).
//
// 실제로 난 사고다: 카메라가 안 잡힌 채로 진행한 방은 `live_broadcast` 행이 없어 LIVE-02가
// 404 `LIVE_NOT_FOUND`를 준다. 그러면 ROOM-09도 안 돌아 **판매자가 자기 경매를 못 닫는다** —
// 홈 목록에 계속 뜨고 입찰도 계속 받는다. 반대로 미디어 서버 실패는 송출이 살아 있을 수 있으므로
// 그때 경매만 닫으면 「끝난 경매의 살아 있는 방송」이 된다 — 그쪽은 멈추는 게 맞다.
describe('ConsoleView 방송 종료', () => {
  /** 확인 한 번 더 누르기(오조작 방지)까지 포함한 실제 클릭 흐름 */
  async function clickEnd(wrapper: ReturnType<typeof mountConsole>) {
    const button = wrapper.get('button.end')
    await button.trigger('click')
    await button.trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    roomMock.mockResolvedValue(detail(true))
    endLiveMock.mockResolvedValue()
    endAuctionMock.mockResolvedValue({} as Awaited<ReturnType<typeof endAuctionAsSeller>>)
  })

  it('둘 다 성공하면 경매를 닫고 홈으로 나간다', async () => {
    const wrapper = mountConsole()
    await flushPromises()
    await clickEnd(wrapper)

    expect(endLiveMock).toHaveBeenCalledWith(7)
    expect(endAuctionMock).toHaveBeenCalledWith(7)
    expect(routerMock.replace).toHaveBeenCalledWith({ name: 'home' })
  })

  it('끊을 방송 기록이 없어도 경매는 닫는다', async () => {
    endLiveMock.mockRejectedValue(
      new ApiError('방송 기록이 존재하지 않습니다.', { status: 404, code: 'LIVE_NOT_FOUND' }),
    )

    const wrapper = mountConsole()
    await flushPromises()
    await clickEnd(wrapper)

    expect(endAuctionMock).toHaveBeenCalledWith(7)
    expect(routerMock.replace).toHaveBeenCalledWith({ name: 'home' })
    expect(wrapper.text()).not.toContain('방송 기록이 존재하지 않습니다.')
  })

  it('미디어 서버가 실패하면 경매를 닫지 않고 콘솔에 남는다', async () => {
    endLiveMock.mockRejectedValue(
      new ApiError('미디어 서버 연동에 실패했습니다.', {
        status: 502,
        code: 'MEDIA_SERVER_ERROR',
      }),
    )

    const wrapper = mountConsole()
    await flushPromises()
    await clickEnd(wrapper)

    expect(endAuctionMock).not.toHaveBeenCalled()
    expect(routerMock.replace).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('미디어 서버 연동에 실패했습니다.')
    expect(wrapper.text()).toContain('진행 콘솔')
  })
})
