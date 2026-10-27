<script setup lang="ts">
// DEAL-05 배송 정보 입력 — 와이어프레임 `#shipModal`.
//
// **두 곳에서 열린다**: 마이페이지(목록의 특정 건)와 낙찰 결과(방금 낙찰한 건).
// 와이어프레임 2338행이 그렇게 정해 뒀고, 그래서 진입점이 아니라 여기(공용)에 둔다.
// 결과 화면 진입은 S15P11A704-268에서 이 컴포넌트를 그대로 쓴다.
//
// 명세 Request에는 `memo`(배송 요청사항)가 있으나 와이어프레임 모달에 필드가 없어 넣지 않는다.
// 「주소 찾기」(우편번호 검색)도 외부 API가 필요해 이번 범위 밖 — 우편번호는 직접 입력한다.
import { computed, ref } from 'vue'
import type { ShippingAddressRequest } from '@/shared/api/deals'
import { formatPoint } from '@/shared/lib/point'

const props = defineProps<{
  productTitle: string
  /** 개최 목록은 낙찰 전이면 null이라 그때는 금액 줄을 숨긴다 */
  finalPrice: number | null
  pending: boolean
  submitError: string | null
}>()

const emit = defineEmits<{ close: []; submit: [value: ShippingAddressRequest] }>()

const receiverName = ref('')
const contact = ref('')
const postalCode = ref('')
const address = ref('')
const detailAddress = ref('')

/** 상세 주소만 선택이다 — 단독주택 등 동·호수가 없는 주소가 있다 */
const canSubmit = computed(
  () =>
    receiverName.value.trim() !== '' &&
    contact.value.trim() !== '' &&
    postalCode.value.trim() !== '' &&
    address.value.trim() !== '',
)

function handleSubmit() {
  if (!canSubmit.value || props.pending) return
  emit('submit', {
    receiverName: receiverName.value.trim(),
    contact: contact.value.trim(),
    postalCode: postalCode.value.trim(),
    address: address.value.trim(),
    detailAddress: detailAddress.value.trim(),
  })
}
</script>

<template>
  <div
    class="bg-stage/55 fixed inset-0 z-50 flex items-center justify-center p-5 backdrop-blur-sm"
    role="dialog"
    aria-modal="true"
    aria-label="배송 정보 입력"
    @click.self="emit('close')"
  >
    <div class="bg-surface flex max-h-[85vh] w-full max-w-125 flex-col overflow-hidden rounded-2xl">
      <header class="flex items-start justify-between gap-4 px-6 pt-5.5">
        <div>
          <h2 class="text-ink text-lg font-bold">배송 정보 입력</h2>
          <p class="text-faint mt-1 text-[12.5px]">
            낙찰 상품을 받을 주소를 입력하면 판매자에게 전달됩니다.
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

      <!-- 어느 거래의 배송지인지 — 목록에서 열면 여러 건 중 하나라 반드시 보여야 한다 -->
      <div class="bg-terra-tint mx-6 mt-4 rounded-xl px-3.5 py-2.75">
        <p class="text-ink truncate text-[13px] font-semibold">{{ productTitle }}</p>
        <p v-if="finalPrice !== null" class="text-terra-tint-text text-[11.5px] tabular-nums">
          낙찰 · {{ formatPoint(finalPrice) }}
        </p>
      </div>

      <form class="flex flex-col gap-3 overflow-y-auto px-6 py-4.5" @submit.prevent="handleSubmit">
        <div class="flex gap-2.5">
          <label class="flex-1">
            <span class="text-faint mb-1.5 block text-[12px]">받는 사람</span>
            <input
              v-model="receiverName"
              type="text"
              autocomplete="name"
              placeholder="이름을 입력하세요"
              class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink w-full rounded-xl border px-3.5 py-2.75 text-[13.5px] transition outline-none"
            />
          </label>
          <label class="flex-1">
            <span class="text-faint mb-1.5 block text-[12px]">연락처</span>
            <input
              v-model="contact"
              type="tel"
              autocomplete="tel"
              inputmode="tel"
              placeholder="010-0000-0000"
              class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink w-full rounded-xl border px-3.5 py-2.75 text-[13.5px] tabular-nums transition outline-none"
            />
          </label>
        </div>

        <fieldset>
          <legend class="text-faint mb-1.5 text-[12px]">주소</legend>
          <input
            v-model="postalCode"
            type="text"
            autocomplete="postal-code"
            inputmode="numeric"
            placeholder="우편번호"
            class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink w-30 rounded-xl border px-3.5 py-2.75 text-[13.5px] tabular-nums transition outline-none"
          />
          <input
            v-model="address"
            type="text"
            autocomplete="street-address"
            placeholder="기본 주소"
            class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink mt-2 w-full rounded-xl border px-3.5 py-2.75 text-[13.5px] transition outline-none"
          />
          <input
            v-model="detailAddress"
            type="text"
            placeholder="상세 주소 (동·호수)"
            class="border-line bg-cream text-ink placeholder:text-faint focus:border-ink mt-2 w-full rounded-xl border px-3.5 py-2.75 text-[13.5px] transition outline-none"
          />
        </fieldset>

        <p v-if="submitError" class="text-red text-[12px]" role="alert">{{ submitError }}</p>
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
          {{ pending ? '전달하는 중…' : '판매자에게 전달' }}
        </button>
      </footer>
    </div>
  </div>
</template>
