import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { offsetFrom, useRoomStore } from './room'
import * as roomsApi from '@/shared/api/rooms'
import { http } from '@/shared/api/http'
import { roomListResponse, roomDetailResponse } from '@/mocks/roomServer'

/** ROOM-08 곧 시작 경매 응답. 서버가 최대 5개로 잘라 주고 초과분은 `hiddenCount`로 알려준다 */
const UPCOMING_RESPONSE = {
  items: [
    {
      auctionId: 21,
      productTitle: '키크론 Q1 Pro',
      imageUrl: null,
      scheduledAt: '2026-08-01T21:30:00+09:00',
      waitingCount: 18,
      status: 'WAITING',
    },
  ],
  totalCount: 4,
  hiddenCount: 3,
}

/**
 * **API 함수가 아니라 `http`를 스텁한다.** 아래 테스트들이 `getAuctionRooms` 호출 횟수를 직접
 * 세고(`toHaveBeenCalledTimes` / `not.toHaveBeenCalled`), 어떤 테스트는 원본 구현을 붙잡아
 * (`const real = roomsApi.getAuctionRooms`) 지연만 끼워 넣는다 — API 함수를 미리 스파이해 두면
 * 그 카운트가 오염되고 원본도 사라진다.
 */
function stubRoomHttp() {
  vi.spyOn(http, 'get').mockImplementation((async (url: string, config?: { params?: object }) => {
    if (url === '/auction-rooms') return { data: roomListResponse(config?.params ?? {}) }
    if (url === '/auction-rooms/upcoming') return { data: UPCOMING_RESPONSE }
    const detail = /^\/auction-rooms\/(\d+)$/.exec(url)
    if (detail) return { data: roomDetailResponse(Number(detail[1])) }
    throw new Error(`가짜 서버에 없는 경로: GET ${url}`)
  }) as never)
  vi.spyOn(http, 'post').mockImplementation((async (url: string) => {
    if (url === '/auction-rooms') {
      return {
        data: {
          auctionId: 101,
          productId: 1,
          sellerId: 25,
          status: 'WAITING',
          reservePrice: 50_000,
          durationMinutes: 15,
          startType: 'IMMEDIATE',
          scheduledAt: null,
          createdAt: '2026-07-29T00:00:00+09:00',
        },
      }
    }
    throw new Error(`가짜 서버에 없는 경로: POST ${url}`)
  }) as never)
}

// 목록이 **전량 로딩에서 커서 페이지로** 바뀌었다 (S15P11A704-396).
// 여기서 조용히 깨지는 것 셋을 잠근다:
//   ① 탭을 빠르게 바꾸면 먼저 보낸 조회가 나중에 도착해 **이전 탭 결과가 그려진다**
//   ② 다음 페이지를 기다리는 사이 탭이 바뀌면 **다른 탭의 방이 이어 붙는다**
//   ③ 목록에 없는 방을 딥링크로 열면 하한가가 폴백으로 떨어져 입찰 검증 기준이 틀어진다
//
// 셋 다 화면은 멀쩡하게 그려진다 — 카드가 몇 장 나오느냐만 달라서 눈으로 못 잡는다.

describe('useRoomStore.loadRooms', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('조건을 서버 쿼리로 넘긴다 — 클라이언트에서 거르지 않는다', async () => {
    const spy = vi.spyOn(roomsApi, 'getAuctionRooms')
    const store = useRoomStore()

    await store.loadRooms({ status: 'ENDED', categoryId: 3 })

    expect(spy).toHaveBeenCalledWith(expect.objectContaining({ status: 'ENDED', categoryId: 3 }))
    expect(store.rooms.every((r) => r.status === 'ENDED')).toBe(true)
  })

  it('첫 페이지는 한 번만 조회한다 — 예전처럼 커서를 끝까지 따라가지 않는다', async () => {
    const spy = vi.spyOn(roomsApi, 'getAuctionRooms')
    const store = useRoomStore()

    await store.loadRooms({ status: 'ENDED' })

    expect(spy).toHaveBeenCalledTimes(1)
    // 목 종료 방이 9개라 8건 페이지 기준 첫 장은 8개 + 다음 장이 있다
    expect(store.rooms).toHaveLength(8)
    expect(store.hasNext).toBe(true)
  })

  it('다음 페이지는 앞 페이지를 지우지 않고 이어 붙인다', async () => {
    const store = useRoomStore()
    await store.loadRooms({ status: 'ENDED' })
    const firstIds = store.rooms.map((r) => r.auctionId)

    await store.loadMoreRooms()

    const allIds = store.rooms.map((r) => r.auctionId)
    expect(allIds.slice(0, firstIds.length)).toEqual(firstIds)
    expect(allIds).toHaveLength(9)
    // 같은 방이 두 번 들어가면 안 된다
    expect(new Set(allIds).size).toBe(allIds.length)
    expect(store.hasNext).toBe(false)
  })

  it('마지막 페이지에서 또 불러도 요청이 안 나간다', async () => {
    const store = useRoomStore()
    await store.loadRooms({ status: 'ENDED' })
    await store.loadMoreRooms()
    const spy = vi.spyOn(roomsApi, 'getAuctionRooms')

    await store.loadMoreRooms()

    expect(spy).not.toHaveBeenCalled()
  })

  it('상태별 정렬은 서버 것을 그대로 쓴다 — ENDED는 종료 최신순', async () => {
    // 화면이 다시 세우면 페이지 경계에서 어긋나므로, 순서는 응답 순서가 정본이어야 한다
    const store = useRoomStore()

    await store.loadRooms({ status: 'ENDED' })

    const endedAt = store.rooms.map((r) => Date.parse(r.endedAt ?? ''))
    expect(endedAt).toEqual([...endedAt].sort((a, b) => b - a))
  })

  describe('탭 카운트 — S15P11A704-417', () => {
    it('status는 집계에서 무시된다 — 라이브 탭에서도 대기 숫자를 그려야 한다', async () => {
      const store = useRoomStore()

      await store.loadRooms({ status: 'LIVE' })

      // 목 방: live 4 · waiting 3 (종료는 집계에 없다)
      expect(store.counts).toEqual({ LIVE: 4, WAITING: 3 })
    })

    it('종료 탭으로 조회해도 진행중·대기 숫자가 온다', async () => {
      const store = useRoomStore()

      await store.loadRooms({ status: 'ENDED' })

      expect(store.counts).toEqual({ LIVE: 4, WAITING: 3 })
    })

    it('categoryId는 집계에도 적용된다 — 안 그러면 칩을 켠 숫자가 카드 수와 안 맞는다', async () => {
      const store = useRoomStore()

      // 스포츠·레저(categoryId 6)에는 종료 방만 2개 있다
      await store.loadRooms({ status: 'ENDED', categoryId: 6 })

      expect(store.counts).toEqual({ LIVE: 0, WAITING: 0 })
      expect(store.rooms).toHaveLength(2)
    })

    it('다음 페이지를 받아도 카운트는 그대로다 — 페이지가 아니라 전체 개수다', async () => {
      const store = useRoomStore()
      await store.loadRooms({ status: 'ENDED' })
      const before = { ...store.counts }

      await store.loadMoreRooms()

      expect(store.counts).toEqual(before)
      // 카드는 늘어났는데 숫자는 그대로여야 한다
      expect(store.rooms.length).toBeGreaterThan(8)
    })

    it('카테고리가 바뀌면 카운트를 비운다 — 이전 조건 숫자가 잠깐 보이면 카드와 안 맞는다', async () => {
      const store = useRoomStore()
      await store.loadRooms({ status: 'ENDED' })
      expect(store.counts).not.toBeNull()

      const pending = store.loadRooms({ status: 'ENDED', categoryId: 6 })
      expect(store.counts).toBeNull()

      await pending
    })

    it('상태만 바뀌면 카운트를 비우지 않는다 — 받아올 숫자가 직전과 같다', async () => {
      // 명세: "요청의 `status`는 `counts` 집계에서 무시한다".
      // 비우면 같은 값이 사라졌다 돌아오는 깜빡임만 생긴다 (카드는 실제로 바뀌므로 비운다)
      const store = useRoomStore()
      await store.loadRooms({ status: 'LIVE' })
      const before = { ...store.counts }

      const pending = store.loadRooms({ status: 'ENDED' })
      expect(store.rooms).toHaveLength(0)
      expect(store.counts).toEqual(before)

      await pending
      expect(store.counts).toEqual(before)
    })

    it('서버가 counts를 안 줘도 깨지지 않는다 — 명세는 확정이지만 구현은 아직이다', async () => {
      const real = roomsApi.getAuctionRooms
      vi.spyOn(roomsApi, 'getAuctionRooms').mockImplementation(async (query = {}) => {
        const { counts: _counts, ...rest } = await real(query)
        return rest
      })
      const store = useRoomStore()

      await store.loadRooms({ status: 'LIVE' })

      expect(store.counts).toBeNull()
      expect(store.rooms.length).toBeGreaterThan(0)
    })
  })

  describe('질의 전환', () => {
    it('조건이 바뀌면 응답을 기다리는 동안 이전 목록을 그리지 않는다', async () => {
      // 안 비우면 종료 탭을 눌러도 진행중 카드가 그대로 보인다 —
      // 사용자에겐 "탭이 안 먹었다"가 아니라 **잘못된 탭 내용**이다
      const store = useRoomStore()
      await store.loadRooms({ status: 'LIVE' })
      expect(store.rooms.length).toBeGreaterThan(0)

      const pending = store.loadRooms({ status: 'ENDED' })
      expect(store.rooms).toHaveLength(0)
      expect(store.loading).toBe(true)

      await pending
      expect(store.rooms.every((r) => r.status === 'ENDED')).toBe(true)
    })

    it('같은 조건을 다시 받을 때는 비우지 않는다 — 멀쩡한 화면을 깜빡이게 할 뿐이다', async () => {
      // 홈은 마운트마다 `status=LIVE`를 다시 건다. 매번 비우면 히어로가 깜빡인다
      const store = useRoomStore()
      await store.loadRooms({ status: 'LIVE' })
      const before = store.rooms.length

      const pending = store.loadRooms({ status: 'LIVE' })
      expect(store.rooms).toHaveLength(before)

      await pending
    })

    it('카테고리만 바뀌어도 비운다', async () => {
      const store = useRoomStore()
      await store.loadRooms({ status: 'ENDED' })

      const pending = store.loadRooms({ status: 'ENDED', categoryId: 6 })
      expect(store.rooms).toHaveLength(0)

      await pending
    })
  })

  describe('요청 경합', () => {
    it('늦게 도착한 이전 탭 결과가 지금 탭을 덮지 않는다', async () => {
      // 탭을 빠르게 바꾸면 실제로 생긴다. 덮이면 "종료를 눌렀는데 진행중이 보인다"
      const real = roomsApi.getAuctionRooms
      vi.spyOn(roomsApi, 'getAuctionRooms').mockImplementation(async (query = {}) => {
        const page = await real(query)
        // 먼저 보낸 LIVE 조회만 느리게 만든다
        if (query.status === 'LIVE') await new Promise((r) => setTimeout(r, 40))
        return page
      })
      const store = useRoomStore()

      const slow = store.loadRooms({ status: 'LIVE' })
      const fast = store.loadRooms({ status: 'ENDED' })
      await Promise.all([slow, fast])

      expect(store.rooms.every((r) => r.status === 'ENDED')).toBe(true)
      expect(store.activeQuery.status).toBe('ENDED')
    })

    it('다음 페이지를 기다리는 사이 탭이 바뀌면 이어 붙이지 않는다', async () => {
      const store = useRoomStore()
      await store.loadRooms({ status: 'ENDED' })

      // **더 보기 요청만 느리게** 만든다. 안 그러면 더 보기가 탭 전환보다 먼저 끝나서
      // 경합 자체가 일어나지 않고, 가드를 지워도 통과하는 공허한 테스트가 된다.
      const real = roomsApi.getAuctionRooms
      vi.spyOn(roomsApi, 'getAuctionRooms').mockImplementation(async (query = {}) => {
        const page = await real(query)
        if (query.cursor !== undefined) await new Promise((r) => setTimeout(r, 40))
        return page
      })

      const more = store.loadMoreRooms()
      await store.loadRooms({ status: 'LIVE' })
      await more

      // 종료 방이 진행중 목록에 섞여 있으면 안 된다
      expect(store.rooms.every((r) => r.status === 'LIVE')).toBe(true)
    })
  })

  it('조회가 실패하면 loadError에 담고 loaded를 세우지 않는다', async () => {
    vi.spyOn(roomsApi, 'getAuctionRooms').mockRejectedValue(new Error('boom'))
    const store = useRoomStore()

    await store.loadRooms()

    expect(store.loaded).toBe(false)
    expect(store.loadError).not.toBeNull()
  })

  it('더 보기가 실패해도 이미 보이는 목록은 지우지 않는다', async () => {
    const store = useRoomStore()
    await store.loadRooms({ status: 'ENDED' })
    const before = store.rooms.length
    vi.spyOn(roomsApi, 'getAuctionRooms').mockRejectedValue(new Error('boom'))

    await store.loadMoreRooms()

    expect(store.rooms).toHaveLength(before)
    expect(store.loadError).not.toBeNull()
  })
})

// ROOM-08 곧 시작 경매 (S15P11A704-542).
//
// 스트립은 목록 위에 얹힌 **보조 정보**다. 실패를 `loadError`로 올리면 멀쩡한 카드 그리드 위에
// 빨간 문구가 뜨고 사용자는 목록이 깨진 줄 안다 — 조용히 비우는 쪽이 맞고, 그게 조용한 만큼
// 반대로 뒤집혀도 티가 안 난다.
describe('useRoomStore.loadUpcoming', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('서버가 준 항목과 잘린 개수를 그대로 담는다 — 다시 자르지도 세우지도 않는다', async () => {
    const store = useRoomStore()

    await store.loadUpcoming()

    expect(store.upcoming.map((r) => r.auctionId)).toEqual([21])
    expect(store.upcomingHidden).toBe(3)
  })

  it('실패하면 조용히 비운다 — 목록 오류 문구를 건드리지 않는다', async () => {
    const store = useRoomStore()
    await store.loadUpcoming()
    expect(store.upcoming).toHaveLength(1)
    vi.spyOn(roomsApi, 'getUpcomingAuctionRooms').mockRejectedValue(new Error('boom'))

    await store.loadUpcoming()

    expect(store.upcoming).toHaveLength(0)
    expect(store.upcomingHidden).toBe(0)
    expect(store.loadError).toBeNull()
  })

  it('목록 조회는 스트립을 건드리지 않는다 — 부르는 쪽은 스트립을 그리는 화면이다', async () => {
    // 예전에는 `loadRooms(status=LIVE)`가 같이 걸어서 스트립이 없는 홈에서도 요청이 나갔다
    const spy = vi.spyOn(roomsApi, 'getUpcomingAuctionRooms')
    const store = useRoomStore()

    await store.loadRooms({ status: 'LIVE' })

    expect(spy).not.toHaveBeenCalled()
  })
})

describe('useRoomStore.loadRoomDetail', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('목록에 없는 방도 딥링크로 찾을 수 있다', async () => {
    // 목록이 "현재 페이지"가 되면서 `rooms.find`가 못 찾는 경우가 기본 동선이 됐다.
    // 못 찾으면 LiveView가 하한가 30,000P 폴백으로 떨어져 입찰 검증 기준이 틀어진다.
    const store = useRoomStore()
    await store.loadRooms({ status: 'LIVE' })
    expect(store.rooms.some((r) => r.auctionId === 9)).toBe(false)

    await store.loadRoomDetail(9)

    expect(store.detailOf(9)?.reservePrice).toBeGreaterThan(0)
  })

  it('같은 방을 두 번 물어도 조회는 한 번만 나간다', async () => {
    const store = useRoomStore()
    await store.loadRoomDetail(1)
    const spy = vi.spyOn(roomsApi, 'getAuctionRoom')

    await store.loadRoomDetail(1)

    expect(spy).not.toHaveBeenCalled()
  })
})

// ROOM-01 방 개설 (S15P11A704-222).
//
// 스토어가 `setTimeout` 뒤에 방을 **지어내고** 있었다. 목 모드에서는 그럴듯해 보였지만
// 실 모드에서는 서버에 아무것도 안 나갔고, 지어낸 상태(`LIVE`)가 명세와 정반대였다.

function draft(over: Partial<import('./room').ProductImageDraft> = {}) {
  return { id: 'a', imageUrl: 'https://cdn/a.jpg', sortOrder: 0, isThumbnail: false, ...over }
}

function payload(
  over: { startType?: 'IMMEDIATE' | 'SCHEDULED'; scheduledAt?: string | null } = {},
) {
  return {
    product: {
      title: '기계식 키보드',
      description: '설명',
      categoryCode: 'ELECTRONICS',
      conditionStatus: null,
      images: [draft(), draft({ id: 'b', imageUrl: 'https://cdn/b.jpg', isThumbnail: true })],
    },
    room: {
      reservePrice: 50_000,
      durationMinutes: 15,
      startType: over.startType ?? 'IMMEDIATE',
      scheduledAt: over.scheduledAt ?? null,
    },
  } as Parameters<ReturnType<typeof useRoomStore>['createRoom']>[0]
}

describe('useRoomStore.createRoom', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('계약 레이어를 거친다 — 예전엔 서버에 아무것도 안 나갔다', async () => {
    const spy = vi.spyOn(roomsApi, 'createAuctionRoom')
    const store = useRoomStore()

    await store.createRoom(payload())

    expect(spy).toHaveBeenCalledOnce()
  })

  it('카테고리는 코드로, 이미지는 대표 한 장만 보낸다', async () => {
    // 명세의 `product.imageUrl`은 단일 값이다 — 여러 장을 올려도 하나만 나간다
    const spy = vi.spyOn(roomsApi, 'createAuctionRoom')
    const store = useRoomStore()

    await store.createRoom(payload())

    expect(spy.mock.calls[0][0].product).toMatchObject({
      category: 'ELECTRONICS',
      imageUrl: 'https://cdn/b.jpg',
    })
  })

  it('IMMEDIATE면 scheduledAt을 아예 빼고 보낸다', async () => {
    // 명세 §검증 규칙: "IMMEDIATE이면 scheduledAt을 생략한다"
    const spy = vi.spyOn(roomsApi, 'createAuctionRoom')
    const store = useRoomStore()

    await store.createRoom(payload({ startType: 'IMMEDIATE' }))

    expect(spy.mock.calls[0][0].auction).not.toHaveProperty('scheduledAt')
  })

  it('SCHEDULED면 scheduledAt을 실어 보낸다', async () => {
    const spy = vi.spyOn(roomsApi, 'createAuctionRoom')
    const store = useRoomStore()

    await store.createRoom(payload({ startType: 'SCHEDULED', scheduledAt: '2026-08-01T20:00:00Z' }))

    expect(spy.mock.calls[0][0].auction).toMatchObject({ scheduledAt: '2026-08-01T20:00:00Z' })
  })

  it('목록을 건드리지 않는다 — 서버가 거른 결과에 지어낸 방을 끼우면 필터와 어긋난다', async () => {
    // 예전에는 개설한 방을 목록 앞에 붙였다. 396 이후 `rooms`는 "현재 질의의 서버 결과"라,
    // 새 방(WAITING)을 진행중 탭 목록에 끼우면 서버 필터에 없는 카드가 한 장 생긴다.
    // 게다가 목록 화면은 진입할 때마다 다시 조회해서 그 항목을 곧바로 덮어쓴다.
    const store = useRoomStore()
    await store.loadRooms({ status: 'LIVE' })
    const before = store.rooms.map((r) => r.auctionId)

    await store.createRoom(payload({ startType: 'IMMEDIATE' }))

    expect(store.rooms.map((r) => r.auctionId)).toEqual(before)
  })

  it('결과는 서버가 준 id뿐이다 — 화면이 개설 응답으로 상태를 분기하지 않는다', async () => {
    // 명세: "어떤 시작 방식이든 생성 직후 상태는 WAITING이다.
    //        판매자가 방송 시작 API를 호출하기 전에는 입찰·예측을 받을 수 없다"(ROOM-05)
    vi.spyOn(roomsApi, 'createAuctionRoom').mockResolvedValue({
      auctionId: 7,
      productId: 1,
      sellerId: 25,
      status: 'WAITING',
      reservePrice: 50_000,
      durationMinutes: 15,
      startType: 'IMMEDIATE',
      scheduledAt: null,
      createdAt: '2026-07-29T00:00:00+09:00',
    })
    const store = useRoomStore()

    const result = await store.createRoom(payload({ startType: 'IMMEDIATE' }))

    // `toEqual`이라 필드가 하나라도 늘면 깨진다 — 개설 응답을 화면까지 실어 나르는 걸 막는다
    expect(result).toEqual({ roomId: 7 })
  })

  it('방 id는 서버가 준 값을 쓴다 — 로컬 시퀀스를 지어내지 않는다', async () => {
    vi.spyOn(roomsApi, 'createAuctionRoom').mockResolvedValue({
      auctionId: 4242,
      productId: 1,
      sellerId: 25,
      status: 'WAITING',
      reservePrice: 50_000,
      durationMinutes: 15,
      startType: 'IMMEDIATE',
      scheduledAt: null,
      createdAt: '2026-07-29T00:00:00+09:00',
    })
    const store = useRoomStore()

    const result = await store.createRoom(payload())

    expect(result.roomId).toBe(4242)
  })

  it('실패하면 creating을 내리고 목록을 건드리지 않는다', async () => {
    vi.spyOn(roomsApi, 'createAuctionRoom').mockRejectedValue(new Error('boom'))
    const store = useRoomStore()
    const before = store.rooms.length

    await expect(store.createRoom(payload())).rejects.toThrow()

    expect(store.creating).toBe(false)
    expect(store.rooms).toHaveLength(before)
  })
})

// ROOM-02 명세 2026-07-29 개정 — 목록 응답의 `serverTime`이 목록·홈 카운트다운의 유일한 서버 시각
// 출처다. 예전에는 오프셋 소스가 없어 카드가 단말 시계로 돌았고, 시계가 틀어진 기기에서
// 남은 시간이 그만큼 틀려도 화면은 멀쩡해 보였다.
describe('offsetFrom', () => {
  it('서버 시각과 단말 시각의 차이를 ms로 준다', () => {
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-07-21T10:59:30Z'))

    expect(offsetFrom('2026-07-21T10:59:35Z')).toBe(5_000)
  })

  it('값이 없거나 파싱이 안 되면 null이다 — 0으로 두면 시각이 같다고 지어낸다', () => {
    expect(offsetFrom(undefined)).toBeNull()
    expect(offsetFrom('서버시각아님')).toBeNull()
  })
})

describe('useRoomStore serverOffset', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('목록을 받으면 serverOffset이 채워진다', async () => {
    const store = useRoomStore()
    expect(store.serverOffset).toBeNull()

    await store.loadRooms({ status: 'LIVE' })

    expect(store.serverOffset).not.toBeNull()
  })

  // 목록의 `serverTime`도 상세와 같은 선택 필드다(배포 시점이 엇갈리면 빠진다). 예전에는 목록만
  // 무조건 대입이라 그런 응답 하나가 이미 갖고 있던 오프셋을 지웠다 — 화면 전체가 `--:--`가 된다.
  // 상세 쪽 같은 이름의 테스트와 규칙이 갈리지 않게 `noteServerTime` 한 곳으로 모았다(S15P11A704-265).
  it('목록에 serverTime이 없어도 기존 오프셋을 지우지 않는다', async () => {
    const store = useRoomStore()
    await store.loadRooms({ status: 'LIVE' })
    const before = store.serverOffset
    expect(before).not.toBeNull()

    vi.spyOn(roomsApi, 'getAuctionRooms').mockResolvedValue({
      items: [],
      nextCursor: null,
      hasNext: false,
    })

    await store.loadRooms({ status: 'ENDED' })

    expect(store.serverOffset).toBe(before)
  })
})

// 딥링크 진입(라이브·결과·유찰)은 목록을 거치지 않아 `serverOffset`이 비어 있었다.
// 상세 응답의 `serverTime`(명세 2026-07-29 개정)이 그 자리를 채운다 — 안 채우면 카운트다운이 `--:--`다.
describe('useRoomStore.loadRoomDetail — 서버 시각', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    stubRoomHttp()
  })

  it('목록을 거치지 않아도 상세가 serverOffset을 채운다', async () => {
    const store = useRoomStore()
    expect(store.serverOffset).toBeNull()

    await store.loadRoomDetail(1)

    expect(store.serverOffset).not.toBeNull()
  })

  it('상세에 serverTime이 없으면 기존 오프셋을 지우지 않는다', async () => {
    const store = useRoomStore()
    await store.loadRooms({ status: 'LIVE' })
    const fromList = store.serverOffset
    expect(fromList).not.toBeNull()

    vi.spyOn(roomsApi, 'getAuctionRoom').mockResolvedValue({
      auctionId: 42,
      title: '서버 시각 없는 상세',
      status: 'LIVE',
      reservePrice: 10_000,
      scheduledAt: null,
      durationMinutes: 15,
      viewerCount: 0,
      isSeller: false,
      notificationCount: null,
      product: { productId: 42, title: 't', description: 'd', imageUrl: '' },
      seller: { nickname: '판**', rating: 4.8 },
      canEnter: true,
    })

    await store.loadRoomDetail(42)

    expect(store.serverOffset).toBe(fromList)
  })
})
