import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deleteChatMessage, pinNotice, unpinNotice } from './chat'
import { http } from './http'

// CHAT-02 고정 공지 경로·본문을 잠근다 (S15P11A704-559).
//
// 목 분기가 없는 자리라 잘못되면 조용히 틀린다: 경로가 어긋나면 404가 나는데 화면은 「고정하지 못했어요」만
// 보여줘서 판매자에겐 권한 문제와 구분되지 않는다. `/v1`을 또 붙여 `/api/v1/v1/...`이 되던 사고도 있었다(395).
//
// messageId를 **ULID 문자열 그대로** 싣는 것이 이 스펙의 핵심이다. 실서버 PinnedNoticeController가
// 이 값을 Long.parseLong으로 검증하는 결함이 있어(수정 대기) 400을 받는 상태인데, 그걸 FE에서 숫자로
// 우회하면 BE가 고쳐지는 순간 반대로 깨진다 — 문서 계약이 기준이라는 것을 여기서 고정한다.

const ULID = '01J8W3P2N9KQ4T'

describe('pinNotice', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('목 분기 없이 상대경로로 POST하고 messageId를 ULID 문자열 그대로 싣는다', async () => {
    // `isMockApi()`는 테스트에서 항상 「켬」이라 목 분기가 있으면 http를 안 탄다.
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: undefined })

    await pinNotice(7, ULID)

    expect(post).toHaveBeenCalledOnce()
    expect(post.mock.calls[0][0]).toBe('/auctions/7/chat/pin')
    expect(post.mock.calls[0][1]).toEqual({ messageId: ULID })
  })
})

describe('unpinNotice', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('같은 경로로 DELETE를 보낸다 — 본문은 없다(204)', async () => {
    const del = vi.spyOn(http, 'delete').mockResolvedValue({ data: undefined })

    await unpinNotice(7)

    expect(del).toHaveBeenCalledOnce()
    expect(del.mock.calls[0][0]).toBe('/auctions/7/chat/pin')
    expect(del.mock.calls[0][1]).toBeUndefined()
  })
})

// CHAT-01 메시지 삭제 (S15P11A704-681) — 경로가 고정 공지(`chat/pin`)와 한 글자 차이인 `chats`(복수)다.
// 오타면 404가 나는데 화면엔 「삭제하지 못했어요」만 남아 권한 문제(403)와 구분되지 않는다.
describe('deleteChatMessage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('메시지 경로로 DELETE하고 messageId를 ULID 문자열 그대로 싣는다 — 판매자·운영자 공용 단일 엔드포인트', async () => {
    const del = vi.spyOn(http, 'delete').mockResolvedValue({ data: undefined })

    await deleteChatMessage(7, ULID)

    expect(del).toHaveBeenCalledOnce()
    expect(del.mock.calls[0][0]).toBe(`/auctions/7/chats/${ULID}`)
  })
})
