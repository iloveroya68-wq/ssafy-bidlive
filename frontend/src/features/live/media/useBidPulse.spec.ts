import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref, watch, type Ref } from 'vue'
import { useBidPulse, type BidPulse } from './useBidPulse'

// BID-04 입찰 펄스 타이밍. 여기서 잠그는 것들은 **초당 몇 건이 몰아칠 때만** 드러나서
// 손으로는 재현하기 어렵다 — 실서버에서 그 상황은 마감 직전 몇십 초뿐이다.
//
// 조용히 깨질 수 있는 지점:
//  1. 쿨다운이 없으면 망치가 매번 처음으로 되돌아가 떨기만 한다(내려치지 못한다).
//  2. 합치기가 깨지면 건수가 새거나(입찰이 사라진다) 큐가 밀려 입찰이 끝난 뒤에도 계속 두들긴다.
//  3. 숨은 탭에서 쌓아 뒀다 몰아치면 지나간 일을 지금처럼 그린다.
//  4. 타이머를 안 치우면 화면을 떠난 뒤에도 계속 돈다.

function start(feed: Ref<string[]>) {
  const scope = effectScope()
  const state = scope.run(() => useBidPulse(() => feed.value))!
  const seen: BidPulse[] = []
  // 스코프 밖에서 sync로 본다 — 스코프를 멈춘 뒤에도(dispose 테스트) 관측이 남아야 한다.
  watch(state.pulse, (p) => p !== null && seen.push(p), { flush: 'sync' })
  return { scope, seen, ...state }
}

/** 서버 이벤트처럼 **새 배열로** 교체한다 — push로 밀면 참조가 그대로여서 실제 흐름과 달라진다. */
async function bid(feed: Ref<string[]>) {
  feed.value = [...feed.value, '누군가 베팅했어요']
  await nextTick()
}

let feed: Ref<string[]>

beforeEach(() => {
  vi.useFakeTimers()
  feed = ref<string[]>([])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useBidPulse 합치기', () => {
  it('쿨다운 안에 10건이 몰려도 타격은 한 번, 대신 +10으로 친다', async () => {
    // 이게 없으면 10번을 다 치려다 애니메이션이 서로를 끊어 화면이 무너진다.
    // 건수는 하나도 안 버린다 — 화면이 접히는 것이지 사실이 접히는 게 아니다.
    const { seen } = start(feed)
    await bid(feed) // 첫 건은 즉시 (여기서 700ms 쿨다운 시작)
    for (let i = 0; i < 10; i++) {
      await bid(feed)
      await vi.advanceTimersByTimeAsync(50)
    }

    expect(seen).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(700)

    expect(seen.map((p) => p.count)).toEqual([1, 10])
  })

  it('쿨다운 중에 온 입찰은 즉시 치지 않는다', async () => {
    const { seen } = start(feed)
    await bid(feed)
    await vi.advanceTimersByTimeAsync(200)
    await bid(feed)

    expect(seen).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(500) // 쿨다운 끝

    expect(seen).toHaveLength(2)
  })

  it('1초 동안 10건이 와도 초당 1.4회를 넘지 않고, 건수는 하나도 안 샌다', async () => {
    const { seen } = start(feed)
    for (let i = 0; i < 10; i++) {
      await bid(feed)
      await vi.advanceTimersByTimeAsync(100)
    }
    await vi.advanceTimersByTimeAsync(700) // 마지막 쿨다운까지 비운다

    expect(seen.map((p) => p.count)).toEqual([1, 6, 3])
    expect(seen.reduce((sum, p) => sum + p.count, 0)).toBe(10)
  })

  it('빈 배열로의 교체는 입찰이 아니다 — 방을 나갈 때($reset) 망치가 울리면 안 된다', async () => {
    const { seen } = start(feed)
    await bid(feed)
    feed.value = []
    await nextTick()
    await vi.advanceTimersByTimeAsync(2000)

    expect(seen).toHaveLength(1)
  })
})

describe('useBidPulse 과열', () => {
  it('한 번에 3건 이상 뭉치면 과열이다', async () => {
    const { seen, hot } = start(feed)
    await bid(feed) // 즉시 타격 1건 — 아직 평소
    expect(hot.value).toBe(false)

    await bid(feed)
    await bid(feed)
    await bid(feed)
    await vi.advanceTimersByTimeAsync(700)

    expect(seen[1].count).toBe(3)
    expect(seen[1].hot).toBe(true)
    expect(hot.value).toBe(true)
  })

  it('한 건씩이라도 5초 안에 세 번 치면 과열이다', async () => {
    // 순간 폭주가 아니라 지속 경합. 건수만 보면 영원히 1이라 과열을 못 잡는다.
    const { seen, hot } = start(feed)
    await bid(feed)
    await vi.advanceTimersByTimeAsync(750)
    await bid(feed)
    await vi.advanceTimersByTimeAsync(750)
    await bid(feed)

    expect(seen.map((p) => p.count)).toEqual([1, 1, 1])
    expect(seen[2].hot).toBe(true)
    expect(hot.value).toBe(true)
  })

  it('마지막 입찰로부터 5초 뒤 식는다', async () => {
    const { hot, streak } = start(feed)
    await bid(feed)
    await bid(feed)
    await bid(feed)
    await bid(feed)
    await vi.advanceTimersByTimeAsync(700)
    expect(hot.value).toBe(true)
    expect(streak.value).toBe(4)

    await vi.advanceTimersByTimeAsync(5000)

    expect(hot.value).toBe(false)
    expect(streak.value).toBe(0)
  })
})

describe('useBidPulse 숨은 탭', () => {
  function setHidden(hidden: boolean) {
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
  }

  afterEach(() => {
    Reflect.deleteProperty(document, 'hidden')
  })

  it('안 보이는 동안에는 치지 않고, 돌아와도 밀린 것을 몰아치지 않는다', async () => {
    // 돌아온 순간 「+37」이 터지면 그건 지금 벌어지는 일이 아니라 지나간 일이다.
    const { seen } = start(feed)
    setHidden(true)
    for (let i = 0; i < 5; i++) await bid(feed)
    await vi.advanceTimersByTimeAsync(3000)

    expect(seen).toHaveLength(0)

    setHidden(false)
    await vi.advanceTimersByTimeAsync(3000)

    expect(seen).toHaveLength(0) // 복귀만으로는 아무 일도 일어나지 않는다

    await bid(feed)

    expect(seen.map((p) => p.count)).toEqual([1]) // 다음 진짜 입찰만, 평소대로
  })
})

describe('useBidPulse 정리', () => {
  it('스코프가 끝나면 예약된 타이머가 남지 않는다', async () => {
    const { scope } = start(feed)
    await bid(feed)
    expect(vi.getTimerCount()).toBeGreaterThan(0)

    scope.stop()

    expect(vi.getTimerCount()).toBe(0)
  })
})
