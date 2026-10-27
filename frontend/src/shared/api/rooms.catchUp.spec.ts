import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from '@/shared/api/http'
import { getCatchUpSummary, type CatchUpSummary } from './rooms'

const summary: CatchUpSummary = {
  auctionId: 7,
  generatedAt: '2026-07-31T09:00:00Z',
  expiresAt: '2026-07-31T09:02:00Z',
  bidSummary: { bidCount: 7, participantCount: 5 },
  extensionSummary: { extensionCount: 1, extendedMinutes: 5 },
  sellerMentions: ['키캡 하나에 하자가 있습니다'],
  serverTime: '2026-07-31T09:00:12Z',
  endAt: '2026-07-31T09:15:00Z',
  sourceType: 'STT_AND_ROOM_EVENT',
}

describe('중간 입장 요약 API', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('200 응답의 요약을 반환한다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ status: 200, data: summary })

    await expect(getCatchUpSummary(7)).resolves.toEqual(summary)
    expect(http.get).toHaveBeenCalledWith('/auction-rooms/7/catch-up-summary')
  })

  it('생성할 정보가 없는 204는 null로 처리한다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({ status: 204, data: undefined })

    await expect(getCatchUpSummary(7)).resolves.toBeNull()
  })
})
