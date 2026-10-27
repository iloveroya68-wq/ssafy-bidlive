import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { AuctionRoomSummary } from '@/stores/room'
import type { HeroSlide } from './homeFeed'

const pushMock = vi.fn()

vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: pushMock }),
  }
})

const RouterLinkStub = { props: ['to'], template: '<a><slot /></a>' }
const HeroTimerChipStub = { props: ['endsAt'], template: '<span />' }

function room(over: Partial<AuctionRoomSummary> = {}): AuctionRoomSummary {
  return {
    auctionId: 1,
    title: 'Test auction',
    thumbnailUrl: null,
    categoryCode: 'ELECTRONICS',
    sellerNickname: 'seller',
    reservePrice: 10_000,
    bidCount: 12,
    predictionParticipantCount: 3,
    scheduledAt: null,
    endAt: new Date(Date.now() + 60_000).toISOString(),
    soldPrice: null,
    endedAt: null,
    status: 'LIVE',
    viewerCount: 4,
    ...over,
  }
}

function slide(): HeroSlide {
  return { room: room(), tag: 'Electronics' }
}

describe('LiveHeroCarousel bid count', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    pushMock.mockReset()
  })

  it('does not increase the server bid count without a new list value', async () => {
    const { default: LiveHeroCarousel } = await import('./LiveHeroCarousel.vue')
    const wrapper = mount(LiveHeroCarousel, {
      props: { slides: [slide()] },
      global: {
        stubs: { RouterLink: RouterLinkStub, HeroTimerChip: HeroTimerChipStub },
      },
    })

    expect(wrapper.text()).toContain('12건')

    await vi.advanceTimersByTimeAsync(10_000)

    expect(wrapper.text()).toContain('12건')
    expect(wrapper.text()).not.toContain('13건')
    wrapper.unmount()
  })
})

describe('LiveHeroCarousel entry actions', () => {
  beforeEach(() => {
    vi.stubGlobal('matchMedia', () => ({ matches: false }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    pushMock.mockReset()
  })

  it('더미 방의 지금 입장은 실제 방으로 이동하지 않고 안내를 띄운다', async () => {
    const { default: LiveHeroCarousel } = await import('./LiveHeroCarousel.vue')
    const wrapper = mount(LiveHeroCarousel, {
      props: { slides: [{ ...slide(), room: room({ auctionId: -1 }) }] },
      global: {
        stubs: { RouterLink: RouterLinkStub, HeroTimerChip: HeroTimerChipStub },
      },
    })

    await wrapper.find('article button').trigger('click')

    expect(pushMock).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('시연용 더미 데이터입니다')
    wrapper.unmount()
  })

  it('실제 방의 지금 입장은 라이브 방으로 이동한다', async () => {
    const { default: LiveHeroCarousel } = await import('./LiveHeroCarousel.vue')
    const wrapper = mount(LiveHeroCarousel, {
      props: { slides: [slide()] },
      global: {
        stubs: { RouterLink: RouterLinkStub, HeroTimerChip: HeroTimerChipStub },
      },
    })

    await wrapper.find('article button').trigger('click')

    expect(pushMock).toHaveBeenCalledWith({ name: 'live', params: { id: '1' } })
    wrapper.unmount()
  })
})
