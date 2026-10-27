<script setup lang="ts">
// AI-03 따라잡기 요약 — 스테이지(#video) 위에 뜨는 반투명 오버레이 (S15P11A704-706).
//
// 와이어프레임 `.catchup`이 원래 이 자리다(스테이지 기준 left:16px/top:54px, 270px, 블러 유리).
// 레일 `#action` 상단에 밝은 카드로 있던 동안에는 데스크톱에서 레일 높이를 먹어 채팅 패널이 잘렸다.
//
// 무대 위 정보는 전부 블러 유리 규약을 따른다(DESIGN.md §Stage) — 타이머 칩(LiveLayout)과 같은 언어다.
// 헤더 색은 원본이 #FFB0B0이지만 팔레트 `:root`에 이름이 없는 색이라 하드코딩하지 않고
// 기존 레드 계열 토큰(terra-tint)으로 근사한다(규칙 1).
//
// 위치·표시 여부·자동 접힘 타이머는 LiveView가 쥔다. 여기는 표시만 한다.
import type { CatchUpSummary } from '@/shared/api/rooms'

defineProps<{
  summary: CatchUpSummary
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <!-- max-h/스크롤은 모바일 안전장치다 — 스테이지가 aspect-video라 세로가 ~190px뿐이라
       언급이 여러 줄이면 하단 prodbar(모바일 상시 노출, z-[6])를 파고든다. -->
  <section
    class="bg-ink/80 text-on-dark absolute top-[54px] left-4 z-[5] max-h-[calc(100%-118px)] w-[270px] overflow-y-auto rounded-[14px] border border-white/10 px-3.5 py-3 text-[12px] shadow-lg backdrop-blur-md"
    aria-label="지금까지의 방송 요약"
  >
    <div
      class="text-terra-tint mb-[7px] flex items-center justify-between gap-2 text-[11px] tracking-[0.06em]"
    >
      <span>따라잡기 — 지금까지의 방송</span>
      <button
        type="button"
        class="text-on-dark-dim hover:text-on-dark -mr-1 flex-none px-[5px] py-[2px] transition-colors motion-reduce:transition-none"
        aria-label="방송 요약 닫기"
        title="방송 요약 닫기"
        @click="emit('close')"
      >
        <span aria-hidden="true">✕</span>
      </button>
    </div>

    <p class="mt-1 leading-[1.55] tabular-nums">
      입찰 <b class="font-semibold text-white">{{ summary.bidSummary.bidCount }}건</b> · 참여
      {{ summary.bidSummary.participantCount }}명
      <template v-if="summary.extensionSummary.extensionCount > 0">
        · 판매자 연장
        <b class="font-semibold text-white">
          {{ summary.extensionSummary.extensionCount }}회 (+{{
            summary.extensionSummary.extendedMinutes
          }}분)
        </b>
      </template>
    </p>

    <div v-if="summary.sellerMentions.length > 0" class="mt-1 flex gap-1.5 leading-[1.55]">
      <span class="flex-none">판매자 언급 —</span>
      <ul class="min-w-0 space-y-0.5">
        <li
          v-for="(mention, index) in summary.sellerMentions"
          :key="`${index}-${mention}`"
          class="font-semibold text-white"
        >
          {{ mention }}
        </li>
      </ul>
    </div>

    <p
      v-if="summary.sourceType === 'STT_AND_ROOM_EVENT'"
      class="text-on-dark-dim mt-2 border-t border-white/10 pt-2 text-[10.5px]"
    >
      AI 요약 · 2분마다 갱신
    </p>
  </section>
</template>
