<script setup lang="ts">
// DEAL-05 배송 정보 (S15P11A704-694) — 판매자가 「어디로 보낼지」를 보는 자리.
//
// 배송지는 지금까지 **쓰기 전용**이었다. 구매자가 입력하면 저장만 되고 되읽는 API가 없어서,
// 판매자는 「발송 확인」 버튼만 받고 주소는 알 수 없었다. 그 조회 API가 생기면서 붙는 화면이다.
//
// 판매자 항목에서만 쓴다. 구매자는 자기가 입력한 주소라 굳이 다시 보여줄 이유가 없고,
// 필요하면 배송지 입력 모달이 이미 그 값을 다룬다.
import { onMounted, ref } from 'vue'
import { getDealShipping, type DealShipping } from '@/shared/api/deals'
import { toApiError } from '@/shared/api/errors'
import type { Id } from '@/shared/api/types'

const props = defineProps<{ dealId: Id }>()

const shipping = ref<DealShipping | null>(null)
const loadError = ref<string | null>(null)

onMounted(async () => {
  try {
    shipping.value = await getDealShipping(props.dealId)
  } catch (error) {
    // 실패해도 할 일 카드 자체는 살아 있어야 한다 — 주소를 못 받은 것이 발송 확인을 막을 이유는 없다
    loadError.value = toApiError(error).message
  }
})
</script>

<template>
  <div class="border-line mt-3 border-t pt-2.5">
    <p v-if="loadError" class="text-red text-[11.5px]" role="alert">{{ loadError }}</p>

    <!-- 구매자가 아직 입력하지 않은 상태. `addressSubmitted`로 가른다 —
         필드가 전부 null인 것과 이 상태를 화면이 다시 해석하지 않게 서버가 판정해 준다 -->
    <p v-else-if="shipping !== null && !shipping.addressSubmitted" class="text-faint text-[11.5px]">
      구매자가 아직 배송지를 입력하지 않았어요
    </p>

    <div v-else-if="shipping !== null" class="text-[11.5px]">
      <p class="text-faint">보낼 곳</p>
      <p class="text-ink mt-1 font-semibold">
        {{ shipping.recipientName }}
        <span class="text-dim font-normal tabular-nums">{{ shipping.recipientPhone }}</span>
      </p>
      <p class="text-dim mt-0.5 leading-relaxed">
        <span class="tabular-nums">[{{ shipping.postalCode }}]</span>
        {{ shipping.address }} {{ shipping.addressDetail }}
      </p>

      <!-- 발송 뒤에는 송장이 채워진다. 없으면 줄 자체를 안 그린다 -->
      <p v-if="shipping.trackingNumber" class="text-dim mt-1.5">
        <span class="text-faint">송장</span>
        {{ shipping.carrier }}
        <span class="tabular-nums">{{ shipping.trackingNumber }}</span>
      </p>
    </div>
  </div>
</template>
