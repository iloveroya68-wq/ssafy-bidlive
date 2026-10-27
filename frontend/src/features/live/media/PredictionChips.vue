<script setup lang="ts">
// PRED-01 예측 참여 연출 — `PREDICTION_ACCEPTED` 한 건에 베팅 칩 다섯 장이 스테이지로 던져진다.
//
// **입찰 연출(VibeOverlay)과 일부러 모든 축을 갈랐다.** 둘은 같은 스테이지에서 동시에 뜰 수 있어서,
// 겹쳤을 때 "무슨 일이 일어났는지"가 구분되지 않으면 둘 다 무의미해진다:
//
//   축      입찰(VibeOverlay)          예측(여기)
//   색      금색 한 가지                카지노 칩 다색 — 레드 계열은 terra와 다른 값을 쓴다
//   형태    망치 1개, 96~192px          칩 5장, 22~38px
//   방향    위에서 **내려친다**          아래에서 **솟구쳤다 떨어진다**
//   길이    900ms                       1.15~1.43초
//
// ⚠️ 망치가 금색이 되면서 **색 축은 더 이상 완전히 갈리지 않는다** — 칩 7티어 중 하나가 같은
// `--color-gold`다. 구분은 나머지 세 축(형태·방향·길이)이 진다. 칩에서 금색을 빼는 것도 방법이지만,
// 액면가 1000이 금색인 게 카지노 칩의 관례라 남겼다. 헷갈린다는 말이 나오면 **여기서 빼는 쪽**이
// 맞다 — 망치는 놋쇠라 금색일 이유가 있고 칩은 없다.
//
// 개수는 **항상 5장 고정**이다. 스테이크에 비례시키면 칩을 세는 것만으로 남의 베팅 규모가 읽혀
// 규칙 7(베팅 분포 라이브 중 노출 금지)에 걸린다. 고정이라 내 예측·남의 예측을 구분할 필요도 없고,
// 제출 REST 응답과 브로드캐스트가 겹쳐 두 번 터지는 문제도 애초에 생기지 않는다.
//
// z는 VibeOverlay와 같은 이유로 안 올린다 — 타이머·prodbar·컨트롤이 항상 칩 위에 그려져야
// 겹칠 때 가려지는 쪽이 언제나 장식이 된다(규칙 5, 남은 시간은 서버 권위).
//
// 색·폰트는 토큰만(규칙 1). pointer-events-none으로 클릭을 막지 않고, aria-hidden으로
// 스크린리더 스팸을 막는다(참여자 수는 #action이 announce).
import { onUnmounted, ref, watch } from 'vue'
import { useLiveStore } from '@/stores/live'

const live = useLiveStore()

/** 한 건에 던지는 장수. 정보를 싣지 않는 것이 이 값의 목적이라 어떤 입력에도 반응하지 않는다. */
const CHIPS = 5
/** 가장 늦게 출발한 칩(지연 최대)이 가장 긴 비행을 마치고도 남는 여유. */
const BURST_MS = 1800

/**
 * 칩 액면가 색. 실제 카지노 순서(화이트 1 · 레드 5 · 그린 25 · 블루 100 · 퍼플 500 · 옐로 1000 · 블랙)지만
 * **뽑기는 무작위**다 — 스테이크를 안 보므로 색에 실을 정보가 없다. 순서는 팔레트를 읽기 쉬우라고 남긴다.
 *
 * `edge`는 가장자리 쐐기 색이다. 이게 **칩을 칩으로 읽히게 하는 유일한 단서**라 빼면 그냥 동그라미가 된다.
 * 화이트·그린·블랙은 신규색을 만들지 않고 기존 토큰(on-dark·success·ink)을 그대로 쓴다.
 */
const TIERS = [
  { fill: 'var(--color-on-dark)', edge: 'var(--color-chip-red)' },
  { fill: 'var(--color-chip-red)', edge: 'var(--color-surface)' },
  { fill: 'var(--color-success)', edge: 'var(--color-surface)' },
  { fill: 'var(--color-chip-blue)', edge: 'var(--color-surface)' },
  { fill: 'var(--color-chip-purple)', edge: 'var(--color-surface)' },
  { fill: 'var(--color-gold)', edge: 'var(--color-ink)' },
  { fill: 'var(--color-ink)', edge: 'var(--color-on-dark)' },
] as const

/** 모션 최소화 선호 — 던지지 않는다. 문구 칩만 남아 "예측이 들어왔다"는 사실은 그대로 전달된다. */
const reducedMotion =
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

interface Chip {
  id: number
  fill: string
  edge: string
  size: number
  /** CSS 커스텀 프로퍼티 묶음. 축마다 값이 달라 style 바인딩으로 넘긴다. */
  vars: Record<string, string>
}

const chips = ref<Chip[]>([])
const message = ref<string | null>(null)
let seq = 0
let clear: ReturnType<typeof setTimeout> | null = null

const rand = (lo: number, hi: number) => lo + Math.random() * (hi - lo)

/**
 * 한 건분(5장)을 만든다.
 *
 * 좌표를 **% 대신 CSS 변수로** 넘기고 세로만 px인 이유: `translateY(%)`의 퍼센트는 컨테이너가 아니라
 * **요소 자기 높이** 기준으로 풀린다. 30px짜리 칩에 78%를 주면 23px 내려오고 끝난다 —
 * 실제로 그렇게 짜서 "깔짝 떨어지고 만다"는 소리를 들었다. 그래서 높이는 컨테이너를 재서 px로 준다.
 */
function makeBurst(stageH: number): Chip[] {
  return Array.from({ length: CHIPS }, () => {
    const tier = TIERS[Math.floor(Math.random() * TIERS.length)]
    // 가로는 폭 전체에 흩는다. 좁게 잡으면 한 점에서 뿜는 분수처럼 보인다.
    const left = rand(10, 90)
    const dir = Math.random() < 0.5 ? -1 : 1
    // 흐르는 폭은 그쪽에 남은 자리만큼만 — 아니면 가장자리에서 던진 칩이 화면 밖으로 나간다.
    const room = dir < 0 ? left : 100 - left
    return {
      id: ++seq,
      fill: tier.fill,
      edge: tier.edge,
      size: Math.round(rand(22, 38)),
      vars: {
        left: `${left}%`,
        '--apex': `${Math.round(stageH * rand(0.72, 0.96))}px`,
        // 착지는 출발점보다 위 — 하단 1/3에 내려앉아야 화면 안에서 멈춘 게 보인다.
        '--land': `${Math.round(-stageH * rand(0.16, 0.32))}px`,
        '--bounce': `${Math.round(stageH * rand(0.05, 0.1))}px`,
        '--drift': `${dir * Math.min(rand(4, 20), room * 0.55)}%`,
        '--spin': `${dir * rand(300, 820)}deg`,
        '--dur': `${Math.round(rand(1150, 1430))}ms`,
        '--delay': `${Math.round(rand(0, 55))}ms`,
      },
    }
  })
}

const field = ref<HTMLElement | null>(null)

// vibeFeed와 같은 이유로 길이가 아니라 **참조**를 본다 — 상한(VIBE_KEEP)에 걸리면 길이가 고정돼
// 그 뒤로 조용히 죽는다. $reset이 만드는 빈 배열은 방을 나갈 때라 무시한다.
watch(
  () => live.predictionFeed,
  (next, prev) => {
    if (next === prev || next.length === 0) return
    message.value = next[next.length - 1] ?? null
    // `??`가 아니라 `||`인 이유: 레이아웃 전이면 clientHeight가 **0**이고 0은 nullish가 아니라
    // ??를 통과한다. 그러면 apex·land가 전부 0px이 돼 칩이 제자리에서 돌기만 한다.
    if (!reducedMotion) chips.value = makeBurst(field.value?.clientHeight || 240)
    if (clear !== null) clearTimeout(clear)
    clear = setTimeout(() => {
      chips.value = []
      message.value = null
      clear = null
    }, BURST_MS)
  },
)

onUnmounted(() => {
  // 언마운트 뒤에 타이머가 깨어나면 사라진 화면 상태를 건드린다. 예약을 남기지 않는다.
  if (clear !== null) clearTimeout(clear)
  clear = null
})
</script>

<template>
  <div
    ref="field"
    class="pointer-events-none absolute inset-0 z-[5] overflow-hidden"
    aria-hidden="true"
  >
    <!-- 포물선은 축을 나눠야 나온다. 가로는 등속, 세로만 중력을 받는다 —
         한 요소에 한 이징으로 밀어넣으면 어떤 값을 넣어도 포물선이 아니라 대각선이 된다.
         .toss(가로+소멸) > .arc(세로) > svg(회전), 각자 한 축씩만 맡는다. -->
    <div
      v-for="chip in chips"
      :key="chip.id"
      class="toss absolute -bottom-[14%]"
      :style="chip.vars"
    >
      <span class="arc block">
        <svg
          :width="chip.size"
          :height="chip.size"
          viewBox="0 0 100 100"
          class="block drop-shadow-md"
        >
          <circle cx="50" cy="50" r="48.5" :fill="chip.fill" />
          <!-- 가장자리 쐐기 6개 -->
          <rect
            v-for="i in 6"
            :key="i"
            x="46"
            y="1.5"
            width="8"
            height="13"
            rx="2.5"
            :fill="chip.edge"
            :transform="`rotate(${(i - 1) * 60} 50 50)`"
          />
          <circle
            cx="50"
            cy="50"
            r="35"
            fill="none"
            :stroke="chip.edge"
            stroke-opacity=".32"
            stroke-width="3"
          />
          <circle cx="50" cy="50" r="22" fill="#000" fill-opacity=".1" />
        </svg>
      </span>
    </div>

    <!-- 문구는 칩과 달리 모션 최소화에서도 남는다. 칩만 있으면 무슨 일인지 모른다. -->
    <div
      v-if="message"
      class="vibe-pill bg-slate absolute bottom-[18px] left-[18px] rounded-full px-[13px] py-1.5 text-[12px] font-semibold text-white"
    >
      {{ message }}
    </div>
  </div>
</template>

<style scoped>
.toss {
  will-change: transform, opacity;
  animation: chip-drift var(--dur) linear var(--delay) forwards;
}
.arc {
  will-change: transform;
  animation: chip-arc var(--dur) linear var(--delay) forwards;
}
.toss svg {
  animation: chip-spin var(--dur) cubic-bezier(0.1, 0.6, 0.4, 1) var(--delay) forwards;
}

@keyframes chip-drift {
  0% {
    transform: translateX(0);
    opacity: 0;
  }
  5% {
    opacity: 1;
  }
  88% {
    opacity: 1;
  }
  100% {
    transform: translateX(var(--drift));
    opacity: 0;
  }
}

/* 솟구침(감속) → 정점 → 낙하(가속) → 착지 튕김. **구간마다 이징이 다른 게 핵심이다** —
   하나로 통으로 돌리면 무게가 없는 종잇조각처럼 보인다. */
@keyframes chip-arc {
  0% {
    transform: translateY(0);
    animation-timing-function: cubic-bezier(0.1, 0.62, 0.35, 1);
  }
  38% {
    transform: translateY(calc(var(--apex) * -1));
    animation-timing-function: cubic-bezier(0.55, 0, 0.85, 0.55);
  }
  76% {
    transform: translateY(var(--land));
    animation-timing-function: cubic-bezier(0.15, 0.6, 0.4, 1);
  }
  86% {
    transform: translateY(calc(var(--land) - var(--bounce)));
    animation-timing-function: cubic-bezier(0.5, 0, 0.85, 0.7);
  }
  100% {
    transform: translateY(var(--land));
  }
}

@keyframes chip-spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(var(--spin));
  }
}

.vibe-pill {
  animation: pill 1800ms ease forwards;
}
@keyframes pill {
  0% {
    transform: translateY(10px);
    opacity: 0;
  }
  12% {
    transform: translateY(0);
    opacity: 1;
  }
  72% {
    opacity: 1;
  }
  100% {
    transform: translateY(-22px);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .vibe-pill {
    animation: none;
  }
}
</style>
