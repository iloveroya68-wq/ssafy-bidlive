// LIVE-06 낙찰 순간 스냅샷 표시 (S15P11A704-705).
//
// MEM-02 낙찰 목록은 `snapshotUrl`을 예전부터 받고 있었는데 카드가 그리지 않아 화면에 없는 값이었다.
// 스냅샷이 없는 낙찰 건도 정상이라(판매자가 캡처를 못 올린 경우) 두 분기를 다 잠근다 —
// 없을 때 `<img :src="null">`을 그리면 카드마다 깨진 이미지 아이콘이 뜬다.
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import MyAuctionsPanel from './MyAuctionsPanel.vue'
import type { HostedAuction, WonAuction } from '@/shared/api/myAuctions'

const { getWonAuctionsMock, getHostedAuctionsMock } = vi.hoisted(() => ({
  getWonAuctionsMock: vi.fn(),
  getHostedAuctionsMock: vi.fn(),
}))
vi.mock('@/shared/api/myAuctions', async () => {
  const actual =
    await vi.importActual<typeof import('@/shared/api/myAuctions')>('@/shared/api/myAuctions')
  return {
    ...actual,
    getWonAuctions: getWonAuctionsMock,
    getHostedAuctions: getHostedAuctionsMock,
    getParticipatedAuctions: vi
      .fn()
      .mockResolvedValue({ items: [], nextCursor: null, hasNext: false }),
  }
})

const emptyPage = { items: [], nextCursor: null, hasNext: false }

const wonAuction: WonAuction = {
  auctionId: 100,
  dealId: 900,
  productTitle: '빈티지 카메라',
  imageUrl: null,
  sellerNickname: '김**',
  finalPrice: 74_000,
  myFinalBid: 82_000,
  dealStatus: 'PAYMENT_COMPLETED',
  snapshotUrl: null,
  endedAt: '2026-08-01T12:00:00Z',
  actionRequired: false,
  actionCode: null,
  actionLabel: null,
}

const mountPanel = () =>
  mount(MyAuctionsPanel, {
    props: {
      confirmingDealId: null,
      pendingDealId: null,
      actionError: null,
      errorDealId: null,
    },
    global: { stubs: { RouterLink: RouterLinkStub } },
  })

const snapshotImage = (wrapper: ReturnType<typeof mountPanel>) =>
  wrapper.findAll('img').filter((img) => img.attributes('alt') === '낙찰 순간 스냅샷')

beforeEach(() => {
  vi.clearAllMocks()
  getWonAuctionsMock.mockResolvedValue(emptyPage)
  getHostedAuctionsMock.mockResolvedValue(emptyPage)
})

describe('MyAuctionsPanel — 낙찰 카드 스냅샷', () => {
  it('스냅샷 URL이 있으면 썸네일을 그린다', async () => {
    getWonAuctionsMock.mockResolvedValue({
      items: [{ ...wonAuction, snapshotUrl: 'https://cdn.example.com/snapshots/100.jpg' }],
      nextCursor: null,
      hasNext: false,
    })

    const wrapper = mountPanel()
    await flushPromises()

    const image = snapshotImage(wrapper)
    expect(image).toHaveLength(1)
    expect(image[0].attributes('src')).toBe('https://cdn.example.com/snapshots/100.jpg')
  })

  it('스냅샷이 없으면 이미지를 얹지 않는다 — 카드는 그대로 나온다', async () => {
    getWonAuctionsMock.mockResolvedValue({
      items: [wonAuction],
      nextCursor: null,
      hasNext: false,
    })

    const wrapper = mountPanel()
    await flushPromises()

    expect(snapshotImage(wrapper)).toHaveLength(0)
    expect(wrapper.text()).toContain('빈티지 카메라')
  })
})

// 개최 탭은 끝난 방과 안 끝난 방이 섞여 링크 목적지가 갈린다 (S15P11A704-711).
// 조건이 뒤집혀도 화면은 멀쩡해 보이고 눌러 봐야 알 수 있어서 분기만 잠근다.
const hostedAuction: HostedAuction = {
  auctionId: 1,
  dealId: null,
  productTitle: '개최한 물건',
  imageUrl: null,
  roomStatus: 'ENDED',
  resultType: null,
  finalPrice: null,
  winnerNickname: null,
  dealStatus: null,
  bidCount: 0,
  participantCount: 0,
  predictionParticipantCount: 0,
  scheduledAt: null,
  startedAt: null,
  endedAt: null,
  actionRequired: false,
  actionCode: null,
  actionLabel: null,
}

describe('MyAuctionsPanel — 개최 카드 링크', () => {
  it('끝난 방은 결과 화면으로, 안 끝난 방은 콘솔로 보낸다', async () => {
    getHostedAuctionsMock.mockResolvedValue({
      ...emptyPage,
      items: [
        { ...hostedAuction, auctionId: 1, resultType: 'SOLD', finalPrice: 74_000 },
        { ...hostedAuction, auctionId: 2 },
        { ...hostedAuction, auctionId: 3, roomStatus: 'LIVE' },
        { ...hostedAuction, auctionId: 4, roomStatus: 'SCHEDULED' },
      ],
    })

    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('#deals-tab-hosted').trigger('click')
    await flushPromises()

    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links.map((link) => link.props('to'))).toEqual([
      { name: 'result', params: { id: 1 } },
      { name: 'result', params: { id: 2 } },
      { name: 'console', params: { id: 3 } },
      { name: 'console', params: { id: 4 } },
    ])
    // 낙찰 건은 금액이 링크에 밀려 사라지지 않는다
    expect(wrapper.text()).toContain('74,000')
  })
})
