import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import RoomCard from './RoomCard.vue'
import { useRoomStore, type AuctionRoomSummary } from '@/stores/room'

// 카드 카운트다운의 기준 시각이 **서버**(목록 응답의 `serverTime`)로 바뀌었다 (S15P11A704-457).
//
// 여기서 잡는 건 조용한 실패 하나다: 오프셋 소스를 붙이면서 값이 안 채워지면 카드의 남은 시간이
// 통째로 `--:--`가 된다. `useCountdown`이 서버 기준 없이 남은 시간을 지어내지 않기 때문이고
// (규칙 5), 그건 의도된 동작이라 예외도 로그도 안 남는다 — 카드는 멀쩡히 그려진다.

const liveRoom = (endAt: string): AuctionRoomSummary => ({
  auctionId: 1,
  title: '테스트 경매',
  thumbnailUrl: null,
  categoryCode: 'ELECTRONICS',
  sellerNickname: '판**',
  reservePrice: 10_000,
  bidCount: 0,
  predictionParticipantCount: 0,
  scheduledAt: null,
  endAt,
  soldPrice: null,
  endedAt: null,
  status: 'LIVE',
  viewerCount: 0,
})

describe('RoomCard 남은 시간', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('serverOffset이 채워져 있으면 MM:SS로 그린다', () => {
    const store = useRoomStore()
    store.serverOffset = 0
    const endAt = new Date(Date.now() + 120_000).toISOString()

    const wrapper = mount(RoomCard, { props: { room: liveRoom(endAt) } })

    expect(wrapper.text()).toMatch(/\d{2}:\d{2}/)
    expect(wrapper.text()).not.toContain('--:--')
  })

  it('serverOffset이 없으면 남은 시간을 지어내지 않는다', () => {
    const store = useRoomStore()
    store.serverOffset = null
    const endAt = new Date(Date.now() + 120_000).toISOString()

    const wrapper = mount(RoomCard, { props: { room: liveRoom(endAt) } })

    expect(wrapper.text()).toContain('--:--')
  })
})
