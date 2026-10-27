<script setup lang="ts">
// ROOM-08 마감 임박 카드 (S15P11A704-309). 디자인 원본 `main` 화면 `.endcard`.
//
// 원본은 `data-t0`에 **남은 초**를 박고 setInterval로 1씩 깎는다. 그러면 탭이 자거나 JS가 밀릴 때
// 실제 마감 시각에서 영영 어긋난다 — 규칙 5가 금지하는 클라이언트 자체 계산이다.
// 여기서는 서버가 준 **절대 마감 시각**(`endAt`)을 기준으로 표시만 한다.
//
// 오프셋은 **목록 응답의 `serverTime`**에서 온다(ROOM-02 명세 2026-07-29 개정, `room.serverOffset`).
// 예전 주석은 "홈에는 오프셋 소스가 아예 없다"였고 그래서 기본값 0(단말 시계)을 썼다 —
// 소켓 TIME_SYNC가 없는 건 지금도 같지만, 목록 API가 서버 시각을 주기 시작해 그 전제가 깨졌다.
//
// 카드가 여러 장이어도 시계는 모듈 스코프에 하나만 돈다.
import { computed } from 'vue'
import { endAtMs, useRoomStore, type AuctionRoomSummary } from '@/stores/room'
import { formatPoint } from '@/shared/lib/point'
import { useCountdown } from '@/shared/lib/useCountdown'
import { cssUrl } from '@/shared/lib/cssUrl'

const props = defineProps<{ room: AuctionRoomSummary }>()
const emit = defineEmits<{ select: [room: AuctionRoomSummary] }>()

// 목록 카드(`RoomCard`)와 같은 3분 기준으로 마감 임박을 강조한다
const roomStore = useRoomStore()
const { display, remainingMs } = useCountdown(() => endAtMs(props.room), {
  serverOffset: () => roomStore.serverOffset,
  urgentMs: 180_000,
})

/**
 * 다 흐르면 `00:00`이 아니라 "마감"이다 (정본 `v ? fmtM(v) : '마감'`).
 * 실 API에서는 서버가 `ENDED`로 바꿔 목록에서 빠지지만, 그 사이 한 틱이라도
 * `00:00`이 보이면 "아직 0초 남았다"로 읽힌다.
 */
const remainDisplay = computed(() => (remainingMs.value === 0 ? '마감' : display.value))

/**
 * 비크리 비공개(규칙 6) — 공개 가능한 건 입찰 건수·예측 참여 인원·하한가뿐이다.
 * 최고가·타인 입찰가는 진행 중 화면에 절대 두지 않는다.
 *
 * 목록이 주는 인원은 **예측 베팅 참여자**뿐이라 라벨도 그렇게 적는다 —
 * "참여"로 두면 입찰 참여자로 읽힌다 (`RoomCard`와 같은 처리, S15P11A704-385).
 */
const meta = computed(
  () =>
    `입찰 ${props.room.bidCount}건 · 예측 ${props.room.predictionParticipantCount} · 하한가 ${formatPoint(props.room.reservePrice)}`,
)

/**
 * 썸네일 — **방의 대표 사진을 쓴다.**
 *
 * 예전에는 방 id를 시드로 한 picsum 랜덤 사진이었다(314의 「로컬 에셋 전환」 대기 상태). 그래서
 * 상품 사진을 올린 방도 홈에서는 무관한 그림이 떴다 — 카드가 매물과 다른 걸 보여주는 셈이다.
 *
 * 사진이 없으면 토큰 배경만 남는다. 깨진 박스가 아니라 의도된 면으로 보이도록 밑에 깔아 둔 것을
 * 그대로 쓴다(`RoomCard`가 카테고리 그라데이션으로 떨어지는 것과 같은 처리).
 *
 * URL은 판매자가 등록한 값이라 신뢰 경계 밖이다 — `cssUrl`이 따옴표로 감싸 CSS 주입을 막는다.
 */
const TOKEN_BACKDROP = 'linear-gradient(var(--color-line), var(--color-line))'
const thumbStyle = computed(() => ({
  backgroundImage:
    props.room.thumbnailUrl === null
      ? TOKEN_BACKDROP
      : `${cssUrl(props.room.thumbnailUrl)}, ${TOKEN_BACKDROP}`,
}))
</script>

<template>
  <button
    type="button"
    class="border-line bg-surface hover:border-faint flex w-full flex-1 items-center gap-3 rounded-[14px] border p-[10px_12px] text-left transition duration-200 hover:-translate-y-0.5 hover:shadow-[0_12px_26px_-14px_rgb(40_40_40/0.3)]"
    @click="emit('select', room)"
  >
    <span class="ethumb" :style="thumbStyle" aria-hidden="true" />
    <span class="min-w-0 flex-1">
      <span class="text-ink line-clamp-2 block text-[13px] leading-[1.3] font-semibold">
        {{ room.title }}
      </span>
      <span class="text-dim mt-[3px] block text-[11.5px] tabular-nums">{{ meta }}</span>
    </span>
    <span class="text-red text-[15px] font-bold tracking-[-0.01em] tabular-nums">
      {{ remainDisplay }}
    </span>
  </button>
</template>

<style scoped>
.ethumb {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 10px;
  background-position: center;
  background-size: cover;
}
</style>
