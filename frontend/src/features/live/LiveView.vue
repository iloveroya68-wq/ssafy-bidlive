<script setup lang="ts">
// LIVE 라이브 화면 — 라우트 진입 + 프레임 조립 (FE1, docs/03-역할-경계-합의.md §1)
// #video·#chat은 FE2 컴포넌트가, #action은 FE1 입찰·예측 dock이 채운다.
//
// 이 파일이 지는 책임은 셋이다.
//   ① 모드 토글 — 보는 건 언제나 자유다. 역할이 확정돼도 전환 버튼은 계속 동작한다.
//   ② 배타 잠금 상태(`myParticipationType`) 보유 + 재접속 복원 (WS-03)
//   ③ BID_ACCEPTED 수신 → 집계값·Vibe 갱신 (지금은 목 드라이버)
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLiveStore } from '@/stores/live'
import { useAuthStore } from '@/stores/auth'
import { useWalletStore } from '@/stores/wallet'
import { useRoomStore, type AuctionRoomSummary } from '@/stores/room'
import { isDemoRoom, withDemoRooms } from '@/features/demo/demoRooms'
import DemoRoomNotice from '@/features/demo/DemoRoomNotice.vue'
import RoomCard from '@/features/room/RoomCard.vue'
import { otherLiveRooms } from './otherLiveRooms'
import { usePredictionStore } from '@/stores/prediction'
import BidDock from '@/features/bid/BidDock.vue'
import PredictionDock from '@/features/prediction/PredictionDock.vue'
import LockNote from '@/shared/ui/LockNote.vue'
import RevealOverlay from '@/features/result/RevealOverlay.vue'
import { useRevealState } from '@/features/result/revealState'
import type { PlaceBidResponse } from '@/shared/api/bids'
import { asRevealScenario, startMockRevealFeed } from '@/mocks/resultReveal'
import { isMockApi } from '@/shared/api/mock'
import { getCatchUpSummary, type CatchUpSummary } from '@/shared/api/rooms'
import LiveStage from '@/features/live/media/LiveStage.vue'
import VibeOverlay from '@/features/live/media/VibeOverlay.vue'
import PredictionChips from '@/features/live/media/PredictionChips.vue'
import ChatPanel from '@/features/chat/ChatPanel.vue'
import ReportModal from '@/features/report/ReportModal.vue'
import { useAuctionReport } from '@/features/report/useAuctionReport'
import CatchUpSummaryCard from './CatchUpSummaryCard.vue'
import LiveLayout from './LiveLayout.vue'

const route = useRoute()
const router = useRouter()
const live = useLiveStore()
// 채팅 삭제 노출 판정에 role이 필요하다(S15P11A704-681) — 로그인 여부·회원 정보의 단일 소스는 auth 스토어다.
const auth = useAuthStore()
const wallet = useWalletStore()
const room = useRoomStore()
const prediction = usePredictionStore()

const auctionId = computed(() => Number(route.params.id) || 0)
const catchUpSummary = ref<CatchUpSummary | null>(null)
const catchUpSummaryVisible = ref(true)
const CATCH_UP_REFRESH_INTERVAL_MS = 15_000
/** 와이어프레임 `init.live` — 입장 700ms 뒤 뜨고 11초 뒤 스스로 접힌다 (S15P11A704-706) */
const CATCH_UP_AUTO_HIDE_MS = 11_000
let catchUpRequestInFlight = false
let catchUpRefreshTimer: number | null = null
let catchUpHideTimer: number | null = null

/** 입찰 집계는 소켓이 가장 빠른 소스다. AI 문장은 주기 조회 결과를 유지한다. */
const displayedCatchUpSummary = computed<CatchUpSummary | null>(() => {
  const summary = catchUpSummary.value
  if (summary === null) return null
  return {
    ...summary,
    bidSummary: {
      bidCount: live.bidCount ?? summary.bidSummary.bidCount,
      participantCount: live.participantCount ?? summary.bidSummary.participantCount,
    },
  }
})

/** AI-03은 보조 기능이다. 204·호출 실패 모두 라이브 입장과 거래 흐름에 영향을 주지 않는다. */
async function loadCatchUpSummary(): Promise<void> {
  if (isMockApi() || auctionId.value <= 0 || catchUpRequestInFlight) return
  catchUpRequestInFlight = true
  try {
    catchUpSummary.value = await getCatchUpSummary(auctionId.value)
  } catch (error) {
    console.warn('[live] 중간 입장 요약을 불러오지 못했습니다:', error)
    // 일시적인 요약 실패로 이미 표시 중인 카드까지 지우지 않는다.
  } finally {
    catchUpRequestInFlight = false
  }
}

// 요약이 처음 도착한 시점부터 11초를 센다 — 스테이지를 계속 덮고 있으면 영상을 가린다.
// 한 번만 건다(타이머 가드). 15초 주기 재조회는 `catchUpSummaryVisible` 조건이 있어 접히면 함께 멈춘다.
watch(
  () => catchUpSummary.value !== null,
  (hasSummary) => {
    if (!hasSummary || catchUpHideTimer !== null) return
    catchUpHideTimer = window.setTimeout(() => {
      catchUpSummaryVisible.value = false
    }, CATCH_UP_AUTO_HIDE_MS)
  },
)

/** 레일 상단 토글 — **역할 확정 여부와 무관하게** 항상 동작한다 (docs/03 §4-1) */
const mode = ref<'bid' | 'bet'>('bid')

// 확정된 참여 역할(배타 잠금, 규칙 7)은 **live 스토어 단일 소스**(myParticipationType)를 읽고 쓴다(S15P11A704-365).
// 첫 액션 시점에 정해지고 이후 되돌릴 수 없다. 로컬 ref는 제거됐다(201에서 스토어로 이관된 값).
//
// 재접속 스냅샷(applySnapshot)이 PREDICTION으로 복원할 때만 예측 제출 상태를 되살린다 —
// `!prediction.submitted` 가드가 신규 제출(onPredictionSubmitted가 store를 세팅)에서 restoreSubmitted가
// 재발화해 방금 제출값(myStake 등)을 기본값으로 덮는 것을 막는다(제출은 PredictionDock이 이미 submitted=true로 만든다).
// 금액·스테이크도 함께 넘긴다 — 스냅샷이 주는 값이라 이제 빈칸으로 복원되지 않는다(S15P11A704-388).
// myStakeAmount는 스냅샷에서 null일 수 있고 restoreSubmitted의 stakeAmount는 number라 0으로 접는다.
watch(
  () => live.myParticipationType,
  (t) => {
    if (t === 'PREDICTION' && !prediction.submitted) {
      prediction.restoreSubmitted(live.myPredictedPrice, live.myStakeAmount ?? 0)
    }
  },
)

// 집계값은 이제 live 스토어(핸들러가 갱신)에서 읽는다 — 목은 startMockLiveFeed, 실은 소켓이 공급(S15P11A704-355).
// 비크리 공개 범위는 건수·참여자 수뿐이다(규칙 6). null(미수신)은 0으로 접지 않고 '—'로 표시한다 —
// 스토어가 null을 남긴 이유가 "0건 0명"을 사실처럼 렌더하지 않기 위함이다(stores/live.ts, 리뷰 2).
// Vibe는 #action 단일 라인 대신 스테이지 오버레이(VibeOverlay, #video)로 렌더한다(S15P11A704-357).
const bidCount = computed(() => live.bidCount)

const bidLocked = computed(() => live.myParticipationType === 'PREDICTION')
const predictionLocked = computed(() => live.myParticipationType === 'BID')

/**
 * BID-10 마감·집계·공개 오버레이 (S15P11A704-66).
 *
 * 마감 신호는 **서버 이벤트뿐**이다 — 남은 시간으로 클라이언트가 마감을 판정하지 않는다(규칙 5).
 * 국면 자체는 화면 로컬이되, 입력은 스토어의 종료 이벤트(`live.endEvent`)가 세운다(아래 watch).
 */
const reveal = useRevealState()
// 템플릿에서 자동 언랩되도록 top-level ref로 꺼내 둔다
const revealPhase = reveal.phase
const revealWinnerLabel = reveal.winnerLabel
const revealFinalPrice = reveal.finalPrice
const revealHighestBid = reveal.highestBidAmount
const revealReasonText = reveal.reasonText
/** 마감 이후에는 입찰·예측 제출을 받지 않는다 (AUCTION_ENDED, BID-10) */
const auctionEnded = reveal.isEnded
let stopRevealFeed: (() => void) | null = null

function goResultDetail() {
  reveal.dismiss()
  router.push({ name: reveal.detailRouteName.value, params: { id: String(auctionId.value) } })
}

/**
 * 방송이 사라진 종료(운영 강제·판매자 이탈) — 이 방에 남는 선택지 자체가 없다.
 *
 * **목적지를 방 목록에서 홈으로 옮겼다** (S15P11A704-688). 끝난 방에서 튕겨 나온 사람에게
 * 필요한 건 「다른 방 고르기」가 아니라 원래 있던 자리로 돌아가는 것이고, 홈에도 라이브 목록이
 * 있어 다음 방을 고르는 길이 막히지 않는다. 강제 종료도 같은 상태라 함께 옮겼다.
 */
function goHome() {
  reveal.dismiss()
  router.push({ name: 'home' })
}

/**
 * 하단 「다른 라이브」 (와이어프레임 `data-screen="live"`의 `.live-below`).
 *
 * 방송을 보다 갈아타는 동선이다. 원문 주석이 범위를 못 박아 뒀다 —
 * *"여긴 갈아타기용 추천 8개까지만. 본격 탐색은 방 목록(무한 스크롤)의 역할"*.
 * 그래서 무한 스크롤 없이 8개 + 「전체 라이브 보기 →」 하나다.
 *
 * 시연 더미도 함께 태운다(`features/demo`). 실 방이 8칸을 채우면 자동으로 밀려난다.
 */
const otherLive = computed(() => otherLiveRooms(withDemoRooms(room.rooms, 'LIVE'), auctionId.value))

function openOtherLive(target: AuctionRoomSummary) {
  if (isDemoRoom(target)) {
    demoNoticeOpen.value = true
    return
  }
  // 같은 라우트의 params만 바뀐다 — `LiveView`가 재사용되므로 마운트 훅이 다시 돌지 않는다.
  // 그래서 `push`가 아니라 새로 들어가는 것과 같아지도록 라우트 키를 바꿔야 하는데, 이 앱은
  // 방 이동이 드물어 그냥 push한다. 스토어 정리는 `onUnmounted`가 아니라 `connect()`의
  // `joinRoom()`이 담당한다(입장 캡처 가드, S15P11A704-367).
  void router.push({ name: 'live', params: { id: String(target.auctionId) } })
}

/** 시연 더미 카드를 눌렀을 때 뜨는 안내 */
const demoNoticeOpen = ref(false)

// 방 정보 — 하한가(`auction_room.reserve_price`)는 입찰 검증의 1단계 기준값이다.
// **목록이 아니라 상세에서 받는다.** 목록은 이제 한 페이지뿐이라 딥링크로 들어오면
// 그 방이 없고, 그러면 아래 폴백(30,000P)으로 조용히 떨어져 입찰 검증 기준이 틀어진다
// (S15P11A704-396).
const currentRoom = computed(() => room.detailOf(auctionId.value))

/**
 * DEAL-04 신고. 상세를 아직 못 받았으면 버튼을 내지 않는다 — `isSeller`를 모르는 채로 띄우면
 * 판매자에게도 보이고, 그건 서버가 403으로 되돌린다.
 */
const {
  open: reportOpen,
  pending: reportPending,
  error: reportError,
  notice: reportNotice,
  openModal: openReport,
  closeModal: closeReport,
  submit: submitReport,
} = useAuctionReport(() => auctionId.value || null)
const canReport = computed(() => currentRoom.value !== undefined && !currentRoom.value.isSeller)
const reservePrice = computed(() => currentRoom.value?.reservePrice ?? 30_000)
const roomTitle = computed(
  () => currentRoom.value?.title ?? '기계식 키보드 커스텀 풀빌드 (게이트론 저소음 적축)',
)
/**
 * 서버가 마스킹한 판매자 닉네임(예: `서연***`). **하드코딩된 '지훈'을 대체한다** — 와이어프레임
 * 더미가 그대로 남아 모든 방의 판매자가 지훈으로 보였다.
 *
 * 상세 도착 전에는 빈 문자열이다. `LiveLayout`이 `v-if="sellerName"`으로 줄을 통째로 접으므로,
 * 제목처럼 더미로 채우지 않는다 — 남의 이름을 잠깐이라도 잘못 띄우는 것이 비는 것보다 나쁘다.
 */
const sellerName = computed(() => currentRoom.value?.seller.nickname ?? '')

// 마감·낙찰 공개(reveal)가 시작되면 **목** 라이브 피드를 멈춘다 — 목 피드는 자체로 멈추지 않아 연출 뒤에서
// 집계가 계속 올라 오버레이 카운트업이 튄다. 실 모드는 서버가 마감 후 BID_ACCEPTED를 멈추므로 불필요하고,
// 오히려 여기서 끊으면 정산 브로드캐스트를 못 받으니 isMockApi 가드를 건다(리뷰 4). disconnect는 상태를 지우지 않아 채팅 표시는 유지.
watch(auctionEnded, (ended) => {
  if (ended && isMockApi()) void live.disconnect()
})

/**
 * 소켓 종료 이벤트 → 오버레이 (BID-10).
 *
 * **이 watch가 실서버에서 오버레이를 띄우는 유일한 경로다.** 예전에는 `reveal.apply`를 부르는 곳이
 * 목 데모 피드(`?reveal=` 딥링크) 하나뿐이라, 실서버에서는 마감돼도 phase가 `hidden`에서 나올 방법이
 * 없었다 — 낙찰 공개가 통째로 안 뜨고 `auctionEnded`도 false로 남아 입찰창이 계속 열려 있었다.
 * 목 데모 피드는 스토어를 거치지 않고 `apply`를 직접 부르므로 이 watch와 겹치지 않는다.
 *
 * ⚠️ **`immediate`를 붙이지 않는다** (!303 리뷰 1). 붙이면 setup 시점에 스토어에 남아 있는
 * **이전 방의** 종료 이벤트를 집는다 — 아래 `onUnmounted`가 `live.$reset()`을 `disconnect()`
 * 뒤로 미루기 때문에, 방 A가 정산된 직후 방 B로 들어가면 B의 setup에서 `endEvent`가 아직 A의 것이다.
 * 그러면 B 화면에 A의 낙찰 오버레이가 뜨고, 더 나쁜 건 `ended`가 true로 남아 **B의 입찰·예측이
 * 잠긴다**는 것이다(`dismiss()`는 `phase`만 되돌리고 `ended`는 그대로 둔다).
 * 잃는 것도 없다 — 이 방의 종료 이벤트는 `onMounted`의 `connect()` 이후에나 오므로 setup 시점에
 * 미리 와 있을 수가 없다. `immediate`가 잡을 수 있는 건 사실상 이전 방의 잔재뿐이다.
 * (같은 파일의 `prediction.$reset()`이 가드 밖에 있는 이유와 같은 사고다 — S15P11A704-546.)
 */
watch(
  () => live.endEvent,
  (event) => {
    if (event !== null) reveal.apply(event)
  },
  { flush: 'sync' },
)

/**
 * 마감 캐치업 — 정산 순간 소켓이 끊겨 있던 뷰어 (S15P11A704-674).
 *
 * 위 watch가 잡는 것은 **종료 이벤트를 실제로 받은** 경우뿐이다. 정산 순간 STOMP가 끊겨 있으면
 * AUCTION_ENDED·SETTLED·NO_SALE을 통째로 유실하고, 재접속 스냅샷(WS-03)이 `auctionStatus`를
 * 'ENDED'로 정확히 복원해도 화면은 타이머 00:00인 라이브에 그대로 멈춰 있었다 — 마감 전환이
 * 이벤트 핸들러에만 걸려 있었기 때문이다.
 *
 * 스냅샷엔 낙찰자·최종가·유찰 사유가 없어 오버레이 연출은 그릴 재료가 없다. 그래서 연출을
 * 흉내 내지 않고 **결과 화면으로 보낸다** — 처음부터 끝난 방에 들어왔을 때와 같은 처리다
 * (`WaitingRoomView`가 `roomStatus === 'ENDED'`에서 하는 replace와 동일). 유찰·강제 종료 구분도
 * 여기서 하지 않는다: `ResultView`가 결과 응답(`isSoldResult`)을 보고 `nosale`로 스스로 replace하고,
 * 정산 전(409)이면 「정산이 끝나면 공개돼요」 안내로 떨어진다.
 *
 * ⚠️ `endEvent !== null`이면 손대지 않는다 — 이벤트를 받은 정상 경로에서는 연출이 우선이다.
 * 종료 핸들러(`handleAuctionEnd`)가 `auctionStatus`와 `endEvent`를 같은 tick에 함께 세우므로
 * 이 watch가 플러시될 때는 이미 채워져 있다.
 * `immediate`도 붙이지 않는다 — 위 watch와 같은 이유로 이전 방의 상태를 집는다. 이 방의 스냅샷은
 * `onMounted`의 `connect()`(→ `joinRoom()`이 'WAITING'으로 초기화) 이후에 오므로 변화로 관측된다.
 *
 * 종료 판정은 **`'ENDED'` 하나뿐**이다. 이 경로로 오는 상태값은 스냅샷이 싣는 것뿐이고, 그 값 집합은
 * BE `RealtimeAuctionStatus`(=Redis Hash를 읽는 `RealtimeAuctionStateSnapshot.Status`)의
 * `WAITING`|`LIVE`|`ENDED` 3값이 전부다. 다만 그 `ENDED`를 쓰는 경로는 둘이고, **결과 API가 이미
 * 열려 있는지는 경로마다 다르다.**
 *   - **정산**: `AuctionSettlementService`가 DB 정산 트랜잭션을 **커밋한 뒤에** Redis completion으로
 *     `ENDED`를 세운다 — 여기서 `ENDED`를 본 시점엔 결과 API가 늘 열려 있다.
 *   - **강제 종료(DEAL-09)**: `RedisAuctionStateCloser`가 **`BEFORE_COMMIT`**에 쓴다. 입력 차단이
 *     트랜잭션 **안**에 있어야 Redis 실패 때 상태 전이·홀드 해제가 함께 롤백되기 때문인 의도적
 *     설계다(537). 그래서 커밋 전 밀리초 창에서는 결과 행(`createResultIfAbsent`)이 아직 안 보여
 *     결과 API가 닫혀(409) 있을 수 있고, 그때는 `ResultView`의 「아직 집계 중이에요」 안내로
 *     떨어진다 — **자동 갱신은 없고** 사용자가 「다시 확인」을 눌러야 한다. 창이 짧고 착지점이
 *     안내 화면이라 여기서 더 하지 않는다.
 *
 * 그래서 이 캐치업이 건지는 건 정산 유실만이 아니다 — `AUCTION_CANCELED`를 놓친 뷰어도 스냅샷의
 * `ENDED`로 결과 화면까지 가고, 거기서 `nosale`로 넘어간다.
 *
 * 정산 세부 상태(`'SETTLING'` 등)는 **일부러 넣지 않는다.** 소켓으로 오는 값은 위 `endEvent` 가드가
 * 늘 먼저 막아 여기까지 오지 않고, 반대로 BE가 나중에 enum을 넓혀 `SETTLING`을 내보내기 시작하면
 * 정산 **중**인 재접속자를 「자동 갱신 없는 결과 화면」으로 쫓아내는 잘못된 전환이 된다.
 */
watch(
  () => live.auctionStatus,
  (status) => {
    const endEventType = live.endEvent?.type
    // AUCTION_ENDED만 복원된 경우에는 종료 이벤트를 기다리지 않고 결과 화면으로 복구한다.
    if (status !== 'ENDED' || (endEventType !== undefined && endEventType !== 'AUCTION_ENDED'))
      return
    void router.replace({ name: 'result', params: { id: String(auctionId.value) } })
  },
)

onMounted(() => {
  // 잔액은 매번 새로 받는다 — 입찰·베팅 판정의 기준값이라 굳으면 안 된다.
  // 중복 조회는 스토어의 single-flight가 막는다 (S15P11A704-333).
  void wallet.loadWallet()
  void room.loadRoomDetail(auctionId.value)

  // 하단 「다른 라이브」가 쓸 목록. **매번 다시 건다** — 이 스토어는 방 목록 화면과 공유라
  // 「종료」 탭을 보고 넘어오면 조건이 그쪽으로 맞춰져 있다(`MainView`가 같은 이유로 매번 건다).
  void room.loadRooms({ status: 'LIVE' })

  // 라이브 소스 연결 (S15P11A704-355 / 208) — 모드 인식 단일 진입점.
  //   목: startMockLiveFeed(집계·채팅·시청자·타이머를 실 핸들러로 흘림)
  //   실: connectLiveSocket(토픽 구독 + WS-03 스냅샷 복원 + 채팅 SEND 주입)
  // 이로써 집계·Vibe·채팅·시청자 수·잠금 복원이 모두 live 스토어를 단일 소스로 흐른다.
  void live.connect(auctionId.value)
  void loadCatchUpSummary()
  catchUpRefreshTimer = window.setInterval(() => {
    if (
      document.visibilityState === 'visible' &&
      catchUpSummaryVisible.value &&
      !auctionEnded.value
    ) {
      void loadCatchUpSummary()
    }
  }, CATCH_UP_REFRESH_INTERVAL_MS)

  // `?reveal=sold|nosale|canceled` 딥링크는 **목 전용 시연 경로**다 — 경매를 실제로 끝내지 않고
  // 연출만 확인한다. 실서버는 위 `live.endEvent` watch가 같은 자리를 채운다.
  const scenario = asRevealScenario(route.query.reveal)
  if (isMockApi() && scenario !== null) {
    stopRevealFeed = startMockRevealFeed(auctionId.value, scenario, reveal.apply)
  }
})

onUnmounted(() => {
  if (catchUpRefreshTimer !== null) {
    window.clearInterval(catchUpRefreshTimer)
    catchUpRefreshTimer = null
  }
  if (catchUpHideTimer !== null) {
    window.clearTimeout(catchUpHideTimer)
    catchUpHideTimer = null
  }
  stopRevealFeed?.()
  stopRevealFeed = null
  // 예측 스토어는 아래 세대 가드 **밖**에서, 즉시 비운다. 이 스토어는 다음 화면에 넘겨줄 값이 없어
  // (쓰는 곳이 이 화면과 PredictionDock뿐) 경합 가드가 애초에 필요 없다 — live 스토어와 다른 점이다.
  // 가드 안에 두면 **다음 화면이 먼저 연결을 세우는 경로에서 통째로 사라진다**: 결과 화면이 마운트에서
  // live.connect() → joinRoom()으로 roomSession을 동기 갱신하므로(S15P11A704-546) 아래 대조가 early
  // return이 되고, 그러면 submitted=true가 남아 다음 방에서 제출이 막히고 이전 방의 확정 카드가 그려진다.
  prediction.$reset()
  // 목 피드/실 소켓 종료가 끝난 뒤 초기화 — disconnect가 async(실 모드 dynamic import)라 순서를 보장한다(WaitingRoomView와 동일, 리뷰 1).
  // 나란히 두면 소켓이 살아 있는 사이 $reset이 먼저 돌아, 초기화된 스토어로 이벤트가 dispatch되고 connectionStatus도 뒤늦게 덮인다.
  // 이 화면의 **입장**을 캡처해, 지연 $reset()이 다음 joinRoom() 뒤에 도착해 그 상태를 지우는 경합을 막는다(S15P11A704-347).
  // 방 번호가 아니라 입장 번호를 보는 이유: 같은 방을 다시 여는 경로에서는 roomId가 같아 대조가 통과한다(S15P11A704-367).
  const leaving = live.roomSession
  void live.disconnect().then(() => {
    if (leaving !== live.roomSession) return
    live.$reset()
  })
})

/** BID-06 접수 성공 — 개인 결과는 REST 응답이 단일 소스다(소켓엔 집계값만 온다) */
function onBidSubmitted(result: PlaceBidResponse) {
  live.$patch({ myBid: result.myBidAmount, myParticipationType: 'BID' })

  const tradeWallet = wallet.wallets.find((w) => w.walletType === 'TRADE')
  if (tradeWallet) {
    tradeWallet.availableBalance = result.availableBalance
    tradeWallet.heldBalance += result.additionalHoldAmount
  }

  // 집계 갱신은 서버 BID_ACCEPTED 브로드캐스트가 담당한다(스토어 단일 소스). 목 모드는 목 피드가 내 입찰을
  // 모르므로, 데모에서 내 행동이 건수에 반영되도록 스토어 진입점으로 즉시 반영한다(실 모드는 무동작, 리뷰 3).
  live.bumpMyBid()
}

/** PRED-01 접수 성공 — 스테이크만큼 베팅 지갑이 홀드로 넘어간다 */
function onPredictionSubmitted() {
  live.myParticipationType = 'PREDICTION'

  const bettingWallet = wallet.wallets.find((w) => w.walletType === 'BETTING')
  if (bettingWallet) {
    bettingWallet.availableBalance -= prediction.myStake
    bettingWallet.heldBalance += prediction.myStake
  }
}

function onBack() {
  router.push({ name: 'room-list' })
}
</script>

<template>
  <LiveLayout
    :title="roomTitle"
    :seller-name="sellerName"
    :viewer-count="live.viewerCount"
    @back="onBack"
  >
    <!-- DEAL-04 신고. 판매자 본인에게는 내지 않는다 — 서버도 403으로 막지만 누를 수 있게 두지 않는다 -->
    <template v-if="canReport" #header-action>
      <button
        type="button"
        class="text-on-dark hover:bg-red/80 rounded-full bg-white/10 px-3 py-[5px] text-xs backdrop-blur-sm transition motion-reduce:transition-none"
        @click="openReport()"
      >
        신고
      </button>
    </template>

    <!-- FE2 — WebRTC 스테이지 (features/live/media/LiveStage.vue). 실 모드는 OpenVidu 수신, 목 모드는 플레이스홀더 -->
    <template #video="{ fullscreenTarget }">
      <!-- 전체화면 대상은 프레임이 준다 — 타이머 칩·prodbar까지 함께 들어가야 한다(S15P11A704-433) -->
      <LiveStage :room-id="auctionId" :fullscreen-target="fullscreenTarget" />
      <!-- FE2 — BID-04 Vibe 오버레이. 스테이지 위로 "지금 벌어지는 일" 칩이 떠오른다(S15P11A704-357) -->
      <VibeOverlay />
      <!-- PRED-01 예측 참여 연출. 입찰 망치와 색·형태·방향을 전부 갈라 겹쳐도 구분된다 -->
      <PredictionChips />
      <!-- AI-03 따라잡기 요약. 와이어프레임 `.catchup`이 원래 스테이지 오버레이다(S15P11A704-706) —
           레일에 있던 동안에는 이 카드가 레일 높이를 먹어 데스크톱 채팅 패널이 잘렸다.
           등장 지연 700ms·0.35s 페이드는 와이어프레임 그대로. 모션 최소화 선호 시 트랜지션 없이 즉시 뜬다. -->
      <Transition
        enter-active-class="transition duration-350 ease-out-quint delay-700 motion-reduce:transition-none"
        enter-from-class="-translate-y-1.5 opacity-0"
        leave-active-class="transition duration-350 ease-out-quint motion-reduce:transition-none"
        leave-to-class="-translate-y-1.5 opacity-0"
      >
        <CatchUpSummaryCard
          v-if="displayedCatchUpSummary !== null && catchUpSummaryVisible"
          :summary="displayedCatchUpSummary"
          @close="catchUpSummaryVisible = false"
        />
      </Transition>
    </template>

    <!-- FE1 — 입찰↔예측 토글 + dock -->
    <template #action>
      <div class="flex flex-col gap-3">
        <!-- 모드 토글: 보는 건 자유. 잠기는 것은 제출뿐이라 여기엔 disabled가 없다. -->
        <div class="bg-line/50 flex flex-none gap-1 rounded-xl p-1">
          <button
            type="button"
            class="flex-1 rounded-[9px] py-2 text-[13px] font-semibold transition motion-reduce:transition-none"
            :class="mode === 'bid' ? 'bg-surface text-ink shadow-sm' : 'text-dim hover:text-ink'"
            :aria-pressed="mode === 'bid'"
            @click="mode = 'bid'"
          >
            상향 입찰
          </button>
          <button
            type="button"
            class="flex-1 rounded-[9px] py-2 text-[13px] font-semibold transition motion-reduce:transition-none"
            :class="mode === 'bet' ? 'bg-surface text-ink shadow-sm' : 'text-dim hover:text-ink'"
            :aria-pressed="mode === 'bet'"
            @click="mode = 'bet'"
          >
            예측 베팅
          </button>
        </div>

        <!-- BID_ACCEPTED 집계는 **스테이지 좌하단 배지**로 옮겼다(LiveLayout) — 영상을 보는 동안
             레일 안의 한 줄은 눈에 안 들어온다는 요청. 같은 수치를 두 곳에 두지는 않는다. -->

        <!-- 마감되면 제출 경로를 통째로 막는다.
             `pointer-events-none`만으로는 키보드로 우회되므로 `inert`를 함께 건다. -->
        <div :class="auctionEnded && 'opacity-45 grayscale'" :inert="auctionEnded">
          <BidDock
            v-show="mode === 'bid'"
            :auction-id="auctionId"
            :reserve-price="reservePrice"
            :my-bid="live.myBid"
            :available-balance="wallet.tradeWallet.availableBalance"
            :locked="bidLocked"
            :role-confirmed="live.myParticipationType === 'BID'"
            @submitted="onBidSubmitted"
            @locked-by-server="live.myParticipationType = 'PREDICTION'"
          />

          <PredictionDock
            v-show="mode === 'bet'"
            :auction-id="auctionId"
            :available-betting-point="wallet.bettingWallet.availableBalance"
            :locked="predictionLocked"
            :role-confirmed="live.myParticipationType === 'PREDICTION'"
            @submitted="onPredictionSubmitted"
            @locked-by-server="live.myParticipationType = 'BID'"
          />
        </div>

        <!-- 잠긴 쪽은 숨기지 말고 안내 문구로 덮는다 (docs/03 §4-1과 같은 원칙) -->
        <LockNote
          v-if="auctionEnded"
          text="🔒 경매가 마감돼 더 이상 입찰·예측을 받지 않아요. 결과가 정산되면 공개됩니다."
        />
      </div>
    </template>

    <!-- FE2 — 실시간 채팅 (features/chat/ChatPanel.vue). 데이터는 live 스토어(핸들러 갱신), 전송은 sendChat.
         라이브라 질문 강조(highlightQuestions) 켬. 공지(pinnedNotice)·검열(BLOCKED/FLAGGED)·뮤트는 ChatPanel이 처리. -->
    <template #chat>
      <!-- CHAT-02 고정 공지 액션은 판매자만(S15P11A704-559). 상세를 아직 못 받았으면 false다 —
           신고 버튼(canReport)과 같은 이유로, isSeller를 모르는 채로 켜면 서버가 403으로 되돌린다.
           CHAT-01 삭제는 운영자도 된다(S15P11A704-681) — 서버가 판매자·ADMIN 둘 다 받고 역할은 서버가
           판정한다. 운영자용 화면을 따로 만들지 않고 여기서 판정식만 넓힌다: 운영자도 방을 지켜보는
           자리가 라이브 화면이라, 지울 메시지를 보고 있는 곳이 곧 여기다.
           고정은 넓히지 않는다 — 그쪽은 판매자 전용이라 서버가 NOT_AUCTION_HOST로 되돌린다. -->
      <ChatPanel
        :room-id="auctionId"
        highlight-questions
        :can-pin="currentRoom?.isSeller ?? false"
        :can-delete="(currentRoom?.isSeller ?? false) || auth.user?.role === 'ADMIN'"
      />
    </template>
  </LiveLayout>

  <!-- ── 「다른 라이브」 (와이어프레임 `.live-below`) ──
       방송을 보다 갈아타는 동선이다. 라이브 프레임이 뷰포트보다 34px 낮게 잡혀 있어
       이 제목이 살짝 걸쳐 보이고, 그게 스크롤 단서다(`LiveLayout` 루트 주석 참조).
       카드는 방 목록의 `RoomCard`를 그대로 쓴다 — 와이어프레임도 같은 카드를 재사용한다. -->
  <section v-if="otherLive.length > 0" class="bg-cream px-4.5 pt-1 pb-7 lg:px-[18px]">
    <h2 class="text-ink mb-3.5 flex items-center gap-2 text-[15px] font-bold">
      <span class="bg-red h-1.5 w-1.5 rounded-full" aria-hidden="true" />
      다른 라이브
    </h2>

    <div class="grid grid-cols-[repeat(auto-fill,minmax(230px,1fr))] gap-4">
      <RoomCard
        v-for="target in otherLive"
        :key="target.auctionId"
        :room="target"
        @select="openOtherLive"
      />
    </div>

    <!-- 무한 스크롤 없음 — 여긴 갈아타기용 추천 8개까지고, 본격 탐색은 방 목록의 역할이다
         (와이어프레임 원문 주석). 그래서 「더 보기」가 아니라 목록으로 보내는 버튼이다. -->
    <div class="mt-4.5 flex justify-center">
      <RouterLink
        :to="{ name: 'room-list' }"
        class="border-line text-ink hover:border-terra hover:text-terra rounded-full border px-6.5 py-2.75 text-[13px] font-medium transition"
      >
        전체 라이브 보기 →
      </RouterLink>
    </div>
  </section>

  <!-- BID-10 집계 → 낙찰 공개 오버레이. 스테이지가 아니라 화면 전체 위에 얹는다
       (LiveLayout 확장은 별도 이슈다). -->
  <RevealOverlay
    v-if="revealPhase !== 'hidden'"
    :phase="revealPhase"
    :bid-count="bidCount ?? 0"
    :winner-label="revealWinnerLabel"
    :final-price="revealFinalPrice"
    :highest-bid-amount="revealHighestBid"
    :reason-text="revealReasonText"
    :reserve-price="reservePrice"
    :my-hold-amount="live.myBid"
    @close="reveal.dismiss()"
    @leave="goHome"
    @detail="goResultDetail"
  />

  <!-- 신고 모달·안내도 스테이지가 아니라 화면 전체 위에 얹는다 (RevealOverlay와 같은 이유) -->
  <ReportModal
    v-if="reportOpen"
    :product-title="roomTitle"
    :seller-nickname="currentRoom?.seller.nickname ?? null"
    :pending="reportPending"
    :submit-error="reportError"
    @close="closeReport"
    @submit="submitReport"
  />
  <p
    v-if="reportNotice"
    class="bg-ink text-on-dark fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-full px-4 py-2.5 text-[13px] font-medium shadow-lg"
    role="status"
  >
    {{ reportNotice }}
  </p>

  <DemoRoomNotice v-if="demoNoticeOpen" @close="demoNoticeOpen = false" />
</template>
