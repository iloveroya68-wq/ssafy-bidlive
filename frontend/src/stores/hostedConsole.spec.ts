import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useHostedConsoleStore } from './hostedConsole'
import { http } from '@/shared/api/http'

// 헤더의 「진행 콘솔」 바로가기가 가리킬 방을 고르는 로직 (S15P11A704-692).
// 깨져도 화면은 멀쩡하고 링크만 사라지거나 엉뚱한 방을 가리켜서 눈으로는 잘 안 잡힌다.
// 넷을 잠근다 — ① 끝난 방을 고르지 않는다 ② 방송 중이 예정보다 먼저다
// ③ 열린 방이 없을 때도 `loaded`가 서서 내비게이션마다 조회가 나가지 않는다 ④ 실패해도 안 죽는다.

function page(items: Array<{ auctionId: number; roomStatus: string }>) {
  return { data: { items, nextCursor: null, hasNext: false } }
}

describe('useHostedConsoleStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('끝난 방은 고르지 않는다 — 콘솔은 종료된 방을 받지 않는다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      page([
        { auctionId: 10, roomStatus: 'ENDED' },
        { auctionId: 11, roomStatus: 'WAITING' },
      ]),
    )
    const store = useHostedConsoleStore()

    await store.load()

    expect(store.roomId).toBe(11)
    expect(store.isLive).toBe(false)
  })

  it('방송 중인 방이 예정된 방보다 먼저다 — 판매자가 찾는 건 지금 도는 방이다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(
      page([
        { auctionId: 20, roomStatus: 'WAITING' },
        { auctionId: 21, roomStatus: 'LIVE' },
      ]),
    )
    const store = useHostedConsoleStore()

    await store.load()

    expect(store.roomId).toBe(21)
    expect(store.isLive).toBe(true)
  })

  /**
   * `roomId === null`을 「아직 안 불렀다」로 읽으면, 열어 둔 방이 없는 대부분의 사용자에게
   * **내비게이션마다 조회가 나간다.** 헤더가 그 값을 재조회 조건으로 쓰므로 여기서 잠근다.
   */
  it('열린 방이 없어도 loaded는 선다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(page([{ auctionId: 30, roomStatus: 'ENDED' }]))
    const store = useHostedConsoleStore()

    await store.load()

    expect(store.roomId).toBeNull()
    expect(store.loaded).toBe(true)
  })

  it('조회가 실패해도 던지지 않는다 — 링크가 안 뜰 뿐이고 그건 이 기능 이전과 같다', async () => {
    vi.spyOn(http, 'get').mockRejectedValue(new Error('boom'))
    const store = useHostedConsoleStore()

    await expect(store.load()).resolves.toBeUndefined()
    expect(store.roomId).toBeNull()
    expect(store.loaded).toBe(true)
  })

  it('겹쳐 불러도 조회는 한 번만 나간다', async () => {
    const spy = vi.spyOn(http, 'get').mockResolvedValue(page([]))
    const store = useHostedConsoleStore()

    await Promise.all([store.load(), store.load(), store.load()])

    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('clear는 다음 회원 기준으로 다시 조회하도록 loaded까지 내린다', async () => {
    vi.spyOn(http, 'get').mockResolvedValue(page([{ auctionId: 40, roomStatus: 'LIVE' }]))
    const store = useHostedConsoleStore()
    await store.load()

    store.clear()

    expect(store.roomId).toBeNull()
    expect(store.loaded).toBe(false)
  })

  /**
   * 헤더는 「방 경로를 빠져나올 때」만 재조회하는데 마감은 서버가 하는 일이라 그 내비게이션과
   * 무관하다 — 방 밖에 있는 동안 타이머로 마감되면 링크가 영영 안 사라진다 (S15P11A704-698).
   * 고치면서 **열어 둔 방이 없는 사용자에게 조회가 새어 나가지 않는지**를 같이 잠근다.
   */
  describe('refreshIfStale', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    it('열어 둔 방이 없으면 조회하지 않는다 — 대부분의 사용자에게 비용이 0이다', async () => {
      const spy = vi.spyOn(http, 'get').mockResolvedValue(page([]))
      const store = useHostedConsoleStore()
      await store.load()
      spy.mockClear()

      vi.advanceTimersByTime(60_000)
      await store.refreshIfStale()

      expect(spy).not.toHaveBeenCalled()
    })

    it('링크가 떠 있어도 쓰로틀 안에서는 조회하지 않는다 — 내비게이션마다 나가면 안 된다', async () => {
      const spy = vi
        .spyOn(http, 'get')
        .mockResolvedValue(page([{ auctionId: 50, roomStatus: 'LIVE' }]))
      const store = useHostedConsoleStore()
      await store.load()
      spy.mockClear()

      vi.advanceTimersByTime(29_000)
      await store.refreshIfStale()

      expect(spy).not.toHaveBeenCalled()
    })

    it('쓰로틀이 지나면 다시 조회해 마감된 방을 떨군다', async () => {
      const spy = vi
        .spyOn(http, 'get')
        .mockResolvedValueOnce(page([{ auctionId: 50, roomStatus: 'LIVE' }]))
        .mockResolvedValueOnce(page([{ auctionId: 50, roomStatus: 'ENDED' }]))
      const store = useHostedConsoleStore()
      await store.load()

      vi.advanceTimersByTime(31_000)
      await store.refreshIfStale()

      expect(spy).toHaveBeenCalledTimes(2)
      expect(store.roomId).toBeNull()
    })
  })
})
