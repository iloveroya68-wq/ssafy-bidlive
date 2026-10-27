<script setup lang="ts">
// ROOM-07 대기방 잠금 dock (FE1) — 디자인 원본 `data-screen="wait"`의 `.dock.locked`.
//
// **미리보기 전용이다.** 실제 `BidDock`·`PredictionDock`을 잠금 상태로 끼우지 않고 따로 그린다.
// 이유는 둘이다.
//   ① 저 둘의 `locked`는 "상호 배타로 잠김"(docs/03 §4-1)이라 안내 문구가 다르다.
//      대기방의 잠금은 배타 잠금이 아니라 **아직 방송이 시작되지 않은 것**이다.
//   ② 대기방에서는 입력·제출 경로 자체가 존재하면 안 된다. 서버 권한도 `permissions.bid=false`다
//      (명세 「대기방 입장 및 상태 조회」: "대기방에서는 입찰과 예측 베팅을 비활성화한다").
//      실제 입력 컴포넌트를 disabled로 두는 것보다, 접수 경로가 없는 미리보기가 더 안전하다.
//
// 그래서 이 안에는 이벤트 핸들러도 API 호출도 없다. 골격과 안내뿐이다.
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { PAYOUT_BANDS, BAND_MULTIPLIER } from '@/stores/prediction'
import LockNote from '@/shared/ui/LockNote.vue'

defineProps<{
  /** `auction_room.reserve_price` — 공개 조건이라 시작 전에도 보여 준다 */
  reservePrice: number
}>()

/** 디자인 원본 `.locknote`의 대기방 카피 그대로 */
const WAITING_LOCK_NOTE =
  '🔒 방송이 시작되면 입찰과 예측 베팅이 열려요. 지금은 채팅으로 판매자에게 질문할 수 있어요.'

/** 디자인 원본의 퀵애드 칩 — 시작 후 `BidDock`이 쓰는 값과 같다 */
const QUICK_ADDS = [1000, 5000, 10000]
</script>

<template>
  <section
    class="border-line bg-surface rounded-[18px] border p-[18px]"
    aria-label="입찰·예측 잠김"
  >
    <!-- ── 상향 입찰 (잠김) ── -->
    <div class="text-dim flex items-center justify-between text-[11.5px]">
      <span class="text-ink font-semibold">상향 입찰</span>
      <span class="tabular-nums">하한가 {{ formatPoint(reservePrice) }}</span>
    </div>

    <!-- 잠긴 골격은 숨기지 않고 흐리게 둔다 — 무엇이 열릴지 미리 보여야 하기 때문(docs/03 §4-1).
         입력 요소가 아니라 div라 키보드로 들어갈 수 없다 -->
    <div class="pointer-events-none opacity-40 grayscale" aria-hidden="true">
      <div class="mt-2.5 flex gap-2">
        <div
          class="border-line text-faint bg-surface min-w-0 flex-1 rounded-[10px] border px-3.5 py-2.5 text-[16px] font-medium tabular-nums"
        >
          {{ formatAmount(reservePrice) }}
        </div>
        <div class="bg-terra rounded-[10px] px-5 py-2.5 text-[14px] font-bold text-white">
          상향 입찰
        </div>
      </div>
      <div class="mt-2.5 flex gap-1.5">
        <div
          v-for="delta in QUICK_ADDS"
          :key="delta"
          class="border-line text-dim bg-surface flex-1 rounded-full border py-[7px] text-center text-[12px] tabular-nums"
        >
          +{{ formatAmount(delta) }}
        </div>
      </div>
    </div>

    <!-- ── 낙찰가 예측 베팅 (잠김) ──
         신 설계 기준(AGENTS.md 규칙 7): 금액 직접 입력 + 스테이크, 오차율 고정 배당.
         구간 선택도 실시간 분포도 그리지 않는다 — 여기 있는 건 고정 배수 안내뿐이다. -->
    <div class="border-line mt-4 border-t pt-4">
      <div class="text-dim flex items-center justify-between text-[11.5px]">
        <span class="text-ink font-semibold">낙찰가 예측 베팅</span>
        <span>방당 1회</span>
      </div>

      <div class="pointer-events-none mt-2.5 opacity-40 grayscale" aria-hidden="true">
        <div
          class="border-line text-faint bg-surface rounded-[10px] border px-3.5 py-2.5 text-[15px] font-medium tabular-nums"
        >
          예상 낙찰가 입력
        </div>
        <div class="mt-2 flex gap-1.5">
          <div
            v-for="band in PAYOUT_BANDS"
            :key="band.band"
            class="bg-slate-tint text-slate-tint-text flex-1 rounded-[9px] py-[7px] text-center text-[11.5px] font-semibold tabular-nums"
          >
            {{ band.label }} ×{{ BAND_MULTIPLIER[band.band] }}
          </div>
        </div>
      </div>
    </div>

    <LockNote :text="WAITING_LOCK_NOTE" />
  </section>
</template>
