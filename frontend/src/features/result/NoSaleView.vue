<script setup lang="ts">
// DEAL-08 유찰·강제 종료 안내 화면 S-12 (S15P11A704-69). 디자인 원본 `data-screen="nosale"`.
//
// 낙찰자·미낙찰자가 존재하지 않으므로 **역할 분기가 없다** — 대신 종료 사유 3분기다(docs/02).
// 정산 결과(홀드 전액 해제, 차감 없음, 예측 전액 환불)는 셋 다 같고
// "왜 끝났는지"와 그 뒤 안내만 갈린다.
//
// 명세 「경매 결과 조회」 §종료 사유:
//   NO_BID    해제할 홀드 없음 / 전액 환불 / 재등록 표시
//   HOST_LEFT 전액 해제        / 전액 환불 / 재등록 표시
//   FORCED    라이브면 전액 해제 / 전액 환불 / 재등록 **숨김** (운영 검토 안내로 대체)
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatPoint } from '@/shared/lib/point'
import { useRoomStore } from '@/stores/room'
import { isSoldResult, type NoSaleAuctionResult, type NoSaleReason } from '@/shared/api/results'
import PredictionBandChart from './PredictionBandChart.vue'
import { isResultNotReady, loadAuctionResult } from './resultSource'

const route = useRoute()
const router = useRouter()
const room = useRoomStore()

const auctionId = computed(() => Number(route.params.id) || 0)

const result = ref<NoSaleAuctionResult | null>(null)
const loading = ref(true)
const notReady = ref(false)
const errorText = ref('')

/**
 * 사유별 문구. 배지·헤드라인은 와이어프레임 NOSALE 맵 그대로다.
 * 응답의 `message`가 헤드라인을 덮어쓰고, 부연 설명은 응답에 자리가 없어 화면 카피로 둔다.
 */
const REASON_COPY: Record<
  NoSaleReason,
  { badge: string; badgeClass: string; head: string; detail: string }
> = {
  NO_BID: {
    badge: '유찰',
    badgeClass: 'bg-faint',
    head: '낙찰 없이 종료됐어요',
    detail: '하한가 이상 입찰이 한 건도 없었어요',
  },
  HOST_LEFT: {
    badge: '유찰',
    badgeClass: 'bg-faint',
    head: '판매자 이탈로 종료됐어요',
    detail: '판매자 연결이 끊겨 경매가 종료됐어요',
  },
  FORCED: {
    badge: '강제 종료',
    badgeClass: 'bg-terra',
    head: '운영팀이 방송을 종료했어요',
    // 하단 "운영 검토 결과 안내" 박스와 같은 문장을 쓰면 한 화면에 같은 말이 두 번 나온다
    detail: '신고가 누적되어 부적절한 방송으로 확인됐어요',
  },
}

const reason = computed<NoSaleReason>(() => result.value?.resultType ?? 'NO_BID')
/**
 * ⚠️ 이 주석은 「강제 종료는 REST `NoSaleReason`에 대응 값이 없다」고 적고 있었는데 **틀렸다**
 * (S15P11A704-524에서 정정). WS 이벤트 타입이 `AUCTION_CANCELED`라 이름은 다르지만,
 * REST 쪽 사유는 `FORCED`로 내려온다 — `AuctionResultQueryService`가 `termination_reason`을
 * `NO_BID`·`HOST_LEFT`·`FORCED`로 좁혀 보내고, 강제 종료(DEAL-09)가 그 컬럼에 `FORCED`를 쓴다.
 *
 * 폴백은 그대로 둔다. 유니온 밖 값이 오면 인덱싱이 `undefined`가 되어 템플릿의
 * `copy.badgeClass`에서 화면 전체가 죽는다 — 값 목록이 늘어날 때를 막는 방어다.
 */
const copy = computed(() => REASON_COPY[reason.value] ?? REASON_COPY.NO_BID)
const isForced = computed(() => reason.value === 'FORCED')

// 목록이 아니라 상세에서 (딥링크 진입이 기본 동선이다 — S15P11A704-396)
const currentRoom = computed(() => room.detailOf(auctionId.value))
const roomTitle = computed(() => currentRoom.value?.title ?? '종료된 경매')

/** 하한가는 유찰 응답에 없다 — 방 정보가 있을 때만 금액까지 붙인다 */
const detailText = computed(() => {
  const reserve = currentRoom.value?.reservePrice
  if (reason.value === 'NO_BID' && reserve !== undefined) {
    return `하한가 ${formatPoint(reserve)} 이상 입찰이 한 건도 없었어요`
  }
  return copy.value.detail
})

/**
 * 해제된 내 입찰 홀드. 개정 전 `holdReleaseAmount`(설명은 "총액")를 대체한 **개인 값**이다 —
 * 총액인지 개인 값인지 모호하던 것이 `myBidSettlement`로 재설계되며 해소됐다.
 */
const holdReleaseAmount = computed(() => result.value?.myBidSettlement?.releasedAmount ?? 0)
const hasHold = computed(() => holdReleaseAmount.value > 0)

/** 입찰로 참여했는가 — 예측자·판매자·관전자에게는 입찰 정산 카드 자체를 그리지 않는다 */
const hasBidSettlement = computed(() => result.value?.myBidSettlement != null)

/**
 * 예측 환불 카드는 **실제로 건 사람에게만** 보여 준다.
 * 예측에 참여하지 않았으면 "전액 환불 +0BP"는 사실이 아니라 빈 카드일 뿐이다.
 * 입찰과 예측은 배타적이라 위 홀드와 이 환불이 동시에 잡히는 응답은 나오지 않는다(규칙 7).
 */
const predictionRefundAmount = computed(
  () => result.value?.myPredictionSettlement?.refundAmount ?? 0,
)
const hasRefund = computed(() => predictionRefundAmount.value > 0)

/**
 * 재등록 CTA. `viewerRole === 'SELLER'`가 판매자 판정이고, `canRelist`는 그중 재등록 가능 여부다
 * (§규칙: "판매자에게만 의미가 있으며 일반 참여자에게는 false 또는 필드 생략").
 * 개정 전에는 판매자 플래그가 없어 `canRelist`가 판정을 겸했다.
 */
const canRelist = computed(
  () => result.value?.viewerRole === 'SELLER' && result.value.canRelist === true,
)

async function load() {
  loading.value = true
  notReady.value = false
  errorText.value = ''
  try {
    const response = await loadAuctionResult(auctionId.value)
    if (isSoldResult(response)) {
      await router.replace({ name: 'result', params: { id: String(auctionId.value) } })
      return
    }
    result.value = response
  } catch (error) {
    if (isResultNotReady(error)) notReady.value = true
    else errorText.value = '종료 안내를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void room.loadRoomDetail(auctionId.value)
  void load()
})

function goRoomList() {
  router.push({ name: 'room-list' })
}

function goRelist() {
  router.push({ name: 'room-create' })
}
</script>

<template>
  <!-- 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <main class="bg-cream min-h-[calc(100dvh-var(--header-h))] px-4 py-8 lg:px-8">
    <div class="mx-auto w-full max-w-[820px]">
      <p v-if="loading" class="text-dim py-20 text-center text-[14px]" role="status">
        종료 안내를 불러오는 중…
      </p>

      <section
        v-else-if="notReady || errorText !== '' || result === null"
        class="border-line bg-surface mx-auto max-w-[520px] rounded-[16px] border p-8 text-center"
      >
        <p class="text-ink text-[15px] font-semibold" role="status">
          {{
            notReady
              ? '아직 정산 중이에요. 잠시 후 다시 확인해 주세요.'
              : errorText || '결과를 찾을 수 없어요.'
          }}
        </p>
        <button
          type="button"
          class="bg-terra hover:bg-terra-d mt-5 rounded-[10px] px-4 py-2.5 text-[13px] font-bold text-white transition motion-reduce:transition-none"
          @click="load"
        >
          다시 확인
        </button>
      </section>

      <template v-else>
        <header class="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span
              class="inline-block rounded-full px-3 py-1 text-[12px] font-semibold text-white"
              :class="copy.badgeClass"
              >{{ copy.badge }}</span
            >
            <h1 class="text-ink mt-2 text-[30px] leading-tight font-semibold">{{ roomTitle }}</h1>
          </div>
        </header>

        <!-- 마감 히어로 — 다크 무대에 종료 사실만 얹는다 -->
        <div class="bg-stage mt-5 overflow-hidden rounded-[16px]">
          <div class="flex h-[190px] flex-col items-center justify-center px-6 text-center">
            <p class="text-on-dark-dim text-[13px] tracking-[0.16em]">＼ 마감 ／</p>
            <p class="text-on-dark mt-2.5 text-[26px] leading-tight font-semibold">
              {{ result.message || copy.head }}
            </p>
            <p class="text-on-dark-dim mt-2 text-[13px] tabular-nums">{{ detailText }}</p>
          </div>
        </div>

        <!--
          2열은 배타 참여(규칙 7)상 **도달하지 않는 방어 분기**다 — 입찰 정산과 예측 환불이
          한 응답에 같이 오는 경우는 없다. 서버가 불변식을 깨도 카드가 겹쳐 보이지 않게만 해 둔다.
        -->
        <div class="mt-4 grid gap-3.5" :class="hasBidSettlement && hasRefund && 'sm:grid-cols-2'">
          <!--
            포인트 정산 — 낙찰이 없으므로 차감은 항상 0이다.
            **입찰로 참여한 사람에게만** 보여 준다: 예측자·판매자·관전자에게 "홀드된 금액 0P"는
            사실이 아니라 빈 카드다. 개정 전에는 총액 필드라 참여 여부를 구분할 수 없어 항상 그렸다.
          -->
          <section
            v-if="hasBidSettlement"
            class="border-line bg-surface rounded-[16px] border p-[18px]"
            aria-label="포인트 정산"
          >
            <h2 class="text-ink mb-2.5 text-[13px] font-semibold">포인트 정산</h2>
            <dl class="flex flex-col gap-2 text-[13px] tabular-nums">
              <div class="flex justify-between">
                <dt class="text-dim">홀드된 금액</dt>
                <dd class="text-ink">{{ formatPoint(holdReleaseAmount) }}</dd>
              </div>
              <div class="flex justify-between">
                <dt class="text-dim">차감</dt>
                <dd class="text-ink">{{ formatPoint(0) }}</dd>
              </div>
              <hr class="bg-line my-0.5 h-px border-0" />
              <div class="flex justify-between font-semibold">
                <dt class="text-ink">전액 즉시 해제</dt>
                <dd :class="hasHold ? 'text-success' : 'text-dim'">
                  {{ hasHold ? `+${formatPoint(holdReleaseAmount)}` : '해제할 홀드 없음' }}
                </dd>
              </div>
            </dl>
            <p class="text-dim mt-[11px] text-[11.5px] leading-relaxed">
              <template v-if="isForced">
                운영 종료도 낙찰이 없는 종료라 차감은 발생하지 않아요. 홀드는 종료 즉시 풀립니다.
              </template>
              <template v-else>
                낙찰 없이 종료되면 차감은 발생하지 않아요. 홀드는 종료 즉시 풀립니다.
              </template>
            </p>
          </section>

          <!-- 예측 베팅 — 낙찰가가 없어 판정 불가 → 전액 환불 (PRED-03 §REFUNDED) -->
          <PredictionBandChart
            v-if="hasRefund"
            variant="refund"
            :refund-amount="predictionRefundAmount"
          />
        </div>

        <div class="mt-5 flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            class="border-line text-ink bg-surface hover:bg-cream rounded-[10px] border px-5 py-3 text-[14px] font-medium transition motion-reduce:transition-none"
            @click="goRoomList"
          >
            다른 방송 보러 가기
          </button>
          <div class="flex-1" />
          <button
            v-if="canRelist"
            type="button"
            class="bg-terra hover:bg-terra-d rounded-[10px] px-6 py-3 text-[14px] font-bold text-white transition motion-reduce:transition-none"
            @click="goRelist"
          >
            이 상품 다시 등록하기
          </button>
        </div>
        <p v-if="canRelist" class="text-faint mt-2.5 text-right text-[11.5px]">
          판매자에게만 보이는 버튼이에요
        </p>

        <!-- 강제 종료 — 제재 검토 중인 상품을 그 자리에서 다시 올리게 두면 안내와 모순된다 -->
        <div
          v-if="isForced"
          class="border-line bg-line/40 text-dim mt-4 rounded-xl border border-dashed px-4 py-3.5 text-[12.5px] leading-relaxed"
        >
          <b class="text-ink">운영 검토 결과 안내</b><br />
          접수된 신고를 확인해 방송을 종료했어요. 추가 조치가 필요하면 별도로 안내드려요.
        </div>
      </template>
    </div>
  </main>
</template>
