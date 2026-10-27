<script setup lang="ts">
// PRED-02 오차 밴드별 예측 결과 (S15P11A704-68). 디자인 원본 `result` 화면의 "예측 베팅 결과".
//
// AGENTS.md 규칙 7: **신 설계 기준**이다 — 오차율 고정 배당(5% ×5 / 10% ×3 / 20% ×1.5 / 그 외 ×0).
// "구간(bucket) 파리뮤추얼"·"실시간 분포 그래프"는 폐기된 구 설계라 만들지 않는다.
// 분포는 **종료 후에만** 공개한다 — 라이브 중 노출은 herding을 부른다(명세 「예측 결과 분포 조회」 비고).
//
// 유찰이면 낙찰가가 없어 판정 자체가 불가능하므로 밴드 대신 전액 환불을 보여준다
// (명세 §규칙 "낙찰가가 없는 종료라면 밴드 대신 전액 환불 결과를 반환한다").
import { computed } from 'vue'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import type { PredictionBand, PredictionBandResult } from '@/shared/api/predictions'

const props = withDefaults(
  defineProps<{
    /** `bands` = 낙찰 결과 분포 · `refund` = 유찰 전액 환불 */
    variant?: 'bands' | 'refund'
    bands?: PredictionBandResult[]
    /** 유찰 시 환불된 스테이크 (베팅 포인트) */
    refundAmount?: number
    loading?: boolean
    /** 조회 실패·정산 전 안내 문구 */
    errorText?: string
  }>(),
  { variant: 'bands', bands: () => [], refundAmount: 0, loading: false, errorText: '' },
)

/**
 * 밴드가 안 열렸을 때(정산 전 409·조회 실패)의 수동 재조회 (S15P11A704-543).
 * 안내 문구 자리에서만 나가므로 `refund` 변형에는 도달하지 않는다.
 */
const emit = defineEmits<{ retry: [] }>()

/** 표시 순서는 배수 내림차순으로 고정한다 — 서버 배열 순서에 기대지 않는다 */
const BAND_ORDER: Array<{ band: PredictionBand; label: string; multiplierLabel: string }> = [
  { band: 'WITHIN_5', label: '5% 이내', multiplierLabel: '×5' },
  { band: 'WITHIN_10', label: '10% 이내', multiplierLabel: '×3' },
  { band: 'WITHIN_20', label: '20% 이내', multiplierLabel: '×1.5' },
  { band: 'MISS', label: '그 외', multiplierLabel: '꽝' },
]

const BAR_CLASS: Record<PredictionBand, string> = {
  WITHIN_5: 'bg-slate',
  WITHIN_10: 'bg-slate',
  WITHIN_20: 'bg-faint',
  MISS: 'bg-faint/50',
}

const totalCount = computed(() => props.bands.reduce((sum, band) => sum + band.participantCount, 0))

const hitCount = computed(() =>
  props.bands
    .filter((band) => band.band !== 'MISS')
    .reduce((sum, band) => sum + band.participantCount, 0),
)

const totalPayout = computed(() =>
  props.bands.reduce((sum, band) => sum + band.totalPayoutAmount, 0),
)

const rows = computed(() =>
  BAND_ORDER.map((meta) => {
    const found = props.bands.find((band) => band.band === meta.band)
    const count = found?.participantCount ?? 0
    return {
      ...meta,
      count,
      // 전체가 0이면 0%로 둔다 (0으로 나누지 않는다)
      percent: totalCount.value > 0 ? Math.round((count / totalCount.value) * 100) : 0,
      barClass: BAR_CLASS[meta.band],
    }
  }),
)
</script>

<template>
  <section
    class="border-line bg-surface rounded-[16px] border p-[18px]"
    aria-label="예측 베팅 결과"
  >
    <!-- 유찰 — 낙찰가가 정해지지 않아 판정할 수 없으므로 스테이크를 그대로 돌려준다 -->
    <template v-if="variant === 'refund'">
      <h3 class="text-ink mb-2.5 text-[13px] font-semibold">예측 베팅</h3>
      <div
        class="bg-slate-tint flex items-center justify-between rounded-xl px-3.5 py-3 text-[13px]"
      >
        <span class="text-slate-tint-text font-semibold">전액 환불</span>
        <span class="text-slate text-[20px] font-bold tabular-nums"
          >+{{ formatPoint(refundAmount, 'BETTING') }}</span
        >
      </div>
      <p class="text-dim mt-[11px] text-[11.5px] leading-relaxed">
        낙찰가가 정해지지 않아 판정할 수 없으므로, 걸어둔 베팅 포인트를 그대로 돌려드려요.
      </p>
    </template>

    <template v-else>
      <h3 class="text-ink text-[13px] font-semibold">
        예측 베팅 결과
        <span
          v-if="!loading && errorText === ''"
          class="text-slate text-[11.5px] font-medium tabular-nums"
        >
          · 참여 {{ formatAmount(totalCount) }}명
        </span>
      </h3>

      <p v-if="loading" class="text-dim mt-3 text-[12.5px]" role="status">
        밴드별 결과를 불러오는 중…
      </p>

      <template v-else-if="errorText !== ''">
        <p class="text-dim mt-3 text-[12.5px] leading-relaxed" role="status">
          {{ errorText }}
        </p>
        <!--
          정산은 화면이 열린 뒤에 끝날 수 있다. 자동 계기는 PRED-03 신호이고(ResultView가 메인 토픽에
          붙는다, S15P11A704-546), 이 버튼은 그 신호가 없는 경우 — 목 모드·연결 실패·신호 유실 — 의
          수동 계기다. 폴링은 하지 않는다. 조회가 도는 동안은 위 `loading` 분기로 바뀌어
          이 버튼 자체가 사라진다(연타 방지).
        -->
        <button
          type="button"
          class="bg-terra hover:bg-terra-d mt-3 rounded-[10px] px-4 py-2.5 text-[13px] font-bold text-white transition motion-reduce:transition-none"
          @click="emit('retry')"
        >
          다시 확인
        </button>
      </template>

      <template v-else>
        <div
          class="bg-slate-tint mt-3 flex items-center justify-between rounded-xl px-3.5 py-2.5 text-[13px]"
        >
          <span class="text-slate-tint-text tabular-nums"
            >적중 <b>{{ formatAmount(hitCount) }}명</b>
            <span class="text-slate-tint-text/80 text-[11px]"> · 오차 20% 이내</span></span
          >
          <span class="text-slate-tint-text font-bold tabular-nums"
            >지급 {{ formatPoint(totalPayout, 'BETTING') }}</span
          >
        </div>

        <p class="text-dim mt-3.5 mb-2 text-[11.5px]">오차 밴드별 결과 — 종료 후 공개</p>
        <ul class="flex flex-col gap-1.5 text-[11.5px] tabular-nums">
          <li
            v-for="row in rows"
            :key="row.band"
            class="grid grid-cols-[54px_22px_1fr_40px] items-center gap-2"
          >
            <span class="text-dim">{{ row.label }}</span>
            <span class="text-slate font-bold">{{ row.multiplierLabel }}</span>
            <span
              class="bg-line h-[5px] overflow-hidden rounded-full"
              role="img"
              :aria-label="`${row.label} ${row.count}명 (${row.percent}%)`"
            >
              <span
                class="block h-full rounded-full transition-[width] duration-700 ease-out motion-reduce:transition-none"
                :class="row.barClass"
                :style="{ width: `${row.percent}%` }"
              />
            </span>
            <span class="text-dim text-right">{{ formatAmount(row.count) }}명</span>
          </li>
        </ul>
      </template>
    </template>
  </section>
</template>
