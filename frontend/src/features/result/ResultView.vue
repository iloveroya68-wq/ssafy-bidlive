<script setup lang="ts">
// DEAL-06·DEAL-08 낙찰 결과 화면 (S15P11A704-67 · 68). 디자인 원본 `data-screen="result"`.
//
// 이 화면이 지는 책임은 넷이다.
//   ① `viewerRole` 4분기 렌더 — 역할은 **서버가 판정하는 사실**이라 쿼리로 지정할 수 없다(docs/02).
//   ② 낙찰자 정산 카드 — 홀드(내 입찰가) → 2등가 차감 → 차액 즉시 복구
//   ③ 전체 입찰 원장 — 전원 마스킹, 본인 행만 "나", 2등가 행 강조 (AGENTS.md 규칙 6)
//   ④ PRED-02 밴드 결과 — 종료 후 1회 조회 + PRED-03 신호 수신 시 1회 재조회 + 수동 「다시 확인」
//      (규칙 7, 신 설계). 신호를 받으려면 이 화면도 메인 토픽에 붙어 있어야 한다 — 아래 onMounted.
//
// 유찰·강제 종료는 역할 분기 자체가 없고 정산 카드 구성도 달라 별도 라우트다(docs/02) —
// 결과가 `SOLD`가 아니면 `/rooms/:id/nosale`로 넘긴다.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatAmount, formatPoint } from '@/shared/lib/point'
import { isMockApi } from '@/shared/api/mock'
import { useRoomStore } from '@/stores/room'
import { useLiveStore } from '@/stores/live'
import { MY_BID_LABEL } from '@/shared/api/bids'
import { isSoldResult, type ResultViewerRole, type SoldAuctionResult } from '@/shared/api/results'
import type {
  PredictionBand,
  PredictionBandResult,
  PredictionRewardMultiplier,
} from '@/shared/api/predictions'
import MyPredictionCard from './MyPredictionCard.vue'
import PredictionBandChart from './PredictionBandChart.vue'
import ResultBidLedger from './ResultBidLedger.vue'
import ResultSettlementCard from './ResultSettlementCard.vue'
import { useCountUp } from '@/shared/lib/useCountUp'
import { isResultNotReady, loadAuctionResult, loadPredictionResults } from './resultSource'

const route = useRoute()
const router = useRouter()
const room = useRoomStore()
const live = useLiveStore()

const auctionId = computed(() => Number(route.params.id) || 0)

const result = ref<SoldAuctionResult | null>(null)
const loading = ref(true)
/** 결과 확정 전에는 조회 자체가 막힌다 (명세 「경매 결과 조회」 §규칙) */
const notReady = ref(false)
const errorText = ref('')

const bands = ref<PredictionBandResult[]>([])
const bandsLoading = ref(false)
const bandsError = ref('')
/** 조회가 도는 중에 들어온 재조회 요청. 반응형일 필요가 없어 ref로 두지 않는다 */
let bandsPending = false
/**
 * 결과 응답의 `myPredictionSettlement`에서 뽑은 표시용 값.
 * 밴드·배수·지급액은 **서버 정산 결과**라 카드가 다시 계산하지 않고 그대로 넘긴다.
 */
const myPrediction = ref<{
  predictedPrice: number
  stakeAmount: number
  errorBand: PredictionBand | null
  multiplier: PredictionRewardMultiplier | null
  payoutAmount: number | null
} | null>(null)

// ── 역할 분기 ──

const viewerRole = computed<ResultViewerRole>(() => result.value?.viewerRole ?? 'SPECTATOR')
const isWinner = computed(() => viewerRole.value === 'WINNING_BIDDER')
const isLosingBidder = computed(() => viewerRole.value === 'LOSING_BIDDER')
const isPredictor = computed(() => viewerRole.value === 'PREDICTOR')

const ROLE_BADGE: Record<ResultViewerRole, { text: string; className: string }> = {
  SELLER: { text: '판매 완료', className: 'bg-success' },
  WINNING_BIDDER: { text: '낙찰 완료', className: 'bg-success' },
  LOSING_BIDDER: { text: '입찰 마감', className: 'bg-slate' },
  PREDICTOR: { text: '예측 결과 공개', className: 'bg-slate' },
  SPECTATOR: { text: '방송 종료', className: 'bg-faint' },
}

/**
 * 값 목록은 §규칙에서 5값으로 확정됐지만, 유니온 밖 문자열이 오면 인덱싱이 `undefined`가 되어
 * 템플릿의 `badge.className`에서 화면 전체가 죽는다 — 모르는 값은 관전자로 떨어뜨린다.
 */
const badge = computed(() => ROLE_BADGE[viewerRole.value] ?? ROLE_BADGE.SPECTATOR)

/** 낙찰자 아바타 이니셜. 라벨은 이미 서버가 마스킹한 값이라 첫 글자만 써도 새로 드러나는 게 없다 */
const winnerInitial = computed(() => result.value?.winnerLabel.trim().charAt(0) ?? '')

/** LIVE-06 스냅샷. 판매자가 안 올린 경매도 정상이라 없으면 플레이스홀더로 남는다 */
const snapshotUrl = computed(() => result.value?.snapshotUrl ?? null)

/** 단독 입찰이면 낙찰가가 2등가가 아니라 하한가로 결정된다 — 문구가 갈린다 */
const isSecondPrice = computed(() => result.value?.priceDecisionType === 'SECOND_PRICE')

const finalPriceLabel = computed(() => {
  const basis = isSecondPrice.value ? '2등 입찰가' : '하한가'
  return isWinner.value ? `내가 지불한 금액 (${basis})` : `최종 낙찰가 (${basis} 지불)`
})

// ── 금액 ──

const finalPrice = computed(() => result.value?.finalPrice ?? 0)
const displayedPrice = useCountUp(() => finalPrice.value, { durationMs: 1000, delayMs: 200 })

/** 원장 1위 = 최고 입찰가. 낙찰가(2등가)와 다르다는 것이 비크리의 요점이다 */
const highestBidAmount = computed(() => {
  const amounts = (result.value?.bids ?? []).map((bid) => bid.amount)
  return amounts.length > 0 ? Math.max(...amounts) : null
})

/**
 * 홀드된 내 입찰가. 개정된 응답의 `myBidSettlement.submittedAmount`가 1차 출처이고,
 * 마스킹 원장의 `"나"` 행은 폴백이다(개정 전에는 원장이 유일한 출처였다).
 */
const myBidAmount = computed(
  () =>
    result.value?.myBidSettlement?.submittedAmount ??
    result.value?.bids.find((bid) => bid.bidderLabel === MY_BID_LABEL)?.amount ??
    null,
)

const myRank = computed(() => {
  const sorted = [...(result.value?.bids ?? [])].sort(
    (a, b) => b.amount - a.amount || Date.parse(a.submittedAt) - Date.parse(b.submittedAt),
  )
  const index = sorted.findIndex((bid) => bid.bidderLabel === MY_BID_LABEL)
  return index >= 0 ? { rank: index + 1, total: sorted.length } : null
})

// `ResultSettlementCard`의 `variant`는 컴포넌트 내부 표현 계약이라 서버의 `viewerRole`과 별개다 —
// 카드는 API 역할 값을 알 필요가 없으므로 `WINNING_BIDDER`로 따라 바꾸지 않는다.
const settlement = computed(() => {
  const my = result.value?.myBidSettlement
  if (my === null || my === undefined) return null

  if (isWinner.value) {
    const paid = my.paymentAmount ?? finalPrice.value
    const held = myBidAmount.value ?? paid + (my.releasedAmount ?? 0)
    return {
      variant: 'WINNER' as const,
      heldAmount: held,
      paidAmount: paid,
      restoredAmount: my.releasedAmount ?? Math.max(0, held - paid),
    }
  }
  if (isLosingBidder.value) {
    const released = my.releasedAmount ?? myBidAmount.value ?? 0
    return {
      variant: 'LOSING_BIDDER' as const,
      heldAmount: myBidAmount.value ?? released,
      paidAmount: 0,
      restoredAmount: released,
    }
  }
  return null
})

// 목록이 아니라 상세에서 (딥링크 진입이 기본 동선이다 — S15P11A704-396)
const roomTitle = computed(() => room.detailOf(auctionId.value)?.title ?? '경매 결과')

// ── 로드 ──

/**
 * PRED-02 밴드 조회. 재조회 계기는 셋이다 — 진입, PRED-03 신호, 「다시 확인」 버튼.
 *
 * 조회 중에 들어온 요청은 **버리지 않고 큐에 세운다**: 신호가 도착한 시점의 응답이 이미 나가 있으면
 * 그 응답은 정산 전 상태일 수 있어, 겹침을 이유로 버리면 화면이 "집계 중"에 굳는다.
 * 큐는 불리언 하나라 연속 신호 N개가 재조회 1회로 합쳐지고, `finally`에서 소진 후 지우므로
 * 조회가 조회를 무한히 부르지 않는다.
 */
async function loadBands() {
  if (bandsLoading.value) {
    bandsPending = true
    return
  }
  bandsLoading.value = true
  bandsError.value = ''
  try {
    // PRED-02는 종료 + 예측 정산 완료 후에만 열린다. 진입 1회 + PRED-03 신호 1회만 부른다(폴링 금지).
    const response = await loadPredictionResults(auctionId.value)
    bands.value = response.bands
  } catch (error) {
    bandsError.value = isResultNotReady(error)
      ? '예측 정산이 끝나면 밴드별 결과가 공개돼요.'
      : '예측 결과를 불러오지 못했어요.'
  } finally {
    bandsLoading.value = false
    if (bandsPending) {
      bandsPending = false
      void loadBands()
    }
  }
}

async function load() {
  loading.value = true
  notReady.value = false
  errorText.value = ''
  try {
    const response = await loadAuctionResult(auctionId.value)
    if (!isSoldResult(response)) {
      // 유찰·강제 종료는 별도 화면이다 — 뒤로가기로 다시 여기 오지 않게 replace
      await router.replace({ name: 'nosale', params: { id: String(auctionId.value) } })
      return
    }
    result.value = response
    void loadBands()
    // 개정 전에는 개인 예측을 받을 경로가 없어 목에서만 채웠는데(구 `loadMyPrediction`),
    // 이제 결과 응답의 `myPredictionSettlement`가 준다 — 실 API에서도 카드가 그려진다.
    // 타입은 `| null`이지만 이 API는 **필드 생략**을 실제로 쓴다(§규칙의 `canRelist` 항목).
    // `undefined`를 안 막으면 여기서 TypeError가 나고, try 안이라 catch가 삼켜서
    // 예측 카드 하나가 아니라 결과 화면 전체가 오류 문구로 대체된다.
    const mine = response.myPredictionSettlement
    myPrediction.value =
      mine === null || mine === undefined || mine.predictedPrice === undefined
        ? null
        : {
            predictedPrice: mine.predictedPrice,
            stakeAmount: mine.stakeAmount,
            errorBand: mine.errorBand ?? null,
            multiplier: mine.multiplier ?? null,
            payoutAmount: mine.payoutAmount ?? null,
          }
  } catch (error) {
    if (isResultNotReady(error)) notReady.value = true
    else errorText.value = '결과를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void room.loadRoomDetail(auctionId.value)
  void load()
  // PRED-03 신호를 받으려면 이 화면도 메인 토픽(`/topic/auctions/{id}`)에 붙어 있어야 한다
  // (S15P11A704-546). 라이브·대기방이 언마운트하며 자기 연결을 끊으므로, **결과 화면에 머무는 동안**
  // 끝나는 정산은 여기서 연 연결로만 들어온다.
  //
  // 결과 전용 경량 옵션(메인 토픽만·WS-03 재생 생략)을 새로 만들지 않고 `live.connect`를 그대로 쓴다 —
  // 딸려 오는 채팅 구독은 **수신만 하고 이 화면이 읽지 않아서** 무해하고("종료된 방이라 안 흐른다"가
  // 아니다), 딸려 오는 WS-03 스냅샷 조회도 새 실패가 아니다. ⚠️ **컨트롤러는 실재한다** —
  // `RealtimeAuctionStateController`(`GET /auctions/{id}/realtime-state`)가 붙어 있고,
  // 674의 마감 캐치업이 바로 그 스냅샷을 근거로 동작한다. 여기서 실패하더라도 그건 종료된 방이라
  // Redis 상태 Hash를 못 읽는 경우이고, 라이브가 이미 매번 삼키는 실패인 데다 이 화면은 스냅샷 값을
  // 하나도 읽지 않는다. 구독 인가도 `/topic/auctions/{id}`는 상태와 무관하게 열려 있다
  // (BE StompSubscriptionAuthorizationService).
  //
  // ⚠️ 대신 **시청자 집계에는 잡힌다.** BE `ViewerSessionStompEventListener`는 경매 상태를 보지 않고
  // auctionId로 해석되는 SUBSCRIBE를 전부 시청 세션으로 기록한 뒤 VIEWER_COUNT를 다시 쏜다 —
  // 결과 화면 체류자가 **종료된 방의 시청자로 집계된다.** 이 화면은 viewerCount를 그리지 않아 렌더에는
  // 영향이 없고, 메인 토픽 하나만 구독해도 같은 경로로 세므로 경량 옵션으로도 못 피한다(BE 게이트 문제).
  //
  // 목 모드는 붙지 않는다: 실 WS가 없어 `connect`가 목 라이브 피드(타이머)를 돌리는데, 그 피드는
  // PREDICTION_SETTLED를 보내지 않고 결과 화면이 읽지도 않는 집계만 흔든다. 목 결과 화면은 애초에
  // `resultSource`의 fixture 데모다.
  if (!isMockApi()) void live.connect(auctionId.value)
})

onUnmounted(() => {
  // 다음 화면이 이미 자기 연결을 세웠으면 **그 연결을 끊지 않는다** — 세대 가드가 스토어 안에 있다
  // (S15P11A704-329). 라이브처럼 지연 `$reset()`을 걸지 않는 이유: 이 화면이 스토어에 남기는 값
  // (viewerCount·roomId·connectionStatus처럼 연결이 채운 것)을 그때까지 읽는 화면이 없고, 다음 방
  // 입장의 `joinRoom`이 어차피 통째로 지운다 — 지연 정리를 걸어 경합을 하나 더 만들 이유가 없다.
  if (!isMockApi()) void live.disconnect()
})

/**
 * PRED-03 `PREDICTION_SETTLED` 소비 (S15P11A704-543).
 *
 * 명세 처리 규칙: "클라이언트는 이벤트를 수신하면 `GET /auctions/{id}/prediction-results`를 호출한다."
 * 마운트 시 조회가 409(정산 전)로 떨어진 뒤에 정산이 끝나는 경우가 있어, 신호가 오면 **한 번 더** 부른다.
 * 폴링·타이머는 쓰지 않는다(규칙 5) — 재조회 계기는 서버 이벤트뿐이다.
 * `settlementStatus` 값은 보지 않는다: 실서버가 `data: {}`를 보내 필드가 없고, `FAILED`여도 다시 물어야
 * 화면이 "집계 중"에 굳지 않는다. 재조회가 또 409면 `loadBands`가 기존 안내 문구로 되돌린다.
 *
 * 신호가 실제로 도달하는 경로는 **이 화면이 직접 연 연결**이다(위 onMounted, S15P11A704-546) —
 * 라이브가 언마운트하며 자기 연결을 끊으므로 결과 화면을 열어 둔 채 정산이 끝나면 여기서 받는다.
 * 밴드 자리의 「다시 확인」 버튼(`PredictionBandChart`의 `retry` → `loadBands`)은 그대로 남는다:
 * 목 모드·연결 실패·신호 유실에서 사용자가 직접 부를 수 있는 유일한 계기다.
 */
watch(
  () => live.predictionSettlement,
  (settlement) => {
    // STOMP 수신은 프레임마다 JSON.parse라 같은 내용이어도 **항상 새 객체**다 — watch는 매번 돈다.
    // 중복·연속 신호를 막는 것은 이 watch가 아니라 `loadBands`의 진행 중 가드 + pending 큐다.
    // 방을 뜨며 `live.$reset()`으로 null이 되는 것만 여기서 걸러낸다(재조회 계기가 아니다).
    if (settlement === null) return
    void loadBands()
  },
)

function goRoomList() {
  router.push({ name: 'room-list' })
}

/**
 * DEAL-07 배송지 입력.
 * 와이어프레임은 이 자리에서 모달을 열지만 배송 플로우는 이 이슈 범위 밖이라
 * 지금은 마이페이지 "지금 할 일"로 넘긴다 — 에스크로 전이가 거기서 이어진다(docs/02).
 */
function goShipping() {
  router.push({ name: 'my-page' })
}
</script>

<template>
  <!-- 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))] px-4 py-8 lg:px-8">
    <div class="mx-auto w-full max-w-[1100px]">
      <!-- 로딩 -->
      <p v-if="loading" class="text-dim py-20 text-center text-[14px]" role="status">
        결과를 불러오는 중…
      </p>

      <!-- 결과 확정 전 — 조회 자체가 막힌다. 폴링하지 않고 사용자가 다시 확인하게 둔다 -->
      <section
        v-else-if="notReady"
        class="border-line bg-surface mx-auto max-w-[520px] rounded-[16px] border p-8 text-center"
      >
        <h1 class="text-ink text-[20px] font-semibold">아직 집계 중이에요</h1>
        <p class="text-dim mt-2.5 text-[13px] leading-relaxed">
          2등가 판정과 포인트 정산이 끝나면 결과가 공개돼요. 잠시 후 다시 확인해 주세요.
        </p>
        <div class="mt-5 flex justify-center gap-2">
          <button
            type="button"
            class="border-line text-dim bg-surface hover:bg-cream rounded-[10px] border px-4 py-2.5 text-[13px] font-medium transition motion-reduce:transition-none"
            @click="goRoomList"
          >
            다른 방송 보러 가기
          </button>
          <button
            type="button"
            class="bg-terra hover:bg-terra-d rounded-[10px] px-4 py-2.5 text-[13px] font-bold text-white transition motion-reduce:transition-none"
            @click="load"
          >
            다시 확인
          </button>
        </div>
      </section>

      <!-- 조회 실패 -->
      <section
        v-else-if="errorText !== '' || result === null"
        class="border-line bg-surface mx-auto max-w-[520px] rounded-[16px] border p-8 text-center"
      >
        <p class="text-ink text-[15px] font-semibold" role="alert">
          {{ errorText || '결과를 찾을 수 없어요.' }}
        </p>
        <button
          type="button"
          class="bg-terra hover:bg-terra-d mt-5 rounded-[10px] px-4 py-2.5 text-[13px] font-bold text-white transition motion-reduce:transition-none"
          @click="load"
        >
          다시 시도
        </button>
      </section>

      <template v-else>
        <!-- 헤더 -->
        <header class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span
              class="inline-block rounded-full px-3 py-1 text-[12px] font-semibold text-white"
              :class="badge.className"
              >{{ badge.text }}</span
            >
            <h1 class="text-ink mt-2 text-[30px] leading-tight font-semibold">{{ roomTitle }}</h1>
          </div>
        </header>

        <div class="mt-5 grid gap-4 lg:grid-cols-[1.1fr_1fr]">
          <!-- ── 낙찰 요약 ── -->
          <section class="border-line bg-surface overflow-hidden rounded-[16px] border">
            <!--
              LIVE-06 낙찰 순간 스냅샷. 판매자 브라우저가 종료 시점 프레임을 올리고(704)
              결과 응답의 `snapshotUrl`로 내려온다(705). 안 올라온 경매도 정상이라 그때는
              예전 플레이스홀더를 그대로 둔다.
            -->
            <div class="bg-stage relative flex h-[190px] items-center justify-center">
              <img
                v-if="snapshotUrl"
                :src="snapshotUrl"
                alt="낙찰 순간 스냅샷"
                class="absolute inset-0 h-full w-full object-cover"
              />
              <span
                class="text-on-dark-dim absolute bottom-3 left-3 rounded-full bg-white/10 px-3 py-1 text-[11px] backdrop-blur-sm"
                >낙찰 순간 스냅샷</span
              >
              <span v-if="!snapshotUrl" class="text-on-dark-dim text-[13px]">스냅샷 준비 중</span>
            </div>

            <div class="p-5">
              <div class="flex items-center gap-2.5">
                <!-- 디자인 원본: 본인이면 이니셜 아바타, 타인이면 익명 아이콘 -->
                <div
                  class="flex h-9 w-9 flex-none items-center justify-center rounded-full text-[13px] font-semibold"
                  :class="isWinner ? 'bg-terra-tint text-terra-tint-text' : 'bg-line text-faint'"
                  aria-hidden="true"
                >
                  <span v-if="isWinner">{{ winnerInitial }}</span>
                  <svg
                    v-else
                    class="h-[18px] w-[18px]"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="1.9"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  >
                    <circle cx="12" cy="8" r="3.4" />
                    <path d="M5 20c0-3.6 3.1-5.6 7-5.6s7 2 7 5.6" />
                  </svg>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="text-dim text-[12px]">낙찰자</p>
                  <p class="text-ink truncate text-[17px] font-semibold tabular-nums">
                    {{ result.winnerLabel
                    }}<span v-if="isWinner" class="text-success ml-1.5 text-[12px] font-semibold"
                      >· 나</span
                    >
                  </p>
                </div>
                <div v-if="isLosingBidder && myRank !== null" class="flex-none text-right">
                  <p class="text-dim text-[11.5px]">내 순위</p>
                  <p class="text-ink text-[17px] font-semibold tabular-nums">
                    {{ myRank.rank }}위
                    <span class="text-dim text-[12px]">/ {{ formatAmount(myRank.total) }}명</span>
                  </p>
                </div>
              </div>

              <div
                class="bg-terra-tint mt-4 flex items-baseline justify-between gap-3 rounded-xl px-4 py-3.5"
              >
                <div class="min-w-0">
                  <p class="text-terra-tint-text text-[12px]">{{ finalPriceLabel }}</p>
                  <p class="text-terra text-[36px] leading-tight font-semibold tabular-nums">
                    {{ formatPoint(displayedPrice) }}
                  </p>
                </div>
                <div
                  v-if="highestBidAmount !== null"
                  class="text-dim flex-none text-right text-[12px]"
                >
                  최고 입찰<br /><b class="text-ink text-[14px] tabular-nums">{{
                    formatPoint(highestBidAmount)
                  }}</b>
                </div>
              </div>

              <div class="mt-3.5 flex gap-2">
                <button
                  v-if="isWinner"
                  type="button"
                  class="bg-terra hover:bg-terra-d flex-1 rounded-[10px] px-3 py-2.5 text-[13px] font-bold text-white transition motion-reduce:transition-none"
                  @click="goShipping"
                >
                  배송 정보 입력하기
                </button>
                <button
                  v-else
                  type="button"
                  class="border-line text-ink bg-surface hover:bg-cream flex-1 rounded-[10px] border px-3 py-2.5 text-[13px] font-medium transition motion-reduce:transition-none"
                  @click="goRoomList"
                >
                  다른 방송 보러 가기 →
                </button>
              </div>
            </div>
          </section>

          <!-- ── 정산 · 예측 카드 ── -->
          <div class="flex flex-col gap-3.5">
            <ResultSettlementCard
              v-if="settlement !== null"
              :variant="settlement.variant"
              :is-second-price="isSecondPrice"
              :held-amount="settlement.heldAmount"
              :paid-amount="settlement.paidAmount"
              :restored-amount="settlement.restoredAmount"
            />

            <MyPredictionCard
              v-if="isPredictor && myPrediction !== null"
              :predicted-price="myPrediction.predictedPrice"
              :stake-amount="myPrediction.stakeAmount"
              :final-price="finalPrice"
              :server-band="myPrediction.errorBand"
              :server-multiplier="myPrediction.multiplier"
              :server-payout="myPrediction.payoutAmount"
            />

            <!-- 예측자인데 내 예측 값을 못 받았을 때 (조회 API 미확정) -->
            <p
              v-else-if="isPredictor"
              class="border-line bg-surface text-dim rounded-[16px] border p-[18px] text-[12.5px] leading-relaxed"
            >
              내 예측 결과는 정산이 반영된 뒤 베팅 포인트 내역에서 확인할 수 있어요.
            </p>

            <PredictionBandChart
              :bands="bands"
              :loading="bandsLoading"
              :error-text="bandsError"
              @retry="loadBands"
            />
          </div>
        </div>

        <div class="mt-4">
          <ResultBidLedger
            :bids="result.bids"
            :final-price="result.finalPrice"
            :is-second-price="isSecondPrice"
          />
        </div>
      </template>
    </div>
  </main>
</template>
