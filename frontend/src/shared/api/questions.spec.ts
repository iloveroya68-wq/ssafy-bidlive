import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { getQuestionQueue, resolveQuestion } from './questions'

// CHAT-06 질문 큐의 경로·쿼리를 잠근다 (S15P11A704-680).
//
// 목 분기를 걷어낸 자리라 **경로 오타가 조용한 404**가 된다. 화면은 「불러오지 못했어요」만 띄워서
// 판매자에겐 권한 문제(403 NOT_AUCTION_HOST)와 구분되지 않는다. `/v1`을 또 붙여 `/api/v1/v1/...`이
// 되던 사고도 있었다(395). 목이 사라진 지금 이게 유일한 감지선이다.

function pageResponse() {
  return {
    data: {
      items: [
        {
          questionId: '01J8W3P2N9KQ4T',
          memberId: 7,
          nickname: '서연',
          content: '키캡 흠집을 보여주세요',
          createdAt: '2026-07-20T21:03:12+09:00',
        },
      ],
      nextCursor: '01J8W3P2N9KQ4T',
      hasNext: false,
    },
  }
}

describe('getQuestionQueue', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('목 분기 없이 baseURL 기준 상대경로로 GET한다', async () => {
    // `isMockApi()`는 테스트에서 항상 「켬」이라 목 분기가 되살아나면 http를 안 탄다.
    const get = vi.spyOn(http, 'get').mockResolvedValue(pageResponse())

    await getQuestionQueue(7)

    expect(get).toHaveBeenCalledOnce()
    expect(get.mock.calls[0][0]).toBe('/auctions/7/questions')
  })

  it('cursor·size를 쿼리 파라미터로 싣는다 — 이름이 어긋나면 서버가 첫 페이지만 돌려준다', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue(pageResponse())

    await getQuestionQueue(7, { cursor: '01J8W3P2N9KQ4T', size: 50 })

    const config = get.mock.calls[0][1] as { params?: Record<string, unknown> }
    expect(config.params).toEqual({ cursor: '01J8W3P2N9KQ4T', size: 50 })
  })
})

describe('resolveQuestion', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('questionId를 ULID 문자열 그대로 경로에 붙여 DELETE한다', async () => {
    const del = vi.spyOn(http, 'delete').mockResolvedValue({ data: undefined })

    await resolveQuestion(7, '01J8W3P2N9KQ4T')

    expect(del).toHaveBeenCalledOnce()
    expect(del.mock.calls[0][0]).toBe('/auctions/7/questions/01J8W3P2N9KQ4T')
  })
})
