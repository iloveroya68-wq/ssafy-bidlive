<script setup lang="ts">
// DEAL-04 경매 신고 — 와이어프레임 `#repModal`.
//
// 대기방과 라이브 두 화면에서 열린다(와이어프레임이 `.rep` 버튼을 두 곳에 뒀다). 그래서 진입점이
// 아니라 여기(공용)에 둔다 — `ShippingAddressModal`과 같은 이유다.
//
// **접수만 알린다.** 명세: *"신고 검토 결과나 강제 종료 여부는 응답에 포함하지 않습니다"* —
// 그래서 성공 문구도 "접수됐다"까지다. 강제 종료는 운영자 검토 후 DEAL-09가 한다.
import { computed, ref } from 'vue'
import type { ReportReasonCode } from '@/shared/api/reports'

const props = defineProps<{
  productTitle: string
  sellerNickname: string | null
  pending: boolean
  submitError: string | null
}>()

const emit = defineEmits<{
  close: []
  submit: [value: { reasonCode: ReportReasonCode; description: string }]
}>()

/**
 * 사유 목록. 코드는 명세, 문구는 와이어프레임 `#repReasons`에서 그대로 가져왔다 —
 * 사용자가 고르는 건 코드가 아니라 이 설명이라 임의로 줄이지 않는다.
 */
const REASONS: { code: ReportReasonCode; title: string; hint: string }[] = [
  { code: 'FALSE_LISTING', title: '허위 매물', hint: '실물과 다르거나 존재하지 않는 상품' },
  { code: 'PROHIBITED_ITEM', title: '금지 품목', hint: '주류·의약품·무기류 등 거래 불가 품목' },
  {
    code: 'INAPPROPRIATE_BROADCAST',
    title: '부적절한 방송·언행',
    hint: '욕설, 선정적 내용, 타인 비방',
  },
  { code: 'OTHER', title: '기타', hint: '아래에 자세히 적어주세요' },
]

// 와이어프레임이 첫 항목을 선택 상태(`.rsn.on`)로 열어 둔다
const reasonCode = ref<ReportReasonCode>('FALSE_LISTING')
const description = ref('')

/** `OTHER`만 상세 설명이 필수다 — 무엇을 확인해야 할지 알 수 없기 때문 */
const descriptionRequired = computed(() => reasonCode.value === 'OTHER')

const canSubmit = computed(() => !descriptionRequired.value || description.value.trim() !== '')

function handleSubmit() {
  if (!canSubmit.value || props.pending) return
  emit('submit', { reasonCode: reasonCode.value, description: description.value.trim() })
}
</script>

<template>
  <div
    class="bg-stage/55 fixed inset-0 z-50 flex items-center justify-center p-5 backdrop-blur-sm"
    role="dialog"
    aria-modal="true"
    aria-label="이 방송 신고하기"
    @click.self="emit('close')"
  >
    <div class="bg-surface flex max-h-[85vh] w-full max-w-125 flex-col overflow-hidden rounded-2xl">
      <header class="flex items-start justify-between gap-4 px-6 pt-5.5">
        <div>
          <h2 class="text-ink text-lg font-bold">이 방송 신고하기</h2>
          <p class="text-faint mt-1 text-[12.5px]">
            접수되면 운영팀이 확인하고, 필요하면 방송을 종료합니다.
          </p>
        </div>
        <button
          type="button"
          class="text-faint hover:text-ink -mt-1 flex-none p-1 text-xl leading-none transition"
          aria-label="닫기"
          @click="emit('close')"
        >
          ×
        </button>
      </header>

      <!-- 어느 방송을 신고하는지 — 라이브에서 열면 방을 착각할 수 있다 -->
      <div class="bg-cream mx-6 mt-4 rounded-xl px-3.5 py-2.75">
        <p class="text-ink truncate text-[13px] font-semibold">{{ productTitle }}</p>
        <p v-if="sellerNickname" class="text-faint text-[11.5px]">판매자 {{ sellerNickname }}</p>
      </div>

      <form class="flex flex-col overflow-y-auto px-6 py-4.5" @submit.prevent="handleSubmit">
        <fieldset>
          <legend class="text-ink mb-2.5 text-[13px] font-semibold">신고 사유</legend>
          <div class="flex flex-col gap-2">
            <label
              v-for="reason in REASONS"
              :key="reason.code"
              class="border-line flex cursor-pointer items-start gap-2.5 rounded-xl border px-3.5 py-2.75 transition"
              :class="reasonCode === reason.code ? 'border-ink bg-cream' : 'hover:border-dim'"
            >
              <input
                v-model="reasonCode"
                type="radio"
                name="report-reason"
                :value="reason.code"
                class="accent-terra mt-0.5"
              />
              <span>
                <span class="text-ink block text-[13px] font-semibold">{{ reason.title }}</span>
                <span class="text-faint block text-[11.5px]">{{ reason.hint }}</span>
              </span>
            </label>
          </div>
        </fieldset>

        <label class="mt-3.5 block">
          <span class="text-faint mb-1.5 block text-[12px]">
            상세 설명
            <span v-if="descriptionRequired" class="text-terra">*</span>
          </span>
          <textarea
            v-model="description"
            rows="3"
            :placeholder="
              descriptionRequired
                ? '어떤 점이 문제인지 적어주세요'
                : '어떤 점이 문제인지 적어주세요 (선택)'
            "
            class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink w-full resize-none rounded-xl border px-3.5 py-2.75 text-[13.5px] transition outline-none"
          ></textarea>
        </label>

        <p
          class="border-line text-dim mt-4 rounded-xl border border-dashed px-3.5 py-3 text-[11.5px] leading-relaxed"
        >
          신고자는 <b class="text-ink">판매자에게 공개되지 않아요.</b>
          다만 허위·반복 신고는 이용에 제한이 있을 수 있어요.
        </p>

        <p v-if="submitError" class="text-red mt-3 text-[12px]" role="alert">{{ submitError }}</p>
      </form>

      <footer class="flex items-center gap-2 px-6 pt-1 pb-5.5">
        <button
          type="button"
          class="border-line text-dim hover:text-ink rounded-xl border px-5 py-3 text-[13.5px] font-semibold transition"
          @click="emit('close')"
        >
          취소
        </button>
        <div class="flex-1"></div>
        <button
          type="button"
          class="bg-terra text-on-dark hover:bg-terra-d rounded-xl px-6 py-3 text-[13.5px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
          :disabled="!canSubmit || pending"
          @click="handleSubmit"
        >
          {{ pending ? '접수하는 중…' : '신고 접수' }}
        </button>
      </footer>
    </div>
  </div>
</template>
