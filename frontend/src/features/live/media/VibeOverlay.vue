<script setup lang="ts">
// BID-04 Vibe 오버레이 (S15P11A704-357) — 입찰이 들어오면 스테이지 위에서 **경매봉이 내려친다.**
//
// 원래는 작은 레드 칩이 4.2초에 걸쳐 26px 떠오르는 연출이었는데, "입찰이 들어온 걸 몰랐다"는 말이
// 반복해서 나왔다. 영상을 보는 사람의 시선은 화면 중앙에 있고, 구석에서 26px 움직이는 12px 글자는
// 주변시에 안 잡힌다. 그래서 연출을 세 겹으로 나눈다 — 겹마다 맡는 일이 다르다.
//
//   ① 알림: 스테이지 흔들림 + 가장자리 섬광 — **위치와 무관**하다. 어디를 보고 있든 걸린다.
//   ② 정체: 경매봉이 내려치고 「땅!」 — 랜덤 슬롯. 무엇이 일어났는지 알려준다.
//   ③ 강도: 몰릴수록 세지고 연속 배지가 붙는다.
//
// **랜덤 위치가 허용되는 것은 오직 ①이 있기 때문이다.** ①이 없으면 "입찰이 들어왔다"는 사실 자체를
// 망치를 찾아다녀야 알게 되고, 그건 고정 위치보다 나쁘다. ①을 지우면 ②도 같이 무너진다.
//
// 타이밍 규칙(합치기·과열·숨은 탭)은 전부 useBidPulse에 있다. 이 파일은 **펄스를 그리는 일만** 한다.
//
// **세 겹 모두 금색이다(`--color-gold`).** 원래 레드였는데 「땅!」이 경고로 읽혔다 —
// 규칙 1의 레드는 마감 임박에도 쓰는 색이라, 같은 스테이지에서 남은 시간과 같은 값으로 겹친다.
// 입찰이 들어왔다는 좋은 소식인데 화면이 "뭔가 잘못됐다"고 말하고 있었다.
//
// ⚠️ 그 대가로 **이 연출에는 규칙 1의 기능색이 하나도 안 남았다.** 「레드 = 라이브·입찰」 대응이
// 여기서만 끊긴다 — 색으로 입찰을 알아보던 단서가 없어졌으니, 남은 단서는 형태(경매봉)와 문구뿐이다.
// 되돌린다면 ①(가장자리 섬광)부터다: 글자가 없어 "경고 문구"로 오해될 일이 없는 유일한 겹이라
// 기능색을 지기에 가장 적합하다.
//
// 겹마다 색을 섞지 않는 이유는 따로 있다 — 한 번의 입찰이 세 겹으로 나뉘어 보이는 연출이라
// 색이 갈리면 **한 사건이 아니라 여러 사건으로 읽힌다.** 글자만 무채색인 건 대비 때문이다.
//
// 폰트도 토큰만(규칙 1). 클릭을 막지 않도록 pointer-events-none, 스크린리더 스팸 방지로
// aria-hidden(집계·상태는 #action이 announce).
import { onUnmounted, ref, watch } from 'vue'
import { useLiveStore } from '@/stores/live'
import { useBidPulse, type BidPulse } from './useBidPulse'

const live = useLiveStore()
const { pulse, hot, streak } = useBidPulse(() => live.vibeFeed)

/** .gavel-strike / .gavel-pop 길이와 맞춘다 */
const LIFE_MS = 900
/** .gavel-shake 길이. **쿨다운(700ms)보다 짧아야 한다** — 아래 shaking 주석 참조. */
const SHAKE_MS = 380

/**
 * 경매봉이 설 수 있는 네 자리. 원래는 UI를 피해 바깥으로 밀어 둔 값이었는데,
 * **영상을 가려도 좋으니 화면 안쪽에서 크게 보이는 편이 낫다**는 결정으로 전부 중앙 쪽으로 당겼다.
 * 즉 지금 이 좌표들이 피하는 것은 UI가 아니라 **스테이지 밖으로 잘리는 것**뿐이다.
 *
 * 자리를 넷으로 유지하는 이유는 크기와 무관하다 — 같은 자리에 두 번 연속 뜨면 시선이 그 자리를
 * 학습해서 셋째 번부터 다시 안 보인다. 커져도 이건 그대로다.
 *
 * ⚠️ **z는 올리지 않는다**(루트는 z-[5]). 타이머·prodbar·컨트롤이 항상 망치 위에 그려져야
 * 겹칠 때 가려지는 쪽이 언제나 장식이 된다. 올리면 마감 임박 30초 동안 남은 시간(규칙 5, 서버 권위)이
 * 900ms씩 망치에 덮인다 — 가려도 되는 건 영상이지 타이머가 아니다.
 */
const SLOTS = [
  'top-[32%] left-1/2 -translate-x-1/2 -translate-y-1/2',
  'top-1/2 left-[34%] -translate-x-1/2 -translate-y-1/2',
  'top-1/2 right-[34%] translate-x-1/2 -translate-y-1/2',
  'top-[68%] left-1/2 -translate-x-1/2 -translate-y-1/2',
] as const

/**
 * 모션 최소화 선호 — 망치도 흔들림도 없이 섬광과 글자만 남긴다. **정보는 하나도 안 줄어든다**(몇 건인지,
 * 연속인지 모두 글자로 있다). 사라지는 것은 움직임뿐이다.
 * jsdom에는 matchMedia가 없어서 존재 확인을 함께 한다 — 없으면 그냥 평소 연출로 둔다.
 */
const reducedMotion =
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const active = ref<(BidPulse & { slot: string; slotIndex: number }) | null>(null)
const shaking = ref(false)
let slotIndex = -1
const timers = new Set<ReturnType<typeof setTimeout>>()

function after(ms: number, run: () => void) {
  const timer = setTimeout(() => {
    timers.delete(timer)
    run()
  }, ms)
  timers.add(timer)
}

watch(pulse, (next) => {
  if (next === null) return

  // 같은 자리 연속 금지 — 두 번 연속이면 "여기 뜨는구나"로 학습돼 셋째 번부터 다시 안 보인다.
  // 나머지 셋 중에서만 고르므로 구조적으로 반복이 불가능하다(같은 값을 뽑고 다시 뽑는 루프가 아니다).
  slotIndex = (slotIndex + 1 + Math.floor(Math.random() * (SLOTS.length - 1))) % SLOTS.length
  active.value = { ...next, slot: SLOTS[slotIndex], slotIndex }
  after(LIFE_MS, () => {
    // 다음 펄스가 이미 들어왔으면 그쪽 것을 지우면 안 된다(쿨다운 700ms < 수명 900ms이라 겹친다).
    if (active.value?.id === next.id) active.value = null
  })

  // 흔들림을 수명(900ms)이 아니라 따로 끊는 이유: 클래스가 붙어 있는 동안에는 애니메이션이 다시
  // 시작되지 않는다. 수명에 묶으면 700ms 뒤 두 번째 입찰에서 클래스가 계속 붙어 있어
  // **두 번째부터 안 흔들린다.** SHAKE_MS < 쿨다운(700ms)이라 다음 펄스 전에 반드시 떨어진다 —
  // 이 부등호가 깨지면 조용히 그 증상으로 돌아간다. (망치·글자는 :key로 다시 마운트돼 무관하다.)
  shaking.value = true
  after(SHAKE_MS, () => (shaking.value = false))
})

onUnmounted(() => {
  // 예약된 정리 타이머까지 치운다 — 언마운트 뒤 fire되어도 무해하지만 누수 없이 깔끔하게.
  // (합치기·과열 타이머는 useBidPulse가 스코프 종료에서 스스로 정리한다.)
  timers.forEach(clearTimeout)
  timers.clear()
  active.value = null
  shaking.value = false
})
</script>

<template>
  <div
    class="pointer-events-none absolute inset-0 z-[5] overflow-hidden"
    :class="shaking && !reducedMotion && (hot ? 'gavel-shake-hot' : 'gavel-shake')"
    aria-hidden="true"
  >
    <!-- ⚠️ 주석을 이 루트 **밖**으로 올리지 말 것 — 루트가 둘(주석 + div)이 되어 프래그먼트가 되고,
         그러면 부모가 준 속성이 어디에도 안 붙는다(테스트에서도 루트 속성이 사라진다).

         흔드는 대상은 **이 래퍼**다. LiveStage의 <video>에 transform을 걸면 브라우저가 매 프레임
         비디오 레이어를 다시 합성해 프레임을 떨군다 — 영상이 끊기는 대가로 얻는 흔들림은 손해다.
         섬광이 스테이지 전체를 덮으므로 래퍼만 흔들어도 "화면이 반응했다"로 읽힌다. -->

    <!-- ① 알림: 가장자리 섬광. 망치와 같은 금색이다 — 한 번의 입찰이 세 겹으로 나뉘어 보이는 연출이라
         겹마다 색이 다르면 **한 사건이 아니라 여러 사건으로 읽힌다.**
         흰빛은 밝은 영상 위에서 씻겨나가 아무것도 안 보이므로 여기서만은 무채색을 못 쓴다.
         과열은 색을 바꾸지 않고 **번지는 범위와 밝기**로만 가른다. -->
    <div
      v-if="active"
      :key="`edge-${active.id}`"
      class="gavel-edge text-gold absolute inset-0"
      :class="active.hot && 'gavel-edge-hot'"
    />

    <!-- ②③ 정체와 강도 -->
    <div
      v-if="active"
      :key="active.id"
      class="absolute flex flex-col items-center gap-1"
      :class="active.slot"
      :data-slot="active.slotIndex"
    >
      <!-- 망치 회전축은 **자루 끝(손)**이다. 자루 가운데를 축으로 잡으면 팔이 아니라 프로펠러처럼 돌아
           내려친다는 느낌이 사라진다. 들어올림 35° → 타격 -8°(머리가 실제로 블록에 닿는 각) → 감쇠 바운스. -->
      <svg
        v-if="!reducedMotion"
        class="gavel-strike text-gold h-24 w-24 drop-shadow-lg sm:h-48 sm:w-48"
        viewBox="0 0 100 100"
        fill="currentColor"
      >
        <rect x="8" y="76" width="48" height="9" rx="3" opacity="0.85" />
        <rect x="2" y="86" width="60" height="7" rx="3" opacity="0.6" />
        <g transform="translate(72,56)">
          <g class="gavel-mallet">
            <rect x="-32" y="-4" width="34" height="8" rx="4" />
            <rect x="-58" y="-16" width="28" height="32" rx="8" />
            <circle cx="0" cy="0" r="5" opacity="0.85" />
          </g>
        </g>
      </svg>

      <div class="gavel-pop flex items-center gap-1.5">
        <!-- 글자만 무채색이다. 금색은 **도구(망치)에만** 남긴다 — 놋쇠 낙찰봉이라 재질로 읽히는데,
             글자까지 금색이면 어두운 스테이지에서 장식으로 보이고 대비도 떨어진다. -->
        <span class="text-on-dark text-[28px] font-black drop-shadow-lg sm:text-[40px]">땅!</span>
        <!-- 합쳐진 건수. 1건이면 숫자가 오히려 노이즈라 안 붙인다. -->
        <span
          v-if="active.count > 1"
          class="bg-gold text-ink rounded-full px-2 py-0.5 text-[13px] font-bold tabular-nums shadow-lg"
          >+{{ active.count }}</span
        >
      </div>

      <span
        v-if="active.hot"
        class="gavel-pop bg-gold/90 text-ink rounded-full px-2.5 py-1 text-[11px] font-bold whitespace-nowrap shadow-lg"
        >🔥 입찰 <span class="tabular-nums">{{ streak }}</span
        >연속!</span
      >
    </div>
  </div>
</template>

<style scoped>
/* 들어올릴 때는 감속(팔이 준비하는 느낌), 내려칠 때는 가속.
   등속으로 내려치면 무게가 없는 물건처럼 보여서 "내려쳤다"가 아니라 "지나갔다"로 읽힌다. */
.gavel-mallet {
  transform-origin: 0 0; /* 부모 <g>가 축(자루 끝)으로 이미 옮겨 놨다 */
  animation: gavel-swing 900ms both;
}
@keyframes gavel-swing {
  0% {
    transform: rotate(0deg);
    animation-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
  }
  22% {
    transform: rotate(35deg);
    animation-timing-function: cubic-bezier(0.7, 0, 0.9, 0.35);
  }
  42% {
    transform: rotate(-8deg);
  }
  58% {
    transform: rotate(6deg);
  }
  72% {
    transform: rotate(-3deg);
  }
  86% {
    transform: rotate(1.5deg);
  }
  100% {
    transform: rotate(0deg);
  }
}

/* 퇴장. 망치에는 원래 페이드가 없었다 — 회전이 끝나는 순간(900ms) v-if가 노드를 걷어내서 **뚝 사라졌다.**
   64px일 때는 눈에 안 띄었지만 화면 안쪽에서 두 배 크기로 키운 뒤로는 한 프레임에 화면 절반이
   없어지는 꼴이 된다. 그래서 스스로 지워지고 나간다.
   꼬리를 마지막 잔바운스(스윙의 72% 이후)와 겹치는 것이 핵심이다 — 다 멈추고 나서 사라지면
   "멈췄다가 없어졌다"로 끊기고, 겹쳐야 여운을 남기며 빠지는 걸로 읽힌다.
   회전은 안쪽 <g>(.gavel-mallet)가 맡고 여기는 <svg>라, 같은 요소에서 transform이 부딪히지 않는다. */
.gavel-strike {
  animation: gavel-vanish 900ms both;
}
@keyframes gavel-vanish {
  0%,
  62% {
    opacity: 1;
    transform: translateY(0);
  }
  100% {
    opacity: 0;
    transform: translateY(10px);
  }
}

/* 「땅!」은 타격 순간(=수명의 42%, 약 378ms)에 맞춰 튀어나온다. 먼저 뜨면 소리가 앞서는 꼴이 된다.
   퇴장은 62%(≈687ms)부터 — 원래 78%였는데 그러면 123ms 만에 툭 꺼져서 망치만 스르륵 빠지고
   글자는 끊기는 짝짝이가 된다. 둘이 같이 사라져야 한 덩어리로 읽힌다. */
.gavel-pop {
  animation: gavel-pop 560ms 340ms both;
}
@keyframes gavel-pop {
  0% {
    transform: scale(0.6);
    opacity: 0;
  }
  18% {
    transform: scale(1.12);
    opacity: 1;
  }
  32% {
    transform: scale(1);
  }
  62% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

.gavel-edge {
  box-shadow: inset 0 0 70px 14px currentColor;
  animation: gavel-edge 900ms ease-out both;
}
/* 과열은 색을 바꾸지 않는다 — 같은 금색을 더 넓게·더 밝게·더 오래 태운다.
   색을 바꾸면 "다른 종류의 일이 일어났다"로 읽혀서 강도 차이가 전달되지 않는다. */
.gavel-edge-hot {
  box-shadow: inset 0 0 110px 24px currentColor;
  animation-name: gavel-edge-hot;
  animation-duration: 1100ms;
}
@keyframes gavel-edge {
  0% {
    opacity: 0;
  }
  12% {
    opacity: 0.8;
  }
  100% {
    opacity: 0;
  }
}
@keyframes gavel-edge-hot {
  0% {
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

/* 흔들림은 translate만 쓴다 — 회전·확대는 둥근 모서리 밖을 드러내거나 영상을 잘라 먹는다. */
.gavel-shake {
  animation: gavel-shake 380ms ease-out;
}
.gavel-shake-hot {
  animation: gavel-shake-hot 380ms ease-out;
}
@keyframes gavel-shake {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  15% {
    transform: translate3d(-3px, 2px, 0);
  }
  32% {
    transform: translate3d(3px, -2px, 0);
  }
  52% {
    transform: translate3d(-2px, -1px, 0);
  }
  74% {
    transform: translate3d(2px, 1px, 0);
  }
}
@keyframes gavel-shake-hot {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  15% {
    transform: translate3d(-7px, 4px, 0);
  }
  32% {
    transform: translate3d(7px, -5px, 0);
  }
  52% {
    transform: translate3d(-5px, -3px, 0);
  }
  74% {
    transform: translate3d(4px, 3px, 0);
  }
}

/* 모션 최소화 선호 — 움직임만 걷어낸다. 섬광은 opacity 페이드라 그대로 두고, 글자는 튀지 않고 뜬다.
   (망치 SVG와 흔들림 클래스는 스크립트 쪽 reducedMotion이 이미 뺀다. 흔들림을 여기서 한 번 더 끄는
   이유는 그 값이 setup 시점에 한 번만 읽히기 때문이다 — 화면을 열어 둔 채 OS 설정을 바꾼 사람에게는
   이 미디어쿼리만 남는다.) */
@media (prefers-reduced-motion: reduce) {
  .gavel-shake,
  .gavel-shake-hot {
    animation: none;
  }
  .gavel-pop {
    animation: gavel-fade 900ms ease both;
  }
  @keyframes gavel-fade {
    0% {
      opacity: 0;
    }
    10%,
    80% {
      opacity: 1;
    }
    100% {
      opacity: 0;
    }
  }
}
</style>
