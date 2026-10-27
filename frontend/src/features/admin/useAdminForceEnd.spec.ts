import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

// 강제 종료 2단계 규칙을 잠근다 (S15P11A704-525).
//
// **되돌릴 수 없는 동작이라 한 번 더 받는다.** 무장이 새면 클릭 한 번에 남의 경매가 끝나고
// 입찰·예측이 전부 환급된다. `useDealAction.spec.ts`가 같은 규칙을 검증하는 본보기다.

vi.mock('@/shared/api/admin', () => ({
  forceEndAuction: vi.fn(),
}))

import { forceEndAuction } from '@/shared/api/admin'
import type { Id } from '@/shared/api/types'
import { useAdminForceEnd } from './useAdminForceEnd'

const forceEndMock = vi.mocked(forceEndAuction)

function ok() {
  return {
    auctionId: 1,
    status: 'ENDED',
    resultType: 'FORCED',
    endedAt: '2026-07-31T10:40:00Z',
  }
}

function apiError(status: number) {
  return { response: { status } }
}

/**
 * **컴포넌트 안에서 부른다** — Vue는 활성 인스턴스가 없으면 `onUnmounted` 등록을 조용히
 * 건너뛴다. 밖에서 부르면 경고도 없이 `cancelled` 가드가 통째로 죽은 코드가 된다.
 * `useDealAction.spec.ts`의 헬퍼와 같다.
 */
function setup(remove: (auctionId: Id) => void = () => {}) {
  let action!: ReturnType<typeof useAdminForceEnd>
  const wrapper = mount(
    defineComponent({
      setup() {
        action = useAdminForceEnd(remove)
        return () => null
      },
    }),
  )
  return { action, wrapper }
}

/** 응답을 붙잡아 둔다. 비행 중 상태를 검사하려면 필요하다. 반환값을 부르면 성공으로 끝난다 */
function deferForceEnd() {
  let settle!: (value: ReturnType<typeof ok>) => void
  forceEndMock.mockReturnValue(
    new Promise<ReturnType<typeof ok>>((resolve) => {
      settle = resolve
    }),
  )
  return () => settle(ok())
}

describe('useAdminForceEnd', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('첫 클릭은 무장만 하고 서버를 부르지 않는다', () => {
    const { action } = setup()

    action.arm(1)

    expect(action.armedId.value).toBe(1)
    expect(forceEndMock).not.toHaveBeenCalled()
  })

  it('무장된 행을 한 번 더 누르면 그때 종료한다', async () => {
    forceEndMock.mockResolvedValue(ok())
    const { action } = setup()

    action.arm(1)
    await action.confirm(1)

    expect(forceEndMock).toHaveBeenCalledWith(1, { reason: 'ADMIN_FORCE_END' })
  })

  it('서버로 나가는 사유는 운영 고정값이다 — 신고 사유가 실리지 않는다', async () => {
    // 이 값은 `AUCTION_CANCELED`로 `/topic/auctions/{id}`에 실려 **그 방 시청자 전원**에게 간다.
    // `PROHIBITED_ITEM` 같은 신고 사유가 들어가면 판매자 혐의가 시청자 브라우저에 도착한다.
    forceEndMock.mockResolvedValue(ok())
    const { action } = setup()

    action.arm(1)
    await action.confirm(1)

    const [, request] = forceEndMock.mock.calls[0]!
    expect(request.reason).toBe('ADMIN_FORCE_END')
    // 신고 사유 코드가 어떤 경로로도 섞이지 않는지 값 전체로 확인한다
    expect(JSON.stringify(request)).not.toMatch(/PROHIBITED_ITEM|FALSE_LISTING|INAPPROPRIATE/)
  })

  it('무장되지 않은 행은 confirm으로도 종료되지 않는다', async () => {
    const { action } = setup()

    await action.confirm(1)

    expect(forceEndMock).not.toHaveBeenCalled()
  })

  it('다른 행을 무장하면 앞 행의 무장이 풀린다', () => {
    // 두 행이 동시에 무장돼 있으면 각각 한 번씩 눌러 둘 다 끝난다 —
    // 운영자는 2단계를 거쳤다고 느끼지 못한다 (useDealAction I-382와 같은 사고).
    const { action } = setup()

    action.arm(1)
    action.arm(2)

    expect(action.armedId.value).toBe(2)
  })

  it('5초가 지나면 무장이 자동으로 풀린다', () => {
    const { action } = setup()

    action.arm(1)
    vi.advanceTimersByTime(5_000)

    expect(action.armedId.value).toBeNull()
  })

  it('성공하면 그 행만 목록에서 지운다 — 전체를 다시 부르지 않는다', async () => {
    forceEndMock.mockResolvedValue(ok())
    const removed: number[] = []
    const { action } = setup((id) => removed.push(Number(id)))

    action.arm(1)
    await action.confirm(1)

    expect(removed).toEqual([1])
    expect(action.armedId.value).toBeNull()
  })

  it('409면 이미 끝났거나 삭제된 방이라 행을 지운다', async () => {
    // 다른 운영자가 먼저 눌렀거나, 정상 종료됐거나, 방이 삭제됐다 —
    // BE가 `deleted() || !endable(status)`를 한 코드로 묶어 셋이 다 409로 온다(MR !246).
    // 목록에 남겨두면 계속 눌러도 계속 409다.
    forceEndMock.mockRejectedValue(apiError(409))
    const removed: number[] = []
    const { action } = setup((id) => removed.push(Number(id)))

    action.arm(1)
    await action.confirm(1)

    expect(removed).toEqual([1])
    expect(action.error.value).toBe('이미 종료됐거나 삭제된 경매예요.')
  })

  it('404도 행을 지우되 **다른 문구**를 쓴다', async () => {
    // 「이미 끝났다」와 「그런 경매가 없다」는 운영자의 다음 행동이 다르다 —
    // 한 문구로 합치면 목록이 낡은 것을 이미 종료된 것으로 오인한다 (디자인 목업 「오류 처리」 표).
    forceEndMock.mockRejectedValue(apiError(404))
    const removed: number[] = []
    const { action } = setup((id) => removed.push(Number(id)))

    action.arm(1)
    await action.confirm(1)

    expect(removed).toEqual([1])
    expect(action.error.value).toBe('경매를 찾을 수 없어요.')
  })

  it('맥락이 바뀌면 화면이 오류를 지울 수 있다', async () => {
    // 안 지우면 `errorId`(=auctionId)가 남아 **다른 목록의 같은 id 행**에 잘못 붙는다.
    forceEndMock.mockRejectedValue(apiError(500))
    const { action } = setup()

    action.arm(1)
    await action.confirm(1)
    expect(action.errorId.value).toBe(1)

    action.clearError()

    expect(action.error.value).toBeNull()
    expect(action.errorId.value).toBeNull()
  })

  it('그 밖의 실패는 행을 남기고 무장만 푼다', async () => {
    forceEndMock.mockRejectedValue(apiError(500))
    const removed: number[] = []
    const { action } = setup((id) => removed.push(Number(id)))

    action.arm(1)
    await action.confirm(1)

    expect(removed).toEqual([])
    expect(action.armedId.value).toBeNull()
    expect(action.errorId.value).toBe(1)
  })

  it('요청이 나가 있는 동안에는 같은 행도 다른 행도 무장되지 않는다', async () => {
    // 무장이 다시 열리면 응답 전에 confirm이 또 통과해 **같은 경매가 두 번 강제 종료된다.**
    // 화면에서 `pendingId`로 버튼을 잠그는 것으로는 못 막는다 — 두 요청이 겹칠 때
    // 먼저 온 응답의 `finally`가 아직 비행 중인 쪽의 잠금까지 풀어 버린다(마지막-쓰기-승리).
    const settle = deferForceEnd()
    const { action } = setup()

    action.arm(1)
    const inFlight = action.confirm(1)
    expect(action.pendingId.value).toBe(1)

    action.arm(1)
    expect(action.armedId.value).toBeNull()
    action.arm(2)
    expect(action.armedId.value).toBeNull()

    settle()
    await inFlight

    expect(forceEndMock).toHaveBeenCalledTimes(1)
  })

  it('응답 전에 화면을 떠나면 뒤늦은 remove가 다음 화면을 건드리지 않는다', async () => {
    const settle = deferForceEnd()
    const removed: number[] = []
    const { action, wrapper } = setup((id) => removed.push(Number(id)))

    action.arm(1)
    const inFlight = action.confirm(1)
    wrapper.unmount()
    settle()
    await inFlight

    expect(removed).toEqual([])
  })
})
