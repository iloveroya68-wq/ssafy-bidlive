import { describe, expect, it } from 'vitest'
import { endingSoon, heroSlides } from './homeFeed'
import type { AuctionRoomSummary } from '@/stores/room'

// 히어로가 방 id 1~4를 대조하던 시절, 실 서버에는 그 id가 없어 슬라이드가 0장이 됐다 —
// 캐러셀은 빈 배열에도 상자를 그리므로 화면에는 검은 상자만 남았다 (S15P11A704-507).
// 여기서 잠그는 것은 「어떤 id가 와도 방이 있으면 슬라이드가 선다」다.

function room(over: Partial<AuctionRoomSummary> & { auctionId: number }): AuctionRoomSummary {
  return {
    title: '방',
    thumbnailUrl: null,
    categoryCode: 'ELECTRONICS',
    sellerNickname: '판매***',
    reservePrice: 50_000,
    bidCount: 0,
    predictionParticipantCount: 0,
    scheduledAt: null,
    endAt: '2026-07-31T12:00:00+09:00',
    soldPrice: null,
    endedAt: null,
    status: 'LIVE',
    viewerCount: 0,
    ...over,
  }
}

const nameByCode = (code: string) => (code === 'ELECTRONICS' ? '전자기기' : code)

describe('heroSlides (S15P11A704-507)', () => {
  it('실 서버 id에서도 슬라이드가 선다 — 예전에는 1~4가 아니면 0장이었다', () => {
    const slides = heroSlides([room({ auctionId: 9203 }), room({ auctionId: 9801 })], nameByCode)

    expect(slides.map((s) => s.room.auctionId)).toEqual([9203, 9801])
  })

  it('목록 순서를 그대로 따른다 — 서버가 endAt 오름차순으로 준다(ROOM-02)', () => {
    const slides = heroSlides(
      [9902, 9203, 9801, 9901, 9700].map((auctionId) => room({ auctionId })),
      nameByCode,
    )

    expect(slides.map((s) => s.room.auctionId)).toEqual([9902, 9203, 9801, 9901])
  })

  it('칩은 카테고리 표시명이다 — 큐레이션 문구를 지어내지 않는다', () => {
    const [slide] = heroSlides([room({ auctionId: 1, categoryCode: 'ELECTRONICS' })], nameByCode)

    expect(slide.tag).toBe('전자기기')
  })

  it('마감 시각이 없는 방은 빠진다 — 카운트다운을 못 그린다', () => {
    const slides = heroSlides([room({ auctionId: 1, endAt: null })], nameByCode)

    expect(slides).toEqual([])
  })
})

describe('endingSoon', () => {
  it('LIVE가 아닌 방은 빠진다', () => {
    const rooms = [room({ auctionId: 1 }), room({ auctionId: 2, status: 'WAITING' })]

    expect(endingSoon(rooms).map((r) => r.auctionId)).toEqual([1])
  })
})
