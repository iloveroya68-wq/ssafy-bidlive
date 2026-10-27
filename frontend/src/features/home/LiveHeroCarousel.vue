<script setup lang="ts">
// ROOM-08 "지금 열려 있는 라이브" 히어로 캐러셀 (S15P11A704-309). 디자인 원본 `main`의 `.heroCar`.
//
// 원본과 다른 점 둘 (설계 문서 §정본 위에 얹는 마감):
//  ① 슬라이드 남은 시간을 `data-left` 초 감소가 아니라 `endsAt` 절대 시각 기준으로 표시한다 (규칙 5)
//  ② 자동 전환이 hover·포커스에서 멈춘다 — 슬라이드 안에 "지금 입장" 버튼이 있어서
//     자동으로 넘어가면 클릭 목표가 도망간다. 원본도 티커에는 같은 이유로 hover 정지를 걸어 뒀다.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { HeroSlide } from './homeFeed'
import { endAtMs } from '@/stores/room'
import { formatPoint } from '@/shared/lib/point'
import { cssUrl } from '@/shared/lib/cssUrl'
import { isDemoRoom } from '@/features/demo/demoRooms'
import DemoRoomNotice from '@/features/demo/DemoRoomNotice.vue'
import HeroTimerChip from './HeroTimerChip.vue'

const props = defineProps<{ slides: HeroSlide[] }>()

const router = useRouter()

/** 시연 더미 슬라이드를 눌렀을 때 뜨는 안내 */
const demoNoticeOpen = ref(false)

/**
 * 슬라이드 배경 — **방의 대표 사진을 쓴다.**
 *
 * 예전에는 방 id를 시드로 한 picsum 랜덤 사진이었다. 상품 사진을 올린 방도 홈 배너에서는
 * 무관한 그림이 떠서, 히어로가 매물과 다른 걸 보여주고 있었다.
 *
 * 맨 위 어두운 그라데이션은 그대로 둔다 — 장식이 아니라 그 위에 얹히는 흰 제목·배지의 대비를
 * 만드는 장치다. 맨 아래 스테이지 색은 사진이 없거나 못 불러왔을 때 깨진 박스 대신 남는 면이다.
 */
function heroBackground(slide: HeroSlide): string {
  const overlay = 'linear-gradient(180deg, rgb(14 14 14 / 0.08) 34%, rgb(14 14 14 / 0.82))'
  const backdrop = 'linear-gradient(var(--color-stage), var(--color-stage))'
  const photo = slide.room.thumbnailUrl === null ? null : cssUrl(slide.room.thumbnailUrl)
  return [overlay, photo, backdrop].filter((layer) => layer !== null).join(', ')
}

const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

const index = ref(0)
const trackStyle = computed(() => ({ transform: `translateX(-${index.value * 100}%)` }))

function goTo(next: number) {
  const count = props.slides.length
  if (count === 0) return
  index.value = (next + count) % count
}

// ── 자동 전환 (3초) ──
// hover·포커스 중에는 멈춘다. reduced-motion이면 아예 시작하지 않는다 — 수동 조작은 그대로 산다.
let autoId: number | undefined

function startAuto() {
  if (reducedMotion || autoId !== undefined || props.slides.length < 2) return
  autoId = window.setInterval(() => goTo(index.value + 1), 3000)
}
function stopAuto() {
  if (autoId === undefined) return
  clearInterval(autoId)
  autoId = undefined
}

// 슬라이드는 부모가 스토어에서 파생하므로 **setup 시점에는 비어 있을 수 있다**
// (첫 방문: `room.rooms`가 `[]`이고 `loadRooms()`는 부모 onMounted에서 돈다).
// 그때 `startAuto()`는 `length < 2`로 그냥 빠져나가므로, 슬라이드가 채워지는 시점을 잡아 다시 건다.
// `immediate`로 최초 1회도 겸한다 — 이미 차 있으면 그 자리에서 시작한다.
watch(() => props.slides.length, startAuto, { immediate: true })

// 입찰 건수는 서버가 내려준 목록 응답을 단일 기준으로 사용한다.
// Bid counts are read from the room list response and are never generated locally.

onBeforeUnmount(stopAuto)

function enter(slide: HeroSlide) {
  // 시연 더미는 입장시키지 않고 안내만 띄운다 (`features/demo/demoRooms.ts` 안전장치 ②).
  // 판정을 여기서 하는 이유: 이 컴포넌트가 라우팅을 직접 쥐고 있어서, 호출부로 올리려면
  // 슬라이드 클릭 계약을 바꿔야 한다 — 시연용 장치 때문에 정상 경로를 흔들지 않는다.
  if (isDemoRoom(slide.room)) {
    demoNoticeOpen.value = true
    return
  }
  void router.push({ name: 'live', params: { id: String(slide.room.auctionId) } })
}
</script>

<template>
  <div
    class="heroCar bg-stage relative overflow-hidden rounded-[20px] shadow-[0_24px_54px_-20px_rgb(30_30_30/0.45)]"
    @mouseenter="stopAuto"
    @mouseleave="startAuto"
    @focusin="stopAuto"
    @focusout="startAuto"
  >
    <div class="heroTrack flex h-full" :style="trackStyle">
      <article
        v-for="slide in slides"
        :key="slide.room.auctionId"
        class="relative flex h-full w-full flex-none cursor-pointer flex-col justify-end overflow-hidden"
        :style="{ backgroundImage: heroBackground(slide) }"
        @click="enter(slide)"
      >
        <div class="absolute top-4 left-4 z-[3] flex gap-2">
          <span
            class="bg-red inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold text-white"
          >
            <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-white" aria-hidden="true" />
            LIVE
          </span>
          <span
            class="bg-ink/70 text-on-dark rounded-full px-2.5 py-1 text-[11px] tabular-nums backdrop-blur-md"
          >
            시청 {{ slide.room.viewerCount }}
          </span>
        </div>

        <HeroTimerChip :ends-at="endAtMs(slide.room)" />

        <div class="relative z-[2] max-w-[74%] px-6 pb-10 text-white">
          <span
            class="bg-red/95 inline-flex items-center gap-1.5 rounded-full px-3 py-[5px] text-[11.5px] font-semibold"
          >
            {{ slide.tag }}
          </span>
          <h3
            class="mt-3 text-[30px] leading-[1.25] font-bold tracking-[-0.02em] [text-wrap:balance]"
          >
            {{ slide.room.title }}
          </h3>
          <p class="mt-2 text-[13px] text-white/80 tabular-nums">
            입찰
            <b class="font-bold text-white"> {{ slide.room.bidCount }}건 </b>&nbsp;· 예측
            {{ slide.room.predictionParticipantCount }}명 · 하한가
            {{ formatPoint(slide.room.reservePrice) }} · 판매자 {{ slide.room.sellerNickname }}
          </p>
        </div>

        <!--
          주 CTA도 카드 전체 클릭과 같은 진입 가드를 탄다.
          더미 판정이 있는 `enter()`가 실제 방 이동까지 담당하므로 기본 링크 이동은 두지 않는다.
        -->
        <button
          type="button"
          class="bg-terra hover:bg-terra-d absolute right-5 bottom-5 z-[3] rounded-[12px] px-5 py-3 text-[14px] font-semibold text-white transition"
          @click.stop="enter(slide)"
        >
          지금 입장 →
        </button>
      </article>
    </div>

    <button
      v-if="slides.length > 1"
      type="button"
      class="carbtn left-2.5 text-white"
      aria-label="이전 라이브"
      @click="goTo(index - 1)"
    >
      ‹
    </button>
    <button
      v-if="slides.length > 1"
      type="button"
      class="carbtn right-2.5 text-white"
      aria-label="다음 라이브"
      @click="goTo(index + 1)"
    >
      ›
    </button>

    <div class="absolute bottom-[18px] left-6 z-[5] flex gap-1.5">
      <button
        v-for="(slide, i) in slides"
        :key="slide.room.auctionId"
        type="button"
        class="h-[3px] rounded-sm transition-all duration-200"
        :class="i === index ? 'w-[34px] bg-white' : 'w-[22px] bg-white/35'"
        :aria-label="`${i + 1}번째 라이브 보기`"
        :aria-current="i === index || undefined"
        @click="goTo(i)"
      />
    </div>
  </div>

  <DemoRoomNotice v-if="demoNoticeOpen" @close="demoNoticeOpen = false" />
</template>

<style scoped>
.heroCar {
  height: 470px;
}
.heroTrack {
  transition: transform 0.52s var(--ease-out-quint);
}

/* 화살표 — 원본은 hover 시에만 노출. 키보드 포커스로도 나와야 도달 가능하다 */
.carbtn {
  position: absolute;
  top: 50%;
  z-index: 5;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: none;
  font-size: 40px;
  line-height: 1;
  cursor: pointer;
  filter: drop-shadow(0 2px 8px rgb(0 0 0 / 0.55));
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%) scale(0.86);
  transition:
    opacity 0.2s,
    transform 0.22s var(--ease-out-quint);
}
.heroCar:hover .carbtn,
.carbtn:focus-visible {
  opacity: 0.85;
  pointer-events: auto;
  transform: translateY(-50%) scale(1);
}
.heroCar:hover .carbtn:hover {
  opacity: 1;
}
.carbtn:active {
  transform: translateY(-50%) scale(0.9);
}

@media (prefers-reduced-motion: reduce) {
  .heroTrack {
    transition: none;
  }
}
</style>
