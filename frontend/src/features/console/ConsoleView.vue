<script setup lang="ts">
// LIVE·ROOM·BID-03·CHAT-06 판매자 콘솔 (FE1, 송출 프리뷰만 FE2) — data-screen="console".
// 대기(시작 전)와 라이브(진행 중)를 한 라우트가 겸한다. 종료는 결과(/result)·유찰(/nosale)이 받는다.
//
// 지표·타이머의 단일 소스는 `live` 스토어다. 기본적으로 읽기만 하지만, 방송 시작(ROOM-05) 응답이
// 주는 마감 시각만은 여기서 써 넣는다 — 소켓보다 먼저 손에 들어오기 때문이다(S15P11A704-523).
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { extendAuction } from '@/shared/api/bids'
import {
  endAuctionAsSeller,
  getAuctionRoom,
  startAuctionRoom,
  type AuctionRoomDetail,
} from '@/shared/api/rooms'
import { isMockApi } from '@/shared/api/mock'
import { toEpochMs } from '@/shared/api/types'
import { API_ERROR_CODE, hasErrorCode, toApiError } from '@/shared/api/errors'
import { useCountdown } from '@/shared/lib/useCountdown'
import { MAX_EXTENSIONS, useLiveStore } from '@/stores/live'
import { useRoomStore } from '@/stores/room'
// LIVE-02 방송 종료는 FE2 소유 모듈의 함수다 — import만 하고 그 파일은 수정하지 않는다.
import { endLiveStream } from '@/features/live/media/api'
import { useRoomDelete } from '@/features/room/useRoomDelete'
import { useRevealState } from '@/features/result/revealState'
import RevealOverlay from '@/features/result/RevealOverlay.vue'
import ChatPanel from '@/features/chat/ChatPanel.vue'
import BroadcastSlot from './BroadcastSlot.vue'
import ConsoleMetrics from './ConsoleMetrics.vue'
import QuestionQueue from './QuestionQueue.vue'
import { useQuestionQueue } from './useQuestionQueue'

const route = useRoute()
const router = useRouter()
const live = useLiveStore()
const roomStore = useRoomStore()

const auctionId = computed(() => Number(route.params.id))
const room = ref<AuctionRoomDetail | null>(null)
const loadError = ref<string | null>(null)

// RoomEditView.vue가 status !== 'WAITING'일 때 여기로 replace하며 안내 사유를 쿼리로 넘긴다
// (spec 「라우트·진입·복귀」). 조용히 튕기면 "방 수정" 버튼이 안 먹은 줄 알기 쉬워 한 줄 문구로 보여준다.
const consoleNotice = ref<string | null>(
  route.query.notice === 'edit-unavailable'
    ? '지금은 방을 수정할 수 없어요 · 대기 중일 때만 가능해요'
    : null,
)
if (route.query.notice !== undefined) {
  const { notice: _notice, ...rest } = route.query
  void router.replace({ query: rest })
}

// 목 모드 시연 토글 (와이어프레임 demorow). 실 모드에서는 노출하지 않는다 —
// 진행 상태는 서버 판정이라 클라이언트가 고르는 값이 아니다.
const demoState = ref<'wait' | 'live' | null>(null)

const displayState = computed<'wait' | 'live'>(() => {
  if (isMockApi() && demoState.value !== null) return demoState.value
  return live.auctionStatus === 'LIVE' ? 'live' : 'wait'
})

const roomTitle = computed(() => room.value?.title ?? '')

// BID-05 서버 권위 타이머 — endsAt + clockOffset이 단일 소스다 (규칙 5: Date.now() 단독 계산 금지).
// TIME_SYNC가 우선이고, 도착 전에는 아래에서 방 상세의 serverTime으로 세운 값을 쓴다(S15P11A704-265).
const serverOffset = computed(() => live.clockOffset)
const {
  remainingMs,
  display: remainingDisplay,
  urgent: remainingUrgent,
} = useCountdown(() => live.deadlineAt, { serverOffset })

// 시작 예정 시각도 같은 규칙으로 센다. 서버 시각 출처가 둘 다 없을 때만 '--:--'가 나온다.
const scheduledAtMs = computed(() => {
  const raw = room.value?.scheduledAt ?? null
  if (raw === null) return null
  const ms = Date.parse(raw)
  return Number.isNaN(ms) ? null : ms
})
const { display: startsInDisplay } = useCountdown(scheduledAtMs, { serverOffset })

// 대기 인원 — 소켓(LIVE-03)이 갱신하기 전에는 방 상세(REST)가 준 값이 초기값이다.
const viewerCount = computed(() => live.viewerCount ?? room.value?.viewerCount ?? null)

// 알림 신청 수 — 방 상세만 주는 값이고 갱신 이벤트는 없다. 판매자·관리자 외에는 서버가 null을 준다.
const notificationCount = computed(() => room.value?.notificationCount ?? null)

// ── 판매자 액션 (ROOM-06 시작 · BID-03 연장) ──

// 연장 상한 분모는 스토어의 MAX_EXTENSIONS 한 벌을 쓴다 (`auction_room.extension_count BETWEEN 0 AND 3`).

const actionPending = ref(false)
const actionError = ref<string | null>(null)

/**
 * 판매자(호스트)가 아님 — 방 상세의 `isSeller`가 단일 판정 근거다 (S15P11A704-326에서 신설).
 *
 * 예전에는 질문 큐 조회가 호스트 전용이라는 **성질에 얹어** 403으로 우회 판정했다.
 * 그 API의 권한 정책이 바뀌면 콘솔 가드가 조용히 풀리는 구조라 서버 플래그로 바꿨다.
 *
 * ⚠️ 상세를 못 불러온 동안(`room === null`)은 **여기서 판정하지 않는다** — 로딩 중을
 * "판매자 아님"으로 단정하면 판매자에게 안내 카드가 깜빡인다. 대신 **템플릿이 그 상태를
 * 따로 그린다**(S15P11A704-714): 모르는 동안 콘솔을 그리면 「방송 종료」가 달린 화면이
 * 비판매자에게 먼저 뜨고, 상세 조회가 실패하면 그대로 남는다. 서버가 액션마다 다시
 * 검증하므로 실제로 종료되지는 않지만, 누를 수 있는 버튼을 보여 줄 이유가 없다.
 */
/**
 * 서버가 판매자 전용 요청을 거절했다 — `isSeller`가 true인데도 403이 오는 경우.
 * 정상이라면 안 생기지만, 생겼다면 화면 판정이 아니라 **서버가 맞다.**
 */
const sellerRejectedByServer = ref(false)

const notSeller = computed(
  () => sellerRejectedByServer.value || (room.value !== null && !room.value.isSeller),
)

/**
 * 연장 응답이 알려준 잔여 횟수. 스토어(FE2 소유)에 쓰지 않고 화면이 들고 있는다.
 * 스냅샷의 `remainingExtensions`가 아직 null인 동안의 표시 근거이기도 하다.
 */
const remainingExtensionsFromResponse = ref<number | null>(null)
const remainingExtensions = computed(
  () => live.remainingExtensions ?? remainingExtensionsFromResponse.value,
)

/**
 * 남은 시간 1분 미만이면 연장 불가 (BID-03, 서버도 `EXTENSION_TOO_LATE`로 막는다).
 * 잔여 횟수를 **모르는 동안(null)은 막지 않는다** — 없는 값을 0으로 단정하면
 * 서버가 허용하는 연장을 화면이 임의로 잠그게 된다. 한도 판정은 서버(`EXTENSION_LIMIT_EXCEEDED`)가 한다.
 */
const canExtend = computed(
  () =>
    !actionPending.value &&
    (remainingExtensions.value === null || remainingExtensions.value > 0) &&
    remainingMs.value !== null &&
    remainingMs.value >= 60_000,
)

function applyActionError(error: unknown) {
  const apiError = toApiError(error)
  if (hasErrorCode(apiError, API_ERROR_CODE.NOT_AUCTION_SELLER)) {
    sellerRejectedByServer.value = true
    return
  }
  actionError.value = apiError.message
}

async function startNow() {
  if (actionPending.value) return
  actionPending.value = true
  actionError.value = null
  try {
    const started = await startAuctionRoom(auctionId.value)
    // 시작 응답이 마감 시각과 서버 시각을 **둘 다** 준다 — 소켓 TIME_SYNC를 기다리지 않고 여기서
    // 타이머를 세운다(S15P11A704-523). 이 방은 목록을 거쳐 들어온 게 아니라 `deadlineOf` 폴백도
    // 비어 있고, 그대로 두면 판매자가 「시작하기」를 누른 직후 자기 화면만 `--:--`다.
    // TIME_SYNC가 오면 그 값이 덮는다 — 여기 값은 그때까지의 임시가 아니라 같은 서버 계산값이다.
    live.$patch({ endsAt: toEpochMs(started.endAt) })
    roomStore.noteServerTime(started.serverTime)
    // 실 모드에서는 서버가 상태 전이를 이벤트로 알려준다. 목에는 그럴 주체가 없어 시연 상태만 바꾼다.
    if (isMockApi()) demoState.value = 'live'
  } catch (error) {
    applyActionError(error)
  } finally {
    actionPending.value = false
  }
}

async function extendFiveMinutes() {
  if (!canExtend.value) return
  actionPending.value = true
  actionError.value = null
  try {
    const result = await extendAuction(auctionId.value)
    remainingExtensionsFromResponse.value = result.remainingExtensions
  } catch (error) {
    applyActionError(error)
  } finally {
    actionPending.value = false
  }
}

/**
 * 방송 종료는 되돌릴 수 없다 — 한 번 더 누르게 해서 오조작을 막는다.
 * 확인 상태를 무기한 열어 두면 한참 뒤 무심코 누른 클릭이 곧바로 종료가 되므로 5초 뒤 자동으로 닫는다.
 */
const endConfirming = ref(false)
const END_CONFIRM_TIMEOUT_MS = 5_000
let endConfirmTimer: ReturnType<typeof setTimeout> | null = null

function disarmEndConfirm() {
  endConfirming.value = false
  if (endConfirmTimer !== null) {
    clearTimeout(endConfirmTimer)
    endConfirmTimer = null
  }
}

/**
 * 판매자 본인의 「방송 종료」로 이 화면을 떠나는 중인지 (S15P11A704-697).
 * 아래 마감 watcher가 같은 순간에 결과 화면으로 끌고 가지 않도록 막는 플래그다.
 */
let leavingConsole = false

async function endBroadcast() {
  if (actionPending.value) return
  if (!endConfirming.value) {
    endConfirming.value = true
    endConfirmTimer = setTimeout(disarmEndConfirm, END_CONFIRM_TIMEOUT_MS)
    return
  }
  disarmEndConfirm()
  actionPending.value = true
  actionError.value = null
  // 종료 요청을 보내는 순간부터 서버가 마감을 브로드캐스트할 수 있다. 아래 watcher가 그걸 잡아
  // 결과 화면으로 보내면 판매자를 홈으로 보내려는 이 흐름과 경합한다 — 성공 경로는 여기서 막고,
  // 실패하면 되돌려 watcher가 대신 화면을 옮기게 둔다.
  leavingConsole = true
  try {
    // **두 API를 순서대로 부른다** (S15P11A704-688). 방송 종료(LIVE-02)는 `live_broadcast`와
    // OpenVidu 세션만 닫아 경매를 건드리지 않는다 — 그것만 부르면 방송은 끊겼는데 타이머가
    // 계속 돌고 입찰도 계속 접수된다. 경매를 닫는 것은 ROOM-09다.
    //
    // 송출부터 끊는 이유: 경매가 먼저 닫히면 그 사이 시청자는 「끝난 경매의 살아 있는 방송」을
    // 본다. 반대 순서면 잠깐 소리 없는 화면일 뿐이라 덜 나쁘다.
    //
    // 다만 **송출이 실패했다고 경매까지 못 닫으면 안 된다**(S15P11A704-714 후속). `live_broadcast` 행은
    // `POST /live/publisher`가 성공해야 생기는데, 프리뷰가 준비되기 전이면 `PublisherPreview`가
    // 조용히 빠져나가 그 행이 없다 — 카메라가 안 잡힌 채로 진행한 방이 그렇다. 그 상태로 「방송 종료」를
    // 누르면 404 `LIVE_NOT_FOUND`만 뜨고 아래 두 줄이 안 돌아, **판매자가 자기 경매를 콘솔에서
    // 닫을 방법이 아예 없다.** 홈 목록에 계속 뜨고 입찰도 계속 받는다.
    //
    // 끊을 방송이 없는 것은 경매를 열어 둘 이유가 아니므로 그것만 삼킨다. 나머지 실패
    // (미디어 서버·권한·상태·네트워크)는 그대로 던진다 — 송출이 아직 살아 있을 수 있고,
    // 그 위에서 경매만 닫으면 위에 적은 「끝난 경매의 살아 있는 방송」을 직접 만드는 셈이다.
    try {
      await endLiveStream(auctionId.value)
    } catch (error) {
      if (!hasErrorCode(error, API_ERROR_CODE.LIVE_NOT_FOUND)) throw error
    }
    await endAuctionAsSeller(auctionId.value)
    if (isMockApi()) {
      leavingConsole = false
      demoState.value = 'wait'
      return
    }
    // 판매자는 홈으로 나간다. 참여자는 서버가 쏘는 `AUCTION_NO_SALE`(reason `HOST_LEFT`)을 받아
    // 각자 유찰 화면으로 간다 — 이쪽에서 해 줄 일이 없다.
    //
    // `replace`다: 뒤로가기로 돌아와도 이미 끝난 방이라 콘솔이 할 수 있는 게 없다.
    await router.replace({ name: 'home' })
  } catch (error) {
    leavingConsole = false
    applyActionError(error)
  } finally {
    actionPending.value = false
  }
}

/**
 * BID-10 낙찰 공개 — 판매자도 참여자와 같은 연출을 본다 (S15P11A704-697).
 *
 * 원래 콘솔에는 마감 처리 자체가 없었다. `displayState`가 `'wait' | 'live'` 둘뿐이라 **ENDED가
 * 대기 화면으로 떨어져서**, 방송이 끝났는데 「지금 시작하기」가 있는 화면이 떴다. 벗어나는 길도
 * 위 `endBroadcast`의 성공 처리 하나뿐이라 아래 셋은 전부 그 자리에 남았다.
 *
 * · 타이머 만료로 마감 (판매자가 콘솔에 앉아 있는 채로)
 * · 운영자 강제 종료 (DEAL-09)
 * · 「방송 종료」를 눌렀는데 `endLiveStream`이 먼저 실패 — catch로 빠져 `endAuctionAsSeller`도
 *   `router.replace`도 안 돈다. **송출만 끊기고 경매는 안 닫힌 채 대기로 보인다.**
 *   (끊을 방송이 없었던 `LIVE_NOT_FOUND`만 위에서 삼키고 지나간다 — 나머지 실패는 여전히 여기 남는다.)
 *
 * 조용히 결과 화면으로 밀어내는 대신 `LiveView`와 **같은 오버레이**를 띄운다 — 자기 물건이 얼마에
 * 팔렸는지가 판매자에게 가장 중요한 순간인데, 그동안 그 자리가 통째로 비어 있었다.
 */
const reveal = useRevealState()

/**
 * 소켓 종료 이벤트 → 오버레이. `LiveView`와 같은 이유로 **`immediate`를 붙이지 않는다** —
 * 붙이면 setup 시점에 스토어에 남아 있는 이전 방의 종료 이벤트를 집는다.
 *
 * 판매자 본인의 「방송 종료」 중에는 건너뛴다. 그쪽은 홈으로 나가는 것이 확정된 흐름이라
 * 오버레이가 한 프레임 스쳤다 사라진다.
 */
watch(
  () => live.endEvent,
  (event) => {
    if (event !== null && !leavingConsole) reveal.apply(event)
  },
)

/**
 * 마감 캐치업 — 종료 이벤트를 통째로 놓친 경우.
 *
 * 정산 순간 STOMP가 끊겨 있었으면 오버레이를 띄울 재료가 없다. 재접속 스냅샷(WS-03)이
 * `auctionStatus`를 'ENDED'로 복원하는 것만 남으므로, 그때는 결과 화면으로 바로 보낸다.
 * `endEvent`가 있으면 위 오버레이가 처리하므로 여기서 빠진다 — `LiveView`와 같은 분업이다.
 * 유찰이면 `ResultView`가 `nosale`로 다시 replace한다.
 */
watch(
  () => live.auctionStatus,
  (status) => {
    if (live.endEvent !== null || status !== 'ENDED' || leavingConsole) return
    void router.replace({ name: 'result', params: { id: String(auctionId.value) } })
  },
)

/** 「자세히 보기」 — 낙찰이면 결과, 유찰·강제 종료면 유찰 화면 */
function goResultDetail() {
  reveal.dismiss()
  void router.replace({
    name: reveal.detailRouteName.value,
    params: { id: String(auctionId.value) },
  })
}

/**
 * 「창 닫기」·「나가기」 — 둘 다 홈이다.
 *
 * 참여자는 창을 닫고 방에 남아 채팅·결과를 마저 볼 수 있지만, **콘솔에는 끝난 방에 남아 할 일이
 * 없다** — 오버레이를 걷으면 그 아래는 「지금 시작하기」가 있는 대기 화면이고, 그게 이 티켓에서
 * 고치려던 바로 그 화면이다. `endBroadcast`도 같은 판단으로 홈에 보낸다.
 */
function leaveConsole() {
  reveal.dismiss()
  void router.replace({ name: 'home' })
}

// ── ROOM-04 방 삭제 (콘솔 두 진입점이 공유) ──

const {
  confirming: deleteConfirming,
  pending: deletePending,
  error: deleteError,
  requestDelete,
} = useRoomDelete(() => auctionId.value)

// ── CHAT-06 질문 큐 ──
//
// 질문은 채팅 버퍼가 아니라 전용 API가 소스다 — 식별자(`questionId` ULID)도 다르고, 조회는 PENDING만 돌려준다.
// REST로 시드한 뒤 소켓 /questions(QUESTION_ADDED/REMOVED)가 실시간 유지한다 — 단일 소스는 live.questions(S15P11A704-364).
// 답변 완료(`resolve`)도 조회와 같은 403(NOT_AUCTION_HOST)을 쓰므로 composable이 함께 들고 있다(S15P11A704-680).

const {
  error: questionError,
  loadingMore: questionsLoadingMore,
  hasMore: hasMoreQuestions,
  lastAppended: lastAppendedQuestions,
  resolving: resolvingQuestionIds,
  load: loadQuestions,
  loadMore: loadMoreQuestions,
  resolve: markAnswered,
} = useQuestionQueue(
  () => auctionId.value,
  live,
  () => {
    sellerRejectedByServer.value = true
  },
)

/**
 * 「더 보기」 결과를 스크린리더에 알린다 (S15P11A704-371).
 * 버튼을 눌러도 붙은 항목이 화면 아래에 조용히 늘 뿐이라, 비시각 사용자에겐 아무 일도 안 난 것처럼 들린다.
 * 총 건수를 함께 넣는 이유: 같은 건수가 연달아 붙으면 문구가 안 바뀌어 리더가 통지를 건너뛴다.
 */
const questionAnnouncement = ref('')

async function onLoadMoreQuestions() {
  await loadMoreQuestions()
  if (questionError.value !== null) return
  if (lastAppendedQuestions.value > 0) {
    questionAnnouncement.value = `질문 ${lastAppendedQuestions.value}건을 더 불러왔어요 · 지금 ${live.questions.length}건`
    return
  }
  // 0건이어도 뜻이 둘이다. 뒤에 더 남았는데 그 페이지가 이미 다 들어와 있던 경우와,
  // 정말 끝인 경우 — 전자에 「더 불러올 질문이 없어요」라고 하면 버튼이 그대로 있는 것과 모순된다.
  questionAnnouncement.value = hasMoreQuestions.value
    ? '새로 불러온 질문은 없어요'
    : '더 불러올 질문이 없어요'
}

// 재연결 시 질문 큐 재시드 — /questions 구독은 onConnect마다 다시 걸리지만 REST 시드는 마운트 1회뿐이라,
// 끊긴 사이 놓친 QUESTION_ADDED/REMOVED 구간이 영영 빈다(메인 토픽의 스냅샷 재생 같은 장치가 질문 큐엔 없음, 리뷰 1).
// 시드가 dedupe 병합이라 재실행이 안전하다. 'reconnecting'→'connected' 전이에만 걸어 첫 연결의 중복 로드를 피한다.
watch(
  () => live.connectionStatus,
  (status, prev) => {
    if (status === 'connected' && prev === 'reconnecting') void loadQuestions()
  },
)

/** 마운트가 끝나기 전에 화면을 떠났는지. 그 경우 뒤늦게 붙은 연결을 되돌려야 한다 */
let cancelled = false

onMounted(async () => {
  // 연결을 첫 await 앞에서 시작한다 — 방 상세를 기다리는 동안 지표가 비어 있지 않게.
  // 정리는 **연결 프라미스에만** 건다. 질문 큐(실 모드에서 최대 5번 순차 요청)까지 기다리면
  // 그 시간만큼 뷰 없는 소켓이 스토어를 갱신하고, 다음 화면이 그 값을 읽는다.
  // 질문 큐 구독은 콘솔(판매자)에서만 켠다 — BE 인가가 판매자 전용이라 참가자 화면이 켜면
  // SUBSCRIPTION_DENIED만 돌아온다(S15P11A704-541).
  // 단, 비판매자가 콘솔 URL을 직접 열면 여기서도 한 번 나간다 — 콘솔 라우트에 진입 가드가 없고
  // connect가 방 상세(판매자 판정) 응답보다 먼저라, SUBSCRIPTION_DENIED 개인 에러 1건을 받는다.
  // 무해하다(화면은 「판매자만」 카드로 막히고 warn 1줄) — 라우트 가드가 생기기 전까지 남는 경로다.
  const connecting = live.connect(auctionId.value, { questions: true })
  void connecting.then(() => {
    if (cancelled) void live.disconnect()
  })

  try {
    room.value = await getAuctionRoom(auctionId.value)
    // 콘솔은 방 상세를 스토어를 거치지 않고 직접 받는다(캐시가 아니라 매번 최신이어야 하는 화면).
    // 그래서 응답의 serverTime이 `room.serverOffset`에 안 들어가고, TIME_SYNC가 오기 전까지
    // clockOffset이 비어 타이머가 '--:--'로 굳었다 — 여기서 넘겨준다(S15P11A704-265).
    roomStore.noteServerTime(room.value.serverTime)
  } catch (error) {
    loadError.value = toApiError(error).message
  }
  await Promise.all([connecting, loadQuestions()])
})

onUnmounted(() => {
  cancelled = true
  if (endConfirmTimer !== null) clearTimeout(endConfirmTimer)
  // 소켓 종료가 끝난 뒤 초기화한다 — 나란히 두면 소켓이 살아 있는 사이 `$reset`이 먼저 돌아,
  // 초기화된 스토어로 이벤트가 dispatch되고 `connectionStatus`도 뒤늦게 덮인다.
  // `LiveView`(355)·`WaitingRoomView`(347)와 같은 모양이다 (S15P11A704-367).
  //
  // 예전에는 여기만 `$reset()`을 동기로 먼저 불렀다. 프라미스 뒤에 붙이면 그사이 마운트된
  // 다음 화면의 상태를 뒤늦게 지운다는 이유였는데, 아래 **입장 캡처 가드**가 그걸 막는다.
  //
  // ⚠️ `roomId` 대조로는 부족하다 — 이 화면의 「라이브로 이동」이 **같은 방** 라이브로 가서
  //    번호가 같다. 그래서 입장 일련번호(`roomSession`)를 본다 (S15P11A704-367).
  const leaving = live.roomSession
  void live.disconnect().then(() => {
    // 다음 화면이 이미 입장했으면(같은 방이어도) 건드리지 않는다. 그쪽 `connect()`가
    // `joinRoom()`에서 이미 초기화했으므로 여기서 또 지울 이유도 없다.
    if (leaving !== live.roomSession) return
    live.$reset()
  })
})
</script>

<template>
  <!-- 세로 여백을 조인다 (S15P11A704-691) — 진행 중에 스크롤해서 지표를 확인하게 두지 않는다.
       이 화면은 방송하는 동안 계속 띄워 두는 곳이라 한 화면에 들어오는 것이 밀도보다 중요하다. -->
  <main class="bg-cream min-h-screen px-6 py-6">
    <!-- 판매자 아님 — 방 상세의 `isSeller`가 판정한다 (S15P11A704-326) -->
    <div v-if="notSeller" class="mx-auto w-full max-w-[560px] py-20 text-center">
      <p class="text-ink text-lg font-bold">이 방의 판매자만 진행 콘솔을 쓸 수 있어요</p>
      <p class="text-dim mt-2 text-[13px]">구매자 화면에서 참여할 수 있습니다.</p>
      <RouterLink
        :to="{ name: 'live', params: { id: auctionId } }"
        class="bg-ink text-on-dark mt-5 inline-block rounded-xl px-5 py-3 text-sm font-semibold"
      >
        라이브로 이동
      </RouterLink>
    </div>

    <!-- 판매자인지 **아직 모른다** (S15P11A704-714) — 방 상세 응답 전이거나 조회가 실패했다.
         모르는 동안 콘솔을 그리면 비판매자에게도 「방송 종료」가 달린 화면이 먼저 뜨고,
         실패하면 `room`이 계속 null이라 그 화면이 그대로 남는다. 여기서 멈춘다.
         조회 실패 문구도 이 자리다 — 콘솔 안에 두면 콘솔을 그려야만 보인다. -->
    <div v-else-if="room === null" class="mx-auto w-full max-w-[560px] py-20 text-center">
      <template v-if="loadError">
        <p class="text-ink text-lg font-bold" role="alert">방 정보를 불러오지 못했어요</p>
        <p class="text-dim mt-2 text-[13px]">{{ loadError }}</p>
      </template>
      <p v-else class="text-faint text-[13px]">불러오는 중이에요…</p>
    </div>

    <div v-else class="mx-auto w-full max-w-[1180px]">
      <h1 class="text-ink mb-3 text-[22px] font-bold tracking-tight">진행 콘솔</h1>

      <p
        v-if="consoleNotice"
        class="bg-slate-tint text-slate-tint-text mb-4 rounded-xl px-4 py-3 text-[13px]"
      >
        {{ consoleNotice }}
      </p>

      <!--
        열 구성 (S15P11A704-690): 왼쪽은 **카메라 → 지표·액션·질문** 세로, 오른쪽은 채팅 한 장이
        열 전체 높이로 선다. 방송 중 판매자가 가장 오래 보는 것이 자기 화면과 채팅이라 둘을
        가장 큰 자리에 둔다.

        ⚠️ **와이어프레임 이탈이다.** `data-screen="console"`은 왼쪽 지표 + 오른쪽 프리뷰 배치이고
        채팅 영역 자체가 없다. 팀 요청에 따른 재배치.

        우측 열 `400px`는 그대로 뒀다 — 채팅 한 줄이 읽히는 폭이고, 라이브 화면의 채팅 레일(344px)과
        비슷한 자리다. 프리뷰가 왼쪽으로 가면서 이 폭이 프리뷰 크기를 정하던 관계는 끊어졌다.
      -->
      <div class="grid gap-4 lg:grid-cols-[1fr_400px] lg:items-start">
        <div class="flex min-w-0 flex-col gap-4">
          <!-- 카메라 카드. 프리뷰 옆 자리를 **상태·제목·진행 제어가 채운다**(S15P11A704-691) —
               프리뷰가 16:9라 폭을 카드만큼 주면 높이가 그만큼 따라오고, 좁게 잡으면 오른쪽이 빈다.
               둘을 나란히 두면 그 여백이 사라지면서 화면 높이도 같이 줄어든다. -->
          <aside class="bg-surface border-line rounded-2xl border p-4">
            <BroadcastSlot
              :state="displayState"
              :pending="actionPending"
              :confirming="endConfirming"
              @end="endBroadcast"
            >
              <template #status>
                <div class="flex items-center gap-2">
                  <span
                    v-if="displayState === 'live'"
                    class="bg-terra text-on-dark inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold"
                  >
                    <span class="bg-on-dark h-1.5 w-1.5 rounded-full" aria-hidden="true" />LIVE
                  </span>
                  <span
                    v-else
                    class="bg-slate-tint text-slate-tint-text rounded-full px-2.5 py-1 text-[11px] font-semibold"
                  >
                    대기
                  </span>
                  <span class="text-ink truncate text-base font-bold tracking-tight">
                    {{ roomTitle }}
                  </span>
                </div>

                <!-- 라이브 액션 — 연장은 방송 제어라 종료 버튼과 같은 자리에 둔다 -->
                <div v-if="displayState === 'live'" class="mt-4">
                  <div class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                    <button
                      type="button"
                      class="bg-terra text-on-dark hover:bg-terra-d rounded-xl px-4.5 py-2.5 text-[13.5px] font-semibold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45"
                      :disabled="!canExtend"
                      @click="extendFiveMinutes"
                    >
                      ＋ 방송 5분 연장
                    </button>
                    <span class="text-faint text-[12.5px] tabular-nums">
                      잔여
                      <b class="text-ink">
                        {{ remainingExtensions ?? '—' }} / {{ MAX_EXTENSIONS }}회
                      </b>
                    </span>
                  </div>
                  <p class="text-faint mt-1.5 text-[11.5px]">
                    ※ 남은 시간 1분 미만에는 연장할 수 없어요
                  </p>
                </div>

                <!-- 대기 액션 — 카메라를 확인하고 그대로 시작하는 흐름이라 프리뷰 옆이 제자리다.
                     「방 삭제」는 여기 하나뿐이다(S15P11A704-691): BroadcastSlot이 그리던 같은 뜻의
                     「방 삭제하기」를 없앴다. 되돌릴 수 없는 동작의 진입점이 한 화면에 둘 있으면 안 된다. -->
                <div v-else class="mt-4 sm:mt-auto">
                  <div class="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      class="bg-terra text-on-dark hover:bg-terra-d rounded-xl px-4.5 py-2.5 text-[13.5px] font-semibold transition active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-45"
                      :disabled="actionPending"
                      @click="startNow"
                    >
                      지금 시작하기
                    </button>
                    <RouterLink
                      :to="`/rooms/${auctionId}/edit`"
                      class="border-line text-dim rounded-xl border px-3.5 py-2.5 text-[13px] font-semibold transition"
                    >
                      방 수정
                    </RouterLink>
                    <button
                      type="button"
                      class="rounded-xl border px-3.5 py-2.5 text-[13px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-45"
                      :class="deleteConfirming ? 'border-red text-red' : 'border-line text-dim'"
                      :disabled="deletePending"
                      @click="requestDelete"
                    >
                      {{ deleteConfirming ? '한 번 더 누르면 삭제' : '방 삭제' }}
                    </button>
                  </div>
                  <p class="text-faint mt-1.5 text-[11.5px]">
                    ※ 예정 시각 전에도 시작할 수 있어요 · 방 수정·삭제는 시작 전에만 가능해요
                  </p>
                </div>
              </template>
            </BroadcastSlot>
          </aside>

          <section class="bg-surface border-line rounded-2xl border p-4">
            <ConsoleMetrics
              :state="displayState"
              :bid-count="live.bidCount"
              :participant-count="live.participantCount"
              :viewer-count="viewerCount"
              :notification-count="notificationCount"
              :remaining-display="remainingDisplay"
              :remaining-urgent="remainingUrgent"
              :starts-in-display="startsInDisplay"
            />

            <p v-if="actionError" class="text-red mt-3 text-[12px]" role="alert">
              {{ actionError }}
            </p>
            <p v-if="deleteError" class="text-red mt-2 text-[12px]" role="alert">
              {{ deleteError }}
            </p>

            <QuestionQueue
              :items="live.questions"
              :resolving-ids="resolvingQuestionIds"
              :has-more="hasMoreQuestions"
              :loading-more="questionsLoadingMore"
              :announcement="questionAnnouncement"
              @answer="markAnswered"
              @more="onLoadMoreQuestions"
            />
            <p v-if="questionError" class="text-red mt-2 text-[11.5px]" role="alert">
              {{ questionError }}
            </p>
          </section>
        </div>

        <!-- 실시간 채팅 (S15P11A704-685) — 질문 큐만으로는 방송 중 반응이 안 보인다는 요청.
             **데이터 배선은 원래 있었다.** `/topic/auctions/{id}/chat` 구독은 `connect()`의 옵션과
             무관하게 항상 걸리고(`liveSocket.ts`), 스토어도 채팅 이력·전송·실패 통지를 모드 무관으로
             세팅한다 — 콘솔은 그걸 받아만 두고 그리지 않던 상태였다.

             높이: `ChatPanel` 루트가 `h-full`이라 높이를 주는 부모가 없으면 접힌다.
             lg에서는 **그리드가 늘려 준다** — 그리드 아이템 기본이 stretch라 왼쪽 열 높이를 그대로
             받는다(690에서 이 열을 채팅 한 장으로 비우면서 가능해졌다). 다만 대기 상태처럼 왼쪽이
             짧을 때를 대비해 바닥값을 둔다. lg 미만은 한 열로 쌓여 늘려 줄 기준이 없으므로
             라이브(`LiveLayout`)와 같은 고정 높이를 쓴다.

             `can-pin`·`can-delete`는 방 상세의 `isSeller`로 가른다(라이브 화면과 같은 판정).
             상세를 아직 못 받았으면 false다 — 모르는 채로 켜면 서버가 403으로 되돌린다.
             운영자(ADMIN)는 넓히지 않는다: 콘솔은 판매자 화면이고 운영자가 지울 자리는 라이브다. -->
        <!-- ChatPanel의 메시지 목록은 내부에서 스크롤되므로, 부모 높이를 고정해 누적 채팅이 페이지를 늘리지 않게 한다. -->
        <div class="h-[400px] lg:h-[420px]">
          <!-- `can-ask-question`을 끈다 — 질문 큐를 받는 쪽이 판매자라 자기 메시지를 자기 큐에
                 넣는 동작에 뜻이 없다. 반대로 `highlight-questions`는 켠다: 어느 채팅이 질문으로
                 올라갔는지는 같은 화면의 질문 큐와 대조하며 봐야 한다. -->
          <ChatPanel
            :room-id="auctionId"
            highlight-questions
            :can-ask-question="false"
            :can-pin="room?.isSeller ?? false"
            :can-delete="room?.isSeller ?? false"
          />
        </div>
      </div>

      <div v-if="isMockApi()" class="mt-6 flex items-center gap-2">
        <span class="text-faint text-[11px]">시연</span>
        <button
          type="button"
          class="border-line rounded-lg border px-3 py-1.5 text-[12px] transition"
          :class="displayState === 'wait' ? 'bg-ink text-on-dark' : 'text-dim'"
          @click="demoState = 'wait'"
        >
          대기방
        </button>
        <button
          type="button"
          class="border-line rounded-lg border px-3 py-1.5 text-[12px] transition"
          :class="displayState === 'live' ? 'bg-ink text-on-dark' : 'text-dim'"
          @click="demoState = 'live'"
        >
          라이브
        </button>
      </div>
    </div>
  </main>

  <!-- BID-10 낙찰 공개. 라이브 화면과 같은 오버레이를 화면 전체 위에 얹는다 (S15P11A704-697).
       `my-hold-amount`는 넘기지 않는다 — 판매자는 입찰자가 아니라 홀드가 없고, 그 자리는
       "내 홀드가 풀린다"는 개인 안내라 판매자에게는 할 말이 아니다. -->
  <RevealOverlay
    v-if="reveal.phase.value !== 'hidden'"
    :phase="reveal.phase.value"
    :bid-count="live.bidCount ?? 0"
    :winner-label="reveal.winnerLabel.value"
    :final-price="reveal.finalPrice.value"
    :highest-bid-amount="reveal.highestBidAmount.value"
    :reason-text="reveal.reasonText.value"
    :reserve-price="room?.reservePrice ?? null"
    @close="leaveConsole"
    @leave="leaveConsole"
    @detail="goResultDetail"
  />
</template>
