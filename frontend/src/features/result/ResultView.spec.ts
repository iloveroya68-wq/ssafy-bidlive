import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useLiveStore } from '@/stores/live'
import ResultView from './ResultView.vue'
import type { SoldAuctionResult } from '@/shared/api/results'
import type { PredictionResultsResponse } from '@/shared/api/predictions'

// PRED-03 `PREDICTION_SETTLED` 소비 (S15P11A704-543).
//
// 이 화면은 진입 시 PRED-02를 1회 부르고, 정산 전이면 409로 안내 문구에 머문다. 정산이 끝났다는
// 신호가 오면 **한 번 더** 부르는 것이 명세 처리 규칙이다 — 폴링은 금지라 이 재조회 계기가 사라지면
// 화면은 새로고침 전까지 영원히 "정산이 끝나면 공개돼요"로 굳는다. 조용히 깨지는 배선이라 잠근다.
//
// 신호가 실제로 도달하려면 이 화면이 **직접** 메인 토픽에 붙어 있어야 한다 — 라이브가 언마운트하며
// 자기 연결을 끊기 때문이다(S15P11A704-546, 아래 세 번째 describe). 「다시 확인」 버튼은 신호가 없는
// 경우(목 모드·연결 실패)의 수동 계기로 남는다 — 아래 두 번째 describe.

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({ params: { id: '7' }, fullPath: '/rooms/7/result' }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  }
})

const { auctionResultMock, predictionResultsMock } = vi.hoisted(() => ({
  auctionResultMock: vi.fn<() => Promise<SoldAuctionResult>>(),
  predictionResultsMock: vi.fn<() => Promise<PredictionResultsResponse>>(),
}))
vi.mock('./resultSource', async () => {
  const actual = await vi.importActual<typeof import('./resultSource')>('./resultSource')
  return {
    ...actual,
    loadAuctionResult: auctionResultMock,
    loadPredictionResults: predictionResultsMock,
  }
})

// 소켓 연결은 **실 모드에서만** 선다(546) — 모드 스위치를 값으로 바꿀 수 있게 두고 기본은 목 모드로
// 둔다(기존 describe들이 연결 없이 신호만 다루던 조건 그대로).
const { mockApi } = vi.hoisted(() => ({ mockApi: { value: true } }))
vi.mock('@/shared/api/mock', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/mock')>('@/shared/api/mock')
  return { ...actual, isMockApi: () => mockApi.value }
})

// 방 제목을 위한 상세 조회는 이 스펙과 무관하다 — 네트워크로 새지 않게 막는다.
vi.mock('@/shared/api/rooms', async () => {
  const actual = await vi.importActual<typeof import('@/shared/api/rooms')>('@/shared/api/rooms')
  return { ...actual, getAuctionRoom: vi.fn().mockRejectedValue(new Error('상세는 이 스펙 밖')) }
})

const soldResult: SoldAuctionResult = {
  auctionId: 7,
  resultType: 'SOLD',
  viewerRole: 'PREDICTOR',
  winnerLabel: '낙**',
  finalPrice: 52_000,
  priceDecisionType: 'SECOND_PRICE',
  myBidSettlement: null,
  myPredictionSettlement: null,
  bids: [],
  predictionBands: [],
}

const bandsResponse: PredictionResultsResponse = {
  auctionId: 7,
  settlementStatus: 'COMPLETED',
  bands: [{ band: 'WITHIN_5', participantCount: 2, multiplier: 5, totalPayoutAmount: 3000 }],
}

/** 정산 전 PRED-02 — 409 PREDICTION_RESULT_NOT_AVAILABLE */
function notReadyError() {
  return { response: { status: 409, data: { code: 'PREDICTION_RESULT_NOT_AVAILABLE' } } }
}

const mountView = () =>
  mount(ResultView, {
    global: {
      stubs: {
        MyPredictionCard: true,
        PredictionBandChart: true,
        ResultBidLedger: true,
        ResultSettlementCard: true,
      },
    },
  })

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  mockApi.value = true
  // jsdom엔 matchMedia가 없다 — 낙찰가 카운트업(useCountUp)이 감속 설정을 물어보다 터진다.
  vi.stubGlobal('matchMedia', () => ({ matches: false }))
  auctionResultMock.mockResolvedValue(soldResult)
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('ResultView — PREDICTION_SETTLED 재조회', () => {
  it('신호가 오면 PRED-02를 다시 부른다 — 진입 시 409로 비어 있던 밴드가 채워진다', async () => {
    predictionResultsMock.mockRejectedValueOnce(notReadyError())
    const live = useLiveStore()
    mountView()
    await flushPromises()
    expect(predictionResultsMock).toHaveBeenCalledTimes(1)

    predictionResultsMock.mockResolvedValueOnce(bandsResponse)
    live.predictionSettlement = { settlementStatus: 'COMPLETED', settledCount: 128 }
    await flushPromises()

    expect(predictionResultsMock).toHaveBeenCalledTimes(2)
    expect(predictionResultsMock).toHaveBeenLastCalledWith(7)
  })

  it('신호가 없으면 진입 1회로 끝난다 — 폴링·타이머는 없다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    mountView()
    await flushPromises()
    await flushPromises()

    expect(predictionResultsMock).toHaveBeenCalledTimes(1)
  })

  // 조회 중에 온 신호를 버리면 그 신호가 통째로 증발한다 — 이미 나가 있던 응답은 정산 전 상태일 수
  // 있어서, 겹쳤다는 이유로 버리면 화면이 "집계 중"에 굳는다. 큐에 세워 두고 끝나면 한 번 더 부른다.
  it('조회 중에 온 신호는 큐잉했다가 끝난 뒤 한 번 더 부른다 — 연속 신호는 1회로 합쳐진다', async () => {
    predictionResultsMock.mockRejectedValueOnce(notReadyError())
    const live = useLiveStore()
    mountView()
    await flushPromises()

    // 응답을 붙잡아 둔 조회를 물려 두고 그 사이에 신호를 두 번 보낸다.
    let settle: (response: PredictionResultsResponse) => void = () => {}
    predictionResultsMock.mockReturnValueOnce(
      new Promise<PredictionResultsResponse>((resolve) => {
        settle = resolve
      }),
    )
    live.predictionSettlement = { settlementStatus: 'PARTIAL', settledCount: 3 }
    await flushPromises()
    expect(predictionResultsMock).toHaveBeenCalledTimes(2) // 진입 1 + 신호 1

    live.predictionSettlement = { settlementStatus: 'COMPLETED', settledCount: 128 }
    await flushPromises()
    expect(predictionResultsMock).toHaveBeenCalledTimes(2) // 도는 중엔 겹쳐 부르지 않는다

    predictionResultsMock.mockResolvedValue(bandsResponse)
    settle(bandsResponse)
    await flushPromises()

    // 큐잉된 신호로 딱 한 번 더. 신호 2개가 2회가 되지도, 재조회가 재조회를 부르지도 않는다.
    expect(predictionResultsMock).toHaveBeenCalledTimes(3)
  })

  it('FAILED 신호도 재조회한다 — 알리지 않으면 화면이 집계 중에 굳는다', async () => {
    predictionResultsMock.mockRejectedValue(notReadyError())
    const live = useLiveStore()
    mountView()
    await flushPromises()

    live.predictionSettlement = { settlementStatus: 'FAILED', settledCount: 0 }
    await flushPromises()

    expect(predictionResultsMock).toHaveBeenCalledTimes(2)
  })

  // 방을 뜨며 live.$reset()이 도는 경로. null 복귀를 재조회로 오해하면 안 된다.
  it('null로 리셋되는 것은 신호가 아니다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    const live = useLiveStore()
    live.predictionSettlement = { settlementStatus: 'COMPLETED', settledCount: 1 }
    mountView()
    await flushPromises()
    expect(predictionResultsMock).toHaveBeenCalledTimes(1)

    live.predictionSettlement = null
    await flushPromises()

    expect(predictionResultsMock).toHaveBeenCalledTimes(1)
  })
})

describe('ResultView — 밴드 미공개 시 수동 재조회', () => {
  // 밴드 차트는 실물로 마운트해야 버튼이 나온다.
  const mountWithChart = () =>
    mount(ResultView, {
      global: {
        stubs: { MyPredictionCard: true, ResultBidLedger: true, ResultSettlementCard: true },
      },
    })

  const retryButton = (wrapper: ReturnType<typeof mountWithChart>) =>
    wrapper.findAll('button').filter((button) => button.text() === '다시 확인')

  it('밴드가 안 열렸으면 버튼이 나오고, 누르면 PRED-02를 한 번 더 부른다', async () => {
    predictionResultsMock.mockRejectedValueOnce(notReadyError())
    const wrapper = mountWithChart()
    await flushPromises()
    expect(retryButton(wrapper)).toHaveLength(1)

    predictionResultsMock.mockResolvedValueOnce(bandsResponse)
    await retryButton(wrapper)[0].trigger('click')
    await flushPromises()

    expect(predictionResultsMock).toHaveBeenCalledTimes(2)
    expect(retryButton(wrapper)).toHaveLength(0) // 밴드가 열리면 버튼도 사라진다
  })

  it('밴드가 공개된 상태에는 버튼이 없다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    const wrapper = mountWithChart()
    await flushPromises()

    expect(retryButton(wrapper)).toHaveLength(0)
  })
})

// 위 재조회 배선을 실전에서 발화시키는 부분 (S15P11A704-546).
//
// 543이 신호→재조회를 깔아 뒀지만 이 화면이 소켓에 붙지 않아 신호가 도착할 길이 없었다 —
// 라이브가 언마운트하며 `live.disconnect()`를 부르기 때문이다. 연결이 빠지면 watch는 그대로 남은 채
// **조용히** 죽고, 화면은 새로고침 전까지 안내 문구에 굳는다. 그래서 연결·정리 두 지점을 잠근다.
//
// 세대 가드(라이브 언마운트의 늦은 꼬리가 이 화면의 연결을 끊는 경합)는 스토어 쪽 관심사라
// `stores/live.spec.ts`가 잠근다 — 실 모드 경합은 그 파일에 적힌 이유로 이 하네스에서 재현되지 않는다.
// LIVE-06 낙찰 순간 스냅샷 (S15P11A704-705).
//
// 판매자가 종료 시점 프레임을 올리고(704) 결과 응답의 `snapshotUrl`로 내려오는데, 그전까지 이 화면은
// 「스냅샷 준비 중」에 하드코딩돼 있었다. 안 올라온 경매도 정상이라 두 분기 다 잠근다 —
// 없을 때 `<img :src="undefined">`를 그리면 깨진 이미지 아이콘이 무대 한가운데에 뜬다.
describe('ResultView — 낙찰 순간 스냅샷', () => {
  const snapshotImage = (wrapper: ReturnType<typeof mountView>) =>
    wrapper.findAll('img').filter((img) => img.attributes('alt') === '낙찰 순간 스냅샷')

  it('URL이 오면 스냅샷을 그리고 플레이스홀더를 치운다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    auctionResultMock.mockResolvedValue({
      ...soldResult,
      snapshotUrl: 'https://cdn.example.com/snapshots/7.jpg',
    })
    const wrapper = mountView()
    await flushPromises()

    const image = snapshotImage(wrapper)
    expect(image).toHaveLength(1)
    expect(image[0].attributes('src')).toBe('https://cdn.example.com/snapshots/7.jpg')
    expect(wrapper.text()).not.toContain('스냅샷 준비 중')
  })

  it('스냅샷이 없으면 플레이스홀더가 남는다 — 빈 `<img>`를 그리지 않는다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    const wrapper = mountView()
    await flushPromises()

    expect(snapshotImage(wrapper)).toHaveLength(0)
    expect(wrapper.text()).toContain('스냅샷 준비 중')
  })
})

describe('ResultView — 메인 토픽 구독 (S15P11A704-546)', () => {
  it('실 모드는 마운트에서 붙고 언마운트에서 끊는다', async () => {
    mockApi.value = false
    predictionResultsMock.mockResolvedValue(bandsResponse)
    const live = useLiveStore()
    const connect = vi.spyOn(live, 'connect').mockResolvedValue()
    const disconnect = vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()
    expect(connect).toHaveBeenCalledWith(7) // 질문 큐 옵션은 켜지 않는다(판매자 전용, 541)
    expect(disconnect).not.toHaveBeenCalled()

    wrapper.unmount()

    expect(disconnect).toHaveBeenCalledTimes(1)
  })

  it('목 모드는 붙지 않는다 — 목 피드는 신호를 안 보내고 결과 화면이 안 읽는 집계만 흔든다', async () => {
    predictionResultsMock.mockResolvedValue(bandsResponse)
    const live = useLiveStore()
    const connect = vi.spyOn(live, 'connect').mockResolvedValue()
    const disconnect = vi.spyOn(live, 'disconnect').mockResolvedValue()

    const wrapper = mountView()
    await flushPromises()
    wrapper.unmount()

    expect(connect).not.toHaveBeenCalled()
    expect(disconnect).not.toHaveBeenCalled()
  })
})
