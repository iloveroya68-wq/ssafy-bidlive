import { describe, expect, it } from 'vitest'
import { OTHER_LIVE_LIMIT, otherLiveRooms } from './otherLiveRooms'
import type { AuctionRoomSummary } from '@/stores/room'

function room(auctionId: number, status: AuctionRoomSummary['status']): AuctionRoomSummary {
  return {
    auctionId,
    title: `방 ${auctionId}`,
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

describe('otherLiveRooms', () => {
  /** 보고 있는 방송이 「다른 라이브」에 또 뜨면 눌러도 아무 일이 없고 8칸 중 하나를 낭비한다 */
  it('지금 보고 있는 방은 뺀다', () => {
    const rooms = [room(1, 'LIVE'), room(2, 'LIVE'), room(3, 'LIVE')]

    expect(otherLiveRooms(rooms, 2).map((r) => r.auctionId)).toEqual([1, 3])
  })

  /**
   * 스토어를 목록 화면과 공유하므로, 「종료」 탭을 보고 넘어오면 새 응답이 오기 전까지
   * 끝난 방이 섞여 있다. 그대로 그리면 라이브 화면 아래에 종료된 방 카드가 뜬다.
   */
  it('진행 중이 아닌 방은 섞이지 않는다', () => {
    const rooms = [room(1, 'ENDED'), room(2, 'WAITING'), room(3, 'LIVE')]

    expect(otherLiveRooms(rooms, 99).map((r) => r.auctionId)).toEqual([3])
  })

  /** 와이어프레임: "여긴 갈아타기용 추천 8개까지만. 본격 탐색은 방 목록의 역할" */
  it('8개를 넘기지 않는다', () => {
    const rooms = Array.from({ length: 20 }, (_, i) => room(i + 1, 'LIVE'))

    expect(otherLiveRooms(rooms, 99)).toHaveLength(OTHER_LIVE_LIMIT)
  })

  it('원본 배열을 건드리지 않는다', () => {
    const rooms = [room(1, 'LIVE'), room(2, 'ENDED')]

    otherLiveRooms(rooms, 1)

    expect(rooms).toHaveLength(2)
  })
})
