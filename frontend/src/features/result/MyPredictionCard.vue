<script setup lang="ts">
// PRED-02 "내 예측 결과" 카드 (S15P11A704-68). 디자인 원본 `result` 화면 `r-bet` 카드.
//
// 밴드 판정식은 명세 「예측 고정 배당 정산」(PRED-03, 상태: 확정)이 단일 소스다.
//   errorRate = abs(predictedPrice - finalPrice) / finalPrice × 100
//   5% 이내 ×5 / 10% 이내 ×3 / 20% 이내 ×1.5 / 그 외 0
// 여기 계산은 **표시 전용**이다 — 실제 배당은 서버가 정산하고 베팅 지갑에 이미 반영돼 있다.
//
// 값의 출처는 「경매 결과 조회」 응답의 `myPredictionSettlement`다 (S15P11A704-322에서 연결).
// PRED-02는 밴드 집계만 주므로 개인 예측은 그쪽에서 받지 않는다.
// 예측으로 참여하지 않았으면 그 필드가 null이고, 호출부가 이 카드를 렌더하지 않는다.
//
// ⚠️ 서버가 `errorBand`·`multiplier`·`payoutAmount`를 주면 **그 값을 그대로 쓴다.**
// 명세가 "배율은 운영 정책에 따라 변경할 수 있으며 API는 정산 시점에 적용된 multiplier를 반환한다"고
// 못박았으므로, 여기 계산식이 정답이라고 우기면 배당이 바뀐 뒤 화면만 옛 배수로 남는다.
// 아래 계산은 값이 안 왔을 때의 **폴백**이다.
import { computed } from 'vue'
import { formatPoint } from '@/shared/lib/point'
import {
  PREDICTION_BAND_MULTIPLIER,
  type PredictionBand,
  type PredictionRewardMultiplier,
} from '@/shared/api/predictions'

const props = defineProps<{
  predictedPrice: number
  stakeAmount: number
  /** 2등가로 확정된 실제 낙찰가 */
  finalPrice: number
  /** 서버가 판정한 밴드. 없으면 오차율로 계산한다 */
  serverBand?: PredictionBand | null
  /** 정산 시점에 적용된 배수 */
  serverMultiplier?: PredictionRewardMultiplier | null
  /** 실제 지급액(원금 포함) */
  serverPayout?: number | null
}>()

const errorAmount = computed(() => Math.abs(props.predictedPrice - props.finalPrice))

/** 낙찰가가 0이면 판정 불가 — 유찰은 애초에 이 카드를 그리지 않는다 */
const errorRate = computed(() =>
  props.finalPrice > 0 ? (errorAmount.value / props.finalPrice) * 100 : Number.POSITIVE_INFINITY,
)

const band = computed<PredictionBand>(() => {
  if (props.serverBand != null) return props.serverBand
  const rate = errorRate.value
  if (rate <= 5) return 'WITHIN_5'
  if (rate <= 10) return 'WITHIN_10'
  if (rate <= 20) return 'WITHIN_20'
  return 'MISS'
})

const multiplier = computed(() => props.serverMultiplier ?? PREDICTION_BAND_MULTIPLIER[band.value])
const payout = computed(
  () => props.serverPayout ?? Math.floor(props.stakeAmount * multiplier.value),
)
const isHit = computed(() => band.value !== 'MISS')

/** 지급액은 원금 포함 금액이므로 순손익은 지급액에서 스테이크를 뺀 값으로 계산한다. */
const netAmount = computed(() => payout.value - props.stakeAmount)

/** 밴드가 뜻하는 오차 구간. 배수는 서버 값이라 라벨에 박지 않고 뒤에 붙인다 */
const BAND_RANGE_LABEL: Record<PredictionBand, string> = {
  WITHIN_5: '적중 · 오차 5% 이내',
  WITHIN_10: '적중 · 오차 10% 이내',
  WITHIN_20: '적중 · 오차 20% 이내',
  MISS: '미적중 · 오차 20% 초과',
}

const bandLabel = computed(() => {
  const range = BAND_RANGE_LABEL[band.value]
  if (!isHit.value) return range
  // 배수는 정산 시점의 서버 값을 우선하고, 누락된 경우에만 현재 정책으로 보완한다
  const suffix = ` ×${multiplier.value}`
  return `${range}${suffix}`
})

const rateLabel = computed(() =>
  Number.isFinite(errorRate.value) ? `${errorRate.value.toFixed(2)}%` : '—',
)
</script>

<template>
  <section class="border-line bg-surface rounded-[16px] border p-[18px]" aria-label="내 예측 결과">
    <h3 class="text-ink mb-3 text-[13px] font-semibold">내 예측 결과</h3>

    <div class="flex items-baseline gap-3">
      <div class="flex-1">
        <p class="text-dim text-[11.5px]">내 예측</p>
        <p class="text-ink text-[24px] font-semibold tabular-nums">
          {{ formatPoint(predictedPrice) }}
        </p>
      </div>
      <div class="flex-1">
        <p class="text-dim text-[11.5px]">실제 낙찰가</p>
        <p class="text-terra text-[24px] font-semibold tabular-nums">
          {{ formatPoint(finalPrice) }}
        </p>
      </div>
    </div>

    <div class="bg-slate-tint mt-3.5 rounded-xl px-3.5 py-3">
      <div class="flex items-center justify-between text-[13px]">
        <span class="text-slate-tint-text font-semibold">{{ bandLabel }}</span>
        <span
          class="text-[20px] font-bold tabular-nums"
          :class="isHit ? 'text-slate' : 'text-faint'"
          >{{ netAmount > 0 ? '+' : '' }}{{ formatPoint(payout, 'BETTING') }}</span
        >
      </div>
      <p class="text-slate-tint-text/80 mt-1.5 text-[11.5px] tabular-nums">
        스테이크 {{ formatPoint(stakeAmount, 'BETTING') }} · 오차 {{ formatPoint(errorAmount) }} ({{
          rateLabel
        }})
      </p>
    </div>
  </section>
</template>
