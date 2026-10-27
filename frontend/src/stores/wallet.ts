import { defineStore } from 'pinia'
import {
  chargeTradePoint as chargeTradePointApi,
  getMyWallets,
  getPointLedgers,
  refundTradePoint as refundTradePointApi,
  type PointLedgerEntry,
  type WalletBalance,
  type WalletType,
} from '@/shared/api/wallets'
import { toApiError } from '@/shared/api/errors'
import { newRequestId } from '@/shared/api/idempotency'

export type { WalletType }
/** 원장 행 — 서버 응답 그대로다 (`createdAt`은 ISO 8601) */
export type LedgerEntry = PointLedgerEntry

// 오너: FE1 (PNT-01~02) — 거래/베팅 이중 지갑. 두 지갑은 별개 행이고 상호 전환 개념이 없다.
//
// 스키마 정렬 (docs/04-백엔드-스키마-메모.md §3.5 · §4.2)
// - `available_balance`는 **이미 보류를 제외한 값**이다.
//   과거 구현의 `tradeAvailable = tradePoint - tradeHold` getter는 보류를 한 번 더 빼는
//   **이중 차감 버그**였다 — 총 보유가 필요하면 반대로 더한다.
// - 베팅 지갑에도 보류 잔액이 있다(구 `betPoint` 단일 값은 그게 누락돼 있었다).
//
// ⚠️ 스토어가 쓰는 이름(`availableBalance`/`heldBalance`)은 **응답 필드명과 다르다.**
//    응답은 `{ balance, heldAmount, availableAmount }`이고 경계(`toWallet`)에서 한 번만 옮긴다.
//    화면 여섯 곳이 스토어 이름을 쓰고 있어 여기서 흡수하는 편이 갈아엎는 것보다 안전하다.

/** 화면이 쓰는 지갑 1행 */
export interface Wallet {
  walletType: WalletType
  /** 지금 새 요청에 쓸 수 있는 금액 */
  availableBalance: number
  /** 입찰·베팅으로 사용이 제한된 금액 */
  heldBalance: number
}

/** 원장 한 지갑분의 커서 상태 */
interface LedgerPage {
  items: LedgerEntry[]
  nextCursor: string | null
  hasNext: boolean
  loading: boolean
  /** 원장 조회 실패 사유. **잔액 조회 실패(`loadError`)와 분리한다** — 표시 자리가 다르다 */
  error: string | null
}

interface WalletState {
  wallets: Wallet[]
  tradeLedger: LedgerPage
  bettingLedger: LedgerPage
  /** 화면에서 보고 있는 지갑 (원장도 이 값을 따라간다) */
  activeWalletType: WalletType
  loaded: boolean
  loadError: string | null
}

const emptyWallet = (walletType: WalletType): Wallet => ({
  walletType,
  availableBalance: 0,
  heldBalance: 0,
})

const emptyPage = (): LedgerPage => ({
  items: [],
  nextCursor: null,
  hasNext: false,
  loading: false,
  error: null,
})

const toWallet = (walletType: WalletType, balance: WalletBalance): Wallet => ({
  walletType,
  availableBalance: balance.availableAmount,
  heldBalance: balance.heldAmount,
})

/**
 * 진행 중인 지갑 조회. 화면 여럿이 `onMounted`에서 부르고(마이페이지·지갑·충전·출금·라이브)
 * 겹치면 같은 조회가 여러 번 나간다. `stores/auth.ts`의 `inFlightTerms`와 같은 방식이다.
 *
 * **이것이 예전 `loaded` 가드를 대신한다** (S15P11A704-333). 가드는 중복을 막는 대신
 * 충전 후 재조회까지 막아서 잔액이 세션 내내 얼어붙었다 — 겹침만 막고 재조회는 열어 둔다.
 */
let inFlightWallet: Promise<void> | null = null

/**
 * 회원 세대 (S15P11A704-332).
 *
 * `$reset()`은 스토어 **상태**만 비운다 — 모듈 변수인 `inFlightWallet`은 그대로다.
 * 그래서 회원이 바뀌어도 **떠 있던 이전 회원의 요청이 살아 있고**, 그 응답이 도착하면
 * 비워 둔 스토어에 남의 잔액이 기록된다. 게다가 그동안 들어온 `loadWallet()`은
 * `if (inFlightWallet) return`으로 **그 프라미스를 재사용**해서 새 요청이 아예 안 나간다.
 *
 * `stores/auth.ts`의 `resetMemberScope` 주석이 "이전 회원 잔액이 그대로 보인다"를 막으려고
 * 존재한다고 적어 둔 바로 그 사고인데, 모듈 레벨 single-flight가 그 방어를 통과한다.
 * 그래서 **await를 건넌 뒤에는 자기 세대일 때만 쓴다** (S15P11A704-329·432와 같은 방식).
 */
let walletGeneration = 0

/**
 * 원장 한 번에 받을 행 수. 명세 기본값은 20이지만 **화면 페이지 크기를 그대로 보낸다** —
 * 서버가 준 20을 받아 화면에서 8씩 잘라 보여주면 "더 보기"가 세 번은 서버를 안 거치고,
 * 그 사이 새 행이 생겨도 안 보인다. 요청 단위와 표시 단위를 맞춘다. (최대 100)
 */
const LEDGER_PAGE_SIZE = 8

export const useWalletStore = defineStore('wallet', {
  state: (): WalletState => ({
    wallets: [],
    tradeLedger: emptyPage(),
    bettingLedger: emptyPage(),
    activeWalletType: 'TRADE',
    loaded: false,
    loadError: null,
  }),
  getters: {
    /** 거래 지갑 — 충전·출금 대상 */
    tradeWallet: (state): Wallet =>
      state.wallets.find((w) => w.walletType === 'TRADE') ?? emptyWallet('TRADE'),
    /** 베팅 지갑 — 무료 재화라 충전·출금 대상이 아니다 */
    bettingWallet: (state): Wallet =>
      state.wallets.find((w) => w.walletType === 'BETTING') ?? emptyWallet('BETTING'),
    /** 총 보유 = 사용 가능 + 보류 (available이 이미 보류 제외 값이므로 빼지 않고 더한다) */
    tradeTotal(): number {
      return this.tradeWallet.availableBalance + this.tradeWallet.heldBalance
    },
    bettingTotal(): number {
      return this.bettingWallet.availableBalance + this.bettingWallet.heldBalance
    },
    activeWallet(): Wallet {
      return this.activeWalletType === 'TRADE' ? this.tradeWallet : this.bettingWallet
    },
    activeTotal(): number {
      return this.activeWallet.availableBalance + this.activeWallet.heldBalance
    },
    /** 현재 지갑의 원장 행 — 재화가 섞이면 금액 단위(P/BP)가 한 줄에 뒤엉킨다 */
    activeLedger(): LedgerEntry[] {
      return this.activeWalletType === 'TRADE' ? this.tradeLedger.items : this.bettingLedger.items
    },
    /** 더 받을 페이지가 있는지 — "더 보기" 버튼의 근거 */
    activeLedgerHasNext(): boolean {
      return this.activeWalletType === 'TRADE'
        ? this.tradeLedger.hasNext
        : this.bettingLedger.hasNext
    },
    activeLedgerLoading(): boolean {
      return this.activeWalletType === 'TRADE'
        ? this.tradeLedger.loading
        : this.bettingLedger.loading
    },
    activeLedgerError(): string | null {
      return this.activeWalletType === 'TRADE' ? this.tradeLedger.error : this.bettingLedger.error
    },
  },
  actions: {
    /**
     * 회원이 바뀔 때 떠 있는 조회를 버린다. **`$reset()`과 함께 부른다** —
     * 세대를 올려 이전 응답이 스토어에 쓰지 못하게 하고, 재사용될 프라미스도 끊는다.
     */
    discardInFlight() {
      walletGeneration += 1
      inFlightWallet = null
    },

    /** PNT-01 지갑 잔액 + PNT-02 원장 첫 페이지 */
    loadWallet(): Promise<void> {
      if (inFlightWallet) return inFlightWallet
      const generation = walletGeneration
      this.loadError = null
      inFlightWallet = (async () => {
        try {
          const wallets = await getMyWallets()
          if (generation !== walletGeneration) return
          this.wallets = [
            toWallet('TRADE', wallets.tradeWallet),
            toWallet('BETTING', wallets.bettingWallet),
          ]
          // 두 재화의 원장은 따로 페이지를 센다 — 합치면 커서가 엉킨다
          await Promise.all([this.reloadLedger('TRADE'), this.reloadLedger('BETTING')])
          if (generation !== walletGeneration) return
          this.loaded = true
        } catch (error) {
          if (generation !== walletGeneration) return
          this.loadError = toApiError(error).message
        } finally {
          // **내 세대일 때만** 슬롯을 비운다. 남의 것을 지우면 그사이 시작된 새 회원의
          // 조회가 single-flight 밖으로 새어 중복 요청이 된다.
          if (generation === walletGeneration) inFlightWallet = null
        }
      })()
      return inFlightWallet
    },

    setActiveWalletType(walletType: WalletType) {
      this.activeWalletType = walletType
    },

    /**
     * 원장 첫 페이지를 다시 받는다 (충전·출금 직후처럼 앞이 바뀌었을 때).
     *
     * ⚠️ **절대 throw하지 않는다.** 이건 화면 새로고침이지 그 자체가 사용자의 요청이 아니다.
     * 충전 성공 뒤에 이게 던지면 호출부의 catch가 **충전이 실패한 것으로 보고**하고,
     * 사용자는 돈이 나간 줄 모르고 다시 누른다 — 이중 충전이 된다.
     */
    async reloadLedger(walletType: WalletType) {
      const generation = walletGeneration
      const page = walletType === 'TRADE' ? this.tradeLedger : this.bettingLedger
      page.loading = true
      page.error = null
      try {
        const result = await getPointLedgers({ walletType, size: LEDGER_PAGE_SIZE })
        // 잔액과 같은 이유로 세대를 대조한다 — 남의 원장이 새 회원 화면에 남으면 안 된다
        if (generation !== walletGeneration) return
        page.items = result.items
        page.nextCursor = result.nextCursor
        page.hasNext = result.hasNext
      } catch (error) {
        if (generation !== walletGeneration) return
        page.error = toApiError(error).message
      } finally {
        if (generation === walletGeneration) page.loading = false
      }
    },

    /**
     * 원장 다음 페이지. 커서는 서버가 준 불투명 문자열을 그대로 되돌려준다 —
     * 명세: "프론트는 커서 내부를 해석하지 않고 다음 요청에 그대로 전달한다".
     */
    async loadMoreLedger() {
      const generation = walletGeneration
      const walletType = this.activeWalletType
      const page = walletType === 'TRADE' ? this.tradeLedger : this.bettingLedger
      if (page.loading || !page.hasNext || page.nextCursor === null) return
      page.loading = true
      try {
        const result = await getPointLedgers({
          walletType,
          cursor: page.nextCursor,
          size: LEDGER_PAGE_SIZE,
        })
        if (generation !== walletGeneration) return
        page.items = [...page.items, ...result.items]
        page.nextCursor = result.nextCursor
        page.hasNext = result.hasNext
      } catch (error) {
        if (generation !== walletGeneration) return
        // 더 보기 실패는 이미 보이는 목록을 지우지 않는다 — 다시 누르면 된다
        page.error = toApiError(error).message
      } finally {
        if (generation === walletGeneration) page.loading = false
      }
    },

    /**
     * PNT-01 거래 포인트 충전 (P0 원클릭 — 실결제 없음).
     * 응답이 갱신된 잔액을 주므로 **낙관 반영 대신 서버 값을 그대로 쓴다.**
     *
     * @param idempotencyKey 재시도 시 **같은 값**을 넘겨야 이중 충전이 막힌다
     *   (`shared/api/idempotency.ts`: "키 생성은 제출 버튼을 누르는 시점에 한 번만").
     *   생략하면 새로 만든다 = 새 충전으로 취급된다. `submitPrediction`과 같은 규약이다.
     * @returns 실패 사유 (성공이면 null)
     */
    async chargeTradePoint(
      amount: number,
      idempotencyKey: string = newRequestId(),
    ): Promise<string | null> {
      if (amount <= 0) return '충전 금액을 입력해 주세요'
      try {
        const result = await chargeTradePointApi(amount, idempotencyKey)
        this.applyTradeBalance(result.availableAmount, result.heldAmount)
        // 원장 앞에 새 행이 생겼다 — 첫 페이지를 다시 받는다
        await this.reloadLedger('TRADE')
        return null
      } catch (error) {
        return toApiError(error).message
      }
    },

    /**
     * PNT-01 거래 포인트 환불(= 화면상 "출금").
     * 보류분은 대상이 아니라 가용 잔액이 상한이다 — 최종 판정은 서버가 한다.
     * @param idempotencyKey 충전과 같은 규약 — 재시도면 같은 값을 넘긴다
     * @returns 실패 사유 (성공이면 null)
     */
    async withdrawTradePoint(
      amount: number,
      idempotencyKey: string = newRequestId(),
    ): Promise<string | null> {
      if (amount <= 0) return '출금 금액을 입력해 주세요'
      // 잔액을 못 받아온 상태(폴백 0P)에서 "잔액 초과"라고 하면 **원인을 잘못 알려준다**
      if (!this.loaded) return '잔액을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
      if (amount > this.tradeWallet.availableBalance) return '사용 가능 잔액을 넘을 수 없어요'
      try {
        const result = await refundTradePointApi(amount, idempotencyKey)
        this.applyTradeBalance(result.availableAmount, result.heldAmount)
        await this.reloadLedger('TRADE')
        return null
      } catch (error) {
        return toApiError(error).message
      }
    },

    /** 거래 지갑 잔액을 서버 응답으로 덮는다 (지갑 행이 아직 없으면 만든다) */
    applyTradeBalance(availableAmount: number, heldAmount: number) {
      const wallet = this.wallets.find((w) => w.walletType === 'TRADE')
      if (wallet) {
        wallet.availableBalance = availableAmount
        wallet.heldBalance = heldAmount
        return
      }
      this.wallets.push({
        walletType: 'TRADE',
        availableBalance: availableAmount,
        heldBalance: heldAmount,
      })
    },
  },
})
