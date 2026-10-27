<script setup lang="ts">
// PRED-01 낙찰가 예측 베팅 dock (FE1, #action 슬롯) — 디자인 원본의 `.betdock`.
//
// 신 설계 기준이다 (AGENTS.md 규칙 7): 금액 직접 입력 + 스테이크, 오차율 고정 배당.
// **구간(bucket) 선택도, 실시간 분포 그래프도 만들지 않는다** — 폐기된 구 설계이고,
// 명세상으로도 진행 중에는 서버가 타인의 예측·분포를 반환하지 않는다.
// 분포는 종료 후 결과 화면(PRED-02) 소관이다.
import { computed, nextTick, ref, watch } from 'vue'
import { commafyInput, parseAmount } from '@/shared/lib/amountInput'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { PREDICTION_LOCK_NOTE } from '@/shared/lib/participation'
import LockNote from '@/shared/ui/LockNote.vue'
import { BAND_MULTIPLIER, PAYOUT_BANDS, usePredictionStore } from '@/stores/prediction'
import type { SubmitPredictionResponse } from '@/shared/api/predictions'
import { isBidAlreadyJoined, predictionErrorMessage } from './api'

const props = defineProps<{
  auctionId: number
  /** 베팅 지갑 `available_balance` — 스테이크 상한 */
  availableBettingPoint: number
  /** 입찰에 먼저 참여해 예측이 잠긴 상태 — 제출만 막고 패널은 그대로 둔다 */
  locked: boolean
  /** 이미 예측자로 역할이 확정됐는지 */
  roleConfirmed: boolean
}>()

const emit = defineEmits<{
  submitted: [result: SubmitPredictionResponse]
  /** 서버가 "이미 입찰에 참여함"으로 거절 — 화면 잠금을 서버 판정에 맞춘다 */
  lockedByServer: []
}>()

const prediction = usePredictionStore()

const rawPrice = ref('')
const rawStake = ref('')
const errorText = ref('')
const shaking = ref(false)
const done = ref(false)
/** 되돌릴 수 없는 첫 참여라 확인 단계를 한 번 거친다 (docs/03 §4-1) */
const confirming = ref(false)

const predictedPrice = computed(() => parseAmount(rawPrice.value))
const stakeAmount = computed(() => parseAmount(rawStake.value))

watch(
  () => props.locked,
  (locked) => {
    if (locked) {
      confirming.value = false
      errorText.value = ''
    }
  },
)

/** 배당 스트립 — 스테이크가 바뀔 때마다 예상 지급액을 다시 그린다 */
const payoutRows = computed(() =>
  PAYOUT_BANDS.map(({ band, label }) => ({
    band,
    label,
    multiplier: BAND_MULTIPLIER[band],
    payout: Math.floor(stakeAmount.value * BAND_MULTIPLIER[band]),
  })),
)

async function shake() {
  shaking.value = false
  await nextTick()
  shaking.value = true
}

function showError(message: string) {
  errorText.value = message
  void shake()
}

function onPriceInput(event: Event) {
  const value = commafyInput(event.target as HTMLInputElement)
  rawPrice.value = value > 0 ? formatAmount(value) : ''
  errorText.value = ''
  confirming.value = false
}

function onStakeInput(event: Event) {
  const value = commafyInput(event.target as HTMLInputElement)
  rawStake.value = value > 0 ? formatAmount(value) : ''
  errorText.value = ''
  confirming.value = false
}

/** 명세 「낙찰가 예측 베팅 접수」의 검증 규칙 중 화면에서 미리 볼 수 있는 것들 */
function validate(): string | null {
  if (predictedPrice.value <= 0) return '예측 낙찰가를 입력해 주세요'
  if (stakeAmount.value <= 0) return '스테이크를 입력해 주세요'
  if (stakeAmount.value > props.availableBettingPoint) {
    return `보유 베팅 포인트가 부족해요 · 보유 ${formatPoint(props.availableBettingPoint, 'BETTING')}`
  }
  return null
}

async function doSubmit() {
  confirming.value = false
  try {
    const result = await prediction.placePrediction(
      props.auctionId,
      predictedPrice.value,
      stakeAmount.value,
    )
    emit('submitted', result)
    done.value = true
    setTimeout(() => {
      done.value = false
    }, 1400)
  } catch (error) {
    if (isBidAlreadyJoined(error)) emit('lockedByServer')
    showError(predictionErrorMessage(error))
  }
}

function onSubmit() {
  if (props.locked || prediction.submitting || prediction.submitted) return

  const message = validate()
  if (message) {
    showError(message)
    return
  }

  if (!props.roleConfirmed && !confirming.value) {
    confirming.value = true
    return
  }

  void doSubmit()
}
</script>

<template>
  <section
    class="border-line bg-surface rounded-[16px] border p-3.5"
    aria-label="낙찰가 예측 베팅"
    :aria-busy="prediction.submitting"
  >
    <div class="text-dim mb-2 flex items-center justify-between text-[11.5px]">
      <span class="text-ink font-semibold">낙찰가 예측 베팅</span>
      <span class="tabular-nums"
        >보유 <b class="text-slate">{{ formatPoint(availableBettingPoint, 'BETTING') }}</b></span
      >
    </div>

    <!-- 제출 완료 — 방당 1회뿐이라(`prediction.auction_participant_id` UNIQUE)
         수정·취소 버튼을 두지 않고 확정 표시로 바꾼다 (docs/04 §3.1) -->
    <div
      v-if="prediction.submitted"
      class="border-slate/30 bg-slate-tint text-slate-tint-text rounded-xl border p-3.5 text-[12.5px]"
    >
      <div class="flex items-center gap-1.5 font-semibold">
        <svg
          class="h-3.5 w-3.5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M20 6L9 17l-5-5" />
        </svg>
        베팅 완료
      </div>
      <dl class="mt-2 space-y-1 tabular-nums">
        <div class="flex justify-between">
          <dt>예측 낙찰가</dt>
          <dd class="font-semibold">
            {{ prediction.myPrediction === null ? '—' : formatPoint(prediction.myPrediction) }}
          </dd>
        </div>
        <div class="flex justify-between">
          <dt>스테이크</dt>
          <dd class="font-semibold">{{ formatPoint(prediction.myStake, 'BETTING') }}</dd>
        </div>
      </dl>
      <p class="mt-2.5 text-[11.5px] opacity-80">
        방당 한 번만 베팅할 수 있어요. 결과는 경매가 끝난 뒤 오차율로 정산돼요.
      </p>
    </div>

    <!-- 잠긴 쪽은 숨기지 않고 흐리게 두되, 키보드 우회를 막으려면 실제로 disabled를 걸어야 한다 -->
    <div v-else :class="locked && 'pointer-events-none opacity-40 grayscale'">
      <div class="flex flex-col gap-2">
        <label
          class="border-line focus-within:border-slate bg-surface flex items-baseline gap-2.5 rounded-[10px] border px-3 py-2.5 transition motion-reduce:transition-none"
          :class="[errorText && 'border-red', shaking && 'shake']"
        >
          <span class="text-dim flex-none self-center text-[11.5px]">예측 낙찰가</span>
          <input
            :value="rawPrice"
            class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[15px] font-semibold tabular-nums outline-none"
            inputmode="numeric"
            autocomplete="off"
            :disabled="locked"
            placeholder="0"
            aria-label="예측 낙찰가"
            @input="onPriceInput"
          />
          <span class="text-dim flex-none text-[12px] font-semibold">P</span>
        </label>

        <label
          class="border-line focus-within:border-slate bg-surface flex items-baseline gap-2.5 rounded-[10px] border px-3 py-2.5 transition motion-reduce:transition-none"
        >
          <span class="text-dim flex-none self-center text-[11.5px]">스테이크</span>
          <input
            :value="rawStake"
            class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[15px] font-semibold tabular-nums outline-none"
            inputmode="numeric"
            autocomplete="off"
            :disabled="locked"
            placeholder="0"
            aria-label="스테이크"
            @input="onStakeInput"
          />
          <span class="text-dim flex-none text-[12px] font-semibold">BP</span>
        </label>
      </div>

      <!-- 배당 스트립 — 오차 밴드별 고정 배수는 베팅 시점에 확정된다 (PRED-03).
           이건 "내 스테이크의 시나리오별 지급액"이지 타인 분포가 아니다. -->
      <div class="border-line bg-cream mt-2.5 grid grid-cols-3 gap-1.5 rounded-[10px] border p-2.5">
        <div v-for="row in payoutRows" :key="row.band" class="text-center leading-snug">
          <div class="text-dim text-[10.5px]">{{ row.label }}</div>
          <div class="text-slate text-[13px] font-bold">×{{ row.multiplier }}</div>
          <div class="text-ink text-[11.5px] font-semibold tabular-nums">
            {{ formatPoint(row.payout, 'BETTING') }}
          </div>
        </div>
      </div>

      <div
        v-if="confirming"
        class="border-slate/40 bg-slate-tint text-slate-tint-text mt-2.5 rounded-[10px] border p-3 text-[12px] leading-relaxed"
      >
        <p>예측 베팅에 참여하면 이 방에서는 입찰할 수 없어요. 계속할까요?</p>
        <div class="mt-2.5 flex gap-2">
          <button
            type="button"
            class="bg-slate rounded-lg px-3 py-1.5 text-[12px] font-semibold text-white transition hover:opacity-90 motion-reduce:transition-none"
            @click="onSubmit"
          >
            베팅할게요
          </button>
          <button
            type="button"
            class="border-line text-dim bg-surface hover:bg-cream rounded-lg border px-3 py-1.5 text-[12px] font-medium transition motion-reduce:transition-none"
            @click="confirming = false"
          >
            취소
          </button>
        </div>
      </div>

      <p v-if="errorText" class="text-red mt-2 text-[12px] leading-relaxed" role="alert">
        {{ errorText }}
      </p>

      <button
        type="button"
        class="mt-3 min-h-11 w-full rounded-[10px] text-[14px] font-bold text-white transition active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none motion-reduce:active:scale-100"
        :class="done ? 'bg-success' : 'bg-slate hover:opacity-90'"
        :disabled="locked || prediction.submitting"
        @click="onSubmit"
      >
        <template v-if="done">베팅 완료 ✓</template>
        <template v-else>{{ prediction.submitting ? '접수 중…' : '이 예측에 베팅' }}</template>
      </button>
    </div>

    <LockNote v-if="locked" :text="PREDICTION_LOCK_NOTE" />
  </section>
</template>

<style scoped>
@keyframes pred-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-5px);
  }
  75% {
    transform: translateX(5px);
  }
}

.shake {
  animation: pred-shake 0.24s ease-in-out;
}

@media (prefers-reduced-motion: reduce) {
  .shake {
    animation: none;
  }
}
</style>
