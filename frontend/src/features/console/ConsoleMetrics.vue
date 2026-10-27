<script setup lang="ts">
// 콘솔 지표 3박스 (와이어프레임 .cmetrics). 대기/라이브 두 세트를 상태로 갈아 끼운다.
// 스토어를 모르는 표현 컴포넌트다 — 값은 전부 props로 받는다.
defineProps<{
  state: 'wait' | 'live'
  /** null = 스냅샷·이벤트 미수신. "0건"으로 단정하지 않고 —로 둔다 */
  bidCount: number | null
  participantCount: number | null
  viewerCount: number | null
  /**
   * 대기방 알림 신청자 수 (방 상세 `notificationCount`).
   * 판매자·관리자에게만 오는 값이라 그 외에는 서버가 null을 준다 — 그때는 —로 둔다.
   */
  notificationCount: number | null
  /** useCountdown의 display — 'MM:SS' 또는 서버 기준이 없으면 '--:--' */
  remainingDisplay: string
  remainingUrgent: boolean
  startsInDisplay: string
}>()

const fmt = (value: number | null, unit: string) => (value === null ? '—' : `${value}${unit}`)
</script>

<template>
  <!-- 바깥 여백은 배치하는 화면이 정한다 — 카드의 첫 요소로도 들어가기 때문이다(S15P11A704-691) -->
  <!-- 라이브는 넷, 대기는 셋이다. 시청자는 방송 중에만 뜻이 있는 값이라 대기 세트에는
       같은 수치가 「대기 인원」으로 이미 들어가 있다 (S15P11A704 콘솔 시청자 요청). -->
  <div class="grid gap-2.5" :class="state === 'live' ? 'grid-cols-4' : 'grid-cols-3'">
    <template v-if="state === 'live'">
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">입찰 건수</div>
        <div class="text-ink mt-1 text-[19px] font-bold tabular-nums">
          {{ fmt(bidCount, '건') }}
        </div>
      </div>
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">참여자</div>
        <div class="text-ink mt-1 text-[19px] font-bold tabular-nums">
          {{ fmt(participantCount, '명') }}
        </div>
      </div>
      <!-- 참여자(입찰한 사람)와 다른 값이다 — 구경만 하는 사람까지 포함한 접속 수.
           ⚠️ 지금은 판매자 본인의 콘솔 세션도 여기 포함된다(BE `ViewerSessionTrackingService`가
           STOMP 세션만 세고 회원을 모른다). 방송하는 사람이 자기를 시청자로 세는 셈이라 별도 건이다. -->
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">시청자</div>
        <div class="text-ink mt-1 text-[19px] font-bold tabular-nums">
          {{ fmt(viewerCount, '명') }}
        </div>
      </div>
      <div
        class="rounded-xl border px-3.5 py-3"
        :class="remainingUrgent ? 'border-terra bg-terra-tint' : 'bg-cream border-line'"
      >
        <div class="text-faint text-[11px]">남은 시간</div>
        <div
          class="mt-1 text-[19px] font-bold tabular-nums"
          :class="remainingUrgent ? 'text-terra-tint-text' : 'text-ink'"
        >
          {{ remainingDisplay }}
        </div>
      </div>
    </template>

    <template v-else>
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">대기 인원</div>
        <div class="text-ink mt-1 text-[19px] font-bold tabular-nums">
          {{ fmt(viewerCount, '명') }}
        </div>
      </div>
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">알림 신청</div>
        <div
          class="mt-1 text-[19px] font-bold tabular-nums"
          :class="notificationCount === null ? 'text-faint' : 'text-ink'"
        >
          {{ fmt(notificationCount, '명') }}
        </div>
      </div>
      <div class="bg-cream border-line rounded-xl border px-3.5 py-3">
        <div class="text-faint text-[11px]">시작까지</div>
        <div class="text-ink mt-1 text-[19px] font-bold tabular-nums">{{ startsInDisplay }}</div>
      </div>
    </template>
  </div>
</template>
