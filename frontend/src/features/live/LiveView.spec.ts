import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import { usePredictionStore } from '@/stores/prediction'
import { applySnapshot, dispatchAuctionTopic } from './socket/handlers'
import type { RealtimeState } from './socket/realtimeState'
import { getCatchUpSummary, type CatchUpSummary } from '@/shared/api/rooms'
import LiveView from './LiveView.vue'

// 라이브를 떠날 때 예측 스토어가 실제로 비워지는지 (S15P11A704-546 리뷰).
//
// `prediction.$reset()`은 전 레포에서 여기 한 곳뿐이라 이게 안 돌면 다음 방에서 `submitted`가 남아
// **제출이 막히고** 이전 방의 확정 카드가 그려진다. 화면은 멀쩡해 보여서 조용히 깨지는 쪽이다.
//
// 잠그는 것은 "언마운트하면 비워진다"가 아니라 **"다음 화면이 먼저 연결을 세워도 비워진다"**이다 —
// 546으로 결과 화면이 마운트에서 `live.connect()`(→`joinRoom()`)를 부르면서 입장 번호(roomSession)가
// 동기로 갱신되고, 리셋이 `disconnect().then`의 세대 가드 안에 있으면 그 경로에서 통째로 사라진다.
//
// 자식 컴포넌트·소켓은 전부 스텁·스파이다. 여기서 보는 것은 언마운트가 남기는 스토어 상태뿐이다.

// 라우터는 호출을 단언해야 하므로 매 호출 새 스파이를 만들지 않고 하나를 고정해 둔다(마감 캐치업, 674).
const { pushMock, replaceMock } = vi.hoisted(() => ({ pushMock: vi.fn(), replaceMock: vi.fn() }))
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({ params: { id: '7' }, query: {}, fullPath: '/rooms/7/live' }),
    useRouter: () => ({ push: pushMock, replace: replaceMock }),
  }
})

// 방 상세·지갑 조회는 이 스펙과 무관하다 — 네트워크로 새지 않게 막는다(둘 다 스토어가 실패를 삼킨다).
vi.mock('@/shared/api/rooms', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/rooms')>('@/shared/api/rooms')
  return {
    ...actual,
    getAuctionRoom: vi.fn().mockRejectedValue(new Error('상세는 이 스펙 밖')),
    getCatchUpSummary: vi.fn().mockResolvedValue(null),
  }
})
vi.mock('@/shared/api/wallets', async () => {
  const actual =
    await vi.importActual<typeof import('@/shared/api/wallets')>('@/shared/api/wallets')
  return { ...actual, getMyWallets: vi.fn().mockRejectedValue(new Error('지갑은 이 스펙 밖')) }
})
vi.mock('@/shared/api/mock', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/mock')>('@/shared/api/mock')
  return { ...actual, isMockApi: () => false }
})

// LiveLayout은 `#action`에 입찰·예측 dock을, `#video`에 스테이지와 그 위 오버레이(따라잡기 요약,
// S15P11A704-706)를 받는다. 기본 스텁은 슬롯을 렌더하지 않아 마감 잠금(`inert`)도 요약 카드도 볼 수
// 없으므로, 두 슬롯을 흘려보내는 스텁으로 바꾼다. 스테이지 자체(LiveStage·VibeOverlay)는 이 스펙 밖이다.
const mountView = () =>
  mount(LiveView, {
    global: {
      stubs: {
        LiveLayout: { template: '<div><slot name="video" /><slot name="action" /></div>' },
        LiveStage: true,
        VibeOverlay: true,
        RevealOverlay: true,
        ReportModal: true,
      },
    },
  })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getCatchUpSummary).mockReset().mockResolvedValue(null)
})
afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

const catchUpSummary: CatchUpSummary = {
  auctionId: 7,
  generatedAt: '2026-07-31T09:00:00Z',
  expiresAt: '2026-07-31T09:02:00Z',
  bidSummary: { bidCount: 1, participantCount: 1 },
  extensionSummary: { extensionCount: 0, extendedMinutes: 0 },
  sellerMentions: ['구성품은 케이블과 박스를 포함합니다'],
  serverTime: '2026-07-31T09:00:12Z',
  endAt: '2026-07-31T09:15:00Z',
  sourceType: 'STT_AND_ROOM_EVENT',
}

describe('LiveView 중간 입장 요약 갱신', () => {
  // 와이어프레임대로 11초 뒤 스스로 접힌다(S15P11A704-706). 스테이지 위 오버레이라 계속 떠 있으면
  // 영상을 가린다.
  //
  // ⚠️ 그 결과 **15초 주기 재조회(S15P11A704-700)는 사실상 발화하지 않는다** — 주기 조건에
  // `catchUpSummaryVisible`이 있어 첫 15초 전에 카드가 접히고, 다시 켜는 경로가 없다.
  // 조용히 죽는 종류라 여기서 잠가 둔다: 접힘 이후 호출 수가 늘지 않는 것이 지금의 계약이다.
  it('11초 뒤 자동으로 접히고 주기 재조회도 함께 멈춘다', async () => {
    vi.useFakeTimers()
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()
    vi.mocked(getCatchUpSummary).mockResolvedValue(catchUpSummary)

    const wrapper = mountView()
    await flushPromises()
    expect(getCatchUpSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.findComponent({ name: 'CatchUpSummaryCard' }).exists()).toBe(true)

    await vi.advanceTimersByTimeAsync(11_000)
    await flushPromises()
    // 퇴장 트랜지션이 끝나고 주기 타이머가 두 번 더 지나갈 만큼 흘린다
    await vi.advanceTimersByTimeAsync(30_000)
    await flushPromises()

    expect(wrapper.findComponent({ name: 'CatchUpSummaryCard' }).exists()).toBe(false)
    expect(getCatchUpSummary).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('웹소켓의 최신 입찰 집계를 AI 문장보다 먼저 카드에 반영한다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()
    vi.mocked(getCatchUpSummary).mockResolvedValue(catchUpSummary)

    const wrapper = mountView()
    await flushPromises()
    // 레일 집계는 아직 '—'이므로 이 문구는 요약 카드에서만 나온다
    expect(wrapper.text()).toContain('입찰 1건')
    expect(wrapper.text()).toContain('참여 1명')

    live.bidCount = 4
    live.participantCount = 3
    await flushPromises()

    expect(wrapper.text()).toContain('입찰 4건')
    expect(wrapper.text()).toContain('참여 3명')
    wrapper.unmount()
  })
})

describe('LiveView 퇴장 정리 (S15P11A704-546)', () => {
  it('언마운트하면 예측 스토어를 비운다 — 다음 화면이 먼저 입장 번호를 올려도', async () => {
    const live = useLiveStore()
    const prediction = usePredictionStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()
    // 이 방에서 예측을 제출한 상태 (PRED-01)
    prediction.restoreSubmitted(52_000, 1_000)

    wrapper.unmount()
    // 결과 화면의 `connect()`가 동기로 하는 일 — 입장 번호가 여기서 바뀐다.
    live.joinRoom(7)
    await flushPromises()

    expect(prediction.submitted).toBe(false)
    expect(prediction.myPrediction).toBeNull()
    expect(prediction.myStake).toBe(0)
  })
})

// 소켓 종료 이벤트 → 오버레이·입력 잠금 (BID-10).
//
// 이 배선이 없던 동안 실서버에서는 **마감돼도 아무 일도 일어나지 않았다** — `reveal.apply`를 부르는
// 곳이 목 데모 딥링크(`?reveal=`)뿐이라 phase가 `hidden`에서 나올 수 없었고, 그래서 낙찰 공개가
// 안 뜨고 입찰창도 계속 열려 있었다. 목 모드로 열면 데모 경로가 대신 떠서 멀쩡해 보이는 종류의 구멍이라
// 화면 쪽에서 잠근다. `RevealOverlay`는 스텁이므로 보는 것은 "렌더됐는가"와 잠금 표식뿐이다.
describe('LiveView 마감 공개 배선', () => {
  it('스토어에 종료 이벤트가 오면 오버레이를 띄우고 입찰·예측 입력을 잠근다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'RevealOverlay' }).exists()).toBe(false)

    live.endEvent = {
      type: 'AUCTION_SETTLED',
      data: {
        winner: '●●3',
        highestBidAmount: 82_000,
        secondHighestBidAmount: 74_000,
        finalPrice: 74_000,
        resultType: 'SECOND_PRICE',
      },
    }
    await flushPromises()

    expect(wrapper.findComponent({ name: 'RevealOverlay' }).exists()).toBe(true)
    // 마감 후 제출 차단 — `inert` 유무로 보지 않는다. Vue가 `false`를 `inert="false"`로 렌더해
    // `[inert]` 셀렉터는 잠기지 않아도 걸린다. 사용자에게 보이는 잠금 안내로 판정한다.
    expect(wrapper.text()).toContain('경매가 마감돼')

    wrapper.unmount()
  })

  // 이전 방의 잔재를 집지 않는지 (!303 리뷰 1).
  //
  // 퇴장 정리의 `live.$reset()`은 `disconnect()` 뒤로 미뤄져 있어, 방 A가 정산된 직후 방 B를 열면
  // B의 setup 시점에 `endEvent`가 아직 A의 것이다. watch에 `immediate`를 붙이면 그걸 그대로 집어
  // **B 화면에 A의 낙찰 오버레이**가 뜨고, `ended`가 true로 남아 B의 입찰·예측까지 잠긴다.
  it('마운트 시점에 남아 있던 이전 방 종료 이벤트는 집지 않는다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    // 이전 방(A)이 정산된 채로 남긴 잔재 — 아직 $reset()이 도착하지 않은 상태
    live.endEvent = {
      type: 'AUCTION_SETTLED',
      data: {
        winner: '●●3',
        highestBidAmount: 82_000,
        secondHighestBidAmount: 74_000,
        finalPrice: 74_000,
        resultType: 'SECOND_PRICE',
      },
    }

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'RevealOverlay' }).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('경매가 마감돼') // 새 방에서 입찰이 잠기면 안 된다

    wrapper.unmount()
  })
})

// 정산 순간 소켓이 끊겨 있던 뷰어의 캐치업 (S15P11A704-674).
//
// 종료 이벤트를 유실하면 마감 전환의 유일한 트리거가 사라진다 — 재접속 스냅샷(WS-03)이 `auctionStatus`를
// 'ENDED'로 정확히 복원해도 화면은 타이머 00:00인 라이브에 멈춰 있었다. 실사로 재현된 구멍이고,
// 이벤트 경로가 멀쩡해서 위 스펙만으로는 잡히지 않는다.
describe('LiveView 마감 캐치업 (S15P11A704-674)', () => {
  const settledEvent = {
    type: 'AUCTION_SETTLED',
    data: {
      winner: '●●3',
      highestBidAmount: 82_000,
      secondHighestBidAmount: 74_000,
      finalPrice: 74_000,
      resultType: 'SECOND_PRICE',
    },
  }

  // 재접속 스냅샷(WS-03)을 실제 복원 함수로 태운다 — 상태를 직접 대입하면 "복원이 auctionStatus를
  // 정말 세우는가"까지는 못 잠근다. 스냅샷엔 낙찰/유찰 payload가 없어 이 경로에 endEvent는 안 생긴다.
  const endedSnapshot: RealtimeState = {
    stateVersion: 12,
    auctionStatus: 'ENDED',
    endAt: '2026-01-01T00:10:00.000Z',
    remainingSeconds: 0,
    extendCount: 0,
    remainingExtensions: 3,
    bidCount: 3,
    bidParticipantCount: 2,
    viewerCount: 10,
    myBidAmount: null,
    myPredictedPrice: null,
    myStakeAmount: null,
    myParticipationType: 'NONE',
    pinnedNotice: null,
    amIMuted: false,
    mutedUntil: null,
  }

  it('이벤트 없이 스냅샷이 ENDED만 복원해도 결과 화면으로 보낸다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    applySnapshot(live, endedSnapshot)
    await flushPromises()

    expect(live.endEvent).toBeNull()
    expect(replaceMock).toHaveBeenCalledWith({ name: 'result', params: { id: '7' } })
    wrapper.unmount()
  })

  it('종료 이벤트를 받은 경로에서는 이동하지 않는다 — 연출이 우선', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    // 실제 핸들러로 흘린다 — auctionStatus와 endEvent를 같은 tick에 세우는 것이 중복 발화를 막는 근거다
    dispatchAuctionTopic(live, settledEvent)
    await flushPromises()

    expect(live.auctionStatus).toBe('SETTLED')
    expect(replaceMock).not.toHaveBeenCalled()
    expect(wrapper.findComponent({ name: 'RevealOverlay' }).exists()).toBe(true)
    wrapper.unmount()
  })

  // 이전 방의 잔재를 집지 않는지 — 위 endEvent 쪽 잔재 스펙(!303 리뷰 1)과 대칭이다.
  //
  // 퇴장 정리의 `live.$reset()`이 `disconnect()` 뒤로 미뤄져 있어, 정산된 방 A에서 곧장 방 B로 들어가면
  // B의 setup 시점에 `auctionStatus`가 아직 A의 'ENDED'다. 이 watch에 `immediate`를 붙이면 그걸 그대로
  // 집어 **B에 들어가자마자 A의 결과 화면으로 튕긴다** — 붙이면 실제로 깨지는 자리라 여기서 잠근다.
  it('마운트 시점에 남아 있던 이전 방 ENDED는 집지 않는다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    // 이전 방(A)이 정산된 채로 남긴 잔재 — 아직 $reset()이 도착하지 않은 상태
    live.auctionStatus = 'ENDED'

    const wrapper = mountView()
    await flushPromises()

    expect(replaceMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('AUCTION_ENDED만 받은 뒤 ENDED 스냅샷이 오면 결과 화면으로 복구한다', async () => {
    const live = useLiveStore()
    vi.spyOn(live, 'connect').mockResolvedValue()
    vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()

    dispatchAuctionTopic(live, {
      type: 'AUCTION_ENDED',
      data: { endedAt: '2026-01-01T00:10:00.000Z', status: 'SETTLING' },
    })
    applySnapshot(live, endedSnapshot)
    await flushPromises()

    expect(live.endEvent?.type).toBe('AUCTION_ENDED')
    expect(replaceMock).toHaveBeenCalledWith({ name: 'result', params: { id: '7' } })
    wrapper.unmount()
  })
})
