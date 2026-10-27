import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchChatHistory } from './history'
import { http } from '@/shared/api/http'

// 채팅 이력 경로·쿼리를 잠근다 (S15P11A704-679).
//
// 목 분기가 없는 자리라 어긋나면 조용히 틀린다 — 경로가 틀리면 404, 커서 키가 틀리면 400 INVALID_CURSOR인데
// 스토어가 그걸 「더 없음」으로 접어 버려서 화면상으로는 "이력이 원래 없는 방"과 구분되지 않는다.
// 첫 페이지 시드에서 `before`가 **빠져야** 한다는 것이 핵심이다: 빈 문자열이 실려 나가면 서버가
// INVALID_CURSOR로 막는다. undefined로 두면 axios가 쿼리에서 뺀다 — 그 값을 여기서 고정한다.

const row = (id: string) => ({
  messageId: id,
  clientMessageId: null,
  memberId: 3,
  nickname: '준호',
  content: '안녕하세요',
  isQuestion: false,
  maskedFields: [],
  createdAt: '2026-08-02T00:00:00Z',
})

describe('fetchChatHistory', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('커서를 주면 before에 ULID 그대로 싣고 응답을 스토어 행으로 정규화한다', async () => {
    const get = vi
      .spyOn(http, 'get')
      .mockResolvedValue({ data: { messages: [row('01J8W3P2N9KQ4T')], hasMore: true } })

    const page = await fetchChatHistory(7, '01J9AAAAAAAAAA')

    expect(get.mock.calls[0][0]).toBe('/auctions/7/chats')
    expect(get.mock.calls[0][1]?.params).toEqual({ before: '01J9AAAAAAAAAA', size: 30 })
    expect(page).toEqual({
      messages: [
        {
          id: '01J8W3P2N9KQ4T',
          nickname: '준호',
          text: '안녕하세요',
          isQuestion: false,
          clientMessageId: null,
          maskedFields: [],
          createdAt: '2026-08-02T00:00:00Z',
        },
      ],
      hasMore: true,
    })
  })

  it('커서가 없으면 before를 undefined로 둔다(axios가 쿼리에서 뺌 → 첫 페이지)', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { messages: [], hasMore: false } })

    await fetchChatHistory(7)

    expect(get.mock.calls[0][1]?.params).toStrictEqual({ before: undefined, size: 30 })
  })
})
