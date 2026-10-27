import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletStore } from './wallet'
import * as walletApi from '@/shared/api/wallets'
import { http } from '@/shared/api/http'
import { resetWalletServer, walletHttpStub } from '@/mocks/walletServer'

// 지갑은 **돈이 보이는 화면**이라 틀려도 렌더는 멀쩡하다. 특히 셋이 조용히 깨진다:
//   ① 충전·출금 후 잔액을 서버 응답이 아니라 낙관 계산으로 두면 서버와 어긋난다
//   ② 원장 커서를 잘못 이어 붙이면 같은 행이 두 번 보이거나 뒷장이 사라진다
//   ③ 실패했는데 성공처럼 넘어가면 "충전됐다"는 문구만 남고 잔액은 그대로다

describe('useWalletStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
    // 지갑 조회(494)·원장(495)·환불(497)이 실 API가 됐다(S15P11A704-504).
    // **API 함수가 아니라 http를 스텁한다** — 아래 테스트들이 `getMyWallets`·`getPointLedgers`
    // 호출 횟수를 직접 세기 때문에, 미리 스파이해 두면 그 카운트가 오염된다.
    resetWalletServer()
    vi.spyOn(http, 'get').mockImplementation(walletHttpStub as never)
    vi.spyOn(http, 'post').mockImplementation(walletHttpStub as never)
  })

  describe('loadWallet', () => {
    it('겹쳐 불러도 조회는 한 번만 나간다 — 화면 여럿이 onMounted에서 부른다', async () => {
      const spy = vi.spyOn(walletApi, 'getMyWallets')
      const store = useWalletStore()

      await Promise.all([store.loadWallet(), store.loadWallet(), store.loadWallet()])

      expect(spy).toHaveBeenCalledTimes(1)
      expect(store.loaded).toBe(true)
    })

    it('끝난 뒤에는 다시 조회한다 — 충전하고 돌아왔을 때 잔액이 굳으면 안 된다', async () => {
      // 예전 `!loaded` 가드가 정확히 이걸 막고 있었다 (S15P11A704-333)
      const spy = vi.spyOn(walletApi, 'getMyWallets')
      const store = useWalletStore()

      await store.loadWallet()
      await store.loadWallet()

      expect(spy).toHaveBeenCalledTimes(2)
    })

    it('두 지갑을 모두 채운다', async () => {
      const store = useWalletStore()

      await store.loadWallet()

      expect(store.tradeWallet.availableBalance).toBeGreaterThan(0)
      expect(store.bettingWallet.availableBalance).toBeGreaterThan(0)
      // 총 보유 = 가용 + 보류 (available이 이미 보류 제외 값이라 빼지 않는다)
      expect(store.tradeTotal).toBe(
        store.tradeWallet.availableBalance + store.tradeWallet.heldBalance,
      )
    })

    it('실패하면 loadError에 담고 loaded를 세우지 않는다', async () => {
      vi.spyOn(walletApi, 'getMyWallets').mockRejectedValue(new Error('boom'))
      const store = useWalletStore()

      await store.loadWallet()

      expect(store.loaded).toBe(false)
      expect(store.loadError).not.toBeNull()
    })
  })

  describe('원장 커서', () => {
    it('첫 페이지는 요청한 크기만큼만 받는다', async () => {
      const store = useWalletStore()

      await store.loadWallet()

      // 거래 원장은 목이 11행이라 8행 페이지 기준 첫 장은 8행 + 다음 장이 있다
      expect(store.activeLedger).toHaveLength(8)
      expect(store.activeLedgerHasNext).toBe(true)
    })

    it('더 보기는 앞 페이지를 지우지 않고 이어 붙인다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      const firstIds = store.activeLedger.map((e) => e.ledgerId)

      await store.loadMoreLedger()

      const allIds = store.activeLedger.map((e) => e.ledgerId)
      expect(allIds.slice(0, firstIds.length)).toEqual(firstIds)
      expect(allIds).toHaveLength(11)
      // 같은 행이 두 번 들어가면 안 된다
      expect(new Set(allIds).size).toBe(allIds.length)
      expect(store.activeLedgerHasNext).toBe(false)
    })

    it('마지막 페이지에서 또 눌러도 아무 일도 없다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      await store.loadMoreLedger()
      const spy = vi.spyOn(walletApi, 'getPointLedgers')

      await store.loadMoreLedger()

      expect(spy).not.toHaveBeenCalled()
    })

    it('지갑을 바꾸면 그 재화의 원장을 본다 — 커서를 공유하지 않는다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      await store.loadMoreLedger() // 거래 원장만 2장까지 받은 상태

      store.setActiveWalletType('BETTING')

      // 베팅 원장은 목이 9행이라 첫 장 8행 + 다음 장 있음 (거래 쪽 진행 상태와 무관)
      expect(store.activeLedger).toHaveLength(8)
      expect(store.activeLedgerHasNext).toBe(true)
      expect(store.activeLedger.every((e) => e.walletType === 'BETTING')).toBe(true)
    })
  })

  describe('충전·출금', () => {
    it('충전 후 잔액을 서버 응답으로 덮는다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      vi.spyOn(walletApi, 'chargeTradePoint').mockResolvedValue({
        walletType: 'TRADE',
        chargedAmount: 50_000,
        balance: 999_000,
        heldAmount: 7_000,
        availableAmount: 992_000,
        ledgerId: 1,
        chargedAt: '2026-07-28T00:00:00+09:00',
      })

      const failure = await store.chargeTradePoint(50_000)

      expect(failure).toBeNull()
      // 낙관 계산(이전 잔액 + 50,000)이 아니라 응답 값이어야 한다
      expect(store.tradeWallet.availableBalance).toBe(992_000)
      expect(store.tradeWallet.heldBalance).toBe(7_000)
    })

    it('재시도용 멱등 키를 그대로 넘긴다 — 같은 키면 서버가 이중 충전을 막는다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      const spy = vi.spyOn(walletApi, 'chargeTradePoint')

      await store.chargeTradePoint(10_000, 'key-1')
      await store.chargeTradePoint(10_000, 'key-1')

      expect(spy.mock.calls.map((c) => c[1])).toEqual(['key-1', 'key-1'])
    })

    it('충전이 실패하면 사유를 돌려주고 잔액을 건드리지 않는다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      const before = store.tradeWallet.availableBalance
      vi.spyOn(walletApi, 'chargeTradePoint').mockRejectedValue(new Error('boom'))

      const failure = await store.chargeTradePoint(50_000)

      expect(failure).not.toBeNull()
      expect(store.tradeWallet.availableBalance).toBe(before)
    })

    it('가용 잔액을 넘는 출금은 서버에 가기 전에 막는다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      const spy = vi.spyOn(walletApi, 'refundTradePoint')

      const failure = await store.withdrawTradePoint(store.tradeWallet.availableBalance + 1)

      expect(failure).toBe('사용 가능 잔액을 넘을 수 없어요')
      expect(spy).not.toHaveBeenCalled()
    })

    it('잔액을 못 받아온 상태의 출금은 "잔액 초과"가 아니라 조회 실패라고 말한다', async () => {
      // 폴백이 0P라 어떤 금액이든 상한을 넘는다 — 원인을 잘못 알려주면 사용자는 계속 다시 누른다
      vi.spyOn(walletApi, 'getMyWallets').mockRejectedValue(new Error('boom'))
      const store = useWalletStore()
      await store.loadWallet()
      const spy = vi.spyOn(walletApi, 'refundTradePoint')

      const failure = await store.withdrawTradePoint(1_000)

      expect(failure).toContain('불러오지 못했')
      expect(spy).not.toHaveBeenCalled()
    })

    it('충전은 됐는데 원장 갱신이 실패해도 성공으로 보고한다 — 실패로 보고하면 이중 충전된다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      vi.spyOn(walletApi, 'getPointLedgers').mockRejectedValue(new Error('ledger down'))

      const failure = await store.chargeTradePoint(10_000)

      // 돈은 이미 나갔다. 여기서 실패라고 하면 사용자는 다시 누르고 서버는 새 키로 또 충전한다.
      expect(failure).toBeNull()
      // 대신 원장 쪽 오류로 남겨 화면이 "내역을 못 불러왔다"고 따로 말할 수 있게 한다
      expect(store.activeLedgerError).not.toBeNull()
    })

    it('출금 성공 후 원장 첫 페이지를 다시 받는다 — 새 행이 맨 앞에 생긴다', async () => {
      const store = useWalletStore()
      await store.loadWallet()
      const spy = vi.spyOn(walletApi, 'getPointLedgers')

      await store.withdrawTradePoint(1_000)

      expect(spy).toHaveBeenCalledWith(expect.objectContaining({ walletType: 'TRADE' }))
    })
  })
})
