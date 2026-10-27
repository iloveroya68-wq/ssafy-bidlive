<script setup lang="ts">
// PNT-01 거래 포인트 충전 (FE1) — data-screen="charge".
// 베팅 포인트는 무료 재화라 충전 대상이 아니다(사행성 차단) — 거래 지갑만 충전한다.
//
// 슬라이스 5에서 `ChargeModal.vue`를 이 라우트 화면으로 옮겼다. 와이어프레임의 charge가 전체 화면이고,
// 헤더 지갑 칩처럼 지갑 화면 밖에서도 진입하기 때문이다. 완료·취소하면 진입 직전 경로로 돌아간다.
//
// ⚠️ 실제 충전은 PNT-01 `POST /members/me/wallets/trade/charge`(확정)다.
//    PG 경유 충전(PNT-04 `POST /payments/charge`)은 별도 흐름이고, 명세 본문에 요청 형식이
//    "확정해야 한다"로 남아 있어 결제 수단은 여기서 **표시만** 한다.
import { computed, onMounted, ref } from 'vue'
import { commafyInput, parseAmount } from '@/shared/lib/amountInput'
import { newRequestId } from '@/shared/api/idempotency'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { useWalletStore } from '@/stores/wallet'
import { useReturnTo } from './useReturnTo'

const wallet = useWalletStore()
// 완료·취소하면 진입 직전 경로로 (출금 화면과 공유하는 처리)
const { goBack, scheduleReturn } = useReturnTo('/wallet/ledger')

const PRESETS = [
  { amount: 10_000, label: '1만' },
  { amount: 50_000, label: '5만' },
  { amount: 100_000, label: '10만' },
]

// `point_charge.payment_provider` — 값 목록이 스키마에도 명세에도 없다. 화면 표시용 두 종만 둔다.
const PROVIDERS = [
  { code: 'KAKAO', label: '카카오페이' },
  { code: 'CARD', label: '신용·체크카드' },
]

const rawAmount = ref('50,000')
const provider = ref('KAKAO')
const submitting = ref(false)
const doneMessage = ref('')
/** 제출이 거절된 사유. 서버 검증·중복 요청(409)이 여기로 온다 (출금 화면과 같은 처리) */
const submitError = ref('')
/**
 * 재시도용 멱등 키 — `BidDock`과 같은 방식이다.
 * 실패 후 다시 누르는 것은 **같은 충전의 재시도**라 키를 재사용해야 이중 충전이 안 된다.
 * 금액이 바뀌면 다른 요청이므로 비운다.
 */
const idempotencyKey = ref<string | null>(null)

const available = computed(() => wallet.tradeWallet.availableBalance)
const held = computed(() => wallet.tradeWallet.heldBalance)
const amount = computed(() => parseAmount(rawAmount.value))

// `charge_point_amount`와 `payment_amount`는 별도 컬럼이다 — 프로모션이 붙으면 달라질 수 있어 따로 표시한다.
// TODO: 실제 보너스 정책은 서버가 계산해 내려준다 (BE 확인 필요)
const paymentAmount = computed(() => amount.value)

// 모달일 때는 지갑 화면이 이미 불러온 잔액을 props로 받았다. 라우트로 독립했으니 직접 불러온다 —
// 헤더 지갑 칩처럼 지갑 화면을 거치지 않고 들어오면 잔액이 0으로 보인다.
// 매번 새로 받는다 — 서버 잔액이 정본이고, 충전 응답도 갱신된 잔액을 준다.
// (예전 `!loaded` 가드는 목 데이터가 초기화되는 것을 막으려던 것이었다 — S15P11A704-333에서 제거)
onMounted(() => {
  void wallet.loadWallet()
})

function onInput(event: Event) {
  // 콤마를 다시 찍으면서 커서를 유지한다 (입찰·예측 dock과 같은 처리)
  const value = commafyInput(event.target as HTMLInputElement)
  rawAmount.value = value > 0 ? formatAmount(value) : ''
  // 금액이 바뀌면 다른 요청이다 — 키를 새로 발급받게 비운다
  idempotencyKey.value = null
  submitError.value = ''
}

function pickPreset(value: number) {
  rawAmount.value = formatAmount(value)
  idempotencyKey.value = null
  submitError.value = ''
}

async function submit() {
  if (amount.value <= 0 || submitting.value) return
  submitting.value = true
  submitError.value = ''
  // 실패 사유를 버리지 않는다 — 서버 검증·중복 요청(409)이 전부 이 분기로 온다
  idempotencyKey.value ??= newRequestId()
  const failure = await wallet.chargeTradePoint(amount.value, idempotencyKey.value)
  if (failure) {
    submitError.value = failure
    doneMessage.value = ''
    submitting.value = false
    return
  }
  idempotencyKey.value = null
  wallet.setActiveWalletType('TRADE')
  // 결과를 잠깐 보여주고 돌아간다 — 복귀 화면으로 토스트 상태를 넘기지 않기 위해서다.
  doneMessage.value = `${formatPoint(amount.value)} 충전 완료 · 테스트 결제`
  scheduleReturn(900)
}
</script>

<template>
  <main class="bg-cream min-h-screen px-5 py-10">
    <div class="mx-auto w-full max-w-130">
      <h1 class="text-ink text-[28px] font-bold tracking-tight">거래 포인트 충전</h1>
      <p class="text-dim mt-1.5 text-[13px]">입찰과 낙찰 결제에 쓰이는 포인트예요.</p>

      <!-- 잔액 조회 실패를 알린다 — 안 그러면 폴백 0P가 "돈이 없음"으로 읽힌다 -->
      <p
        v-if="wallet.loadError"
        class="border-terra/40 bg-terra-tint text-terra-tint-text mt-5 rounded-[10px] border p-3 text-[12px] leading-relaxed"
        role="alert"
      >
        {{ wallet.loadError }}
      </p>

      <!-- 현재 잔액 — 사용 가능 / 보류를 따로 보여준다 (available_balance는 이미 보류 제외 값) -->
      <div class="border-line bg-surface mt-5 rounded-2xl border p-4.5">
        <div class="flex items-center justify-between">
          <span class="text-dim text-[12.5px]">사용 가능</span>
          <span class="text-ink text-[24px] font-bold tabular-nums">{{
            formatPoint(available)
          }}</span>
        </div>
        <div class="border-line mt-2.5 flex items-center justify-between border-t pt-2.5">
          <span class="text-faint text-[12px]">보류 중</span>
          <span class="text-dim text-[13px] font-semibold tabular-nums">{{
            formatPoint(held)
          }}</span>
        </div>
      </div>

      <!-- 충전 금액 -->
      <div class="mt-6">
        <div class="text-ink text-[13px] font-semibold">충전 금액</div>
        <div class="mt-3 grid grid-cols-3 gap-2">
          <button
            v-for="preset in PRESETS"
            :key="preset.amount"
            type="button"
            class="rounded-xl border py-3.5 text-center text-[14px] font-semibold tabular-nums transition"
            :class="
              amount === preset.amount
                ? 'bg-terra-tint border-terra text-terra-tint-text'
                : 'border-line text-ink hover:bg-cream bg-surface'
            "
            @click="pickPreset(preset.amount)"
          >
            {{ preset.label }}
          </button>
        </div>
        <label
          class="border-line focus-within:border-slate bg-surface mt-2 flex items-baseline gap-2.5 rounded-xl border px-3 py-2.5 transition"
        >
          <span class="text-dim flex-none self-center text-[11.5px]">직접 입력</span>
          <input
            :value="rawAmount"
            class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[17px] font-semibold tabular-nums outline-none"
            inputmode="numeric"
            aria-label="충전 금액"
            @input="onInput"
          />
          <span class="text-dim flex-none text-[12px] font-semibold">P</span>
        </label>
      </div>

      <!-- 결제 수단 (`point_charge.payment_provider`) — PG 실연동은 범위 밖이라 선택만 한다 -->
      <div class="mt-6">
        <div class="text-ink mb-2.5 text-[13px] font-semibold">결제 수단</div>
        <div class="flex flex-col gap-2">
          <button
            v-for="item in PROVIDERS"
            :key="item.code"
            type="button"
            class="flex items-center gap-2.5 rounded-xl border px-3.5 py-3 text-left transition"
            :class="
              provider === item.code
                ? 'bg-terra-tint border-terra'
                : 'border-line hover:bg-cream bg-surface'
            "
            @click="provider = item.code"
          >
            <span
              class="h-3.5 w-3.5 flex-none rounded-full border-[4.5px] transition"
              :class="provider === item.code ? 'border-terra' : 'border-line'"
              aria-hidden="true"
            />
            <span class="text-ink flex-1 text-[13.5px]">{{ item.label }}</span>
            <span class="text-faint text-[11px]">테스트 결제</span>
          </button>
        </div>
      </div>

      <!-- 충전 포인트 ≠ 결제 금액일 수 있어(프로모션) 두 값을 각각 표시한다 -->
      <div class="border-line mt-5 rounded-xl border border-dashed px-4 py-3">
        <div class="flex items-center justify-between text-[12.5px]">
          <span class="text-dim">받는 포인트</span>
          <span class="text-ink font-semibold tabular-nums">{{ formatPoint(amount) }}</span>
        </div>
        <div class="mt-1.5 flex items-center justify-between text-[12.5px]">
          <span class="text-dim">결제 금액</span>
          <span class="text-ink font-semibold tabular-nums"
            >{{ formatAmount(paymentAmount) }}원</span
          >
        </div>
        <p class="text-faint mt-2.5 text-[11.5px] leading-relaxed">
          PG 샌드박스(테스트 키)로 동작해 실제 결제가 발생하지 않아요. 보류 중인 포인트를 제외하면
          언제든 출금할 수 있어요.
        </p>
      </div>

      <p v-if="submitError" class="text-red mt-4 text-[13px]" role="alert">{{ submitError }}</p>

      <p v-if="doneMessage" class="text-success mt-4 text-[13px] font-semibold" role="status">
        {{ doneMessage }}
      </p>

      <div class="mt-5 flex items-center gap-2.5">
        <button
          type="button"
          class="border-line text-dim hover:bg-cream bg-surface rounded-xl border px-5 py-3 text-[14px] font-medium transition"
          @click="goBack"
        >
          취소
        </button>
        <button
          type="button"
          class="bg-terra text-on-dark hover:bg-terra-d flex-1 rounded-xl px-4 py-3 text-[15px] font-semibold tabular-nums transition disabled:cursor-not-allowed disabled:opacity-45"
          :disabled="amount <= 0 || submitting"
          @click="submit"
        >
          {{ amount > 0 ? `${formatPoint(amount)} 충전하기` : '금액을 입력하세요' }}
        </button>
      </div>
    </div>
  </main>
</template>
