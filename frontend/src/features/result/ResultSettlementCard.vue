<script setup lang="ts">
// DEAL-06 포인트 정산 카드 (S15P11A704-67). 디자인 원본 `result` 화면의 "포인트 정산".
//
// 비크리의 핵심을 화면에서 보여 주는 자리다 — 홀드는 **내 입찰가**로 잡히고,
// 실제 차감은 **2등가**이며, 차액은 즉시 복구된다 (명세 「비크리 엔진」 §포인트 처리).
// 최고가로 질러도 2등가만 낸다는 사실이 여기서 숫자로 보여야 한다.
//
// ⚠️ 포인트 흐름을 원장 스키마 가정(`docs/04`)이 아니라 명세의 정산 규칙으로 그린다 —
// 실제 원장은 단일 테이블 + `transactionType` 구조라 `docs/04`의 2테이블 가정과 다르다.
import { computed } from 'vue'
import { formatPoint, formatSignedPoint } from '@/shared/lib/point'

const props = defineProps<{
  /** 낙찰자면 차감이 생기고, 미낙찰자는 전액 복구뿐이다 */
  variant: 'WINNER' | 'LOSING_BIDDER'
  /**
   * 낙찰가 결정 방식. `false`(= `RESERVE_PRICE`)면 단독 입찰이라 2등 입찰가가 **존재하지 않는다**.
   * 이때 "2등가만 지불한다"고 설명하면 지불 근거를 잘못 알려주는 것이고,
   * 같은 화면 원장에서 2등가 강조 행이 사라지는 것과도 모순된다.
   */
  isSecondPrice: boolean
  /** 홀드된 내 최종 입찰가 */
  heldAmount: number
  /** 실제 차감액 (= 최종 낙찰가). 미낙찰자는 0 */
  paidAmount: number
  /** 즉시 복구된 금액 */
  restoredAmount: number
}>()

const isWinner = computed(() => props.variant === 'WINNER')

const deductionLabel = computed(() => {
  if (!isWinner.value) return '최종 차감'
  return props.isSecondPrice ? '최종 차감 (2등가)' : '최종 차감 (하한가)'
})
</script>

<template>
  <section class="border-line bg-surface rounded-[16px] border p-[18px]" aria-label="포인트 정산">
    <h3 class="text-ink mb-2.5 text-[13px] font-semibold">포인트 정산</h3>

    <dl class="flex flex-col gap-2 text-[13px] tabular-nums">
      <div class="flex justify-between">
        <dt class="text-dim">홀드된 내 입찰가</dt>
        <dd class="text-ink">{{ formatPoint(heldAmount) }}</dd>
      </div>
      <div class="flex justify-between">
        <dt class="text-dim">{{ deductionLabel }}</dt>
        <dd :class="isWinner ? 'text-terra-d' : 'text-ink'">
          {{ isWinner ? formatSignedPoint(-paidAmount) : formatPoint(0) }}
        </dd>
      </div>
      <div v-if="isWinner" class="flex justify-between">
        <dt class="text-dim">차액 즉시 복구</dt>
        <dd class="text-success">{{ formatSignedPoint(restoredAmount) }}</dd>
      </div>

      <hr class="bg-line my-0.5 h-px border-0" />

      <div class="flex justify-between font-semibold">
        <dt class="text-ink">{{ isWinner ? '실제 지불' : '전액 즉시 복구' }}</dt>
        <dd :class="isWinner ? 'text-ink' : 'text-success'">
          {{ isWinner ? formatPoint(paidAmount) : formatSignedPoint(restoredAmount) }}
        </dd>
      </div>
    </dl>

    <p class="text-dim mt-[11px] text-[11.5px] leading-relaxed">
      <template v-if="isWinner && isSecondPrice">
        2등가 방식이라 최고가로 입찰해도 <b class="text-ink">2등 입찰가만</b> 지불해요.
      </template>
      <template v-else-if="isWinner">
        경쟁 입찰이 없어 <b class="text-ink">하한가</b>로 낙찰됐어요.
      </template>
      <template v-else> 미낙찰 홀드는 종료 즉시 전액 풀립니다. 차감된 포인트는 없어요. </template>
    </p>
  </section>
</template>
