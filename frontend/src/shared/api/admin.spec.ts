import { beforeEach, describe, expect, it, vi } from 'vitest'
import { forceEndAuction, getAdminReports } from './admin'
import { http } from './http'

// 운영자 API의 **실 경로와 응답 무가공**을 잠근다 (S15P11A704-525).
//
// 목 분기가 없는 자리라 잘못되면 조용히 틀린다: 경로가 어긋나면 404가 나는데 화면은
// 「불러오지 못했어요」만 띄워 서버 문제로 보이고, 응답을 가공하면 `reportCount`와
// 사유 합이 갈려 운영자가 잘못된 근거로 방을 끝낸다.

function page() {
  return {
    data: {
      items: [
        {
          auctionId: 9203,
          title: '빈티지 카메라',
          thumbnailUrl: null,
          sellerNickname: '서연***',
          status: 'LIVE',
          reportCount: 8,
          reasonCounts: [
            { reasonCode: 'PROHIBITED_ITEM', count: 5 },
            { reasonCode: 'FALSE_LISTING', count: 3 },
          ],
          latestReportedAt: '2026-07-31T10:31:00Z',
        },
      ],
      nextCursor: null,
      hasNext: false,
    },
  }
}

describe('getAdminReports', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(page())

    await getAdminReports()

    expect(get.mock.calls[0][0]).toBe('/admin/reports')
  })

  it('빈 질의는 파라미터를 싣지 않는다 — 서버 기본값(20건·진행 중만)을 쓴다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(page())

    await getAdminReports()

    const config = get.mock.calls[0][1] as { params?: Record<string, unknown> }
    expect(config.params).toEqual({})
  })

  it('준 값만 파라미터로 싣는다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(page())

    await getAdminReports({ includeEnded: true, cursor: 'abc', size: 50 })

    const config = get.mock.calls[0][1] as { params?: Record<string, unknown> }
    expect(config.params).toEqual({ includeEnded: true, cursor: 'abc', size: 50 })
  })

  it('응답을 가공하지 않고 그대로 돌려준다 — 집계는 전부 서버 값이다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(page())

    expect(await getAdminReports()).toEqual(page().data)
  })

  it('thumbnailUrl이 null이어도 그대로 통과시킨다 — 이미지 없는 상품이 있다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(page())

    const result = await getAdminReports()

    expect(result.items[0].thumbnailUrl).toBeNull()
  })
})

describe('forceEndAuction', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('경매 id를 경로에 넣는다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: {
        auctionId: 9203,
        status: 'ENDED',
        resultType: 'FORCED',
        endedAt: '2026-07-31T10:40:00Z',
      },
    })

    await forceEndAuction(9203, { reason: 'PROHIBITED_ITEM' })

    expect(post.mock.calls[0][0]).toBe('/admin/auctions/9203/force-end')
  })

  it('reason을 본문에 싣는다 — 서버가 빈 값을 400으로 거절한다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: {
        auctionId: 9203,
        status: 'ENDED',
        resultType: 'FORCED',
        endedAt: '2026-07-31T10:40:00Z',
      },
    })

    await forceEndAuction(9203, { reason: 'PROHIBITED_ITEM', adminNote: '신고 누적' })

    expect(post.mock.calls[0][1]).toEqual({
      reason: 'PROHIBITED_ITEM',
      adminNote: '신고 누적',
    })
  })
})
