<script setup lang="ts">
// MEM-02 마이페이지 「지금 할 일」 패널 — 와이어프레임 mypage의 `todo` 패널.
//
// 판정은 서버가 한다. 「내 거래 할 일 조회」가 `deal.status` + 내 거래 역할로 계산해
// **내가 지금 처리해야 하는 것만** 내려주므로, 화면은 role·dealStatus 조합을 다시 따지지 않는다.
// 정렬(오래 묵은 것 먼저)도 서버 순서를 그대로 쓴다.
//
// 조회는 `MyPageView`가 한다 — 탭 배지(`todoN`)가 **탭을 열지 않아도** 개수를 보여야 해서,
// 이 패널이 자체 조회하면 배지가 늦게 뜬다. 여기서는 받은 것을 그리기만 한다.
//
// 액션은 `actionPath`가 아니라 **`actionCode`로 분기**한다(S15P11A704-337) — 와이어프레임에 거래 화면이
// 없어서 대응 라우트를 만들지 않았다. 배송지는 모달, 발송·수령은 확인 전이다. 처리는 `useDealAction`이
// 화면(`MyPageView`)에서 하고, 이 패널은 눌린 대상만 올려보낸다.
import { computed } from 'vue'
import type { DealTodo } from '@/shared/api/deals'
import type { DealActionTarget } from '@/features/deal/useDealAction'
import { dealStatusLabel } from '@/shared/lib/dealStatus'
import { formatPoint } from '@/shared/lib/point'
import { isActionableTodo } from '@/shared/api/deals'
import DealShippingInfo from '@/features/deal/DealShippingInfo.vue'
import type { Id } from '@/shared/api/types'

const props = defineProps<{
  items: DealTodo[]
  pending: boolean
  loadError: string | null
  /** 수령 확인 「한 번 더 눌러」 단계에 들어간 거래 */
  confirmingDealId: Id | null
  /** 요청이 나가 있는 거래 — 그 항목만 잠근다 */
  pendingDealId: Id | null
  actionError: string | null
  /** 오류를 어느 줄 밑에 붙일지 */
  errorDealId: Id | null
}>()

const emit = defineEmits<{ action: [target: DealActionTarget] }>()

function targetOf(todo: DealTodo): DealActionTarget {
  return {
    dealId: todo.dealId,
    actionCode: todo.actionCode,
    productTitle: todo.productTitle,
    finalPrice: todo.finalPrice,
  }
}

/** 수령 확인만 2단계다 — 되돌릴 수 없는 지급이라(AGENTS.md 규칙 아님, 티켓 337 판단) */
function buttonLabel(todo: DealTodo): string {
  if (props.pendingDealId === todo.dealId) return '처리 중…'
  if (props.confirmingDealId === todo.dealId) return '한 번 더 눌러 확정'
  return todo.actionLabel
}

const isEmpty = computed(() => props.items.length === 0)

/** 자동 확정까지 남은 날. 수령 대기 건에만 값이 있다 */
function autoConfirmNote(todo: DealTodo): string {
  if (todo.autoConfirmAt === null) return ''
  const remainMs = Date.parse(todo.autoConfirmAt) - Date.now()
  if (Number.isNaN(remainMs)) return ''
  if (remainMs <= 0) return '곧 자동 구매 확정돼요'
  const days = Math.ceil(remainMs / (24 * 60 * 60 * 1000))
  return `${days}일 뒤 자동 구매 확정돼요`
}
</script>

<template>
  <div>
    <p v-if="pending" class="text-faint py-8 text-center text-[13px]">불러오는 중이에요…</p>
    <p v-else-if="loadError" class="text-red py-8 text-center text-[13px]" role="alert">
      {{ loadError }}
    </p>
    <p v-else-if="isEmpty" class="text-faint py-8 text-center text-[13px]">
      진행 중인 거래가 없어요.
    </p>

    <ul v-else class="flex flex-col gap-2.5">
      <li
        v-for="todo in items"
        :key="todo.dealId"
        class="border-line bg-surface rounded-2xl border px-4.5 py-4"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex items-center gap-1.5">
              <!-- 내 역할에 따라 할 일의 성격이 다르다 (구매자는 받는 쪽, 판매자는 보내는 쪽) -->
              <span
                class="flex-none rounded-full px-2 py-0.5 text-[10.5px] font-semibold"
                :class="
                  todo.role === 'BUYER'
                    ? 'bg-terra-tint text-terra-tint-text'
                    : 'bg-slate-tint text-slate-tint-text'
                "
              >
                {{ todo.role === 'BUYER' ? '구매' : '판매' }}
              </span>
              <p class="text-ink truncate text-[14px] font-semibold">{{ todo.productTitle }}</p>
            </div>
            <p class="text-dim mt-1.5 text-[12px]">{{ todo.actionDescription }}</p>
            <p v-if="autoConfirmNote(todo)" class="text-faint mt-1 text-[11.5px]">
              {{ autoConfirmNote(todo) }}
            </p>
          </div>

          <!-- 문구는 서버가 준 것을 그대로 쓴다. 확인 단계·진행 중일 때만 화면이 바꿔 말한다.
               **`AWAIT_RECEIPT`에는 버튼을 그리지 않는다** (S15P11A704-694) — 판매자가 발송을
               마치고 구매자의 수령 확인을 기다리는 표시 전용 항목이라 누를 것이 없다. -->
          <button
            v-if="isActionableTodo(todo.actionCode)"
            type="button"
            class="bg-terra text-on-dark hover:bg-terra-d flex-none rounded-full px-3 py-1.5 text-[11.5px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
            :disabled="pendingDealId !== null"
            @click="emit('action', targetOf(todo))"
          >
            {{ buttonLabel(todo) }}
          </button>
        </div>

        <p v-if="errorDealId === todo.dealId" class="text-red mt-2 text-[11.5px]" role="alert">
          {{ actionError }}
        </p>

        <!-- 판매자에게만 — 보낼 곳이 없으면 발송 자체를 할 수 없다 (S15P11A704-694).
             구매자는 자기가 입력한 주소라 다시 보여줄 이유가 없다. -->
        <DealShippingInfo v-if="todo.role === 'SELLER'" :deal-id="todo.dealId" />

        <div class="border-line mt-3 flex items-center justify-between border-t pt-2.5 text-[12px]">
          <span class="text-faint">{{ dealStatusLabel(todo.dealStatus) }}</span>
          <span class="text-ink font-semibold tabular-nums">{{
            formatPoint(todo.finalPrice)
          }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>
