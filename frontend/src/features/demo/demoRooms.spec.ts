import { describe, expect, it } from 'vitest'
import { demoLiveRooms, demoWaitingRooms, isDemoRoom, withDemoRooms } from './demoRooms'
import type { AuctionRoomSummary } from '@/stores/room'

// 시연 더미의 안전장치를 잠근다. 셋 다 깨져도 화면은 멀쩡해 보이고, 그래서 시연 당일에야 드러난다.
//   ① 진짜 방이 항상 앞이다 — 앞에 끼면 새로 만든 방이 더미에 밀려 안 보인다
//   ② 더미는 판별 가능하다 — 카드 클릭이 이걸로 라우팅을 막는다
//   ③ 끝난 방에는 안 붙는다 — 결과 화면과 이어지지 않는 가짜 낙찰가가 생긴다

function realRoom(auctionId: number, status: AuctionRoomSummary['status']): AuctionRoomSummary {
  return {
    auctionId,
    title: '실제 방',
    thumbnailUrl: null,
    categoryCode: 'ELECTRONICS',
    sellerNickname: '서연***',
    reservePrice: 10_000,
    bidCount: 0,
    predictionParticipantCount: 0,
    scheduledAt: null,
    endAt: null,
    soldPrice: null,
    endedAt: null,
    status,
    viewerCount: 0,
  }
}

describe('withDemoRooms', () => {
  it('진짜 방이 항상 앞이다 — 더미는 뒤에만 붙는다', () => {
    const rooms = [realRoom(10, 'LIVE'), realRoom(11, 'LIVE')]

    const merged = withDemoRooms(rooms, 'LIVE')

    expect(merged.slice(0, 2).map((r) => r.auctionId)).toEqual([10, 11])
    expect(merged.length).toBeGreaterThan(2)
    expect(merged.slice(2).every(isDemoRoom)).toBe(true)
  })

  it('원본 배열을 건드리지 않는다 — 스토어 상태를 화면이 늘리면 안 된다', () => {
    const rooms = [realRoom(10, 'LIVE')]

    withDemoRooms(rooms, 'LIVE')

    expect(rooms).toHaveLength(1)
  })

  it('끝난 방 목록에는 붙이지 않는다 — 가짜 낙찰 결과를 만들지 않기 위해', () => {
    const rooms = [realRoom(10, 'ENDED')]

    expect(withDemoRooms(rooms, 'ENDED')).toEqual(rooms)
  })

  it('상태를 지정하지 않으면(전체 탭) 진행 중과 예정 더미를 모두 붙인다', () => {
    const merged = withDemoRooms([])

    expect(merged.some((r) => r.status === 'LIVE')).toBe(true)
    expect(merged.some((r) => r.status === 'WAITING')).toBe(true)
  })
})

describe('isDemoRoom', () => {
  it('더미는 음수 id라 실 데이터와 겹치지 않는다', () => {
    expect(demoLiveRooms().every(isDemoRoom)).toBe(true)
    expect(isDemoRoom(realRoom(1, 'LIVE'))).toBe(false)
  })
})

describe('더미 데이터 자체의 규칙', () => {
  /**
   * 실 시드에 없는 코드를 쓰면 배지에 표시명 대신 코드가 그대로 나오고 썸네일 그라데이션도
   * 폴백으로 떨어진다 — 실제로 `FURNITURE`를 넣었다가 그렇게 보였다.
   */
  it('카테고리 코드가 실제 시드 6종 안에 있다', () => {
    const seeded = ['ELECTRONICS', 'HOBBY', 'FASHION', 'LIVING', 'MEDIA', 'SPORTS']

    for (const room of [...demoLiveRooms(), ...demoWaitingRooms()]) {
      expect(seeded).toContain(room.categoryCode)
    }
  })

  it('id가 서로 겹치지 않는다 — 카드 :key가 뭉개진다', () => {
    const ids = [...demoLiveRooms(), ...demoWaitingRooms()].map((r) => r.auctionId)

    expect(new Set(ids).size).toBe(ids.length)
  })

  /** 시작 전 방에 입찰·예측이 잡혀 있으면 화면이 거짓말을 한다 */
  it('시작 예정 더미는 입찰·예측이 0이다', () => {
    for (const room of demoWaitingRooms()) {
      expect(room.bidCount).toBe(0)
      expect(room.predictionParticipantCount).toBe(0)
      expect(room.scheduledAt).not.toBeNull()
    }
  })
})

describe('demoLiveRooms', () => {
  /**
   * 고정 시각을 박아 두면 시연 중에 「마감 -3:21」이 뜬다. 카운트다운은 서버 권위 값을 표시만 하는
   * 규칙(규칙 5)이라 화면 쪽에 예외를 둘 수 없어, 값 자체가 항상 미래여야 한다.
   */
  it('마감 시각이 조회 시점 기준 미래다', () => {
    const now = Date.now()

    for (const room of demoLiveRooms()) {
      expect(room.endAt).not.toBeNull()
      expect(Date.parse(room.endAt as string)).toBeGreaterThan(now)
    }
  })

  /**
   * 카드·컬럼의 긴급 강조는 남은 3분 이하에서 켜진다(`RoomCard`·`EndingSoonCard`의 `urgentMs`).
   * 그 안에 드는 방이 없으면 빨간 타이머를 시연할 수 없다.
   */
  it('마감 임박(3분 이하) 방이 들어 있다', () => {
    const now = Date.now()
    const urgent = demoLiveRooms().filter(
      (room) => Date.parse(room.endAt as string) - now <= 180_000,
    )

    expect(urgent.length).toBeGreaterThanOrEqual(3)
  })

  /** 홈 히어로·마감 임박이 목록을 앞에서 자르므로 정렬이 곧 노출 우선순위다 */
  it('마감이 가까운 순으로 정렬돼 있다', () => {
    const endAts = demoLiveRooms().map((room) => Date.parse(room.endAt as string))

    expect(endAts).toEqual([...endAts].sort((a, b) => a - b))
  })
})
