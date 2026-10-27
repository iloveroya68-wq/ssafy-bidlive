<script setup lang="ts">
// MEM-02 마이페이지 (FE1) — data-screen="mypage".
//
// 와이어프레임은 좌측 세로 탭 4개(지갑 / 지금 할 일 / 경매 목록 / 리더보드)다.
// 기본 탭이 지갑인 이유는 원본 주석 그대로다 — 지갑이 비면 아무것도 못 하는 구조라 진입 즉시 잔액을 보여준다.
//
// 1차(S15P11A704-334)는 프로필 헤더 + 탭 틀 + 지갑 탭까지다. 나머지 세 탭은 대응 API가 모두 있으므로
// (「내 거래 할 일 조회」·「낙찰 받은/개최한/참여한 경매 조회」·「일일 예측 리더보드 조회」)
// 다음 슬라이스에서 채운다.
//
// 잔액은 `stores/wallet.ts`(목)를 그대로 읽는다 — 충전·출금·원장 화면과 같은 소스여야 숫자가 어긋나지 않는다.
import { computed, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDealTodos, type DealTodo } from '@/shared/api/deals'
import { toApiError } from '@/shared/api/errors'
import { getMyProfile, type MemberActivitySummary } from '@/shared/api/members'
import { formatPoint } from '@/shared/lib/point'
import { useAuthStore } from '@/stores/auth'
import { useWalletStore, type WalletType } from '@/stores/wallet'
import { curOf } from '@/features/wallet/ledgerQuery'
import ShippingAddressModal from '@/features/deal/ShippingAddressModal.vue'
import { useDealAction } from '@/features/deal/useDealAction'
import LeaderboardPanel from './LeaderboardPanel.vue'
import MyAuctionsPanel from './MyAuctionsPanel.vue'
import MyTodosPanel from './MyTodosPanel.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const wallet = useWalletStore()

type MyTab = 'wallet' | 'todo' | 'deals' | 'board'

const TABS: { key: MyTab; label: string }[] = [
  { key: 'wallet', label: '지갑' },
  { key: 'todo', label: '지금 할 일' },
  { key: 'deals', label: '경매 목록' },
  { key: 'board', label: '리더보드' },
]

const TAB_KEYS = TABS.map((tab) => tab.key)

function isMyTab(value: unknown): value is MyTab {
  return typeof value === 'string' && (TAB_KEYS as string[]).includes(value)
}

function readTabFromQuery(): MyTab {
  return isMyTab(route.query.tab) ? route.query.tab : 'wallet'
}

/**
 * 알아볼 수 없는 `?tab=` 값은 URL에서도 정리한다.
 * 화면만 기본 탭으로 떨어뜨리고 주소를 그대로 두면, 공유·새로고침 때마다 같은 어긋남이 반복된다.
 */
function normalizeTabQuery() {
  const raw = route.query.tab
  if (raw === undefined || isMyTab(raw)) return
  void router.replace({ query: { ...route.query, tab: activeTab.value } })
}

// 탭은 `?tab=`으로 공유·새로고침이 되게 쿼리에 노출한다. 쿼리가 정본이고 화면 상태는 그 사본이다.
const activeTab = ref<MyTab>(readTabFromQuery())

/** 탭 전환은 쿼리를 바꾸는 것으로 처리한다 — 히스토리를 더럽히지 않게 replace */
function selectTab(tab: MyTab) {
  if (tab === activeTab.value) return
  void router.replace({ query: { ...route.query, tab } })
}

// 쿼리 → 화면 상태. 같은 라우트 안에서 쿼리만 바뀌면 재마운트가 없으므로 정규화도 여기서 한다
// (`onMounted`만으로는 `/me?tab=deals` → `/me?tab=bogus` 같은 이동을 놓친다).
watch(
  () => route.query.tab,
  () => {
    activeTab.value = readTabFromQuery()
    normalizeTabQuery()
  },
)
const summary = ref<MemberActivitySummary | null>(null)
const profileError = ref<string | null>(null)

const nickname = computed(() => auth.user?.nickname ?? '')
const avatarInitial = computed(() => nickname.value[0] ?? '?')

/** 활동 요약 한 줄. 값이 오기 전에는 비워 둔다 — 0으로 단정하지 않는다 */
const summaryText = computed(() => {
  const s = summary.value
  if (s === null) return ''
  return `판매 ${s.salesCount} · 구매 ${s.purchaseCount} · 예측 적중률 ${Math.round(s.predictionHitRate)}%`
})

// 「지금 할 일」은 **탭을 열지 않아도** 개수 배지를 보여야 한다(와이어프레임 `todoN`은 상시 노출).
// 그래서 조회를 화면이 맡고 패널에는 결과만 내린다 — 패널이 자체 조회하면 배지가 늦게 뜬다.
const todos = ref<DealTodo[]>([])
const todoCount = ref<number | null>(null)
const todosPending = ref(true)
const todosError = ref<string | null>(null)

async function loadTodos() {
  todosError.value = null
  try {
    const response = await getDealTodos()
    todos.value = response.items
    todoCount.value = response.totalCount
  } catch (error) {
    todosError.value = toApiError(error).message
  } finally {
    todosPending.value = false
  }
}

onMounted(async () => {
  normalizeTabQuery()
  // 매번 새로 받는다 — 마이페이지는 충전·출금하고 돌아오는 자리다.
  // 중복 조회는 스토어의 single-flight가 막는다 (S15P11A704-333에서 `loaded` 가드 제거).
  void wallet.loadWallet()
  try {
    summary.value = (await getMyProfile()).activitySummary
  } catch (error) {
    profileError.value = toApiError(error).message
  }

  await loadTodos()
})

// ── DEAL-05 거래 액션 ──
//
// 세 패널 자리(지금 할 일 · 낙찰 · 개최)가 같은 액션을 쓰므로 composable도 모달도 **하나만** 둔다.
// 패널마다 두면 모달이 셋이 되고, 어느 게 열렸는지에 따라 동작이 갈리는 상태가 생긴다.
const auctionsPanel = useTemplateRef('auctionsPanel')

const {
  addressTarget,
  confirmingDealId,
  pendingDealId,
  error: dealActionError,
  errorDealId: dealActionErrorId,
  act,
  closeAddress,
  submitAddress,
  disarmConfirm,
} = useDealAction(async () => {
  // 전이가 끝나면 그 건은 내 할 일에서 빠지고 경매 목록의 상태 라벨도 바뀐다 — 둘 다 다시 받는다
  await Promise.all([loadTodos(), auctionsPanel.value?.refresh() ?? Promise.resolve()])
})

/**
 * 탭이 바뀌면 수령 확인 2단계를 푼다 (S15P11A704-382).
 *
 * `useDealAction`이 하나뿐이라 무장 상태도 패널 사이에 공유되는데, 같은 거래가
 * 「지금 할 일」과 「경매 목록」 **양쪽에서** 액션 버튼을 갖는다(`myAuctions`의 `actionCode`).
 * 그래서 한 탭에서 1회 + 다른 탭에서 1회를 누르면 **두 번째 클릭이 곧바로 대금 지급**이 된다 —
 * 각 탭에서 한 번씩 눌렀을 뿐이라 사용자는 되돌릴 수 없는 동작을 한 것을 모른다.
 *
 * 위 탭 watch에 합치지 않은 이유는 `disarmConfirm`이 여기서 선언돼 그 시점엔 아직 없기 때문이다.
 * 지키는 대상 옆에 두는 편이 "왜 여기서 푸는지"도 잘 읽힌다.
 */
watch(activeTab, disarmConfirm)

/**
 * 원장 화면은 활성 지갑 타입으로 탭이 갈린다 — 어느 카드에서 들어왔는지 `?cur=`로 넘겨 준다.
 * 스토어를 직접 세팅하면 안 된다(S15P11A704-340): 원장 화면이 쿼리를 정본으로 읽으므로,
 * 쿼리 없이 보내면 진입 직후 기본 지갑으로 덮인다.
 */
function goLedger(walletType: WalletType) {
  void router.push({ path: '/wallet/ledger', query: { cur: curOf(walletType) } })
}

// 복귀 경로에 현재 탭이 실려야 충전·출금을 마치고 돌아왔을 때 보던 탭이 유지된다
function goCharge() {
  void router.push({ name: 'wallet-charge', query: { redirect: route.fullPath } })
}

function goWithdraw() {
  void router.push({ name: 'wallet-withdraw', query: { redirect: route.fullPath } })
}

async function logout() {
  // 서버가 Refresh 쿠키를 지울 때까지 기다린다. 먼저 이동하면 화면만 로그아웃처럼 보이고
  // 새로고침 시 부팅 refresh가 도로 로그인시킨다 (S15P11A704-223).
  await auth.logout()
  // 로그인 화면이 아니라 홈으로 보낸다 — 로그아웃은 "나가기"지 "다시 로그인하기"가 아니다.
  // `replace`인 이유: `push`면 히스토리에 /me가 남아 뒤로가기로 되돌아온다(가드가 튕겨 내긴 하지만
  // 뒤로가기가 제자리를 맴돌게 된다).
  void router.replace({ name: 'home' })
}
</script>

<template>
  <!-- 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))]">
    <div class="mx-auto max-w-[1100px] px-6 py-10">
      <!-- 프로필 헤더 -->
      <header class="mb-6.5 flex items-center gap-4">
        <div
          class="bg-terra-tint text-terra-tint-text flex h-15 w-15 flex-none items-center justify-center rounded-full text-2xl font-semibold"
          aria-hidden="true"
        >
          {{ avatarInitial }}
        </div>
        <div class="min-w-0 flex-1">
          <h1 class="text-ink truncate text-2xl font-bold tracking-tight">{{ nickname }}</h1>
          <p v-if="summaryText" class="text-faint mt-0.5 text-[13px] tabular-nums">
            {{ summaryText }}
          </p>
          <p v-else-if="profileError" class="text-faint mt-0.5 text-[12px]">{{ profileError }}</p>
        </div>
        <button
          type="button"
          class="border-line text-dim hover:bg-surface flex-none rounded-xl border px-4.5 py-2.5 text-[13px] transition"
          @click="logout"
        >
          로그아웃
        </button>
      </header>

      <div class="grid gap-5 lg:grid-cols-[180px_1fr]">
        <!-- 좌측 세로 탭 (좁은 폭에서는 가로 스크롤) -->
        <!-- 넓은 폭에선 세로 탭. `self-start`가 없으면 그리드 셀 높이만큼 버튼이 늘어난다 -->
        <div
          class="flex gap-2 overflow-x-auto lg:flex-col lg:self-start lg:overflow-visible"
          role="tablist"
          aria-label="마이페이지 탭"
        >
          <button
            v-for="tab in TABS"
            :id="`mypage-tab-${tab.key}`"
            :key="tab.key"
            type="button"
            role="tab"
            class="flex-none rounded-xl px-4 py-2.5 text-left text-[13.5px] font-semibold transition"
            :class="
              activeTab === tab.key
                ? 'bg-ink text-on-dark'
                : 'text-dim hover:bg-surface border-line border'
            "
            :aria-selected="activeTab === tab.key"
            :aria-controls="`mypage-panel-${tab.key}`"
            @click="selectTab(tab.key)"
          >
            {{ tab.label }}
            <span
              v-if="tab.key === 'todo' && todoCount !== null && todoCount > 0"
              class="bg-terra text-on-dark ml-1.5 rounded-full px-1.5 py-0.5 text-[10.5px] tabular-nums"
            >
              {{ todoCount }}
            </span>
          </button>
        </div>

        <!-- 지갑 탭 -->
        <section
          v-if="activeTab === 'wallet'"
          id="mypage-panel-wallet"
          role="tabpanel"
          aria-labelledby="mypage-tab-wallet"
          class="grid gap-4 sm:grid-cols-2"
        >
          <!-- 거래 지갑 — 충전·출금 대상 -->
          <div class="bg-terra-tint border-terra/25 rounded-[18px] border p-5.5">
            <div class="flex items-center justify-between gap-2">
              <span
                class="bg-terra text-on-dark rounded-full px-2.5 py-1 text-[11px] font-semibold"
              >
                거래 포인트
              </span>
              <div class="flex flex-none gap-1.5">
                <button
                  type="button"
                  class="border-line text-dim bg-surface rounded-full border px-3 py-1 text-[11.5px] font-medium transition"
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

            <div class="text-terra-d mt-2.5 text-[36px] leading-none font-bold tabular-nums">
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
              @click="goLedger('TRADE')"
            >
              <span>거래 내역 보기</span>
              <span aria-hidden="true">→</span>
            </button>
          </div>

          <!-- 베팅 지갑 — 무료 재화라 충전·출금·전환이 없다 -->
          <div class="bg-slate-tint border-slate rounded-[18px] border-2 p-5.5">
            <div class="flex items-center justify-between gap-2">
              <span
                class="bg-slate text-on-dark rounded-full px-2.5 py-1 text-[11px] font-semibold"
              >
                베팅 포인트
              </span>
              <span class="text-slate-tint-text text-[11px]">무료 재화 · 전환 불가</span>
            </div>

            <div class="text-slate mt-2.5 text-[36px] leading-none font-bold tabular-nums">
              {{ formatPoint(wallet.bettingWallet.availableBalance, 'BETTING') }}
            </div>
            <div class="text-slate-tint-text mt-1.5 text-[11.5px]">
              사용 가능 · 보류를 제외한 금액
            </div>

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
              class="border-slate/25 text-slate-tint-text mt-3 flex w-full items-center justify-between border-t pt-3 text-[12px] font-semibold"
              @click="goLedger('BETTING')"
            >
              <span>베팅 내역 보기</span>
              <span aria-hidden="true">→</span>
            </button>
          </div>
        </section>

        <!-- 경매 목록 탭 -->
        <section
          v-else-if="activeTab === 'deals'"
          id="mypage-panel-deals"
          role="tabpanel"
          aria-labelledby="mypage-tab-deals"
        >
          <MyAuctionsPanel
            ref="auctionsPanel"
            :confirming-deal-id="confirmingDealId"
            :pending-deal-id="pendingDealId"
            :action-error="dealActionError"
            :error-deal-id="dealActionErrorId"
            @action="act"
          />
        </section>

        <!-- 지금 할 일 탭 -->
        <section
          v-else-if="activeTab === 'todo'"
          id="mypage-panel-todo"
          role="tabpanel"
          aria-labelledby="mypage-tab-todo"
        >
          <MyTodosPanel
            :items="todos"
            :pending="todosPending"
            :load-error="todosError"
            :confirming-deal-id="confirmingDealId"
            :pending-deal-id="pendingDealId"
            :action-error="dealActionError"
            :error-deal-id="dealActionErrorId"
            @action="act"
          />
        </section>

        <!-- 리더보드 탭 -->
        <section v-else id="mypage-panel-board" role="tabpanel" aria-labelledby="mypage-tab-board">
          <LeaderboardPanel />
        </section>
      </div>
    </div>

    <!-- DEAL-05 배송지 — 세 자리가 공유하는 단일 모달. 결과 화면 진입은 S15P11A704-268 -->
    <ShippingAddressModal
      v-if="addressTarget"
      :product-title="addressTarget.productTitle"
      :final-price="addressTarget.finalPrice"
      :pending="pendingDealId !== null"
      :submit-error="dealActionError"
      @close="closeAddress"
      @submit="submitAddress"
    />
  </main>
</template>
