<script setup lang="ts">
// DEAL-06 전체 입찰 내역 (S15P11A704-67). 디자인 원본 `#ledger`.
//
// AGENTS.md 규칙 6: 전체 입찰 내역은 **종료 후 결과 화면에서만**, 그것도 마스킹해서 공개한다.
// 마스킹은 전부 서버가 한다 — 프론트는 `bidderLabel`을 그대로 그리고, 본인 행만 서버가 `"나"`로 내려준다.
// 낙찰자도 예외 없이 마스킹 대상이다(금액이 함께 붙는 만큼 오히려 더 민감하다).
import { computed } from 'vue'
import { formatPoint } from '@/shared/lib/point'
import { MY_BID_LABEL, type MaskedBidEntry } from '@/shared/api/bids'

const props = defineProps<{
  bids: MaskedBidEntry[]
  /** 2등가로 확정된 최종 낙찰가 */
  finalPrice: number
  /** `SECOND_PRICE`일 때만 2등 행이 낙찰가와 같다 (단독 입찰이면 하한가로 결정된다) */
  isSecondPrice: boolean
}>()

/**
 * 명세 「비크리 엔진」 §순위 판정: 금액 내림차순 → 최종 접수 시각 오름차순.
 * 서버가 이미 정렬해 주더라도 화면이 순위를 매기므로 같은 규칙으로 한 번 더 고정한다.
 */
const rows = computed(() =>
  [...props.bids]
    .sort((a, b) => b.amount - a.amount || Date.parse(a.submittedAt) - Date.parse(b.submittedAt))
    .map((bid, index) => ({
      rank: index + 1,
      isWinner: index === 0,
      // 낙찰가와 같은 금액의 2등 행 — "이 금액을 낙찰자가 낸다"가 비크리 설명의 핵심이다
      isFinalPrice: props.isSecondPrice && index === 1 && bid.amount === props.finalPrice,
      isMine: bid.bidderLabel === MY_BID_LABEL,
      label: bid.bidderLabel,
      amount: bid.amount,
      time: formatClock(bid.submittedAt),
    })),
)

/** 접수 시각 — 같은 방송 안의 순서를 읽는 값이라 시:분:초까지만 */
function formatClock(iso: string): string {
  const parsed = Date.parse(iso)
  if (Number.isNaN(parsed)) return '—'
  const date = new Date(parsed)
  return [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((part) => String(part).padStart(2, '0'))
    .join(':')
}
</script>

<template>
  <section class="border-line bg-surface rounded-[16px] border p-5" aria-label="전체 입찰 내역">
    <h3 class="text-ink mb-3 text-[13px] font-semibold">전체 입찰 내역 (종료 후 공개 · 마스킹)</h3>

    <p v-if="rows.length === 0" class="text-dim py-6 text-center text-[13px]">
      공개할 입찰 내역이 없어요.
    </p>

    <div v-else class="overflow-x-auto">
      <div class="flex min-w-[420px] flex-col gap-1.5 text-[13px]">
        <div
          class="text-faint grid grid-cols-[40px_1fr_110px_92px] gap-2.5 px-3 text-[11.5px]"
          aria-hidden="true"
        >
          <span>순위</span>
          <span>입찰자</span>
          <span class="text-right">입찰가</span>
          <span class="text-right">접수 시각</span>
        </div>

        <div
          v-for="row in rows"
          :key="`${row.rank}-${row.label}`"
          class="grid grid-cols-[40px_1fr_110px_92px] items-center gap-2.5 rounded-[10px] px-3 py-2.5 tabular-nums"
          :class="
            row.isWinner ? 'bg-terra-tint' : row.isFinalPrice ? 'bg-slate-tint' : 'bg-line/40'
          "
        >
          <span
            class="font-bold"
            :class="row.isWinner ? 'text-terra' : row.isFinalPrice ? 'text-slate' : 'text-faint'"
            >{{ row.rank }}</span
          >
          <span class="truncate" :class="row.isMine ? 'text-ink font-bold' : 'text-ink'">
            <template v-if="row.isMine"
              >나<template v-if="row.isWinner"> (낙찰)</template></template
            >
            <template v-else>{{ row.label }}</template>
          </span>
          <span
            class="text-right"
            :class="
              row.isFinalPrice
                ? 'text-terra font-bold'
                : row.isWinner
                  ? 'text-ink font-bold'
                  : 'text-ink'
            "
            >{{ formatPoint(row.amount) }}</span
          >
          <span class="text-dim text-right">{{ row.time }}</span>
        </div>
      </div>
    </div>

    <p class="text-faint mt-3 text-[11.5px] leading-relaxed">
      진행 중에는 아무도 남의 입찰가를 볼 수 없어요. 종료 후에도 닉네임은 마스킹된 채로만
      공개됩니다.
    </p>
  </section>
</template>
