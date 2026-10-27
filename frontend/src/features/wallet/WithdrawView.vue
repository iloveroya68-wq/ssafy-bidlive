<script setup lang="ts">
// PNT 거래 포인트 출금 (FE1) — data-screen="withdraw".
// `available_balance >= 0` 제약 + 보류분은 정산 전까지 출금 불가 → 사용 가능 잔액이 출금 상한이다.
//
// 슬라이스 6에서 `WithdrawModal.vue`를 이 라우트 화면으로 옮겼다(충전과 같은 이유 — 와이어프레임이 전체 화면).
//
// 이 화면이 **직접 배선하는 API 두 개**이고 둘 다 실연동이 끝났다(지갑·원장 조회는 스토어 경유로 더 나간다).
//   계좌 등록·변경: GET·PUT /members/me/settlement-account  (MEM-03) — ✅ S15P11A704-387에서 배선
//   잔액 차감:      POST /members/me/wallets/trade/refunds  (PNT-01) — ✅ S15P11A704-391에서 배선
//                   (`stores/wallet.ts`의 `withdrawTradePoint` → `refundTradePoint`)
//
// 이름이 "환불"인 이유는 실제 송금이 아니라 프로젝트 내 포인트 차감이기 때문이고,
// 계좌는 그와 별개로 정산·출금 대상 정보다. 두 개를 한 화면이 쓰는 게 맞다.
import { computed, onMounted, ref } from 'vue'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { commafyInput, parseAmount } from '@/shared/lib/amountInput'
import { newRequestId } from '@/shared/api/idempotency'
import { useWalletStore } from '@/stores/wallet'
import {
  SETTLEMENT_BANKS,
  getSettlementAccount,
  putSettlementAccount,
  type SettlementAccountResponse,
} from '@/shared/api/wallets'
import { toApiError } from '@/shared/api/errors'
import { useReturnTo } from './useReturnTo'

const wallet = useWalletStore()
// 완료·취소하면 진입 직전 경로로 (충전 화면과 공유하는 처리)
const { goBack, scheduleReturn } = useReturnTo('/wallet/ledger')

const PRESETS = [
  { amount: 10_000, label: '1만' },
  { amount: 50_000, label: '5만' },
  { amount: 100_000, label: '10만' },
]

const rawAmount = ref('50,000')
const submitting = ref(false)
const doneMessage = ref('')
/** 제출이 거절된 사유. 서버 검증·잔액 경합·중복 요청(409)이 여기로 온다 */
const submitError = ref('')
/** 재시도용 멱등 키 — 충전 화면·`BidDock`과 같은 방식. 금액이 바뀌면 비운다 */
const idempotencyKey = ref<string | null>(null)

// ── 정산 계좌 (MEM-03) ──
//
// 서버는 **마스킹된 요약만** 준다 — 계좌번호 원문은 응답에 없다(명세 보안 원칙).
// 그래서 "변경"은 항상 새로 입력받는 것이고, 기존 번호를 폼에 채워 넣을 수 없다.
const account = ref<SettlementAccountResponse | null>(null)
const accountLoading = ref(true)
const accountError = ref('')
const accountSaving = ref(false)
const editingAccount = ref(false)
const draftBankCode = ref<string>(SETTLEMENT_BANKS[0].code)
const draftNumber = ref('')
const draftHolder = ref('')

const available = computed(() => wallet.tradeWallet.availableBalance)
const held = computed(() => wallet.tradeWallet.heldBalance)
const amount = computed(() => parseAmount(rawAmount.value))

const accountText = computed(() =>
  account.value === null
    ? ''
    : `${account.value.bankName} ${account.value.maskedAccountNumber} · ${account.value.accountHolderName}`,
)

/** 계좌가 없으면 출금 자체가 성립하지 않는다 — 보낼 곳이 없다 */
const hasAccount = computed(() => account.value !== null)

/** 입력 단계 검증 — 최종 판정은 서버가 한다 */
const inputError = computed(() => {
  if (amount.value <= 0) return ''
  // 잔액을 못 받아온 상태(폴백 0P)에서는 상한을 말할 근거가 없다 — 배너가 원인을 따로 알린다
  if (!wallet.loaded) return ''
  if (amount.value > available.value) return '사용 가능 잔액을 넘을 수 없어요'
  return ''
})

/** 화면에 띄울 오류 한 줄. 입력이 이미 틀렸으면 그쪽을 먼저 보여준다 */
const error = computed(() => inputError.value || submitError.value)

// 충전 화면과 같은 이유로 직접 부른다. 매번 새로 받는다 —
// 출금 상한이 가용 잔액이라 굳은 값으로 판정하면 안 된다 (S15P11A704-333에서 `loaded` 가드 제거).
onMounted(() => {
  void wallet.loadWallet()
  void loadAccount()
})

async function loadAccount() {
  accountLoading.value = true
  accountError.value = ''
  try {
    account.value = await getSettlementAccount()
    // 등록된 계좌가 없으면 바로 입력 상태로 연다 — 안 그러면 빈 칸만 보고 나가게 된다
    if (account.value === null) editingAccount.value = true
  } catch (error) {
    accountError.value = toApiError(error).message
  } finally {
    accountLoading.value = false
  }
}

function onInput(event: Event) {
  // 콤마를 다시 찍으면서 커서를 유지한다 (입찰·예측 dock과 같은 처리)
  const value = commafyInput(event.target as HTMLInputElement)
  rawAmount.value = value > 0 ? formatAmount(value) : ''
  submitError.value = ''
  // 금액이 바뀌면 다른 요청이다 — 키를 새로 발급받게 비운다
  idempotencyKey.value = null
}

function pickPreset(value: number) {
  rawAmount.value = formatAmount(value)
  // 금액이 바뀌면 이전 거절 사유는 더 이상 이 입력에 대한 것이 아니다 (onInput과 같은 처리)
  submitError.value = ''
  idempotencyKey.value = null
}

function startEditAccount() {
  draftBankCode.value = account.value?.bankCode ?? SETTLEMENT_BANKS[0].code
  // 계좌번호 원문은 서버가 주지 않으므로 채워 넣을 값이 없다. 예금주는 마스킹 대상이 아니라 이어받는다.
  draftNumber.value = ''
  draftHolder.value = account.value?.accountHolderName ?? ''
  accountError.value = ''
  editingAccount.value = true
}

/** 저장 가능 조건 — 형식 최종 판정은 서버(`INVALID_ACCOUNT_NUMBER`)가 한다 */
const canSaveAccount = computed(
  () =>
    !accountSaving.value &&
    draftNumber.value.replace(/\D/g, '').length > 0 &&
    draftHolder.value.trim().length > 0,
)

async function saveAccount() {
  if (!canSaveAccount.value) return
  accountSaving.value = true
  accountError.value = ''
  try {
    account.value = await putSettlementAccount({
      bankCode: draftBankCode.value,
      // 명세: "공백과 구분 기호를 제거한 계좌번호"
      accountNumber: draftNumber.value.replace(/\D/g, ''),
      accountHolderName: draftHolder.value.trim(),
    })
    editingAccount.value = false
  } catch (error) {
    // 지원하지 않는 은행·형식 오류·진행 중 정산으로 인한 변경 차단이 전부 여기로 온다
    accountError.value = toApiError(error).message
  } finally {
    accountSaving.value = false
  }
}

async function submit() {
  if (amount.value <= 0 || inputError.value || submitting.value || !hasAccount.value) return
  submitting.value = true
  submitError.value = ''
  // 실패 사유를 버리지 않는다 — 서버 검증·잔액 경합·중복 요청(409)이 전부 이 분기로 온다
  idempotencyKey.value ??= newRequestId()
  const failure = await wallet.withdrawTradePoint(amount.value, idempotencyKey.value)
  if (failure) {
    submitError.value = failure
    doneMessage.value = ''
    submitting.value = false
    return
  }
  idempotencyKey.value = null
  wallet.setActiveWalletType('TRADE')
  doneMessage.value = `${formatPoint(amount.value)} 출금 신청 완료 · 테스트 송금`
  scheduleReturn(900)
}
</script>

<template>
  <main class="bg-cream min-h-screen px-5 py-10">
    <div class="mx-auto w-full max-w-130">
      <h1 class="text-ink text-[28px] font-bold tracking-tight">거래 포인트 출금</h1>
      <p class="text-dim mt-1.5 text-[13px]">
        등록된 계좌로 보내요. 보류 중인 포인트는 정산 전까지 출금할 수 없어요.
      </p>

      <!-- 잔액 조회 실패를 알린다 — 안 그러면 폴백 0P가 "돈이 없음"으로 읽힌다 -->
      <p
        v-if="wallet.loadError"
        class="border-terra/40 bg-terra-tint text-terra-tint-text mt-5 rounded-[10px] border p-3 text-[12px] leading-relaxed"
        role="alert"
      >
        {{ wallet.loadError }}
      </p>

      <!-- 출금 가능 금액 = 사용 가능 잔액 (보류는 상한에서 제외된 값이다) -->
      <div class="border-line bg-surface mt-5 rounded-2xl border p-4.5">
        <div class="flex items-center justify-between">
          <span class="text-dim text-[12.5px]">출금 가능 금액</span>
          <span class="text-ink text-[24px] font-bold tabular-nums">{{
            formatPoint(available)
          }}</span>
        </div>
        <div class="border-line mt-2.5 flex items-center justify-between border-t pt-2.5">
          <span class="text-faint text-[12px]">보류 중 (출금 불가)</span>
          <span class="text-slate text-[13px] font-semibold tabular-nums">{{
            formatPoint(held)
          }}</span>
        </div>
      </div>

      <!-- 출금 금액 -->
      <div class="mt-6">
        <div class="text-ink text-[13px] font-semibold">출금 금액</div>
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
          class="bg-surface mt-2 flex items-baseline gap-2.5 rounded-xl border px-3 py-2.5 transition"
          :class="inputError ? 'border-red' : 'border-line focus-within:border-slate'"
        >
          <span class="text-dim flex-none self-center text-[11.5px]">직접 입력</span>
          <input
            :value="rawAmount"
            class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[17px] font-semibold tabular-nums outline-none"
            inputmode="numeric"
            aria-label="출금 금액"
            @input="onInput"
          />
          <span class="text-dim flex-none text-[12px] font-semibold">P</span>
        </label>
        <p v-if="error" class="text-red mt-1.5 text-[12px]" role="alert">{{ error }}</p>
      </div>

      <!-- 받을 계좌 — 조회/편집 두 상태 (와이어프레임 wdAccountView / wdAccountEdit) -->
      <div class="border-line bg-surface mt-5 rounded-2xl border px-4.5 py-4">
        <p v-if="accountLoading" class="text-faint text-[12.5px]">계좌 정보를 불러오는 중…</p>

        <div v-else-if="!editingAccount" class="flex items-center justify-between gap-3">
          <div class="min-w-0">
            <div class="text-faint text-[11px]">받을 계좌</div>
            <div class="text-ink mt-1 truncate text-[13.5px] tabular-nums">{{ accountText }}</div>
            <!-- 실명 검증 전까지 서버가 verified=false를 준다. "검증 완료"로 보이게 하지 않는다 -->
            <div v-if="account && !account.verified" class="text-faint mt-1 text-[11px]">
              예금주 확인 전이에요
            </div>
          </div>
          <button
            type="button"
            class="border-line text-dim hover:bg-cream flex-none rounded-full border px-3 py-1.5 text-[12px] transition"
            @click="startEditAccount"
          >
            계좌 변경
          </button>
        </div>

        <div v-else>
          <div class="text-faint text-[11px]">받을 계좌</div>
          <div class="text-ink mt-2 text-[13px] font-semibold">은행</div>
          <div class="mt-2 grid grid-cols-3 gap-2">
            <button
              v-for="bank in SETTLEMENT_BANKS"
              :key="bank.code"
              type="button"
              class="rounded-xl border py-2.5 text-center text-[12.5px] font-medium transition"
              :class="
                draftBankCode === bank.code
                  ? 'bg-terra-tint border-terra text-terra-tint-text'
                  : 'border-line text-ink hover:bg-cream bg-surface'
              "
              @click="draftBankCode = bank.code"
            >
              {{ bank.name }}
            </button>
          </div>

          <label
            class="border-line focus-within:border-slate bg-surface mt-3 flex items-baseline gap-2.5 rounded-xl border px-3 py-2.5 transition"
          >
            <span class="text-dim flex-none self-center text-[11.5px]">계좌번호</span>
            <input
              v-model="draftNumber"
              class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[15px] font-semibold tabular-nums outline-none"
              inputmode="numeric"
              placeholder="- 없이 숫자만 입력"
              aria-label="계좌번호"
            />
          </label>

          <label
            class="border-line focus-within:border-slate bg-surface mt-2 flex items-baseline gap-2.5 rounded-xl border px-3 py-2.5 transition"
          >
            <span class="text-dim flex-none self-center text-[11.5px]">예금주</span>
            <input
              v-model="draftHolder"
              class="text-ink w-full min-w-0 border-0 bg-transparent text-right text-[15px] font-semibold outline-none"
              placeholder="계좌에 등록된 이름"
              aria-label="예금주"
            />
          </label>

          <div class="mt-2.5 flex gap-2">
            <button
              v-if="hasAccount"
              type="button"
              class="border-line text-dim hover:bg-cream bg-surface flex-1 rounded-xl border py-2.5 text-[12.5px] font-medium transition"
              @click="editingAccount = false"
            >
              취소
            </button>
            <button
              type="button"
              class="bg-terra text-on-dark hover:bg-terra-d flex-1 rounded-xl py-2.5 text-[12.5px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
              :disabled="!canSaveAccount"
              @click="saveAccount"
            >
              {{ accountSaving ? '저장 중…' : '저장' }}
            </button>
          </div>
        </div>

        <p v-if="accountError" class="text-red mt-2 text-[12px]" role="alert">{{ accountError }}</p>
      </div>

      <div class="border-line text-dim mt-4 rounded-xl border border-dashed px-4 py-3">
        <p class="text-[11.5px] leading-relaxed">
          영업일 기준 1~2일 내 입금돼요. 테스트 환경이라 실제 송금은 발생하지 않아요.
        </p>
      </div>

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
          :disabled="amount <= 0 || !!inputError || submitting || !hasAccount"
          @click="submit"
        >
          <!-- 보낼 계좌가 없으면 금액이 맞아도 출금이 성립하지 않는다 -->
          {{
            !hasAccount
              ? '받을 계좌를 먼저 등록하세요'
              : amount > 0
                ? `${formatPoint(amount)} 출금 신청`
                : '금액을 입력하세요'
          }}
        </button>
      </div>
    </div>
  </main>
</template>
