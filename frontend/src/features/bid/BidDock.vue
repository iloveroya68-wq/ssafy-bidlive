<script setup lang="ts">
// BID-06 상향 입찰 dock (FE1, #action 슬롯) — 디자인 원본의 `.dock`.
//
// 비크리 비공개 원칙(AGENTS.md 규칙 6): 이 패널은 **타인 입찰가·최고가·순위를 절대 그리지 않는다.**
// 화면에 나오는 금액은 하한가(공개 조건), 내 가용 잔액(내 지갑), 내 입찰가(본인 값)뿐이다.
//
// 검증은 서버가 최종 판정한다(명세 「금액 직접 입력 입찰」 검증 규칙). 여기 3단 검증은
// 왕복 한 번을 아끼기 위한 사전 안내이고, 문구는 서버 오류 코드 문장과 짝을 맞춰 두었다.
import { computed, nextTick, ref, watch } from 'vue'
import { commafyInput, parseAmount } from '@/shared/lib/amountInput'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { BID_LOCK_NOTE } from '@/shared/lib/participation'
import LockNote from '@/shared/ui/LockNote.vue'
import { placeBid, type PlaceBidResponse } from '@/shared/api/bids'
import { newRequestId } from '@/shared/api/idempotency'
import { bidErrorMessage, isPredictionAlreadyJoined } from './api'

const props = defineProps<{
  auctionId: number
  /** `auction_room.reserve_price` — 이 아래로는 접수되지 않는다 */
  reservePrice: number
  /** **본인** 최종 입찰가. 미입찰이면 null */
  myBid: number | null
  /** 거래 지갑 `available_balance` (이미 보류 제외 값) */
  availableBalance: number
  /** 예측 베팅에 먼저 참여해 입찰이 잠긴 상태 — 제출만 막고 패널은 그대로 둔다 */
  locked: boolean
  /** 이미 입찰자로 역할이 확정됐는지. 확정 전 첫 제출에만 확인 단계를 띄운다 */
  roleConfirmed: boolean
}>()

const emit = defineEmits<{
  /** 접수 성공 — 부모가 myBid 갱신 + 역할 확정 처리 */
  submitted: [result: PlaceBidResponse]
  /** 서버가 "이미 예측에 참여함"으로 거절 — 화면 잠금을 서버 판정에 맞춘다 */
  lockedByServer: []
}>()

const QUICK_ADDS = [1000, 5000, 10000]

const rawAmount = ref('')
const errorText = ref('')
const shaking = ref(false)
const pending = ref(false)
const done = ref(false)
/** 되돌릴 수 없는 첫 참여라 확인 단계를 한 번 거친다 (docs/03 §4-1) */
const confirming = ref(false)

/**
 * `Idempotency-Key` — 제출 시 만들고 **성공할 때까지 유지**한다.
 * `bid.idempotency_key`가 NOT NULL UNIQUE라 재시도마다 키를 새로 만들면
 * 네트워크 재시도가 중복 입찰로 접수될 수 있다 (명세: 실제 처리는 한 번만).
 */
const idempotencyKey = ref<string | null>(null)

const amount = computed(() => parseAmount(rawAmount.value))

// 잠긴 상태에서 남아 있던 확인 단계는 의미가 없다 — 접고 오류 문구도 지운다.
watch(
  () => props.locked,
  (locked) => {
    if (locked) {
      confirming.value = false
      errorText.value = ''
    }
  },
)

/** 상향만 가능하므로 다음 입찰의 최소값은 "내 입찰가 초과" 또는 "하한가 이상" 중 큰 쪽 */
const minimumAmount = computed(() =>
  props.myBid === null ? props.reservePrice : Math.max(props.reservePrice, props.myBid + 1),
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

function clearError() {
  errorText.value = ''
}

/** 3단 검증 — 통과하면 null, 아니면 화면에 그릴 문장 */
function validate(): string | null {
  const value = amount.value
  if (value <= 0) return '입찰 금액을 입력해 주세요'

  // ① 하한가 미만 (서버 코드: BID_BELOW_RESERVE_PRICE)
  if (value < props.reservePrice) {
    return `하한가 ${formatPoint(props.reservePrice)} 미만은 접수되지 않아요`
  }

  // ② 내 이전 입찰가 이하 (서버 코드: BID_NOT_HIGHER_THAN_PREVIOUS)
  if (props.myBid !== null && value <= props.myBid) {
    return `내 입찰가 ${formatPoint(props.myBid)}보다 높은 금액만 가능해요 — 상향만, 하향·취소 불가`
  }

  // ③ 가용 잔액 초과 (서버 코드: INSUFFICIENT_AVAILABLE_BALANCE)
  //    명세는 "가용 거래 포인트가 **추가 홀드 금액** 이상"이라 총액이 아니라 차액으로 본다.
  //    상향 입찰이면 이미 홀드된 만큼은 다시 잡지 않기 때문이다.
  const additionalHold = value - (props.myBid ?? 0)
  if (additionalHold > props.availableBalance) {
    return `가용 잔액 ${formatPoint(props.availableBalance)}로는 부족해요 — 추가 홀드 ${formatPoint(additionalHold)}가 필요해요`
  }

  return null
}

function onInput(event: Event) {
  const value = commafyInput(event.target as HTMLInputElement)
  rawAmount.value = value > 0 ? formatAmount(value) : ''
  clearError()
  confirming.value = false
  // 금액이 바뀌면 다른 요청이다 — 멱등키를 새로 발급받게 비운다.
  idempotencyKey.value = null
}

function quickAdd(delta: number) {
  if (props.locked) return
  clearError()
  confirming.value = false
  idempotencyKey.value = null
  // 비어 있으면 내 입찰가(없으면 하한가)를 기준으로 올린다 — 디자인 원본과 같은 동작
  const base = amount.value || props.myBid || props.reservePrice
  rawAmount.value = formatAmount(base + delta)
}

async function doSubmit() {
  confirming.value = false
  // `crypto.randomUUID`는 보안 컨텍스트에서만 있다 — 사내망 http 데모에서 죽지 않도록
  // 폴백이 있는 계약 레이어 헬퍼를 쓴다(`shared/api/idempotency`).
  idempotencyKey.value ??= newRequestId()
  pending.value = true
  try {
    const result = await placeBid(props.auctionId, { amount: amount.value }, idempotencyKey.value)
    idempotencyKey.value = null
    emit('submitted', result)

    // CTA 체크 애니메이션 — 접수됐다는 신호를 버튼 자리에서 바로 준다
    done.value = true
    setTimeout(() => {
      done.value = false
    }, 1400)
  } catch (error) {
    if (isPredictionAlreadyJoined(error)) emit('lockedByServer')
    showError(bidErrorMessage(error))
  } finally {
    pending.value = false
  }
}

function onSubmit() {
  if (props.locked || pending.value) return

  const message = validate()
  if (message) {
    showError(message)
    return
  }

  // 첫 참여는 되돌릴 수 없다 — 버튼만 봐서는 알 수 없으므로 확인 단계를 거친다.
  // 두 번째 입찰부터는 띄우지 않는다(`bid`는 sequence_no로 여러 번 가능).
  if (!props.roleConfirmed && !confirming.value) {
    confirming.value = true
    return
  }

  void doSubmit()
}
</script>

<template>
  <section
    class="border-line bg-surface rounded-[18px] border p-[18px]"
    aria-label="상향 입찰"
    :aria-busy="pending"
  >
    <!-- 헤더: 제목 + 하한가 (공개 조건이라 노출해도 규칙 6에 걸리지 않는다) -->
    <div class="text-dim mb-2 flex items-center justify-between text-[11.5px]">
      <span class="text-ink font-semibold">상향 입찰</span>
      <span class="tabular-nums">하한가 {{ formatPoint(reservePrice) }}</span>
    </div>

    <!-- 잠긴 쪽은 숨기지 않고 흐리게 두되, 키보드로 들어가 제출하는 우회를 막으려면
         pointer-events만으로는 부족하므로 실제로 disabled를 건다 -->
    <div :class="locked && 'pointer-events-none opacity-40 grayscale'">
      <div class="mt-2.5 flex gap-2">
        <input
          :value="rawAmount"
          class="border-line text-ink focus:border-terra bg-surface min-w-0 flex-1 rounded-[10px] border px-3.5 py-2.5 text-[16px] font-medium tabular-nums transition outline-none"
          :class="[errorText && 'border-red', shaking && 'shake', 'motion-reduce:transition-none']"
          inputmode="numeric"
          autocomplete="off"
          :disabled="locked"
          :placeholder="formatAmount(minimumAmount)"
          aria-label="입찰 금액"
          :aria-invalid="Boolean(errorText)"
          aria-describedby="bid-error bid-hint"
          @input="onInput"
          @keydown.enter="onSubmit"
        />
        <button
          type="button"
          class="bg-terra hover:bg-terra-d relative overflow-hidden rounded-[10px] px-5 py-2.5 text-[14px] font-bold text-white transition active:scale-[0.96] disabled:cursor-not-allowed disabled:opacity-60 motion-reduce:transition-none motion-reduce:active:scale-100"
          :disabled="locked || pending"
          @click="onSubmit"
        >
          <span
            class="inline-flex items-center transition-[opacity,filter] duration-150 motion-reduce:transition-none"
            :class="done && 'opacity-0 blur-[3px]'"
          >
            {{ pending ? '접수 중…' : '상향 입찰' }}
          </span>
          <svg
            class="absolute top-1/2 left-1/2 h-4 w-4 -translate-x-1/2 -translate-y-1/2 transition duration-200 motion-reduce:transition-none"
            :class="done ? 'scale-100 opacity-100' : 'scale-75 opacity-0'"
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
        </button>
      </div>

      <!-- 퀵애드 칩 — 기준은 현재 입력값(없으면 내 입찰가) -->
      <div class="mt-2.5 flex gap-1.5">
        <button
          v-for="delta in QUICK_ADDS"
          :key="delta"
          type="button"
          class="border-line text-dim bg-surface hover:bg-cream flex-1 rounded-full border py-[7px] text-[12px] tabular-nums transition active:scale-[0.94] disabled:cursor-not-allowed motion-reduce:transition-none motion-reduce:active:scale-100"
          :disabled="locked"
          @click="quickAdd(delta)"
        >
          +{{ formatAmount(delta) }}
        </button>
      </div>

      <!-- 되돌릴 수 없는 첫 참여 확인 (docs/03 §4-1) -->
      <div
        v-if="confirming"
        class="border-terra/40 bg-terra-tint text-terra-tint-text mt-2.5 rounded-[10px] border p-3 text-[12px] leading-relaxed"
      >
        <p>입찰에 참여하면 이 방에서는 예측 베팅에 참여할 수 없어요. 계속할까요?</p>
        <div class="mt-2.5 flex gap-2">
          <button
            type="button"
            class="bg-terra hover:bg-terra-d rounded-lg px-3 py-1.5 text-[12px] font-semibold text-white transition motion-reduce:transition-none"
            @click="onSubmit"
          >
            입찰할게요
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

      <p
        v-if="errorText"
        id="bid-error"
        class="text-red mt-2 text-[12px] leading-relaxed"
        role="alert"
      >
        {{ errorText }}
      </p>

      <!-- 내 입찰가는 본인 값이라 표시 가능. 타인 최고가는 여기 절대 오지 않는다. -->
      <p id="bid-hint" class="text-dim mt-2.5 text-[11px] tabular-nums">
        가용 잔액 {{ formatPoint(availableBalance) }} ·
        <template v-if="myBid !== null">내 입찰가 {{ formatPoint(myBid) }} · </template>
        내 입찰가 초과 금액만 (하향·취소 불가)
      </p>
    </div>

    <LockNote v-if="locked" :text="BID_LOCK_NOTE" />
  </section>
</template>

<style scoped>
/* 검증 실패 쉐이크 — 디자인 원본의 bidInput.animate(translateX ±5px, 240ms)와 같은 동작 */
@keyframes bid-shake {
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
  animation: bid-shake 0.24s ease-in-out;
}

@media (prefers-reduced-motion: reduce) {
  .shake {
    animation: none;
  }
}
</style>
