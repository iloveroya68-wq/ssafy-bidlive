import { beforeEach, describe, expect, it, vi } from 'vitest'

// `loadAuctionResult`·`loadPredictionResults`의 **목 단락**을 잠근다 (S15P11A704-518·520).
//
// `shared/api/results.ts`·`predictions.ts`의 목 분기를 걷어낼 수 있었던 근거가 바로 이 단락이다 —
// 목 모드에서 여기가 fixture를 먼저 돌려주므로 그 아래 API 함수까지 내려가지 않는다. 그래서 이
// 분기를 지우면 **목 모드가 조용히 실 서버를 때리고** 역할 4분기·유찰 3분기 데모가 통째로 사라진다.
// 둘 다 아래에 폴백이 없는 자리라 실패가 눈에 안 띈다.

vi.mock('@/shared/api/mock', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/mock')>()),
  isMockApi: vi.fn(),
  mockDelay: vi.fn(() => Promise.resolve()),
}))

vi.mock('@/shared/api/results', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/results')>()),
  getAuctionResult: vi.fn(),
}))

vi.mock('@/shared/api/predictions', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api/predictions')>()),
  getPredictionResults: vi.fn(),
}))

import { isMockApi } from '@/shared/api/mock'
import { getAuctionResult } from '@/shared/api/results'
import { getPredictionResults } from '@/shared/api/predictions'
import { loadAuctionResult, loadPredictionResults } from './resultSource'

const isMockApiMock = vi.mocked(isMockApi)
const getAuctionResultMock = vi.mocked(getAuctionResult)
const getPredictionResultsMock = vi.mocked(getPredictionResults)

describe('loadAuctionResult', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('목 모드에서는 fixture를 돌려주고 API를 부르지 않는다', async () => {
    isMockApiMock.mockReturnValue(true)

    const result = await loadAuctionResult(1)

    // **이 순서를 지킨다.** 단락이 사라지면 API가 undefined를 돌려주는데, fixture를 먼저 검사하면
    // 실패가 "Cannot read properties of undefined"로 나와 원인을 안 알려 준다.
    expect(getAuctionResultMock).not.toHaveBeenCalled()
    // 방 1은 `mocks/resultFixtures.ts`가 낙찰자 시나리오로 잡아 둔 자리다
    expect(result.viewerRole).toBe('WINNING_BIDDER')
  })

  it('실 모드에서는 fixture를 건너뛰고 getAuctionResult로 내려간다', async () => {
    isMockApiMock.mockReturnValue(false)
    const fromServer = { auctionId: 1, resultType: 'NO_BID', viewerRole: 'SELLER' }
    getAuctionResultMock.mockResolvedValue(
      fromServer as unknown as Awaited<ReturnType<typeof getAuctionResult>>,
    )

    const result = await loadAuctionResult(1)

    expect(getAuctionResultMock).toHaveBeenCalledWith(1)
    expect(result).toBe(fromServer)
  })
})

describe('loadPredictionResults', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('목 모드에서는 fixture를 돌려주고 API를 부르지 않는다', async () => {
    isMockApiMock.mockReturnValue(true)

    const result = await loadPredictionResults(1)

    expect(getPredictionResultsMock).not.toHaveBeenCalled()
    // 인원 3/8/12/27은 디자인 원본 `result` 화면의 요약 줄과 맞춘 값이다
    expect(result.bands.map((band) => band.participantCount)).toEqual([3, 8, 12, 27])
  })

  it('실 모드에서는 fixture를 건너뛰고 getPredictionResults로 내려간다', async () => {
    isMockApiMock.mockReturnValue(false)
    const fromServer = { auctionId: 1, settlementStatus: 'COMPLETED', bands: [] }
    getPredictionResultsMock.mockResolvedValue(fromServer)

    const result = await loadPredictionResults(1)

    expect(getPredictionResultsMock).toHaveBeenCalledWith(1)
    expect(result).toBe(fromServer)
  })
})
