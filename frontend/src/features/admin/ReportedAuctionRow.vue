<script setup lang="ts">
// 신고 접수 경매 한 줄 (S15P11A704-525). 상태는 갖지 않고 부모의 무장 상태를 props로 받는다.
import { computed } from 'vue'
import type { ReportedAuctionItem } from '@/shared/api/admin'
import type { ReportReasonCode } from '@/shared/api/reports'
import type { Id } from '@/shared/api/types'

const props = defineProps<{
  item: ReportedAuctionItem
  armed: boolean
  /** 이 행의 종료 요청이 나가 있다 */
  pending: boolean
  /**
   * 목록 어딘가에 종료 요청이 나가 있다. 안전장치가 아니라 **피드백**이다 —
   * 비행 중에는 composable이 다른 행의 `arm()`을 조용히 무시하므로, 눌러도 아무 일이
   * 없는 버튼을 활성으로 두면 고장처럼 보인다.
   */
  busy: boolean
  /** 이 행에서 난 오류. 다른 행 것이면 null */
  error: string | null
}>()

// `Id`를 쓴다 — `number`로 좁히면 `useAdminForceEnd`·`admin.ts`와 타입이 갈린다.
//
// **사유를 실어 보내지 않는다.** 서버로 나가는 `reason`은 `useAdminForceEnd`의 운영 고정값이다 —
// 신고 사유는 `AUCTION_CANCELED`로 그 방 시청자 전원에게 브로드캐스트되기 때문이다(그쪽 주석).
// 아래 `topReason()`은 이 화면 안에서만 쓰는 표시값이다.
const emit = defineEmits<{
  arm: [auctionId: Id]
  confirm: [auctionId: Id]
}>()

/**
 * 신고 사유 코드 → 한국어. 접수 화면(`shared/api/reports.ts`)과 같은 값 집합이라 키를 그 유니온으로
 * 묶는다 — 코드가 늘면 여기서 컴파일이 깨진다.
 * 같은 라벨이 두 번째 화면에 나오면 그때 `shared/`로 뺀다 (AGENTS.md: 공통을 미리 만들지 않는다).
 */
const REASON_LABELS: Record<ReportReasonCode, string> = {
  FALSE_LISTING: '허위 매물',
  PROHIBITED_ITEM: '금지 품목',
  INAPPROPRIATE_BROADCAST: '부적절한 방송',
  OTHER: '기타',
}

/** 모르는 코드가 와도 칸이 비지 않게 코드를 그대로 보여준다 */
const reasonLabel = (code: string) => REASON_LABELS[code as ReportReasonCode] ?? code

/**
 * 서버가 건수 내림차순으로 정렬해 보낸다 — 첫 항목이 최빈 사유다.
 * **운영자에게 보이는 표시 전용**이다. 종료 요청에는 실리지 않는다(위 emits 주석).
 */
const topReason = () => props.item.reasonCounts[0]?.reasonCode ?? 'OTHER'

/** 신고가 없는 방(「전체 방」 탭)은 붙일 사유가 없다 — 2단계 안내에서 그 절을 뺀다 */
const topReasonLabel = computed(() =>
  props.item.reasonCounts.length > 0 ? reasonLabel(topReason()) : null,
)

/** 「전체 방」 탭은 신고 정보가 없어 빈 문자열이 되고, 그러면 이 줄 자체를 그리지 않는다 */
const reasonSummary = computed(() =>
  props.item.reasonCounts.map((r) => `${reasonLabel(r.reasonCode)} ${r.count}`).join(' · '),
)

// 진행 중은 레드(지금 벌어지는 일), 대기는 슬레이트(예정된 일) — AGENTS.md 규칙 1의 기능색 구분.
// `ENDED`는 없다 — 두 탭 모두 종료된 방을 배제하므로 그리는 자리가 없고, 그래도 오면 아래 폴백이 받는다.
const STATUS_STYLES: Record<string, { label: string; class: string }> = {
  LIVE: { label: '진행 중', class: 'bg-terra text-on-dark' },
  WAITING: { label: '대기', class: 'bg-slate-tint text-slate-tint-text' },
}

const status = computed(
  () => STATUS_STYLES[props.item.status] ?? { label: props.item.status, class: 'bg-line text-dim' },
)

const buttonLabel = computed(() => {
  if (props.pending) return '종료하는 중…'
  return props.armed ? '한 번 더 눌러 종료' : '강제 종료'
})

/** 1단계·2단계를 버튼 하나가 겸한다 — 무장 여부는 부모가 판정해 내려준다 */
function press() {
  if (props.armed) emit('confirm', props.item.auctionId)
  else emit('arm', props.item.auctionId)
}
</script>

<template>
  <li class="border-line bg-surface rounded-2xl border px-4.5 py-4">
    <div class="flex items-start gap-3">
      <img
        v-if="item.thumbnailUrl"
        :src="item.thumbnailUrl"
        alt=""
        class="bg-line h-12 w-12 flex-none rounded-xl object-cover"
      />
      <!-- 이미지가 없는 상품은 `thumbnailUrl`이 null이다. `<img :src="null">`은 깨진 아이콘이 뜬다 -->
      <div v-else class="bg-line h-12 w-12 flex-none rounded-xl" aria-hidden="true"></div>

      <div class="min-w-0 flex-1">
        <div class="flex flex-wrap items-center gap-2">
          <p class="text-ink min-w-0 truncate text-[14px] font-semibold">{{ item.title }}</p>
          <span
            class="flex-none rounded-full px-2 py-0.5 text-[11px] font-semibold"
            :class="status.class"
          >
            {{ status.label }}
          </span>
          <span
            v-if="item.reportCount > 0"
            class="bg-terra-tint text-terra-tint-text flex-none rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums"
          >
            신고 {{ item.reportCount }}
          </span>
        </div>
        <p class="text-faint mt-1 text-[11.5px]">판매자 {{ item.sellerNickname }}</p>
        <p v-if="reasonSummary" class="text-dim mt-1 text-[12px]">{{ reasonSummary }}</p>
      </div>

      <button
        type="button"
        class="bg-terra text-on-dark hover:bg-terra-d flex-none rounded-full px-3 py-1.5 text-[12px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
        :disabled="pending || busy"
        :aria-label="`${item.title} ${buttonLabel}`"
        @click="press"
      >
        {{ buttonLabel }}
      </button>
    </div>

    <!--
      2단계 안내. 되돌릴 수 없는 동작이라 무엇이 일어나는지를 먼저 둔다.
      「사유」는 이 방에 접수된 최빈 신고 사유이고 **운영자만 본다** — 종료 요청에는 실리지 않는다.
    -->
    <p
      v-if="armed"
      class="bg-terra-tint text-terra-tint-text mt-2.5 rounded-xl px-3 py-2 text-[12px]"
      role="status"
    >
      입찰·예측 전액 환급됩니다<template v-if="topReasonLabel">
        · 사유: {{ topReasonLabel }}</template
      >
    </p>

    <p v-if="error" class="text-red mt-2 text-[11.5px]" role="alert">{{ error }}</p>
  </li>
</template>
