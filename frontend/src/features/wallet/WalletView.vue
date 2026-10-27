<script setup lang="ts">
// PNT-01·02 이중 지갑 + 원장 (FE1)
//
// 스키마 제약이 화면에 그대로 반영돼 있다 (docs/04-백엔드-스키마-메모.md §3.5):
// - 지갑은 회원당 TRADE / BETTING 2행이고 상호 전환 개념이 없다 → 카드 2장, 이체 UI 없음.
// - `available_balance`는 이미 보류를 제외한 값 → "사용 가능 / 보류 중 / 총 보유"를 각각 표시한다.
//   총 보유 = available + held. 여기서 보류를 다시 빼면 이중 차감이다.
// - 원장은 `trade_point_ledger`/`betting_point_ledger` 두 테이블로 분리 → 병합하지 않고 탭으로 나눈다.
// - 홀드 행은 두 델타의 합이 0이라 "증감 0"으로 보인다 → 금액 대신 "보류/해제" 라벨과 이동 방향으로 렌더.
// - 원장은 append-only다 → 수정·삭제 UI를 두지 않는다.
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWalletStore } from '@/stores/wallet'
import type { LedgerEntry, WalletType } from '@/stores/wallet'
import { formatLedgerTime, formatPoint, formatSignedPoint } from '@/shared/lib/point'
import { curOf, isLedgerCurrency, walletTypeOfCur } from './ledgerQuery'
import {
  holdDirectionLabel,
  isHoldTransfer,
  ledgerKindLabel,
  ledgerNetDelta,
  ledgerTone,
  type LedgerTone,
} from './ledgerLabel'

const route = useRoute()
const router = useRouter()
const wallet = useWalletStore()

onMounted(() => {
  // 매번 새로 받는다. 충전·출금하고 돌아오는 동선이라 잔액이 굳어 있으면 안 된다 —
  // 중복 조회는 스토어의 single-flight가 막는다 (S15P11A704-333에서 `loaded` 가드를 걷어냈다).
  void wallet.loadWallet()
})

const WALLET_TABS: { type: WalletType; label: string }[] = [
  { type: 'TRADE', label: '거래 포인트' },
  { type: 'BETTING', label: '베팅 포인트' },
]

// ── `?cur=` 딥링크 (S15P11A704-340) ──
// 쿼리가 정본이고 화면 상태(스토어)는 그 사본이다 — MyPageView의 `?tab=`과 같은 규약.
// 탭 자체는 wallet 스토어가 들고 있어서(원장 페이징이 같이 걸려 있다) 여기서는 쿼리 ↔ 스토어를 잇기만 한다.
// 값 매핑은 보내는 쪽(마이페이지 지갑 카드)과 공유해야 해서 `ledgerQuery.ts`에 있다.

/**
 * 알아볼 수 없는 `?cur=` 값은 URL에서도 정리한다.
 * 화면만 기본 탭으로 떨어뜨리고 주소를 그대로 두면 공유·새로고침 때마다 같은 어긋남이 반복된다.
 */
function normalizeCurQuery() {
  const raw = route.query.cur
  if (raw === undefined || isLedgerCurrency(raw)) return
  void router.replace({ query: { ...route.query, cur: curOf(wallet.activeWalletType) } })
}

/** 탭 전환은 쿼리를 바꾸는 것으로 처리한다 — 필터 조작이 뒤로가기 이력을 더럽히지 않게 replace */
function selectWalletType(type: WalletType) {
  if (type === wallet.activeWalletType) return
  void router.replace({ query: { ...route.query, cur: curOf(type) } })
}

// 쿼리 → 스토어. 같은 라우트 안에서 쿼리만 바뀌면 재마운트가 없으므로 정규화도 여기서 한다.
// `immediate`라 진입 시점에도 돈다 — 쿼리 없이 들어오면 기본 지갑으로 맞춘다.
watch(
  () => route.query.cur,
  () => {
    wallet.setActiveWalletType(walletTypeOfCur(route.query.cur))
    normalizeCurQuery()
  },
  { immediate: true },
)

// 원장은 서버 커서 페이지다(기본 20). 예전에는 전체를 받아 `slice`로 흉내 냈는데,
// 이제 "더 보기"가 실제로 다음 페이지를 받아 온다.
const visibleLedger = computed(() => wallet.activeLedger)
const hasMore = computed(() => wallet.activeLedgerHasNext)

const ledgerSub = computed(() =>
  wallet.activeWalletType === 'TRADE'
    ? '입찰 보류부터 낙찰 차감까지 전부 남아요'
    : '충전할 수 없는 무료 재화예요 — 가입·출석·적중으로만 받아요',
)

const TONE_BADGE: Record<LedgerTone, string> = {
  up: 'bg-success-tint text-success',
  dn: 'bg-terra-tint text-terra-tint-text',
  hold: 'bg-slate-tint text-slate',
}
const TONE_AMOUNT: Record<LedgerTone, string> = {
  up: 'text-success',
  dn: 'text-terra-d',
  hold: 'text-slate',
}

/** 원장 행의 금액 문구 — 보류 이동 행은 총 증감이 0이라 이동한 보류 금액을 부호 없이 보여준다 */
function amountText(entry: LedgerEntry): string {
  if (isHoldTransfer(entry)) return formatPoint(Math.abs(entry.heldChange), entry.walletType)
  return formatSignedPoint(ledgerNetDelta(entry), entry.walletType)
}

// 충전·출금은 전체 화면으로 옮겼다 — 완료 후 이 화면으로 돌아온다 (슬라이스 5·6)
function goCharge() {
  void router.push({ name: 'wallet-charge', query: { redirect: route.fullPath } })
}

function goWithdraw() {
  void router.push({ name: 'wallet-withdraw', query: { redirect: route.fullPath } })
}
</script>

<template>
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-3xl px-6 py-10">
      <header>
        <h1 class="text-ink text-[30px] font-bold tracking-tight">지갑 · 원장</h1>
        <p class="text-dim mt-1.5 text-[13px]">
          거래 포인트와 베팅 포인트는 별개 지갑이에요 — 서로 옮길 수 없어요.
        </p>
      </header>

      <!-- 조회 실패를 알린다. 안 그러면 폴백 0P가 "돈이 없음"으로 읽힌다 -->
      <p
        v-if="wallet.loadError"
        class="border-terra/40 bg-terra-tint text-terra-tint-text mt-5 rounded-[10px] border p-3 text-[12px] leading-relaxed"
        role="alert"
      >
        {{ wallet.loadError }}
      </p>

      <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <!-- 거래 지갑 (TRADE) — 충전·출금 대상 -->
        <section
          class="bg-terra-tint border-terra/25 rounded-2xl border p-5"
          :class="wallet.activeWalletType === 'TRADE' ? 'ring-terra ring-2' : ''"
          aria-labelledby="trade-wallet-label"
        >
          <div class="flex items-center justify-between gap-2">
            <span
              id="trade-wallet-label"
              class="bg-terra text-on-dark rounded-full px-2.5 py-1 text-[11px] font-semibold whitespace-nowrap"
              >거래 포인트</span
            >
            <div class="flex flex-none gap-1.5">
              <button
                type="button"
                class="border-line text-dim bg-surface rounded-full border px-3 py-1 text-[11.5px] font-medium transition hover:bg-white/70"
                @click="goWithdraw"
              >
                출금
              </button>
              <button
                type="button"
                class="bg-terra text-on-dark hover:bg-terra-d rounded-full px-3 py-1 text-[11.5px] font-semibold transition"
                @click="goCharge"
              >
                충전
              </button>
            </div>
          </div>

          <div class="text-terra-d mt-3 text-[32px] leading-none font-bold tabular-nums">
            {{ formatPoint(wallet.tradeWallet.availableBalance) }}
          </div>
          <div class="text-terra mt-1.5 text-[11.5px]">사용 가능 · 보류를 제외한 금액</div>

          <dl class="border-terra/20 mt-4 grid grid-cols-2 gap-2 border-t pt-3 text-[12px]">
            <div>
              <dt class="text-dim">보류 중</dt>
              <dd class="text-ink mt-0.5 font-semibold tabular-nums">
                {{ formatPoint(wallet.tradeWallet.heldBalance) }}
              </dd>
            </div>
            <div class="text-right">
              <dt class="text-dim">총 보유</dt>
              <dd class="text-ink mt-0.5 font-semibold tabular-nums">
                {{ formatPoint(wallet.tradeTotal) }}
              </dd>
            </div>
          </dl>

          <button
            type="button"
            class="border-terra/20 text-terra-d mt-3 flex w-full items-center justify-between border-t pt-3 text-[12px] font-semibold"
            @click="selectWalletType('TRADE')"
          >
            <span>거래 내역 보기</span>
            <span aria-hidden="true">→</span>
          </button>
        </section>

        <!-- 베팅 지갑 (BETTING) — 무료 재화라 충전·출금·전환이 없다 -->
        <section
          class="bg-slate-tint border-slate/40 rounded-2xl border p-5"
          :class="wallet.activeWalletType === 'BETTING' ? 'ring-slate ring-2' : ''"
          aria-labelledby="betting-wallet-label"
        >
          <div class="flex items-center justify-between gap-2">
            <span
              id="betting-wallet-label"
              class="bg-slate text-on-dark rounded-full px-2.5 py-1 text-[11px] font-semibold whitespace-nowrap"
              >베팅 포인트</span
            >
            <span class="text-slate text-[11px]">무료 재화 · 전환 불가</span>
          </div>

          <div class="text-slate mt-3 text-[32px] leading-none font-bold tabular-nums">
            {{ formatPoint(wallet.bettingWallet.availableBalance, 'BETTING') }}
          </div>
          <div class="text-slate mt-1.5 text-[11.5px]">사용 가능 · 보류를 제외한 금액</div>

          <dl class="border-slate/25 mt-4 grid grid-cols-2 gap-2 border-t pt-3 text-[12px]">
            <div>
              <dt class="text-dim">보류 중</dt>
              <dd class="text-ink mt-0.5 font-semibold tabular-nums">
                {{ formatPoint(wallet.bettingWallet.heldBalance, 'BETTING') }}
              </dd>
            </div>
            <div class="text-right">
              <dt class="text-dim">총 보유</dt>
              <dd class="text-ink mt-0.5 font-semibold tabular-nums">
                {{ formatPoint(wallet.bettingTotal, 'BETTING') }}
              </dd>
            </div>
          </dl>

          <button
            type="button"
            class="border-slate/25 text-slate mt-3 flex w-full items-center justify-between border-t pt-3 text-[12px] font-semibold"
            @click="selectWalletType('BETTING')"
          >
            <span>베팅 내역 보기</span>
            <span aria-hidden="true">→</span>
          </button>
        </section>
      </div>

      <!-- 원장 (PNT-02) -->
      <section class="mt-8">
        <div class="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 class="text-ink text-[20px] font-bold tracking-tight">포인트 내역</h2>
            <p class="text-dim mt-1 text-[12.5px]">{{ ledgerSub }}</p>
          </div>
          <div class="bg-line inline-flex gap-1 rounded-xl p-1">
            <button
              v-for="tab in WALLET_TABS"
              :key="tab.type"
              type="button"
              class="rounded-lg px-4 py-2 text-[13px] transition"
              :class="
                wallet.activeWalletType === tab.type
                  ? 'text-ink bg-surface font-semibold shadow-sm'
                  : 'text-dim hover:text-ink'
              "
              @click="selectWalletType(tab.type)"
            >
              {{ tab.label }}
            </button>
          </div>
        </div>

        <ul v-if="visibleLedger.length > 0" class="border-line bg-surface mt-4 rounded-2xl border">
          <li
            v-for="entry in visibleLedger"
            :key="entry.ledgerId"
            class="border-line flex items-center gap-3 border-b px-4 py-3.5 last:border-b-0"
          >
            <span
              class="flex-none rounded-full px-2.5 py-1 text-[11px] font-bold"
              :class="TONE_BADGE[ledgerTone(entry)]"
            >
              {{ ledgerKindLabel(entry) }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="text-ink truncate text-[13px] leading-snug">{{ entry.description }}</div>
              <div class="text-faint mt-0.5 text-[11.5px] tabular-nums">
                {{ formatLedgerTime(entry.createdAt) }} · 잔액
                {{ formatPoint(entry.availableBalanceAfter, entry.walletType) }} / 보류
                {{ formatPoint(entry.heldBalanceAfter, entry.walletType) }}
              </div>
            </div>
            <div class="flex-none text-right">
              <div
                class="text-[14px] font-bold tabular-nums"
                :class="TONE_AMOUNT[ledgerTone(entry)]"
              >
                {{ amountText(entry) }}
              </div>
              <!-- 총 보유가 그대로인 행은 금액만 보면 "증감 0"처럼 보인다 — 이동 방향을 함께 적는다 -->
              <div v-if="isHoldTransfer(entry)" class="text-faint mt-0.5 text-[11px]">
                {{ holdDirectionLabel(entry) }}
              </div>
            </div>
          </li>
        </ul>

        <p
          v-else-if="wallet.activeLedgerError"
          class="text-red py-14 text-center text-[13px]"
          role="alert"
        >
          {{ wallet.activeLedgerError }}
        </p>

        <p v-else class="text-faint py-14 text-center text-[13px]">아직 포인트 내역이 없어요</p>

        <div v-if="visibleLedger.length > 0" class="mt-4 text-center">
          <button
            v-if="hasMore"
            type="button"
            class="border-line text-dim hover:bg-cream bg-surface rounded-full border px-5 py-2.5 text-[13px] font-medium transition disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="wallet.activeLedgerLoading"
            @click="wallet.loadMoreLedger()"
          >
            {{ wallet.activeLedgerLoading ? '불러오는 중…' : '더 보기' }}
          </button>
          <p v-else class="text-faint text-[12.5px]">더 이상 내역이 없어요</p>
          <p v-if="wallet.activeLedgerError" class="text-red mt-2 text-[12.5px]" role="alert">
            {{ wallet.activeLedgerError }}
          </p>
        </div>
      </section>
    </div>
  </main>
</template>
