<script setup lang="ts">
// ROOM-01 방 개설 2단계 — 경매 조건 (auction_room)
// 스키마 제약(docs/04 §3.4):
//  - reserve_price > 0, duration_minutes > 0 → 0 이하 입력 차단
//  - start_type IN ('IMMEDIATE','SCHEDULED') + ck_auction_room_schedule
//    (IMMEDIATE면 scheduled_at NULL, SCHEDULED면 NOT NULL) → 예약 선택 시 시각 필수 검증
//  - extension_count BETWEEN 0 AND 3 → 연장 안내 문구는 최대 3회
import { computed } from 'vue'
import type { StartType } from '@/stores/room'

const reservePrice = defineModel<number | null>('reservePrice', { required: true })
const durationMinutes = defineModel<number | null>('durationMinutes', { required: true })
const startType = defineModel<StartType>('startType', { required: true })
const scheduledDate = defineModel<string>('scheduledDate', { required: true })
const scheduledTime = defineModel<string>('scheduledTime', { required: true })

defineProps<{
  errors: Partial<Record<'reservePrice' | 'durationMinutes' | 'scheduledAt', string>>
}>()

const DURATION_PRESETS = [10, 15, 30]

const reservePriceLabel = computed(() =>
  reservePrice.value && reservePrice.value > 0
    ? `${reservePrice.value.toLocaleString('ko-KR')}P`
    : '—',
)

// 즉시 시작으로 되돌리면 예약 시각을 비워 보낸다 (ck_auction_room_schedule)
function selectStartType(next: StartType) {
  startType.value = next
  if (next === 'IMMEDIATE') {
    scheduledDate.value = ''
    scheduledTime.value = ''
  }
}
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <div class="flex flex-col gap-3.5">
      <!-- 하한가 -->
      <section class="border-line bg-surface rounded-2xl border p-5">
        <label for="cr-reserve" class="text-ink text-[13px] font-semibold">
          하한가 <span class="text-terra">*</span>
        </label>
        <p class="text-dim mt-1 mb-2.5 text-[12px]">이 금액 아래로는 낙찰되지 않아요.</p>
        <div
          class="border-line focus-within:border-terra flex items-center gap-2 rounded-xl border px-3.5 py-2.5 transition"
          :class="errors.reservePrice && 'border-red'"
        >
          <input
            id="cr-reserve"
            v-model.number="reservePrice"
            type="number"
            min="1"
            step="1"
            inputmode="numeric"
            placeholder="금액 입력"
            class="text-ink placeholder:text-faint w-full min-w-0 bg-transparent text-[18px] font-semibold tabular-nums outline-none"
          />
          <span class="text-dim flex-none text-[14px] font-semibold">P</span>
        </div>
        <p v-if="errors.reservePrice" class="text-red mt-1 text-[12px]">
          {{ errors.reservePrice }}
        </p>
        <p v-else class="text-faint mt-1 text-[11.5px] tabular-nums">
          입력한 하한가 · {{ reservePriceLabel }}
        </p>
      </section>

      <!-- 진행 시간 -->
      <section class="border-line bg-surface rounded-2xl border p-5">
        <div class="text-ink text-[13px] font-semibold">
          진행 시간 <span class="text-terra">*</span>
        </div>
        <div class="mt-2.5 flex flex-wrap gap-1.5">
          <button
            v-for="preset in DURATION_PRESETS"
            :key="preset"
            type="button"
            class="rounded-xl px-4 py-2 text-[13px] font-medium tabular-nums transition"
            :class="
              durationMinutes === preset
                ? 'bg-terra text-on-dark'
                : 'border-line text-dim hover:bg-cream bg-surface border'
            "
            @click="durationMinutes = preset"
          >
            {{ preset }}분
          </button>
        </div>
        <div class="mt-2.5 flex items-center gap-2">
          <label for="cr-duration" class="text-dim flex-none text-[12px]">직접 입력</label>
          <input
            id="cr-duration"
            v-model.number="durationMinutes"
            type="number"
            min="1"
            step="1"
            inputmode="numeric"
            class="border-line text-ink focus:border-terra w-24 rounded-xl border px-3 py-2 text-[13px] tabular-nums transition outline-none"
            :class="errors.durationMinutes && 'border-red'"
          />
          <span class="text-dim text-[12px]">분</span>
        </div>
        <p v-if="errors.durationMinutes" class="text-red mt-1 text-[12px]">
          {{ errors.durationMinutes }}
        </p>
        <p class="text-dim mt-2 text-[11.5px]">
          방송 중 1회 +5분씩, 최대 3회까지 연장할 수 있어요. (BID-03)
        </p>
      </section>
    </div>

    <div class="flex flex-col gap-3.5">
      <!-- 시작 방식 -->
      <section class="border-line bg-surface rounded-2xl border p-5">
        <div class="text-ink text-[13px] font-semibold">
          시작 방식 <span class="text-terra">*</span>
        </div>
        <div class="mt-2.5 flex gap-2">
          <button
            type="button"
            class="flex-1 rounded-xl border-[1.5px] px-3 py-3.5 text-left transition"
            :class="
              startType === 'IMMEDIATE'
                ? 'border-terra bg-terra-tint'
                : 'border-line hover:border-faint bg-surface'
            "
            @click="selectStartType('IMMEDIATE')"
          >
            <div class="text-ink text-[13.5px] font-semibold">지금 바로 시작</div>
            <!-- 「개설하면 곧장 방송」이었다 (S15P11A704-684) — 개설은 방을 만들 뿐이고
                 방송·카운트다운은 콘솔의 「지금 시작하기」가 시작한다(ROOM-01: 생성 직후는 항상 WAITING).
                 그 문구를 읽고 개설만 하고 기다린 사례가 팀 테스트에서 나왔다. -->
            <div class="text-dim mt-0.5 text-[11.5px]">개설 후 콘솔에서 시작</div>
          </button>
          <button
            type="button"
            class="flex-1 rounded-xl border-[1.5px] px-3 py-3.5 text-left transition"
            :class="
              startType === 'SCHEDULED'
                ? 'border-terra bg-terra-tint'
                : 'border-line hover:border-faint bg-surface'
            "
            @click="selectStartType('SCHEDULED')"
          >
            <div class="text-ink text-[13.5px] font-semibold">예정 시각 지정</div>
            <div class="text-dim mt-0.5 text-[11.5px]">대기방으로 먼저 열기</div>
          </button>
        </div>

        <!-- SCHEDULED에서만 노출·필수. IMMEDIATE면 scheduled_at은 NULL로 보낸다 -->
        <div v-if="startType === 'SCHEDULED'" class="mt-3.5">
          <div class="flex gap-2.5">
            <div class="flex-1">
              <label for="cr-date" class="text-dim mb-1.5 block text-[12px]">날짜</label>
              <input
                id="cr-date"
                v-model="scheduledDate"
                type="date"
                class="border-line text-ink focus:border-terra w-full rounded-xl border px-3 py-2.5 text-[13.5px] tabular-nums transition outline-none"
                :class="errors.scheduledAt && 'border-red'"
              />
            </div>
            <div class="flex-1">
              <label for="cr-time" class="text-dim mb-1.5 block text-[12px]">시각</label>
              <input
                id="cr-time"
                v-model="scheduledTime"
                type="time"
                class="border-line text-ink focus:border-terra w-full rounded-xl border px-3 py-2.5 text-[13.5px] tabular-nums transition outline-none"
                :class="errors.scheduledAt && 'border-red'"
              />
            </div>
          </div>
          <p v-if="errors.scheduledAt" class="text-red mt-1.5 text-[12px]">
            {{ errors.scheduledAt }}
          </p>
          <p
            class="bg-slate-tint text-slate-tint-text mt-3 rounded-xl px-3.5 py-3 text-[12px] leading-relaxed"
          >
            개설하면 바로 <b>대기방</b>이 열려요. 참여자는 채팅하며 기다리고, 입찰은 시작 후에
            열립니다. 시작은 예정 시각에 직접 누르시면 돼요. (ROOM-07)
          </p>
        </div>
      </section>

      <!-- 검수 안내: moderation_status는 서버가 정하므로 폼 필드가 아니라 안내로만 -->
      <section class="border-line bg-cream rounded-2xl border p-5">
        <div class="text-ink text-[13px] font-semibold">등록 후 검수</div>
        <p class="text-dim mt-1.5 text-[12px] leading-relaxed">
          등록한 상품과 사진은 검수를 거쳐요. 검수 중에는 목록에 "검수 중"으로 표시될 수 있어요.
        </p>
      </section>
    </div>
  </div>
</template>
