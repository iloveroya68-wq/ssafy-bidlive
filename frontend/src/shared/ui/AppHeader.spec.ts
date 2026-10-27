import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ref } from 'vue'
import AppHeader from './AppHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useWalletStore } from '@/stores/wallet'
import * as walletsApi from '@/shared/api/wallets'
import { http } from '@/shared/api/http'
import { resetWalletServer, walletHttpStub } from '@/mocks/walletServer'
import type { AuthMemberSummary } from '@/shared/api/auth'

/** 내비게이션을 흉내 내는 상자 — 헤더가 경로 변화에 반응하는지 보려면 바꿀 수 있어야 한다 */
const currentPath = ref('/')

// PNT-01 헤더 지갑 칩 (S15P11A704-332).
//
// 칩이 하드코딩 `128,000P`였던 게 이 티켓의 발단이다 — 진짜처럼 보여서 아무도 못 알아챘고,
// 충전해도 입찰해도 숫자가 그대로였다. 여기서 잠그는 것도 전부 "틀려도 화면은 멀쩡한" 것들이다.

const RouterLinkStub = {
  props: ['to'],
  template: '<a><slot /></a>',
}

function mountHeader() {
  return mount(AppHeader, {
    global: {
      stubs: { RouterLink: RouterLinkStub },
      mocks: { $route: { fullPath: '/' } },
    },
  })
}

// `useRoute()`는 라우터 없이 부르면 undefined다 — 칩의 redirect 쿼리와 재시도 시점에만 쓰인다
vi.mock('vue-router', async () => {
  const actual = await vi.importActual<typeof import('vue-router')>('vue-router')
  return {
    ...actual,
    useRoute: () => ({
      get fullPath() {
        return currentPath.value
      },
    }),
  }
})

function member(over: Partial<AuthMemberSummary> = {}): AuthMemberSummary {
  return {
    memberId: 25,
    nickname: '준호',
    onboardingRequired: false,
    status: 'ACTIVE',
    role: 'USER',
    ...over,
  }
}

describe('AppHeader 지갑 칩', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    currentPath.value = '/'
    // 지갑 조회가 실 API가 됐다(S15P11A704-504). 스펙이 `getMyWallets` 호출 횟수를 세므로
    // API 함수가 아니라 http를 스텁한다.
    resetWalletServer()
    vi.spyOn(http, 'get').mockImplementation(walletHttpStub as never)
  })

  it('비로그인이면 지갑을 부르지 않는다', async () => {
    const wallet = useWalletStore()
    const spy = vi.spyOn(wallet, 'loadWallet')

    mountHeader()
    await new Promise((r) => setTimeout(r, 0))

    expect(spy).not.toHaveBeenCalled()
  })

  it('로그인 상태면 헤더가 직접 지갑을 받는다 — 홈·방 목록은 아무도 안 부른다', async () => {
    const auth = useAuthStore()
    await auth.establishSession(member())
    const wallet = useWalletStore()

    mountHeader()
    await vi.waitFor(() => expect(wallet.loaded).toBe(true))

    expect(wallet.tradeWallet.availableBalance).toBeGreaterThan(0)
  })

  it('총 보유가 아니라 가용 잔액을 보여준다', async () => {
    // 총 보유를 띄우면 입찰해도(가용 → 홀드로 옮길 뿐) 숫자가 안 변해
    // 하드코딩이던 때와 화면상 구분이 안 간다 — 이 티켓이 생긴 이유가 그거다.
    const auth = useAuthStore()
    await auth.establishSession(member())
    const wallet = useWalletStore()
    await wallet.loadWallet()
    wallet.wallets = [
      { walletType: 'TRADE', availableBalance: 128_000, heldBalance: 82_000 },
      { walletType: 'BETTING', availableBalance: 950, heldBalance: 300 },
    ]

    const wrapper = mountHeader()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).toContain('128,000P')
    expect(wrapper.text()).toContain('950BP')
    // 총 보유(210,000P / 1,250BP)가 아니어야 한다
    expect(wrapper.text()).not.toContain('210,000P')
    expect(wrapper.text()).not.toContain('1,250BP')
  })

  it('조회 전에는 0을 그리지 않는다 — 잔액 0과 "못 받았다"가 구분되지 않는다', async () => {
    const auth = useAuthStore()
    await auth.establishSession(member())

    const wrapper = mountHeader()
    await wrapper.vm.$nextTick()

    expect(wrapper.text()).not.toContain('0P')
    expect(wrapper.text()).toContain('—')
  })

  it('조회에 실패하면 같은 화면에서 재시도 루프를 돌지 않는다', async () => {
    const auth = useAuthStore()
    await auth.establishSession(member())
    const wallet = useWalletStore()
    const spy = vi.spyOn(wallet, 'loadWallet').mockImplementation(async () => {
      wallet.loadError = 'boom'
    })

    mountHeader()
    await vi.waitFor(() => expect(wallet.loadError).not.toBeNull())
    await new Promise((r) => setTimeout(r, 10))

    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('실패해도 다음 화면에서 한 번 더 시도한다 — 헤더가 유일한 호출자라 영구히 —로 남는다', async () => {
    // `loadError`를 지우는 건 `loadWallet` 자신뿐이다. 홈·방 목록만 도는 사용자는
    // 지갑을 부르는 화면을 안 거치므로 복구 수단이 아예 없어진다.
    const auth = useAuthStore()
    await auth.establishSession(member())
    const wallet = useWalletStore()
    const spy = vi.spyOn(wallet, 'loadWallet').mockImplementation(async () => {
      wallet.loadError = 'boom'
    })

    mountHeader()
    await vi.waitFor(() => expect(spy).toHaveBeenCalledTimes(1))

    currentPath.value = '/rooms'
    await vi.waitFor(() => expect(spy).toHaveBeenCalledTimes(2))
  })

  it('지갑이 비워지면 다시 받는다 — 재로그인은 isLoggedIn이 안 바뀔 수 있다', async () => {
    // `resetMemberScope()`가 `$reset()`으로 loaded를 내린다. isLoggedIn만 보면
    // 로그아웃을 거치지 않은 재로그인에서 같은 틱에 false→true라 감시가 안 걸린다.
    const auth = useAuthStore()
    await auth.establishSession(member())
    const wallet = useWalletStore()

    mountHeader()
    await vi.waitFor(() => expect(wallet.loaded).toBe(true))

    wallet.$reset()
    await vi.waitFor(() => expect(wallet.loaded).toBe(true))
  })

  it('재로그인 중이면 이전 회원의 응답이 칩에 뜨지 않는다', async () => {
    // 지갑 조회의 single-flight는 **모듈 변수**라 `$reset()`이 닿지 않는다.
    // 회원 A의 요청이 떠 있는 채로 B가 로그인하면, 그 프라미스가 재사용되고
    // A의 잔액이 B의 스토어에 기록된다 — `resetMemberScope`가 막으려던 바로 그 사고다.
    const auth = useAuthStore()
    await auth.establishSession(member({ memberId: 1 }))
    const wallet = useWalletStore()

    let releaseA: (() => void) | null = null
    vi.spyOn(walletsApi, 'getMyWallets').mockImplementation(async () => {
      await new Promise<void>((resolve) => {
        releaseA = resolve
      })
      return {
        tradeWallet: { balance: 999_999, heldAmount: 0, availableAmount: 999_999 },
        bettingWallet: { balance: 999, heldAmount: 0, availableAmount: 999 },
      } as Awaited<ReturnType<typeof walletsApi.getMyWallets>>
    })

    mountHeader()
    await vi.waitFor(() => expect(releaseA).not.toBeNull())

    // A의 응답이 도착하기 전에 회원이 바뀐다
    auth.resetMemberScope()
    releaseA!()
    await new Promise((r) => setTimeout(r, 10))

    expect(wallet.loaded).toBe(false)
    expect(wallet.tradeWallet.availableBalance).toBe(0)
  })
})
