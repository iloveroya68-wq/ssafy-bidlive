<script setup lang="ts">
// BID-10 집계 중 → 낙찰 공개 오버레이 (S15P11A704-66). 디자인 원본의 `#reveal`.
//
// 흐름은 서버 이벤트가 그대로 끌고 간다 (AGENTS.md 규칙 5 — 클라이언트가 마감을 자체 판단하지 않는다).
//   AUCTION_ENDED  → `settling` : 입력 차단 + 봉투 여는 중
//   AUCTION_SETTLED→ `sold`     : 낙찰자 공개 + 낙찰가 카운트업 + 컨페티
//   AUCTION_NO_SALE→ `nosale`   : 유찰 카드 (컨페티 없음 — 축하할 일이 아니다)
//   AUCTION_CANCELED→`canceled` : 운영 강제 종료 (DEAL-09)
//
// 규칙 6: 이 오버레이가 공개하는 타인 금액은 **종료 후 확정된 낙찰가·최고가**뿐이다.
// 진행 중에는 이 컴포넌트가 뜨지 않는다.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { fireConfetti } from './confetti'
import { useCountUp } from '@/shared/lib/useCountUp'
import { REVEAL_SETTLING_DURATION_MS, type RevealPhase } from './revealState'

const props = withDefaults(
  defineProps<{
    /** `hidden`이면 부모가 아예 렌더하지 않는다 */
    phase: Exclude<RevealPhase, 'hidden'>
    /** 집계 중 세어 올릴 입찰 건수 */
    bidCount?: number
    /** 서버가 마스킹한 낙찰자 라벨 */
    winnerLabel?: string
    /** 2등가로 확정된 최종 낙찰가 */
    finalPrice?: number
    /** 최고 입찰가 — 낙찰가와 다르다는 것이 비크리의 핵심이라 함께 보여준다 */
    highestBidAmount?: number | null
    /** 종료 사유. `nosale`이면 `NoSaleReason`, `canceled`면 자유 문자열이다 */
    reasonText?: string
    /** 하한가. 낙찰이 하한가 이상임을 / 유찰이 하한가 미달임을 설명하는 데 쓴다 */
    reservePrice?: number | null
    /** 홀드된 내 입찰가. 종료 시 전액 해제된다는 개인 안내에 쓴다 */
    myHoldAmount?: number | null
  }>(),
  {
    bidCount: 0,
    winnerLabel: '',
    finalPrice: 0,
    highestBidAmount: null,
    reasonText: '',
    reservePrice: null,
    myHoldAmount: null,
  },
)

const emit = defineEmits<{
  /** 창 닫기 — 오버레이만 걷고 화면에는 남는다 */
  close: []
  /** 결과/유찰 화면으로 이동 */
  detail: []
  /**
   * 방을 떠난다. **방송이 이미 사라진 종료에서 쓴다** — 운영 강제 종료와 판매자 이탈
   * (S15P11A704-688에서 후자가 추가됐다. 그 전에는 강제 종료 전용이었다).
   * 둘 다 유찰처럼 "창 닫고 이 방을 계속 본다"가 성립하지 않는다(디자인 원본 `.forced-over`).
   */
  leave: []
}>()

const canvas = ref<HTMLCanvasElement | null>(null)
const primaryButton = ref<HTMLButtonElement | null>(null)
let stopConfetti: (() => void) | null = null
/** 컨페티 발사 지연 타이머. 발사 전에 언마운트되면 취소해야 detached canvas에 rAF 루프가 남지 않는다 */
let confettiTimer: number | null = null

function stopConfettiEffects() {
  if (confettiTimer !== null) window.clearTimeout(confettiTimer)
  confettiTimer = null
  stopConfetti?.()
  stopConfetti = null
}

const tallyCount = useCountUp(() => (props.phase === 'settling' ? props.bidCount : 0), {
  durationMs: REVEAL_SETTLING_DURATION_MS,
})
const revealedPrice = useCountUp(() => (props.phase === 'sold' ? props.finalPrice : 0), {
  durationMs: 1100,
  delayMs: 350,
})

const detailLabel = computed(() =>
  props.phase === 'sold'
    ? '낙찰 결과 자세히 →'
    : props.phase === 'canceled'
      ? '정산 내역 보기 →'
      : '유찰 안내 자세히 →',
)

/**
 * `AUCTION_CANCELED.reason`은 명세상 자유 문자열이라 코드가 그대로 올 수 있다.
 * 화면에 원문 enum을 노출하지 않도록 아는 값만 우리말로 바꾸고, 모르면 일반 문구로 덮는다.
 */
const REASON_LABEL: Record<string, string> = {
  FORCED: '운영 검토',
  FORCED_END: '운영 검토',
  HOST_LEFT: '판매자 이탈',
  NO_BID: '입찰 없음',
}

// 모르는 값에 "운영 검토"를 붙이면 서버가 말한 적 없는 신고·제재를 함의하게 된다 — 중립 문구로 둔다
const reasonLabel = computed(() => REASON_LABEL[props.reasonText] ?? '운영 종료')

/**
 * 유찰 헤드라인·부연. `AUCTION_NO_SALE.reason`은 `NoSaleReason` 3값이고,
 * `HOST_LEFT`는 **입찰이 여러 건 있는 상태에서도** 발생한다 —
 * 사유와 무관하게 "유효 입찰 0건"으로 단정하면 그 참여자 전원에게 거짓말이 된다.
 */
const NOSALE_COPY: Record<string, { head: string; metric: string; value: string }> = {
  NO_BID: { head: '낙찰 없이 종료됐어요', metric: '유효 입찰', value: '0건' },
  HOST_LEFT: { head: '판매자 이탈로 종료됐어요', metric: '종료 사유', value: '판매자 이탈' },
  FORCED: { head: '운영팀이 이 방송을 종료했어요', metric: '종료 사유', value: '운영 검토' },
}

/**
 * 방송이 이미 사라진 종료인가 (S15P11A704-688).
 *
 * 운영 강제 종료와 판매자 이탈이 그렇다 — 둘 다 송출이 끊긴 뒤라 오버레이를 닫으면 검은 스테이지만
 * 남는다. `NO_BID`는 다르다: 마감까지 간 정상 종료라 방에 남아 채팅·결과를 마저 볼 수 있다.
 */
const broadcastGone = computed(() => props.phase === 'canceled' || props.reasonText === 'HOST_LEFT')

const nosaleCopy = computed(
  () =>
    NOSALE_COPY[props.reasonText] ?? {
      head: '낙찰 없이 종료됐어요',
      metric: '종료 사유',
      value: reasonLabel.value,
    },
)

/** "하한가 30,000P 이상 입찰이 없었어요" — 왜 0건인지는 사유가 `NO_BID`일 때만 이 문장이 답한다 */
const nosaleDetail = computed(() => {
  if (props.reasonText !== 'NO_BID' || props.reservePrice === null) return ''
  return `하한가 ${formatPoint(props.reservePrice)} 이상 입찰이 없었어요`
})

/** 홀드 해제 안내. 개인 금액이 있으면 그것이 가장 궁금한 정보다 */
const holdNote = computed(() =>
  props.myHoldAmount !== null && props.myHoldAmount > 0
    ? `내 입찰 홀드 ${formatPoint(props.myHoldAmount)} · 전액 해제 완료`
    : '홀드된 내 입찰이 없어 해제할 금액이 없어요',
)

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.phase !== 'settling') emit('close')
}

watch(
  () => props.phase,
  (phase) => {
    stopConfettiEffects()
    if (phase === 'sold' && canvas.value !== null) {
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      if (!reduced) {
        const target = canvas.value
        confettiTimer = window.setTimeout(() => {
          confettiTimer = null
          stopConfetti = fireConfetti(target)
        }, 500)
      }
    }
    if (phase !== 'settling') {
      void Promise.resolve().then(() => primaryButton.value?.focus())
    }
  },
  { immediate: true },
)

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  stopConfettiEffects()
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center px-5"
    role="dialog"
    aria-modal="true"
    aria-label="경매 종료"
  >
    <div class="bg-ink/70 absolute inset-0 backdrop-blur-md" aria-hidden="true" />
    <canvas
      ref="canvas"
      class="pointer-events-none fixed inset-0 z-[2]"
      :class="phase !== 'sold' && 'hidden'"
      aria-hidden="true"
    />

    <!-- ① 집계 중 (BID-10) — 서버가 2등가를 판정하는 실제 대기 구간 -->
    <div
      v-if="phase === 'settling'"
      class="text-on-dark relative z-[3] text-center"
      aria-live="polite"
    >
      <p class="text-on-dark-dim text-[13px] tracking-[0.2em]">＼ 마감 ／</p>
      <div class="my-[22px] flex justify-center gap-[9px]" aria-hidden="true">
        <span v-for="i in 3" :key="i" class="envelope bg-on-dark/30 h-[17px] w-[13px] rounded-sm" />
      </div>
      <p class="text-[26px] font-bold tracking-tight">입찰 봉투를 여는 중</p>
      <p class="text-on-dark-dim mt-2.5 text-[13px] tabular-nums">
        <b class="text-on-dark">{{ formatAmount(tallyCount) }}</b
        >건의 입찰을 확인하고 있어요
      </p>
    </div>

    <!-- ② 낙찰 공개 — 전원 동시 -->
    <div v-else-if="phase === 'sold'" class="reveal-card text-on-dark relative z-[3] text-center">
      <p class="text-terra-tint text-[13px] tracking-[0.2em]">＼ 전원 동시 공개 ／</p>
      <p class="mt-3.5 text-[42px] leading-tight font-bold tracking-tight tabular-nums">
        {{ winnerLabel }} <span class="text-[16px] font-normal opacity-70">님</span>
      </p>
      <div
        class="bg-surface text-ink mx-auto mt-[22px] w-[320px] max-w-full rounded-[22px] p-6 shadow-2xl"
      >
        <p class="text-dim text-[13px]">최종 낙찰가 (2등 입찰가)</p>
        <p class="text-terra text-[48px] leading-none font-bold tracking-tight tabular-nums">
          {{ formatPoint(revealedPrice) }}
        </p>
        <p v-if="highestBidAmount !== null" class="text-faint mt-1.5 text-[12px] tabular-nums">
          최고 입찰 {{ formatPoint(highestBidAmount)
          }}<template v-if="reservePrice !== null">
            · 하한가 {{ formatPoint(reservePrice) }} 이상 확정</template
          >
        </p>
      </div>
      <p class="text-on-dark-dim mt-3 text-[12.5px]">
        미낙찰자 홀드 전액 즉시 복구 · 낙찰자는 2등 입찰가만 지불
      </p>
      <div class="mt-5 flex justify-center gap-2.5">
        <button
          type="button"
          class="text-on-dark rounded-[10px] border border-white/40 px-[22px] py-3 text-[14px] font-medium transition active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100"
          @click="emit('close')"
        >
          창 닫기
        </button>
        <button
          ref="primaryButton"
          type="button"
          class="bg-surface text-terra rounded-[10px] px-[26px] py-3 text-[14px] font-bold transition active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100"
          @click="emit('detail')"
        >
          {{ detailLabel }}
        </button>
      </div>
    </div>

    <!-- ③ 유찰 · 강제 종료 — 낙찰자가 없으므로 가격 카드 대신 해제 안내 -->
    <div v-else class="reveal-card text-on-dark relative z-[3] text-center">
      <p class="text-on-dark-dim text-[13px] tracking-[0.2em]">
        {{ phase === 'canceled' ? '＼ 운영 종료 ／' : '＼ 마감 ／' }}
      </p>
      <p class="mt-3.5 text-[34px] leading-tight font-bold tracking-tight">
        {{ phase === 'canceled' ? '운영팀이 이 방송을 종료했어요' : nosaleCopy.head }}
      </p>
      <p v-if="phase === 'canceled'" class="text-on-dark-dim mt-2.5 text-[13px] leading-relaxed">
        신고가 누적되어 부적절한 방송으로 확인됐어요.<br />낙찰 없이 끝나므로 차감은 발생하지
        않아요.
      </p>
      <div
        class="mx-auto mt-[22px] w-[320px] max-w-full rounded-[22px] bg-white/10 p-6 backdrop-blur-sm"
      >
        <p class="text-on-dark-dim text-[13px]">
          {{ phase === 'canceled' ? '종료 사유' : nosaleCopy.metric }}
        </p>
        <p class="text-on-dark mt-1 text-[24px] leading-snug font-bold tabular-nums">
          {{ phase === 'canceled' ? reasonLabel : nosaleCopy.value }}
        </p>
        <p v-if="phase !== 'canceled' && nosaleDetail" class="text-on-dark-dim mt-1.5 text-[12px]">
          {{ nosaleDetail }}
        </p>
      </div>
      <p class="text-on-dark-dim mt-3 text-[12.5px] tabular-nums">
        {{ holdNote }}<template v-if="phase !== 'canceled'"> · 예측 베팅 전액 환불</template>
      </p>
      <div class="mt-5 flex justify-center gap-2.5">
        <!-- 방송 자체가 사라진 종료는 창만 닫고 남아 있을 화면이 없다 — 검은 스테이지에 덩그러니 남는다.
             강제 종료(`canceled`)가 그래서 「나가기」를 쓰고 있었고, **판매자 이탈도 같은 상태다**
             (S15P11A704-688). `NO_BID`만 「창 닫기」가 남는다: 그건 마감까지 간 정상 종료라
             방에 남아 채팅·결과를 마저 볼 수 있다. -->
        <button
          v-if="broadcastGone"
          type="button"
          class="text-on-dark rounded-[10px] border border-white/40 px-[22px] py-3 text-[14px] font-medium transition active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100"
          @click="emit('leave')"
        >
          나가기
        </button>
        <button
          v-else
          type="button"
          class="text-on-dark rounded-[10px] border border-white/40 px-[22px] py-3 text-[14px] font-medium transition active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100"
          @click="emit('close')"
        >
          창 닫기
        </button>
        <button
          ref="primaryButton"
          type="button"
          class="bg-surface text-terra rounded-[10px] px-[26px] py-3 text-[14px] font-bold transition active:scale-[0.97] motion-reduce:transition-none motion-reduce:active:scale-100"
          @click="emit('detail')"
        >
          {{ detailLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes reveal-pop {
  from {
    transform: translateY(40px) scale(0.9);
    opacity: 0;
  }
  to {
    transform: none;
    opacity: 1;
  }
}

.reveal-card {
  animation: reveal-pop 0.52s cubic-bezier(0.18, 1.2, 0.4, 1) forwards;
}

@keyframes reveal-envelope {
  0%,
  100% {
    transform: scaleY(1);
    opacity: 0.35;
  }
  50% {
    transform: scaleY(1.5);
    opacity: 0.9;
  }
}

.envelope {
  animation: reveal-envelope 1.05s cubic-bezier(0.77, 0, 0.175, 1) infinite;
}

.envelope:nth-child(2) {
  animation-delay: 0.14s;
}

.envelope:nth-child(3) {
  animation-delay: 0.28s;
}

@media (prefers-reduced-motion: reduce) {
  .reveal-card,
  .envelope {
    animation: none;
    opacity: 1;
  }
}
</style>
