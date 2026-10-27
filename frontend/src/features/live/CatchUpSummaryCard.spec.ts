import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CatchUpSummaryCard from './CatchUpSummaryCard.vue'

const summary = {
  auctionId: 7,
  generatedAt: '2026-07-31T09:00:00Z',
  expiresAt: '2026-07-31T09:02:00Z',
  bidSummary: { bidCount: 7, participantCount: 5 },
  extensionSummary: { extensionCount: 1, extendedMinutes: 5 },
  sellerMentions: ['키캡 하나에 하자가 있습니다'],
  serverTime: '2026-07-31T09:00:12Z',
  endAt: '2026-07-31T09:15:00Z',
  sourceType: 'STT_AND_ROOM_EVENT' as const,
}

describe('중간 입장 요약 카드', () => {
  it('공개 집계와 판매자 발화 요약을 표시한다', () => {
    const wrapper = mount(CatchUpSummaryCard, { props: { summary } })

    expect(wrapper.text()).toContain('지금까지의 방송')
    expect(wrapper.text()).toContain('입찰 7건')
    expect(wrapper.text()).toContain('참여 5명')
    expect(wrapper.text()).toContain('1회 (+5분)')
    expect(wrapper.text()).toContain('키캡 하나에 하자가 있습니다')
    expect(wrapper.text()).toContain('AI 요약')
  })

  it('닫기 버튼으로 close 이벤트를 보낸다', async () => {
    const wrapper = mount(CatchUpSummaryCard, { props: { summary } })

    await wrapper.get('button[aria-label="방송 요약 닫기"]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
