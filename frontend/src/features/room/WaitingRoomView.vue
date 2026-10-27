<script setup lang="ts">
// ROOM-07 대기방 (FE1) — 디자인 원본 `data-screen="wait"`.
//
// 이 화면이 지는 책임은 셋이다.
//   ① ROOM-07 join 호출 → `permissions`·`destinations`·`scheduledAt`으로 화면을 구성한다.
//      권한을 화면이 지어내지 않고 **서버 응답 그대로** 반영하는 것이 요점이다.
//   ② 입찰·예측은 잠근다 (명세: "대기방에서는 입찰과 예측 베팅을 비활성화한다").
//      숨기지 않고 안내로 덮는다 — docs/03 §4-1.
//   ③ `AUCTION_STARTED` 수신 → 전원 라이브 전환 (S15P11A704-65).
//      `endAt`은 live 스토어(`endsAt`)로 넘겨 서버 권위 타이머를 잇는다 (BID-05).
//
// **자동 시작은 없다.** `scheduledAt`은 안내용이고(명세 「대기방 입장 및 상태 조회」 검증 규칙),
// 실제 시작은 판매자의 ROOM-05 호출뿐이다. 그래서 카운트다운이 0이 되어도 화면은 아무것도 하지 않는다.
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getAuctionRoom,
  joinAuctionRoom,
  type AuctionRoomDetail,
  type JoinAuctionRoomResponse,
} from '@/shared/api/rooms'
import { API_ERROR_CODE } from '@/shared/api/errors'
import { isMockApi } from '@/shared/api/mock'
import { toEpochMs } from '@/shared/api/types'
import { extractApiErrorCode } from '@/shared/lib/apiError'
import { cssUrl } from '@/shared/lib/cssUrl'
import { formatPoint } from '@/shared/lib/point'
import { useLiveStore } from '@/stores/live'
import { offsetFrom } from '@/stores/room'
import {
  MOCK_DURATION_MINUTES,
  seedMockServerClock,
  makeMockJoinError,
  subscribeMockWaitingRoom,
  type MockWaitingRoomSubscription,
  type WaitingRoomEvent,
} from '@/mocks/waitingRoom'
import WaitingStage from './WaitingStage.vue'
import WaitingLockedDock from './WaitingLockedDock.vue'
import ReportModal from '@/features/report/ReportModal.vue'
import { useAuctionReport } from '@/features/report/useAuctionReport'
import ChatPanel from '@/features/chat/ChatPanel.vue'
import {
  dispatchAuctionTopic,
  dispatchChatTopic,
  handleViewerCount,
} from '@/features/live/socket/handlers'

const route = useRoute()
const router = useRouter()
const live = useLiveStore()

const auctionId = computed(() => Number(route.params.id) || 0)

type Phase = 'loading' | 'waiting' | 'error'
const phase = ref<Phase>('loading')

const join = ref<JoinAuctionRoomResponse | null>(null)
const detail = ref<AuctionRoomDetail | null>(null)

/** 서버 오류 코드. 문구 분기의 유일한 근거다 */
const errorCode = ref<string | null>(null)
/** 서버가 준 문장 — 코드에 매칭되는 안내가 없을 때 그대로 보여 준다 */
const errorMessage = ref('')

// 대기 인원(= 활성 세션 수, VIEWER_COUNT/LIVE-03)은 이제 live 스토어(viewerCount)가 보유한다(S15P11A704-212).
// 로컬 ref를 없애고 스토어를 단일 소스로 둔다 — 대기방↔라이브 전환에도 값이 이어진다.

/** `AUCTION_STARTED`를 받아 라이브로 넘어가는 중 — 중복 트리거와 스토어 초기화를 막는다 */
const transitioning = ref(false)

let subscription: MockWaitingRoomSubscription | null = null
let transitionTimer: ReturnType<typeof setTimeout> | null = null
/**
 * 언마운트 여부. `enter()`가 await로 멈춘 사이 라우트를 벗어나면 `onUnmounted`가 **먼저** 돌기
 * 때문에, 재개된 `enter()`가 정리 주체 없는 구독을 새로 만들고 뒤이어 `AUCTION_STARTED`가
 * 도착하면 이미 떠난 방으로 사용자를 되돌려 보낸다. 그 경로를 막는 플래그다.
 */
let disposed = false

const scheduledAtMs = computed(() =>
  toEpochMs(join.value?.scheduledAt ?? detail.value?.scheduledAt),
)
const reservePrice = computed(() => detail.value?.reservePrice ?? 0)
const durationMinutes = computed(() => detail.value?.durationMinutes ?? MOCK_DURATION_MINUTES)
const productTitle = computed(() => detail.value?.product.title ?? detail.value?.title ?? '')
const productImage = computed(() => detail.value?.product.imageUrl || null)
const sellerName = computed(() => detail.value?.seller.nickname ?? '')

/**
 * DEAL-04 신고. 판매자에게는 버튼을 내지 않는다 — 서버가 403
 * `OWN_AUCTION_REPORT_NOT_ALLOWED`로 막지만, 누를 수 있게 두고 거절하는 것보다 낫다.
 * 판정 근거는 `isSeller`뿐이다(`seller.nickname`은 마스킹돼 비교할 수 없다).
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
const canReport = computed(() => detail.value !== null && !detail.value.isSeller)

/** 서버 권한. 응답이 없으면 "아무것도 못 한다"가 안전한 기본값이다 */
const permissions = computed(
  () => join.value?.permissions ?? { chat: false, bid: false, prediction: false },
)

/** 대기 채팅 안내 — 권한 있으면 문맥 안내, 없으면 잠금 문구로 대신한다 */
const chatHint = computed(() =>
  permissions.value.chat
    ? '시작 전에도 질문을 남길 수 있어요 · 부적절한 채팅은 자동 검열됩니다'
    : undefined,
)

/** 시연 컨트롤은 목 모드에서만. 실 서버가 붙으면 판매자 콘솔의 시작 버튼이 그 자리를 대신한다 */
const showDemoControls = computed(() => isMockApi() && phase.value === 'waiting')

// ── 오류 문구 ──

const ERROR_TITLE: Record<string, string> = {
  [API_ERROR_CODE.ROOM_ACCESS_DENIED]: '이 방에는 입장할 수 없어요',
  [API_ERROR_CODE.AUCTION_ALREADY_ENDED]: '이미 종료된 경매예요',
  [API_ERROR_CODE.AUCTION_ROOM_NOT_FOUND]: '방을 찾을 수 없어요',
  [API_ERROR_CODE.AUCTION_NOT_FOUND]: '방을 찾을 수 없어요',
  [API_ERROR_CODE.UNAUTHORIZED]: '로그인이 필요해요',
  [API_ERROR_CODE.NETWORK_ERROR]: '서버에 연결하지 못했어요',
}

const ERROR_DESCRIPTION: Record<string, string> = {
  [API_ERROR_CODE.ROOM_ACCESS_DENIED]:
    '회원 상태나 방 정책 때문에 입장이 제한됐어요. 자세한 사유는 고객센터로 문의해 주세요.',
  [API_ERROR_CODE.AUCTION_ALREADY_ENDED]:
    '종료된 경매방에는 새로 입장할 수 없어요. 결과는 결과 화면에서 확인할 수 있어요.',
  [API_ERROR_CODE.AUCTION_ROOM_NOT_FOUND]: '삭제됐거나 잘못된 주소예요.',
  [API_ERROR_CODE.AUCTION_NOT_FOUND]: '삭제됐거나 잘못된 주소예요.',
  [API_ERROR_CODE.UNAUTHORIZED]: '대기방 입장은 로그인 후에 가능해요.',
  [API_ERROR_CODE.NETWORK_ERROR]: '네트워크 상태를 확인하고 다시 시도해 주세요.',
}

const errorTitle = computed(() => ERROR_TITLE[errorCode.value ?? ''] ?? '대기방을 열지 못했어요')
const errorDescription = computed(
  () =>
    (ERROR_DESCRIPTION[errorCode.value ?? ''] ?? errorMessage.value) ||
    '잠시 후 다시 시도해 주세요.',
)
/** 종료된 방은 되돌아갈 곳이 결과 화면이다 */
const showResultLink = computed(() => errorCode.value === API_ERROR_CODE.AUCTION_ALREADY_ENDED)
const showRetry = computed(
  () =>
    errorCode.value !== API_ERROR_CODE.AUCTION_ALREADY_ENDED &&
    errorCode.value !== API_ERROR_CODE.ROOM_ACCESS_DENIED,
)

function failWith(error: unknown) {
  errorCode.value = extractApiErrorCode(error)
  errorMessage.value = error instanceof Error ? error.message : ''
  phase.value = 'error'
}

// ── 소켓 이벤트 (지금은 목 드라이버) ──

/**
 * 실소켓 전환 시 그대로 재사용할 핸들러 (docs/03 §6-6).
 * 이벤트 모양은 `{ type, data }` 래핑이 전제다 (AGENTS.md 규칙 9).
 */
function handleRoomEvent(event: WaitingRoomEvent) {
  switch (event.type) {
    case 'AUCTION_STARTED':
      // 실 핸들러로 → auctionStatus='LIVE' + endsAt 세팅. 전환은 아래 watch가 감지한다(실 소켓 경로와 단일화).
      dispatchAuctionTopic(live, event)
      break
    case 'VIEWER_COUNT':
      handleViewerCount(live, event.data) // LIVE-03 → 스토어 viewerCount(202 핸들러 재사용)
      break
    case 'CHAT_MESSAGE':
      // 대기 채팅도 실 핸들러로 → live.chat 갱신(ChatPanel이 구독). 실소켓 destination은 /chat.
      dispatchChatTopic(live, event)
      break
  }
}

/**
 * ROOM-05 → 대기방 전원 라이브 전환 (S15P11A704-65).
 *
 * 트리거는 `auctionStatus === 'LIVE'` **단일 소스**다 — 목 드라이버(handleRoomEvent)와 실 STOMP
 * (connectWaitingRoom → connectLiveSocket → handleAuctionStarted)가 모두 이 상태를 세팅하므로,
 * 화면은 소스와 무관하게 이 watch 하나로 전환한다(S15P11A704-270).
 * `endsAt`은 handleAuctionStarted가 서버 권위 `endAt`으로 이미 세팅했다(BID-05, 규칙 5 — 재계산 금지).
 */
watch(
  () => live.auctionStatus,
  (status) => {
    if (status !== 'LIVE' || transitioning.value) return
    transitioning.value = true
    // 화면이 왜 바뀌는지 먼저 알리고 넘어간다 (디자인 원본도 토스트 후 650ms 뒤 전환한다)
    transitionTimer = setTimeout(() => {
      void router.replace({ name: 'live', params: { id: String(auctionId.value) } })
    }, 700)
  },
)

// ── 진입 ──

async function enter() {
  // 방 입장(재진입·재시도 포함)마다 이전 방/세션의 채팅·상태를 비운다 — 재입장 시 시드 채팅 중복 방지(리뷰 반영)
  live.joinRoom(auctionId.value)
  phase.value = 'loading'
  errorCode.value = null
  errorMessage.value = ''

  // 재시도로 다시 들어올 수 있다 — 이전 구독이 남아 있으면 먼저 끊는다
  subscription?.unsubscribe()
  subscription = null

  let joined: JoinAuctionRoomResponse
  try {
    // 상세(ROOM-03)는 화면 표시용이라 실패해도 입장을 막지 않는다 — join만 치명적이다.
    const [joinResult, detailResult] = await Promise.allSettled([
      joinAuctionRoom(auctionId.value),
      getAuctionRoom(auctionId.value),
    ])
    if (joinResult.status === 'rejected') throw joinResult.reason
    joined = joinResult.value
    if (disposed) return
    detail.value = detailResult.status === 'fulfilled' ? detailResult.value : null
  } catch (error) {
    if (disposed) return
    failWith(error)
    return
  }

  join.value = joined

  // 대기방 카운트다운의 서버 시각 기준 (명세 2026-07-29 개정으로 join이 `serverTime`을 준다).
  // 실 모드에서 소켓 TIME_SYNC가 오기 전 **가장 정확한** 출처다 — TIME_SYNC가 도착하면 더 최신
  // 값으로 덮인다. join이 이 필드를 빼고 오면(선택 필드다) 목록에서 받아둔 `room.serverOffset`이
  // `live.clockOffset`의 폴백으로 서고, 그것마저 없을 때만 '시작까지'가 `--:--`다(S15P11A704-265).
  // (목 모드는 아래 `seedMockServerClock`이 0으로 채운다 — 목엔 TIME_SYNC가 아예 없다.)
  const joinOffset = offsetFrom(joined.serverTime)
  if (joinOffset !== null) live.$patch({ serverOffset: joinOffset })

  // 상태별 분기 — join 응답의 `roomStatus`가 단일 소스다.
  // 이미 시작된 방의 /waiting URL을 열었거나(공유 링크·새로고침) 종료된 방이면 제자리로 보낸다.
  if (joined.roomStatus === 'LIVE') {
    void router.replace({ name: 'live', params: { id: String(auctionId.value) } })
    return
  }
  if (joined.roomStatus === 'ENDED') {
    void router.replace({ name: 'result', params: { id: String(auctionId.value) } })
    return
  }

  phase.value = 'waiting'

  // 대기 인원 초기값은 상세의 `viewerCount`. 이후로는 VIEWER_COUNT 이벤트가 덮는다.
  // 스토어 시드도 갱신과 같은 handleViewerCount로 통일한다 — 진입점이 하나라 실소켓 전환 때 seam이 갈리지 않는다(리뷰 1).
  // 상세에 값이 있을 때만 시드하고, 없으면 null("아직 모름")을 유지한다 — 0("아무도 없음")으로 덮지 않는다(리뷰 2).
  const seedViewerCount = detail.value?.viewerCount ?? 0
  if (detail.value?.viewerCount != null) {
    handleViewerCount(live, { viewerCount: detail.value.viewerCount })
  }

  if (isMockApi()) {
    // 목 모드엔 소켓이 없어 TIME_SYNC가 오지 않는다 — serverOffset이 null로 남으면 카운트다운이 계산되지
    // 않아 칩이 빈 값이 된다(S15P11A704-448). 목은 서버 시각 = 로컬 시각이라 오프셋 0이 사실이다.
    // 시드는 구독보다 먼저 — 첫 렌더부터 '시작까지'가 뜨게 한다.
    seedMockServerClock(live)
    // 목 모드 — 대기방 전용 드라이버가 채팅·시청자·시작 이벤트를 흘린다(전송은 낙관 반영, 시연용).
    // join 응답의 destination으로 구독한다 — 실 STOMP로 바뀌어도 이 호출부가 그대로 남는다.
    // `/viewers`는 이제 join 응답이 직접 준다 — 명세 2026-07-29 개정에 추가됐고 서버가 구현했다
    // (S15P11A704-471). 예전에는 응답에 없어 `destinations.room`에서 파생시켰다.
    const roomDestination = joined.destinations?.room ?? `/topic/auctions/${auctionId.value}`
    subscription = subscribeMockWaitingRoom(
      {
        room: roomDestination,
        viewers: joined.destinations?.viewers ?? `${roomDestination}/viewers`,
      },
      handleRoomEvent,
      { seedViewerCount },
    )
  } else {
    // 실 모드 — 라이브와 같은 STOMP 토픽에 연결(채팅 송수신·AUCTION_STARTED·viewers, S15P11A704-270).
    // 스냅샷(WS-03)이 viewerCount·pinnedNotice·muted를 권위 있게 덮으므로 위 시드는 목 모드용이다.
    await live.connectWaitingRoom(auctionId.value)
    if (disposed) void live.disconnect() // await 사이 이탈 시 방금 연 연결을 정리(누수 방지)
  }
}

onMounted(() => {
  // roomId 확정·상태 초기화는 enter()의 joinRoom이 담당한다(재시도 경로도 같이 커버)
  void enter()
})

onUnmounted(() => {
  disposed = true
  if (transitionTimer !== null) clearTimeout(transitionTimer)
  transitionTimer = null
  subscription?.unsubscribe()
  subscription = null

  // 이 화면의 **입장**을 캡처해 둔다 — 지연 $reset()이 실행될 때 그 입장이 아직 살아 있는지 확인해,
  // A→B 전환에서 A의 지연 $reset()이 B의 joinRoom() 뒤에 도착해 B를 지우는 경합을 막는다(S15P11A704-347).
  // 방 번호가 아니라 입장 번호인 이유: 같은 방을 다시 여는 경로에서는 roomId가 같아 대조가 통과한다(S15P11A704-367).
  const leaving = live.roomSession

  if (!isMockApi()) {
    // 실 모드: 전환·이탈 어느 경로든 이 화면이 연 STOMP 연결을 직접 닫는다 — 연결을 라이브와 나눠 갖지 않아
    // 누수(재연결 루프·$reset된 스토어로의 dispatch)를 만들지 않는다(리뷰 1). 라이브는 자기 연결을 새로 연다(299).
    // 이탈이면 소켓 종료(disconnect 완료) 후 $reset해 경합을 없앤다(리뷰 2). 전환이면 endsAt 핸드오프를 위해 상태를 남긴다.
    if (transitioning.value) void live.disconnect()
    else
      void live.disconnect().then(() => {
        if (leaving === live.roomSession) live.$reset()
      })
    return
  }
  // 목 모드: 소켓이 없다. 방을 떠날 때만 초기화한다(전환 중엔 endsAt 핸드오프를 위해 상태 유지).
  // 동기 실행이라 위 지연-경합이 없어 가드가 불필요하다.
  if (!transitioning.value) live.$reset()
})

function goToList() {
  void router.push({ name: 'room-list' })
}

function goToResult() {
  void router.push({ name: 'result', params: { id: String(auctionId.value) } })
}

/** 시연 — 판매자가 ROOM-05를 호출했을 때 방 전체에 도착하는 이벤트를 그대로 흘려보낸다 */
function demoStart() {
  subscription?.emitAuctionStarted(durationMinutes.value)
}

/**
 * 시연 — join 실패 분기. 목 어댑터가 항상 성공을 돌려주기 때문에 넣어 둔 확인용 경로다.
 * 실제 실패와 **같은 함수**(`failWith`)를 타므로 분기 로직 자체는 그대로 검증된다.
 */
function demoFail(code: string, status: number) {
  subscription?.unsubscribe()
  subscription = null
  failWith(makeMockJoinError(code, status))
}
</script>

<template>
  <!-- 오류·로딩은 스테이지 레이아웃(높이 고정 + overflow 숨김)을 쓰지 않는다.
       그 안에 넣으면 카드가 고정 높이 컨테이너 기준으로 밀려 가운데 정렬이 깨진다. -->

  <!-- ── 오류 ── -->
  <!-- F2: 전역 헤더(--header-h)만큼 뷰포트 기준 높이를 보정한다 -->
  <div
    v-if="phase === 'error'"
    class="bg-cream flex min-h-[calc(100dvh-var(--header-h))] items-center justify-center p-6"
  >
    <div class="border-line bg-surface w-full max-w-[420px] rounded-2xl border p-7 text-center">
      <h1 class="text-ink text-lg font-bold">{{ errorTitle }}</h1>
      <p class="text-dim mt-2.5 text-[13px] leading-relaxed">{{ errorDescription }}</p>
      <p v-if="errorCode" class="text-faint mt-2 font-mono text-[11px]">{{ errorCode }}</p>

      <div class="mt-5 flex justify-center gap-2">
        <button
          type="button"
          class="border-line text-dim bg-surface hover:bg-cream rounded-full border px-4 py-2 text-[13px] font-medium transition motion-reduce:transition-none"
          @click="goToList"
        >
          목록으로
        </button>
        <button
          v-if="showResultLink"
          type="button"
          class="bg-slate rounded-full px-4 py-2 text-[13px] font-semibold text-white transition hover:opacity-90 motion-reduce:transition-none"
          @click="goToResult"
        >
          결과 보기
        </button>
        <button
          v-else-if="showRetry"
          type="button"
          class="bg-terra hover:bg-terra-d rounded-full px-4 py-2 text-[13px] font-semibold text-white transition motion-reduce:transition-none"
          @click="enter"
        >
          다시 시도
        </button>
      </div>
    </div>
  </div>

  <!-- ── 로딩 ── -->
  <div
    v-else-if="phase === 'loading'"
    class="bg-cream flex min-h-[calc(100dvh-var(--header-h))] items-center justify-center p-6"
  >
    <p class="text-dim text-[13px]" role="status">대기방에 들어가는 중…</p>
  </div>

  <!-- ── 대기방 ── -->
  <div
    v-else
    class="bg-cream min-h-[calc(100dvh-var(--header-h))] lg:h-[calc(100dvh-var(--header-h))] lg:overflow-hidden"
  >
    <div class="flex flex-col gap-3.5 p-3 lg:h-full lg:flex-row lg:p-[18px]">
      <!-- 왼쪽: 스테이지 + 상품 정보 바 -->
      <div class="flex min-w-0 flex-col gap-3 lg:min-h-0 lg:flex-1">
        <div class="min-h-0 lg:flex-1">
          <WaitingStage
            :seller-name="sellerName"
            :waiting-count="live.viewerCount"
            :scheduled-at="scheduledAtMs"
            :image-url="productImage"
            @back="goToList"
          >
            <template v-if="transitioning" #overlay>
              <div class="px-6 text-center" role="status" aria-live="assertive">
                <p class="text-[22px] font-bold tracking-[-0.028em] text-white">
                  판매자가 방송을 시작했어요
                </p>
                <p class="text-on-dark-dim mt-2 text-[13px]">라이브 화면으로 넘어가는 중…</p>
              </div>
            </template>
          </WaitingStage>
        </div>

        <!-- 상품 정보 바 — 하한가는 공개 조건이라 시작 전에도 보여 준다 (규칙 6에 걸리지 않는다) -->
        <div
          class="border-line bg-surface flex flex-none items-center gap-3.5 rounded-xl border px-4 py-3"
        >
          <div
            class="bg-line h-[46px] w-[46px] flex-none rounded-[9px] bg-cover bg-center"
            :style="productImage ? { backgroundImage: cssUrl(productImage) } : undefined"
            aria-hidden="true"
          />
          <div class="min-w-0 flex-1">
            <p class="text-ink truncate text-[13.5px] font-semibold">{{ productTitle }}</p>
            <p class="text-faint mt-[3px] text-[12px] tabular-nums">
              하한가 {{ formatPoint(reservePrice) }} · 진행 {{ durationMinutes }}분 예정
            </p>
          </div>
          <button
            v-if="canReport"
            type="button"
            class="border-line text-dim bg-surface hover:border-terra hover:text-terra flex-none rounded-[10px] border px-4 py-[9px] text-[13px] font-medium transition motion-reduce:transition-none"
            @click="openReport()"
          >
            신고
          </button>
          <button
            type="button"
            class="border-line text-dim bg-surface hover:bg-cream flex-none rounded-[10px] border px-4 py-[9px] text-[13px] font-medium transition motion-reduce:transition-none"
            @click="goToList"
          >
            목록으로
          </button>
        </div>
      </div>

      <!-- 오른쪽 레일: 잠긴 dock + 대기 채팅 -->
      <div class="flex w-full flex-none flex-col gap-3 lg:min-h-0 lg:w-[344px]">
        <!-- 대기방에서는 **항상** 잠긴 dock을 그린다 (규칙 7: 잠긴 쪽은 숨기지 않고 안내로 덮는다).
             디자인 원본도 `.dock.locked`를 조건 없이 렌더하고 CSS로만 흐리며,
             화면설계서 S-08이 이 패널의 목적을 "학습용"(무엇이 열릴지 미리 보여 준다)으로 적었다.
             권한으로 렌더 여부를 가르면 서버가 값을 하나만 열어 줘도 패널이 통째로 사라진다. -->
        <div class="flex-none">
          <WaitingLockedDock :reserve-price="reservePrice" />
        </div>

        <!-- 대기 채팅 (CHAT) — ChatPanel 재사용. 시작 전 질문 수집(highlightQuestions), 권한 없으면 입력 잠금 -->
        <div class="h-[320px] lg:h-auto lg:min-h-0 lg:flex-1">
          <ChatPanel
            :room-id="auctionId"
            title="대기 채팅"
            highlight-questions
            :disabled="!permissions.chat"
            :hint="chatHint"
            disabled-text="🔒 이 방에서는 채팅이 제한됐어요"
          />
        </div>
      </div>
    </div>

    <!-- 시연 컨트롤 — 디자인 원본의 `.live-ctrl`(bottom:100px, 가운데 정렬)과 같은 자리다.
         목 모드 전용이고, 실 연동 후에는 판매자 콘솔(ROOM-05)이 시작 버튼을 대신한다. -->
    <div
      v-if="showDemoControls"
      class="border-line bg-surface/95 fixed bottom-24 left-1/2 z-[12] flex -translate-x-1/2 items-center gap-[3px] rounded-full border p-[5px] shadow-xl backdrop-blur"
    >
      <span class="text-faint pr-2 pl-2.5 text-[10.5px]">시연</span>
      <button
        type="button"
        class="text-dim hover:bg-cream rounded-full px-3 py-[7px] text-[12px] font-medium transition disabled:opacity-50 motion-reduce:transition-none"
        :disabled="transitioning"
        @click="demoFail(API_ERROR_CODE.ROOM_ACCESS_DENIED, 403)"
      >
        입장 거부
      </button>
      <button
        type="button"
        class="text-dim hover:bg-cream rounded-full px-3 py-[7px] text-[12px] font-medium transition disabled:opacity-50 motion-reduce:transition-none"
        :disabled="transitioning"
        @click="demoFail(API_ERROR_CODE.AUCTION_ALREADY_ENDED, 409)"
      >
        종료된 방
      </button>
      <button
        type="button"
        class="bg-ink rounded-full px-3.5 py-[7px] text-[12px] font-semibold text-white transition hover:opacity-90 disabled:opacity-50 motion-reduce:transition-none"
        :disabled="transitioning"
        @click="demoStart"
      >
        판매자가 방송 시작
      </button>
    </div>

    <ReportModal
      v-if="reportOpen"
      :product-title="productTitle"
      :seller-nickname="sellerName || null"
      :pending="reportPending"
      :submit-error="reportError"
      @close="closeReport"
      @submit="submitReport"
    />

    <!-- 접수 안내. 처리 결과가 아니라 "접수됐다"까지만 말한다 (DEAL-04) -->
    <p
      v-if="reportNotice"
      class="bg-ink text-on-dark fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-full px-4 py-2.5 text-[13px] font-medium shadow-lg"
      role="status"
    >
      {{ reportNotice }}
    </p>
  </div>
</template>
