import { beforeEach, describe, expect, it, vi } from 'vitest'
import { extendAuction, placeBid } from './bids'
import { http } from './http'
import { IDEMPOTENCY_KEY_HEADER } from './idempotency'

// BID-06 제출 경로를 잠근다 (S15P11A704-395).
//
// 예전에는 목 모드가 http에 닿지도 않아서 여기서 잡는 것들이 전부 가려져 있었다.
// 실제로 `/v1/auctions/...`로 적혀 최종 URL이 `/api/v1/v1/...`가 되던 버그가 그대로 머지됐고,
// 화면이 쓰지 않던 사본만 맞게 짜여 있었다(394 → 395). 목 분기는 504에서 걷어냈다.

function respond() {
  return {
    data: {
      auctionId: 7,
      myBidAmount: 30_000,
      previousBidAmount: null,
      additionalHoldAmount: 30_000,
      totalHeldAmount: 30_000,
      availableBalance: 70_000,
      submittedAt: '2026-07-29T00:00:00+09:00',
    },
  }
}

describe('placeBid', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('baseURL 기준 상대경로로 보낸다 — /v1을 또 붙이면 /api/v1/v1/...가 된다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await placeBid(7, { amount: 30_000 }, 'key-1')

    expect(post.mock.calls[0][0]).toBe('/auctions/7/bids')
  })

  it('금액을 본문에 싣는다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await placeBid(7, { amount: 30_000 }, 'key-1')

    expect(post.mock.calls[0][1]).toEqual({ amount: 30_000 })
  })

  it('넘겨받은 멱등키를 그대로 헤더에 싣는다 — 새로 만들면 재시도가 중복 입찰이 된다', async () => {
    // bid.idempotency_key가 NOT NULL UNIQUE라, 재시도마다 키가 바뀌면 서버는 별건으로 접수한다
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await placeBid(7, { amount: 30_000 }, 'retry-me')

    const config = post.mock.calls[0][2] as { headers?: Record<string, string> }
    expect(config.headers?.[IDEMPOTENCY_KEY_HEADER]).toBe('retry-me')
  })

  it('키를 생략하면 호출마다 다른 키를 만든다 — 생략은 "최초 시도"라는 뜻이다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(respond())

    await placeBid(7, { amount: 30_000 })
    await placeBid(7, { amount: 31_000 })

    const keyOf = (i: number) =>
      (post.mock.calls[i][2] as { headers?: Record<string, string> }).headers?.[
        IDEMPOTENCY_KEY_HEADER
      ]
    expect(keyOf(0)).toBeTruthy()
    expect(keyOf(0)).not.toBe(keyOf(1))
  })
})

// BID-03 연장의 **실 모드 경로와 응답 무가공**을 잠근다 (S15P11A704-548).
//
// 목 분기를 걷어낸 함수라 되살아나면 조용히 틀린다 — 목은 잔여를 늘 2/3회로 돌려줘서
// 한도 소진(`EXTENSION_LIMIT_EXCEEDED`)도 마감 임박(`EXTENSION_TOO_LATE`)도 화면에서 재현되지 않는다.
// 콘솔의 잔여 표시와 버튼 잠금이 이 응답만 보고 판정하므로 가공도 하지 않는다.

function extendResponse() {
  return {
    data: {
      auctionId: 7,
      previousEndAt: '2026-08-01T12:00:00+09:00',
      endAt: '2026-08-01T12:05:00+09:00',
      extensionCount: 1,
      remainingExtensions: 2,
    },
  }
}

describe('extendAuction', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('목 모드에서도 실 호출로 나간다 — baseURL 기준 상대경로다', async () => {
    // `isMockApi()`는 기본값이 「켬」이라 목 분기가 있으면 http를 안 탄다.
    const post = vi.spyOn(http, 'post').mockResolvedValue(extendResponse())

    await extendAuction(7)

    expect(post).toHaveBeenCalledOnce()
    expect(post.mock.calls[0][0]).toBe('/auctions/7/extensions')
  })

  it('본문도 멱등키도 싣지 않는다 — 연장은 항상 5분이고 명세에 키가 없다', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue(extendResponse())

    await extendAuction(7)

    expect(post.mock.calls[0][1]).toBeUndefined()
    const config = post.mock.calls[0][2] as { headers?: Record<string, string> } | undefined
    expect(config?.headers?.[IDEMPOTENCY_KEY_HEADER]).toBeUndefined()
  })

  it('응답을 가공하지 않고 그대로 돌려준다 — 마감 시각·잔여 횟수는 전부 서버 값이다', async () => {
    vi.spyOn(http, 'post').mockResolvedValue(extendResponse())

    expect(await extendAuction(7)).toEqual(extendResponse().data)
  })
})
