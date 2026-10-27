<script setup lang="ts">
// ROOM-07 대기방 스테이지 (FE1) — 디자인 원본 `data-screen="wait"`의 `.stage.wstage`.
//
// 라이브의 스테이지와 골격은 같지만 채우는 것이 다르다: 아직 스트림이 없고,
// `LIVE` 뱃지 대신 `대기` 뱃지가, `남은 시간` 대신 `시작까지`가 붙는다.
// `LiveLayout.vue`를 재사용하지 않은 이유는 저 프레임이 라이브 전용 요소(LIVE 뱃지, 마감 타이머,
// prodbar hover)를 하드코딩하고 있어서다 — 대기방을 끼우려면 프레임을 고쳐야 하는데
// 그 파일은 지금 다른 작업이 함께 잡고 있다.
import { computed } from 'vue'
import { cssUrl } from '@/shared/lib/cssUrl'
import { useCountdown } from '@/shared/lib/useCountdown'
import { useLiveStore } from '@/stores/live'

const props = withDefaults(
  defineProps<{
    /** 서버가 마스킹한 판매자 닉네임 */
    sellerName?: string
    /**
     * 대기 인원. `VIEWER_COUNT`(LIVE-03)로 갱신되며 **고유 회원 수가 아니라 활성 세션 수**다.
     * null이면 아직 첫 이벤트를 못 받은 상태라 표시하지 않는다.
     */
    waitingCount?: number | null
    /**
     * `scheduledAt` — **안내용 시각**이다(명세 「대기방 입장 및 상태 조회」 검증 규칙:
     * "해당 시각 도달만으로 상태가 변경되지 않는다"). epoch ms, 없으면 null.
     */
    scheduledAt?: number | null
    /** 스테이지 배경으로 깔 상품 이미지. 없으면 어두운 무대만 남는다 */
    imageUrl?: string | null
  }>(),
  { sellerName: '', waitingCount: null, scheduledAt: null, imageUrl: null },
)

const emit = defineEmits<{
  /** 헤더 뒤로가기(대기방 나가기) */
  back: []
}>()

// 시작까지 남은 시간도 **서버가 준 절대 시각 기준으로 표시만** 한다 (AGENTS.md 규칙 5).
// `Date.now()`를 단독으로 쓰지 않고 useCountdown의 공유 시계 + serverOffset을 거친다.
// ⚠️ 0이 되어도 아무 일도 일어나지 않는다 — 자동 시작은 존재하지 않고, 화면 전환의 유일한
//    트리거는 `AUCTION_STARTED` 이벤트다.
// 보통은 join 응답의 serverTime이 첫 렌더 전에 `serverOffset`을 세우지만, 그 필드는 선택이라
// 없는 응답이면 목록에서 받아둔 값(clockOffset의 폴백)이 대신 선다(S15P11A704-265).
const live = useLiveStore()
const { display, remainingMs } = useCountdown(() => props.scheduledAt, {
  serverOffset: () => live.clockOffset,
})

/** 예정 시각을 이미 지났는데도 시작되지 않은 상태 — 흔한 정상 상황이다 */
const overdue = computed(() => remainingMs.value !== null && remainingMs.value <= 0)

/**
 * "판매자 서연 · 12명 대기 중". 두 값의 출처가 다르므로(상세 ROOM-03 / `VIEWER_COUNT`)
 * 하나가 비어도 나머지는 남는다 — 상세 조회가 실패해도 대기 인원은 계속 보여야 한다.
 */
const sellerMeta = computed(() => {
  const parts: string[] = []
  if (props.sellerName) parts.push(`판매자 ${props.sellerName}`)
  if (props.waitingCount !== null) parts.push(`${props.waitingCount}명 대기 중`)
  return parts.join(' · ')
})

const pad2 = (n: number) => String(n).padStart(2, '0')

/**
 * "오늘 20:20" / "내일 20:20" / "7월 25일 20:20" — 서버는 시각을 주고 포맷은 FE가 한다.
 *
 * "오늘/내일" 판정의 기준 시각도 카운트다운과 같은 서버 시계(`serverOffset` 보정)를 쓴다.
 * 단말 시계로 판정하면 기기 시각이 하루 어긋난 사용자에게 "시작까지 03:12"와
 * "내일 20:20 시작 예정"이 동시에 뜬다.
 */
const scheduledLabel = computed(() => {
  if (props.scheduledAt === null) return ''
  const date = new Date(props.scheduledAt)
  const hhmm = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`

  // 서버 시각 출처가 하나도 없으면 이 예정 시각 라벨은 '오늘/내일'의 코스한 날짜 판정이라
  // 단말 시계로 폴백한다 — 정밀 카운트다운(useCountdown)과 달리 규칙 5 대상이 아니다.
  const startOfToday = new Date(Date.now() + (live.clockOffset ?? 0))
  startOfToday.setHours(0, 0, 0, 0)
  const dayDiff = Math.floor((date.getTime() - startOfToday.getTime()) / 86_400_000)

  if (dayDiff === 0) return `오늘 ${hhmm}`
  if (dayDiff === 1) return `내일 ${hhmm}`
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${hhmm}`
})
</script>

<template>
  <div
    class="group bg-stage relative aspect-video w-full overflow-hidden rounded-[20px] shadow-2xl lg:aspect-auto lg:h-full"
  >
    <!-- 상품 이미지를 어둡게 깔아 둔다. 아직 스트림은 없다 -->
    <div
      v-if="imageUrl"
      class="absolute inset-0 bg-cover bg-center"
      :style="{ backgroundImage: cssUrl(imageUrl) }"
      aria-hidden="true"
    />
    <div class="from-stage/70 to-stage/90 absolute inset-0 bg-gradient-to-b" aria-hidden="true" />

    <!-- 헤더(좌상단): 뒤로가기 · 대기 뱃지 · 판매자 메타 -->
    <div class="absolute top-4 left-4 z-[4] flex flex-wrap items-center gap-2">
      <button
        type="button"
        class="text-on-dark flex h-[26px] w-[26px] flex-none items-center justify-center rounded-full bg-white/10 backdrop-blur-sm transition-colors hover:bg-white/20 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
        aria-label="대기방 나가기"
        title="대기방 나가기"
        @click="emit('back')"
      >
        <svg
          class="h-4 w-4"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
      <!-- 기능색 2(슬레이트) = 예정된 일. 대기 상태에 레드를 쓰지 않는다 (AGENTS.md 규칙 1) -->
      <span class="bg-slate rounded-full px-2.5 py-1 text-[11px] font-bold text-white">대기</span>
      <span
        v-if="sellerMeta"
        class="text-on-dark rounded-full bg-white/10 px-3 py-[5px] text-xs tabular-nums backdrop-blur-sm"
      >
        {{ sellerMeta }}
      </span>
    </div>

    <!-- 시작까지 칩(상단 중앙). scheduledAt이 없으면 아예 그리지 않는다 -->
    <div
      v-if="scheduledAt !== null"
      class="text-on-dark bg-ink/80 absolute top-4 left-1/2 z-[6] flex -translate-x-1/2 items-center gap-2.5 rounded-full border border-white/10 px-4 py-2 text-[13px] whitespace-nowrap shadow-lg backdrop-blur-md"
    >
      <span class="text-on-dark-dim">시작까지</span>
      <span v-if="overdue" class="text-[13px] font-semibold">판매자 시작 대기 중</span>
      <span v-else class="text-[17px] font-bold tabular-nums">{{ display }}</span>
    </div>

    <!-- 중앙 안내 -->
    <div class="relative z-[2] flex h-full flex-col items-center justify-center px-6 text-center">
      <span
        class="bg-slate/90 inline-flex items-center rounded-full px-[13px] py-[5px] text-[11.5px] font-semibold tracking-[0.02em] text-white"
      >
        방송 시작 전
      </span>

      <!-- overdue면 중앙도 갈린다(S15P11A704-427). 이전에는 예정 시각이 지나도 "오늘 21:00 시작 예정"을
           30px로 계속 띄워, 화면에서 가장 큰 글씨가 이미 지난 시각을 예정이라 말했다 — 정정하는 문구는
           가장 작은 상단 칩에만 있었다. 예정 시각이 지나는 건 흔한 정상 상황이라(ROOM-05: 예정 시각은
           안내용이고 자동 시작 트리거가 아니다) 그 상태를 큰 글씨가 틀리게 말하면 안 된다.

           역할을 나눈다 — **칩은 상태**(`판매자 시작 대기 중`), **중앙은 이유**(`예정 시각이 지났어요`).
           둘이 같은 말을 반복하지 않는다. 지난 시각 자체는 다시 보여주지 않는다: 이미 본 값이고,
           지금 필요한 것은 "왜 아직인가"다. 뒤 문장이 "그래서 무엇을 기다리는가"를 잇는다. -->
      <p v-if="overdue" class="mt-3.5 text-[30px] font-bold tracking-[-0.028em] text-white">
        예정 시각이 지났어요
      </p>
      <p
        v-else-if="scheduledLabel"
        class="mt-3.5 text-[30px] font-bold tracking-[-0.028em] text-white tabular-nums"
      >
        {{ scheduledLabel }} 시작 예정
      </p>
      <p v-else class="mt-3.5 text-[30px] font-bold tracking-[-0.028em] text-white">
        시작 시각 미정
      </p>

      <!-- 디자인 원본 `.wsub`의 문장 그대로다 — overdue에서도 바꾸지 않는다. 이 문장이 "판매자가 시작하면"
           으로 다음 일을 알려 주므로, 중앙의 이유 + 칩의 상태와 겹치지 않고 셋이 한 흐름이 된다. -->
      <p class="text-on-dark-dim mt-2.5 max-w-[38ch] text-[13px] leading-relaxed">
        판매자가 시작하면 자동으로 라이브 화면으로 넘어가요
      </p>

      <!-- aria-live를 뗐다(S15P11A704-422). 문장 전체에 polite가 걸려 있어 인원이 바뀔 때마다
           "먼저 온 N명이 채팅 중이에요"가 통째로 재낭독됐다 — 목 드라이버는 5.2초마다 인원을 올리고
           (mocks/waitingRoom viewerIntervalMs), 실 서버도 VIEWER_COUNT를 주기적으로 브로드캐스트하므로
           대기 내내 같은 문장이 반복된다. 대기 인원은 정보성이라 바뀔 때마다 알릴 값이 아니고,
           지금 벌어지는 일(시작 여부)은 상단 칩과 라이브 전환이 담당한다. -->
      <p
        v-if="waitingCount !== null"
        class="text-on-dark mt-5 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2.5 text-[12.5px] backdrop-blur-md"
      >
        <svg
          class="h-4 w-4 flex-none"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="8" r="3.4" />
          <path d="M5 20c0-3.6 3.1-5.6 7-5.6s7 2 7 5.6" />
        </svg>
        먼저 온 <b class="tabular-nums">{{ waitingCount }}</b
        >명이 채팅 중이에요
      </p>
    </div>

    <!-- 시작 안내 오버레이(AUCTION_STARTED 수신 직후) — 화면이 바뀌는 이유를 먼저 알린다 -->
    <div
      v-if="$slots.overlay"
      class="bg-stage/80 absolute inset-0 z-[8] flex items-center justify-center backdrop-blur-sm"
    >
      <slot name="overlay" />
    </div>
  </div>
</template>
