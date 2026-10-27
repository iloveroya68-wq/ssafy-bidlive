<script setup lang="ts">
// MEM-02 마이페이지 「경매 목록」 패널 — 와이어프레임 mypage의 `deals` 패널.
// 서브 탭 3개(낙찰 받은 / 개최한 / 참여한)는 각각 다른 API이고 **응답 필드가 서로 다르다**.
// 그래서 카드도 탭별로 그린다 — 공통 카드로 합치면 대부분이 옵셔널이 되어 무엇을 그릴 수 있는지 흐려진다.
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { toApiError } from '@/shared/api/errors'
import {
  getHostedAuctions,
  getParticipatedAuctions,
  getWonAuctions,
  type HostedAuction,
  type ParticipatedAuction,
  type WonAuction,
} from '@/shared/api/myAuctions'
import { dealStatusLabel } from '@/shared/lib/dealStatus'
import { formatPoint } from '@/shared/lib/point'
import type { DealActionTarget } from '@/features/deal/useDealAction'
import type { Id } from '@/shared/api/types'

type DealTab = 'won' | 'hosted' | 'joined'

const TABS: { key: DealTab; label: string }[] = [
  { key: 'won', label: '낙찰 받은 경매' },
  { key: 'hosted', label: '개최한 경매' },
  { key: 'joined', label: '참여한 경매' },
]

// DEAL-05 액션은 화면(`MyPageView`)의 `useDealAction` 하나가 처리한다 — 「지금 할 일」 탭과 같은 액션이라
// 패널마다 두면 모달이 여러 개가 된다. 여기서는 눌린 대상만 올려보낸다(S15P11A704-337).
const props = defineProps<{
  confirmingDealId: Id | null
  pendingDealId: Id | null
  actionError: string | null
  errorDealId: Id | null
}>()

const emit = defineEmits<{ action: [target: DealActionTarget] }>()

const activeTab = ref<DealTab>('won')
const pending = ref(false)
const loadError = ref<string | null>(null)

const won = ref<WonAuction[]>([])
const hosted = ref<HostedAuction[]>([])
const joined = ref<ParticipatedAuction[]>([])

/** 이미 받아 온 탭은 다시 부르지 않는다 — 탭을 오갈 때마다 재조회할 이유가 없다 */
const fetched = ref<Set<DealTab>>(new Set())

const isEmpty = computed(() => {
  if (activeTab.value === 'won') return won.value.length === 0
  if (activeTab.value === 'hosted') return hosted.value.length === 0
  return joined.value.length === 0
})

const fmtDate = (iso: string | null) =>
  iso === null ? '' : new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })

async function load(tab: DealTab) {
  if (fetched.value.has(tab)) return
  pending.value = true
  loadError.value = null
  try {
    if (tab === 'won') won.value = (await getWonAuctions()).items
    else if (tab === 'hosted') hosted.value = (await getHostedAuctions()).items
    else joined.value = (await getParticipatedAuctions()).items
    fetched.value = new Set(fetched.value).add(tab)
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    pending.value = false
  }
}

watch(activeTab, (tab) => void load(tab), { immediate: true })

/**
 * 거래 전이 후 화면이 부른다 — 상태 라벨과 액션 여부가 바뀌므로 캐시를 버리고 다시 받는다.
 * 지금 보고 있는 탭만 다시 받는다. 안 보이는 탭은 열 때 어차피 비어 있는 캐시로 조회된다.
 */
async function refresh(): Promise<void> {
  const tab = activeTab.value
  const next = new Set(fetched.value)
  next.delete(tab)
  fetched.value = next
  await load(tab)
}

defineExpose({ refresh })

/** 낙찰·개최 목록에서 액션 버튼을 그릴 수 있는 조건 — `dealId`가 있어야 부를 API가 정해진다 */
function actionable(item: {
  actionRequired: boolean
  actionCode: string | null
  actionLabel: string | null
  dealId: Id | null
}): boolean {
  return (
    item.actionRequired &&
    item.actionCode !== null &&
    item.actionLabel !== null &&
    item.dealId !== null
  )
}

function targetOf(item: {
  dealId: Id | null
  actionCode: string | null
  productTitle: string
  finalPrice: number | null
}): DealActionTarget {
  return {
    // `actionable()`을 통과한 항목만 넘어온다 — 그때 둘 다 non-null이다
    dealId: item.dealId as Id,
    actionCode: item.actionCode as string,
    productTitle: item.productTitle,
    finalPrice: item.finalPrice,
  }
}

function buttonLabel(item: { dealId: Id | null; actionLabel: string | null }): string {
  if (props.pendingDealId === item.dealId) return '처리 중…'
  if (props.confirmingDealId === item.dealId) return '한 번 더 눌러 확정'
  return item.actionLabel ?? ''
}
</script>

<template>
  <div>
    <!-- 서브 탭 -->
    <div
      class="bg-line inline-flex gap-1 rounded-xl p-1"
      role="tablist"
      aria-label="경매 목록 구분"
    >
      <button
        v-for="tab in TABS"
        :id="`deals-tab-${tab.key}`"
        :key="tab.key"
        type="button"
        role="tab"
        class="rounded-lg px-3.5 py-1.5 text-[12.5px] transition"
        :class="activeTab === tab.key ? 'bg-surface text-ink font-semibold shadow-sm' : 'text-dim'"
        :aria-selected="activeTab === tab.key"
        aria-controls="deals-panel"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div
      id="deals-panel"
      role="tabpanel"
      :aria-labelledby="`deals-tab-${activeTab}`"
      class="mt-3.5"
    >
      <p v-if="pending" class="text-faint py-8 text-center text-[13px]">불러오는 중이에요…</p>
      <p v-else-if="loadError" class="text-red py-8 text-center text-[13px]" role="alert">
        {{ loadError }}
      </p>
      <p v-else-if="isEmpty" class="text-faint py-8 text-center text-[13px]">
        아직 여기 표시할 경매가 없어요.
      </p>

      <!-- 낙찰 받은 경매 — 비크리라 실제 지불액(2등가)과 내 입찰가가 다르다 -->
      <ul v-else-if="activeTab === 'won'" class="flex flex-col gap-2.5">
        <li
          v-for="item in won"
          :key="item.auctionId"
          class="border-line bg-surface rounded-2xl border px-4.5 py-4"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex min-w-0 items-start gap-3">
              <!--
                LIVE-06 낙찰 순간 스냅샷. 판매자가 캡처를 못 올린 낙찰 건도 정상이라 **있을 때만**
                얹는다 — 빈 자리를 만들면 대부분의 카드가 회색 상자를 달고 있게 된다.
              -->
              <img
                v-if="item.snapshotUrl"
                :src="item.snapshotUrl"
                alt="낙찰 순간 스냅샷"
                class="bg-line h-12 w-12 flex-none rounded-xl object-cover"
              />
              <div class="min-w-0">
                <p class="text-ink truncate text-[14px] font-semibold">{{ item.productTitle }}</p>
                <p class="text-faint mt-1 text-[11.5px]">
                  판매자 {{ item.sellerNickname }} · {{ fmtDate(item.endedAt) }} 종료
                </p>
              </div>
            </div>
            <button
              v-if="actionable(item)"
              type="button"
              class="bg-terra text-on-dark hover:bg-terra-d flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
              :disabled="pendingDealId !== null"
              @click="emit('action', targetOf(item))"
            >
              {{ buttonLabel(item) }}
            </button>
            <!-- 모르는 코드는 표시를 생략한다 (`dealStatusLabel`이 빈 문자열을 준다) -->
            <span
              v-else-if="dealStatusLabel(item.dealStatus)"
              class="text-faint flex-none text-[11.5px]"
            >
              {{ dealStatusLabel(item.dealStatus) }}
            </span>
          </div>

          <p v-if="errorDealId === item.dealId" class="text-red mt-2 text-[11.5px]" role="alert">
            {{ actionError }}
          </p>

          <dl class="border-line mt-3 grid grid-cols-2 gap-2 border-t pt-2.5 text-[12px]">
            <div>
              <dt class="text-dim">결제 금액 (2등가)</dt>
              <dd class="text-terra-d mt-0.5 font-bold tabular-nums">
                {{ formatPoint(item.finalPrice) }}
              </dd>
            </div>
            <div class="text-right">
              <dt class="text-dim">내 입찰가</dt>
              <dd class="text-dim mt-0.5 font-semibold tabular-nums">
                {{ formatPoint(item.myFinalBid) }}
              </dd>
            </div>
          </dl>

          <!-- 낙찰 목록은 전부 끝난 경매다 — 결과 화면으로 갈 자리를 항상 둔다 (DEAL-06) -->
          <div class="mt-2.5 text-right">
            <RouterLink
              :to="{ name: 'result', params: { id: item.auctionId } }"
              class="text-terra-d text-[12px] font-semibold"
            >
              결과 보기 →
            </RouterLink>
          </div>
        </li>
      </ul>

      <!-- 개최한 경매 — 진행 중인 방과 끝난 방이 섞인다 -->
      <ul v-else-if="activeTab === 'hosted'" class="flex flex-col gap-2.5">
        <li
          v-for="item in hosted"
          :key="item.auctionId"
          class="border-line bg-surface rounded-2xl border px-4.5 py-4"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-ink truncate text-[14px] font-semibold">{{ item.productTitle }}</p>
              <p class="text-faint mt-1 text-[11.5px] tabular-nums">
                입찰 {{ item.bidCount }}건 · 참여 {{ item.participantCount }}명 · 예측
                {{ item.predictionParticipantCount }}명
              </p>
            </div>
            <button
              v-if="actionable(item)"
              type="button"
              class="bg-terra text-on-dark hover:bg-terra-d flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
              :disabled="pendingDealId !== null"
              @click="emit('action', targetOf(item))"
            >
              {{ buttonLabel(item) }}
            </button>
            <!-- 진행 중은 레드(지금 벌어지는 일), 시작 전은 슬레이트(예정된 일) — 규칙 1의 기능색 구분 -->
            <span
              v-else-if="item.roomStatus === 'LIVE'"
              class="bg-terra text-on-dark flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold"
            >
              진행 중
            </span>
            <span
              v-else-if="item.roomStatus !== 'ENDED'"
              class="bg-slate-tint text-slate-tint-text flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold"
            >
              대기
            </span>
          </div>

          <p v-if="errorDealId === item.dealId" class="text-red mt-2 text-[11.5px]" role="alert">
            {{ actionError }}
          </p>

          <div
            class="border-line mt-3 flex items-center justify-between border-t pt-2.5 text-[12px]"
          >
            <span class="text-dim">
              <template v-if="item.resultType === 'SOLD'"
                >낙찰 · {{ item.winnerNickname }}</template
              >
              <template v-else-if="item.roomStatus === 'ENDED'">유찰</template>
              <template v-else-if="item.roomStatus === 'LIVE'">
                진행 중 · 입찰 {{ item.bidCount }}건
              </template>
              <template v-else>{{ fmtDate(item.scheduledAt) }} 시작 예정</template>
            </span>

            <!-- SOLD는 금액과 링크가 함께 선다 — 낙찰가를 지우고 링크로 바꾸면 목록에서 값이 사라진다 -->
            <div class="flex flex-none items-center gap-2.5">
              <span v-if="item.finalPrice !== null" class="text-terra-d font-bold tabular-nums">
                {{ formatPoint(item.finalPrice) }}
              </span>
              <!-- 끝난 방은 결과 화면, 끝나지 않은 방은 진행 콘솔 (대기·진행 중 모두 콘솔이 받는다) -->
              <RouterLink
                v-if="item.roomStatus === 'ENDED'"
                :to="{ name: 'result', params: { id: item.auctionId } }"
                class="text-terra-d font-semibold"
              >
                결과 보기 →
              </RouterLink>
              <RouterLink
                v-else
                :to="{ name: 'console', params: { id: item.auctionId } }"
                class="text-terra-d font-semibold"
              >
                콘솔 열기 →
              </RouterLink>
            </div>
          </div>
        </li>
      </ul>

      <!-- 참여한 경매 — 입찰과 예측이 섞이고, 보여줄 값이 서로 다르다 -->
      <ul v-else class="flex flex-col gap-2.5">
        <li
          v-for="item in joined"
          :key="item.auctionId"
          class="border-line bg-surface rounded-2xl border px-4.5 py-4"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-ink truncate text-[14px] font-semibold">{{ item.productTitle }}</p>
              <p class="text-faint mt-1 text-[11.5px]">{{ fmtDate(item.endedAt) }} 종료</p>
            </div>
            <span
              class="flex-none rounded-full px-2.5 py-1 text-[11px] font-semibold"
              :class="
                item.participationType === 'BID'
                  ? 'bg-terra-tint text-terra-tint-text'
                  : 'bg-slate-tint text-slate-tint-text'
              "
            >
              {{ item.participationType === 'BID' ? '입찰' : '예측' }}
            </span>
          </div>

          <div
            class="border-line mt-3 flex items-center justify-between border-t pt-2.5 text-[12px]"
          >
            <span class="text-dim">{{ item.resultLabel }}</span>

            <div class="flex flex-none items-center gap-2.5">
              <!-- 입찰: 내 입찰가와 해제된 보류액 -->
              <span v-if="item.participationType === 'BID'" class="text-dim tabular-nums">
                내 입찰 {{ item.myFinalBid === null ? '—' : formatPoint(item.myFinalBid) }}
              </span>
              <!-- 예측: 스테이크 대비 지급액. 오차율은 서버 계산값을 그대로 쓴다 -->
              <span
                v-else
                class="tabular-nums"
                :class="item.payoutAmount ? 'text-success' : 'text-dim'"
              >
                {{ formatPoint(item.stakeAmount ?? 0, 'BETTING') }} →
                {{ formatPoint(item.payoutAmount ?? 0, 'BETTING') }}
              </span>
              <!-- 참여 목록도 전부 끝난 경매다 -->
              <RouterLink
                :to="{ name: 'result', params: { id: item.auctionId } }"
                class="text-terra-d font-semibold"
              >
                결과 보기 →
              </RouterLink>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
