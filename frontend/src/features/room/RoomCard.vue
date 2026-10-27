<script setup lang="ts">
// ROOM-02 방 목록 카드 — 상태별(진행중/대기/종료)로 배지·우상단 카운트·하단 메타만 갈리고 골격은 공유
//
// 「경매방 목록 조회」 응답(`AuctionRoomSummary`)을 그대로 읽는다 (S15P11A704-390).
// 이전에는 목 `Room`을 직접 읽어서 `categoryCode`·`soldPrice`·`endedAt`이 타입에만 있고
// 카드에는 닿지 않았다 — 318이 "카드가 못 그린다"고 올렸던 바로 그 세 값이다.
import { computed } from 'vue'
import { roomStateOf, endAtMs, useRoomStore, type AuctionRoomSummary } from '@/stores/room'
import { useCategoryStore } from '@/stores/category'
import { useCountdown } from '@/shared/lib/useCountdown'
import { formatRelativeDayIso, formatScheduledAt } from '@/shared/lib/relativeTime'
import { cssUrl } from '@/shared/lib/cssUrl'

const props = defineProps<{ room: AuctionRoomSummary }>()

const categoryStore = useCategoryStore()

const emit = defineEmits<{ select: [room: AuctionRoomSummary] }>()

const fmtPoint = (n: number) => `${n.toLocaleString('ko-KR')}P`

const state = computed(() => roomStateOf(props.room))

// BID-05: 서버가 준 **예정** 마감 시각(endAt) 기준 표시 — 목록 카드도 3분 이하면 강조.
// `endedAt`(실제 종료 시각)과 다른 값이다. 명세가 둘을 구분한다.
//
// 오프셋은 목록 응답의 `serverTime`에서 온다(명세 2026-07-29 개정). 이전에는 넘기지 않아
// 기본값 0, 즉 **단말 시계 기준**이었다 — 시계가 틀어진 기기에서 남은 시간이 그만큼 틀렸다.
const roomStore = useRoomStore()
const { display: remainDisplay, urgent } = useCountdown(() => endAtMs(props.room), {
  serverOffset: () => roomStore.serverOffset,
  urgentMs: 180_000,
})
const isUrgent = computed(() => state.value === 'live' && urgent.value)

/** 종료 카드의 "어제 낙찰" — 서버는 절대 시각만 주고 표기는 FE가 한다 */
const endedLabel = computed(() => formatRelativeDayIso(props.room.endedAt))
/** 대기 카드의 시작 예정 시각. 예약 시각은 안내용이고 실제 시작은 판매자 수동이다 (ROOM-05) */
const scheduledLabel = computed(() => formatScheduledAt(props.room.scheduledAt))

// 썸네일 폴백: 이미지가 없을 때 쓰는 토큰 기반 그라데이션 (카테고리별 색감).
// 서버가 주는 표준 코드로 분기한다 — 표시명은 로케일·문구가 바뀔 수 있어 키로 쓰지 않는다.
//
// **`thumbnailUrl`이 있으면 사진을 그린다** — 그전에는 이 값을 받아 두고도 쓰지 않아서, 판매자가
// 올린 상품 사진이 카드에 한 번도 나오지 않았다. 와이어프레임의 `roomCard`는 처음부터
// `linear-gradient(...) , url(사진)` 조합이라 어두운 오버레이 위에 배지·제목이 얹힌다.
const THUMB_BY_CATEGORY: Record<string, string> = {
  ELECTRONICS: 'from-slate-tint to-slate/40',
  HOBBY: 'from-terra-tint to-terra/30',
  FASHION: 'from-line to-faint/50',
  LIVING: 'from-cream to-line',
  MEDIA: 'from-slate-tint to-line',
  SPORTS: 'from-terra-tint to-line',
}
const thumbClass = computed(
  () => THUMB_BY_CATEGORY[props.room.categoryCode] ?? 'from-line to-faint/40',
)

/**
 * 사진이 있으면 배경으로 깐다. 위에 얹는 어두운 그라데이션은 와이어프레임 값 그대로다 —
 * 사진이 밝으면 흰 배지·제목이 묻히므로 대비를 만드는 장치이지 장식이 아니다.
 *
 * URL은 판매자가 등록한 값이라 신뢰 경계 밖이다. `cssUrl`이 따옴표로 감싸 CSS 주입을 막는다.
 */
const thumbStyle = computed(() => {
  if (props.room.thumbnailUrl === null) return undefined
  return {
    backgroundImage: `linear-gradient(180deg, rgba(22,22,22,0.06), rgba(22,22,22,0.44)), ${cssUrl(
      props.room.thumbnailUrl,
    )}`,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
  }
})
// 표시명은 「경매방 카테고리 목록 조회」 응답에서 온다 (397에서 하드코딩 표를 걷어냈다).
// 목록을 아직 못 받았으면 코드가 그대로 보인다 — 빈칸보다 낫고 무엇이 빠졌는지 드러난다
const categoryLabel = computed(() => categoryStore.nameByCode(props.room.categoryCode))
</script>

<template>
  <article
    class="group border-line hover:border-faint hover:shadow-stage/20 bg-surface cursor-pointer overflow-hidden rounded-2xl border transition duration-300 hover:-translate-y-1 hover:shadow-xl motion-reduce:transition-none motion-reduce:hover:translate-y-0"
    @click="emit('select', room)"
  >
    <!-- 썸네일 — 사진이 있으면 사진, 없으면 카테고리 그라데이션 -->
    <div
      class="relative h-38"
      :class="[
        room.thumbnailUrl === null && ['bg-gradient-to-br', thumbClass],
        state !== 'live' && 'brightness-95',
      ]"
      :style="thumbStyle"
    >
      <!-- 상태 배지 -->
      <span
        v-if="state === 'live'"
        class="bg-red text-on-dark absolute top-3 left-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold"
      >
        <span class="bg-on-dark h-1.5 w-1.5 animate-pulse rounded-full" aria-hidden="true" />
        LIVE
      </span>
      <span
        v-else-if="state === 'waiting'"
        class="bg-slate text-on-dark absolute top-3 left-3 rounded-full px-2.5 py-1 text-[11px] font-bold"
      >
        대기
      </span>
      <span
        v-else
        class="bg-faint text-on-dark absolute top-3 left-3 rounded-full px-2.5 py-1 text-[11px] font-bold"
      >
        종료
      </span>

      <!-- 우상단 카운트: live=시청자, waiting=대기 인원 -->
      <span
        v-if="state === 'live'"
        class="bg-stage/60 text-on-dark absolute top-3 right-3 inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] tabular-nums backdrop-blur-sm"
      >
        <svg
          class="h-3.5 w-3.5"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7Z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
        {{ room.viewerCount }}
      </span>
      <span
        v-else-if="state === 'waiting'"
        class="bg-stage/60 text-on-dark absolute top-3 right-3 inline-flex items-center gap-1 rounded-full px-2 py-1 text-[11px] tabular-nums backdrop-blur-sm"
      >
        <svg
          class="h-3.5 w-3.5"
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
        {{ room.viewerCount }}
      </span>

      <!-- 남은 시간 요약 (live만) — 목록 정렬 기준이므로 카드에 반드시 보인다 -->
      <span
        v-if="state === 'live'"
        class="text-on-dark absolute right-3 bottom-3 rounded-full px-2.5 py-1 text-[11.5px] font-semibold tabular-nums backdrop-blur-sm"
        :class="isUrgent ? 'bg-red/90' : 'bg-stage/60'"
      >
        {{ remainDisplay }}
      </span>
    </div>

    <div class="px-4 pt-3.5 pb-4">
      <div class="text-faint mb-1 text-[10.5px]">{{ categoryLabel }}</div>
      <div class="text-ink line-clamp-2 h-10 text-sm leading-snug font-semibold">
        {{ room.title }}
      </div>

      <!-- 하단 메타 — 비크리 비공개: 최고가·타인 입찰가 없음. 하한가·입찰 건수·집계 인원만 -->
      <div class="mt-2.5 flex items-baseline justify-between gap-2">
        <template v-if="state === 'live'">
          <div class="text-ink text-xl font-bold tracking-tight tabular-nums">
            입찰 {{ room.bidCount }}건
          </div>
          <!-- 와이어프레임은 "참여 N"인데 **예측**으로 적는다. 목록이 주는 인원은 예측 베팅
               참여자뿐이고(입찰 참여자 수는 응답에 없다), "참여"로 두면 어느 쪽인지 오해된다.
               명세 개요도 카드에 오는 값을 "예측 베팅 참여 인원"으로 못박았다. 문구 확정은 S15P11A704-385. -->
          <div class="text-dim text-xs tabular-nums">
            예측 {{ room.predictionParticipantCount }} · 하한가 {{ fmtPoint(room.reservePrice) }}
          </div>
        </template>
        <template v-else-if="state === 'waiting'">
          <div class="text-slate text-[15px] font-bold tracking-tight">
            {{ scheduledLabel }}
          </div>
          <div class="text-dim text-xs tabular-nums">하한가 {{ fmtPoint(room.reservePrice) }}</div>
        </template>
        <template v-else>
          <!-- 낙찰가는 종료 후에만 공개 (비크리 비공개 원칙). 유찰이면 soldPrice가 null이다 -->
          <div
            v-if="room.soldPrice !== null"
            class="text-ink text-xl font-bold tracking-tight tabular-nums"
          >
            {{ fmtPoint(room.soldPrice) }}
          </div>
          <div v-else class="text-faint text-[15px] font-bold tracking-tight">유찰</div>
          <div v-if="endedLabel !== ''" class="text-dim text-xs">
            {{ endedLabel }} {{ room.soldPrice !== null ? '낙찰' : '종료' }}
          </div>
        </template>
      </div>
    </div>
  </article>
</template>
