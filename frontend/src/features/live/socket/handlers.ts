// 소켓 이벤트 핸들러 디스패처 — `{ type, data }` → live 스토어 갱신 (FE2)
//
// docs/03-역할-경계-합의.md §7: 가짜 STOMP 클라이언트는 만들지 않는다. 여기 있는 핸들러가
// **실소켓과 목 드라이버가 그대로 재사용하는 진짜 코드**다. 실소켓 전환 시 이 파일은 손대지 않고
// STOMP 클라이언트가 구독별로 아래 디스패처를 호출하도록 붙이기만 한다.
//
// 이벤트 이름·필드의 단일 소스는 백엔드 「최종(종합) 명세서」 = socket/types.ts(그 사본).
// 입력은 소켓에서 온 JSON이라 `{ type: string; data: unknown }`으로 받고 type으로 좁힌다 —
// 명세에 아직 없는 타입(BID-10 SETTLING 등)이 와도 크래시 없이 무시·로깅한다(§6-8).

import type { useLiveStore } from '@/stores/live'
import { capChat, type ChatMessage } from '@/stores/live'
import type { RealtimeState } from './realtimeState'
import { toEpochMs } from '@/shared/api/types'
import type {
  TimeSyncData,
  AuctionStartedData,
  AuctionExtendedData,
  BidAcceptedData,
  PredictionAcceptedData,
  AuctionEndEvent,
  AuctionEndedData,
  AuctionEndedEvent,
  AuctionSettledEvent,
  AuctionNoSaleEvent,
  AuctionCanceledEvent,
  ChatMessageData,
  ChatDeletedData,
  NoticeData,
  ViewerCountData,
  ModerationWarningData,
  ChatMutedData,
  QuestionAddedData,
  QuestionRemovedData,
  PredictionSettledData,
} from './types'

type LiveStore = ReturnType<typeof useLiveStore>

/** 소켓에서 갓 파싱된 원본 메시지. data는 type이 정해지기 전까지 unknown이다. */
interface RawEvent {
  type: string
  data: unknown
}

/** vibe 피드는 최근 N개만 유지한다 — 화면이 길게 붙들지 않도록(디자인: 좌하단 연출) */
const VIBE_KEEP = 8
// 채팅 버퍼 상한(capChat/CHAT_KEEP)은 stores/live.ts가 소유·export한다 — sendChat 낙관 추가와 상한을 공유하기 위함.

// ─────────────────────────────────────────────────────────────
// 메인 토픽  /topic/auctions/{auctionId}
// ─────────────────────────────────────────────────────────────

/**
 * 서버 기준 마감 시각·시각 보정을 갱신한다 (BID-05).
 * serverOffset 계산에만 로컬 시계를 1회 쓴다 — 이것이 "동기화 지점"이고,
 * 이후 남은 시간은 endsAt + serverOffset로만 계산한다(자체 카운트 금지, 규칙 5).
 */
function handleTimeSync(store: LiveStore, data: TimeSyncData): void {
  const serverNow = toEpochMs(data.serverTime)
  if (serverNow !== null) store.serverOffset = serverNow - Date.now()
  store.endsAt = toEpochMs(data.endAt)
}

/**
 * 판매자 수동 시작 (ROOM-05) — 대기방 전원 라이브 전환 트리거. 전환 라우팅은 구독자(대기방 화면)가 감지
 *
 * **집계 둘을 0으로 세운다.** 시작 전에는 스냅샷 조회가 아예 실패한다 — 실시간 상태 Hash를
 * `AUCTION_STARTED`가 만들기 때문에(BE `RedisAuctionStateInitializer`) 그 전에 부르면 서버가
 * unavailable을 던지고, `liveSocket`은 버퍼만 흘려보내고 실시간으로 넘어간다. 그래서 접속 시점이
 * 대기 중이면 `bidCount`·`participantCount`가 **null로 남아 「—」로 굳었다.** 첫 입찰(BID_ACCEPTED)이
 * 오거나 새로고침(재연결 → 이제 Hash가 있으니 스냅샷 성공)해야 숫자가 떴다.
 *
 * 0은 추측이 아니라 정의다 — 시작 전에는 입찰 자체가 거절되므로(Redis `AUCTION_NOT_FOUND`)
 * 막 시작한 경매의 건수·참여자는 반드시 0이다. `null = 미수신` 규약을 어기는 것도 아니다:
 * **null일 때만** 세워서, 스냅샷이나 집계를 이미 받은 상태는 건드리지 않는다(재연결 중 버퍼에
 * 남아 있던 낡은 시작 이벤트가 최신 집계를 0으로 덮지 않게).
 */
function handleAuctionStarted(store: LiveStore, data: AuctionStartedData): void {
  store.auctionStatus = 'LIVE'
  store.endsAt = toEpochMs(data.endAt)
  if (store.bidCount === null) store.bidCount = 0
  if (store.participantCount === null) store.participantCount = 0
}

/** +5분 연장 (BID-03). 틱 이벤트는 없다 — endsAt만 갱신하고 카운트다운은 렌더러가 그린다 */
function handleAuctionExtended(store: LiveStore, data: AuctionExtendedData): void {
  store.endsAt = toEpochMs(data.endAt)
  store.extensionCount = data.extensionCount
  store.remainingExtensions = data.remainingExtensions
}

/**
 * 입찰 접수 브로드캐스트 (BID-01·02·04). 비크리 공개 범위는 이 세 필드뿐 —
 * 금액·입찰자·순위·최고가는 오지도 않고 표시하지도 않는다(규칙 6).
 */
function handleBidAccepted(store: LiveStore, data: BidAcceptedData): void {
  store.bidCount = data.bidCount
  store.participantCount = data.participantCount
  if (data.vibeMessage) store.vibeFeed = [...store.vibeFeed, data.vibeMessage].slice(-VIBE_KEEP)
}

/**
 * 예측 참여 브로드캐스트 (PRED-01).
 *
 * **`vibeFeed`에 넣으면 안 된다.** 그 배열은 `useBidPulse`가 참조 변화를 세어 경매봉을 내려치는
 * 입력이라(`media/useBidPulse.ts`), 예측이 섞이면 **입찰 망치가 예측에 울린다** — 스테이지 흔들림과
 * 가장자리 섬광까지 같이 터져서, 예측 참여가 입찰과 구분되지 않는다(규칙 1: 슬레이트=베팅).
 * 피드를 따로 두는 이유가 이것뿐이라
 * 합치고 싶어지면 먼저 useBidPulse의 "1 = 1" 전제부터 다시 읽어야 한다.
 *
 * `participantCount`는 `BID_ACCEPTED`와 같은 카운터라(types.ts 주석) 같은 필드에 그대로 쓴다.
 */
function handlePredictionAccepted(store: LiveStore, data: PredictionAcceptedData): void {
  store.participantCount = data.participantCount
  if (data.vibeMessage)
    store.predictionFeed = [...store.predictionFeed, data.vibeMessage].slice(-VIBE_KEEP)
}

/**
 * 종료 계열 4종(BID-07·DEAL-09)의 공통 처리.
 *
 * **상태 문자열만 남기고 payload를 버리면 안 된다.** 오버레이(BID-10 집계→공개)가 낙찰자·최종가·
 * 유찰 사유를 이 이벤트에서만 받기 때문이다 — 예전에는 `_data`로 흘려보내서, 실서버에서는
 * 오버레이가 뜰 방법 자체가 없었고 마감 후에도 입찰창이 열려 있었다(S15P11A704-613 후속).
 * 결과 화면(DEAL-06 REST)이 상세를 다시 받아 오는 것과는 별개다 — 그건 종료 **후** 화면이고
 * 이건 종료 **순간**의 연출이라 소켓 payload 말고는 소스가 없다.
 */
function handleAuctionEnd(store: LiveStore, event: AuctionEndEvent, status: string): void {
  store.auctionStatus = status
  store.endEvent = event
}

/**
 * 예측 정산 배치 종료 (PRED-03, S15P11A704-543).
 *
 * 분포·배당은 여기 실리지 않는다(규칙 7) — 명세 처리 규칙대로 **수신 사실만 남기고**
 * 실제 조회는 화면이 PRED-02(`GET /auctions/{id}/prediction-results`)로 한다.
 * `settlementStatus`가 `FAILED`여도 그대로 담는다: 발행을 생략하지 않는 이유가
 * "화면이 집계 중에 굳지 않게"라서, 실패도 재조회 트리거로 흘려보내야 한다.
 * (재조회가 다시 409면 화면은 기존 "정산이 끝나면 공개돼요" 안내로 떨어진다.)
 */
function handlePredictionSettled(store: LiveStore, data: PredictionSettledData): void {
  store.predictionSettlement = data
}

/**
 * 메인 토픽 이벤트 라우팅. 명세에 없는 타입(BID-10 SETTLING 등)은 무시·로깅한다(§6-8).
 */
export function dispatchAuctionTopic(store: LiveStore, event: RawEvent): void {
  switch (event.type) {
    case 'TIME_SYNC':
      return handleTimeSync(store, event.data as TimeSyncData)
    case 'AUCTION_STARTED':
      return handleAuctionStarted(store, event.data as AuctionStartedData)
    case 'AUCTION_EXTENDED':
      return handleAuctionExtended(store, event.data as AuctionExtendedData)
    case 'BID_ACCEPTED':
      return handleBidAccepted(store, event.data as BidAcceptedData)
    case 'PREDICTION_ACCEPTED':
      return handlePredictionAccepted(store, event.data as PredictionAcceptedData)
    case 'AUCTION_ENDED':
      // 마감 상태 문자열만 서버 값을 그대로 쓴다(없으면 'SETTLING'). 나머지 셋은 이벤트가 곧 상태다.
      return handleAuctionEnd(
        store,
        event as AuctionEndedEvent,
        (event.data as AuctionEndedData).status ?? 'SETTLING',
      )
    case 'AUCTION_SETTLED':
      return handleAuctionEnd(store, event as AuctionSettledEvent, 'SETTLED')
    case 'AUCTION_NO_SALE':
      return handleAuctionEnd(store, event as AuctionNoSaleEvent, 'NO_SALE')
    case 'AUCTION_CANCELED':
      return handleAuctionEnd(store, event as AuctionCanceledEvent, 'CANCELED')
    case 'PREDICTION_SETTLED':
      return handlePredictionSettled(store, event.data as PredictionSettledData)
    default:
      // BID-10 SETTLING 타입명 등 명세 미확정 이벤트. 확정 시 위 분기에 추가한다(socket/types.ts).
      console.warn('[live/socket] 미정의 메인 토픽 이벤트 무시:', event.type)
  }
}

// ─────────────────────────────────────────────────────────────
// 채팅 토픽  /topic/auctions/{auctionId}/chat
// ─────────────────────────────────────────────────────────────

/** CHAT_MESSAGE.data → 스토어 ChatMessage. 발신자 null(탈퇴)·서버가 가린 필드 목록을 그대로 넘긴다 */
// 과거 조회 REST(features/chat/history.ts)도 같은 정규화를 쓴다 — 이력 응답이 소켓과 같은 shape로 확정됐다(S15P11A704-679).
// **탈퇴 회원 메시지는 이력에도 그대로 나온다.** 이력 SQL이 거르는 것은 `sender_member_id IS NULL`인
// 행뿐인데(JdbcChatHistoryPersistenceAdapter의 member INNER JOIN), 그 NULL은 member 행이 **하드 삭제**될 때만
// 생기고(V1__init.sql `ON DELETE SET NULL`) 이 레포의 탈퇴는 소프트다(MemberStatus.WITHDRAWN — 회원 행을
// 지우는 코드가 없다). nickname null 가능성도 스키마상 남는다(`member.nickname VARCHAR(30) UNIQUE`, NOT NULL 아님).
// 어느 쪽이든 렌더러가 이미 null을 처리한다(ChatPanel의 '(탈퇴한 사용자)').
export function toChatMessage(data: ChatMessageData): ChatMessage {
  return {
    id: data.messageId,
    nickname: data.nickname,
    text: data.content,
    isQuestion: data.isQuestion,
    clientMessageId: data.clientMessageId,
    maskedFields: data.maskedFields,
    createdAt: data.createdAt,
  }
}

function handleChatMessage(store: LiveStore, data: ChatMessageData): void {
  // 내가 보낸 낙관 행의 에코는 버리지 않고 서버 값으로 교체(update-in-place)한다 —
  // 서버 messageId·createdAt·maskedFields로 승격해야 고정공지(NOTICE_*)·질문 큐 messageId 매칭,
  // 재접속 병합 정렬, 서버 마스킹된 content 반영이 성립한다(에코를 폐기하면 이 셋이 영영 소실됨).
  if (data.clientMessageId) {
    const idx = store.chat.findIndex((m) => m.clientMessageId === data.clientMessageId)
    if (idx !== -1) {
      const next = [...store.chat]
      next[idx] = toChatMessage(data)
      store.chat = next
      return
    }
  }
  // messageId(ULID)로 서버 중복 이벤트를 거른다 (명세 "클라이언트 처리 규칙", S15P11A704-416).
  // 재접속·경계 겹침으로 남이 보낸 같은 메시지가 재브로드캐스트돼도 두 번 쌓이지 않는다.
  // (낙관 행 id는 `opt-N`이라 서버 ULID와 겹치지 않아, 위 clientMessageId 교체 경로를 침범하지 않는다.)
  if (store.chat.some((m) => m.id === data.messageId)) return
  // 상한 적용은 capChat — 낙관 행(pending/failed)은 밀려나지 않게 예외 처리한다(S15P11A704-399).
  store.chat = capChat([...store.chat, toChatMessage(data)])
}
// 검열 마스킹은 서버가 발신 시점에 끝내 content를 이미 가린 채 브로드캐스트한다(CHAT-03, 최종 명세 S15P11A704-413).
// 구 설계의 per-message moderationStatus 갱신(303)은 폐기라 **content를 제자리 교체할 일은 없다.**
// ⚠️ 다만 "발신 후 상태가 영영 그대로"는 아니다 — 판매자·운영자 삭제와 AI 사후 차단이 아래 CHAT_DELETED로
// 오고, 그건 제자리 교체가 아니라 행 제거다. 개인 대상 경고·뮤트만 별도 채널(/user/queue/moderation)로 온다.

/**
 * CHAT_DELETED — 떠 있는 행을 지운다 (CHAT-01).
 *
 * 명세가 「같은 messageId를 화면·로컬 버퍼에서 제거」로 확정했다 — 「삭제된 메시지입니다」 자리표시를 남기지
 * 않는다. 없는 id가 와도 filter가 그대로 통과시켜 무해하다: 상한(capChat)에 밀려났거나 애초에 못 받은
 * 메시지의 삭제 이벤트가 정상적으로 도착할 수 있다.
 *
 * 이력·스냅샷·질문 큐는 서버가 `is_deleted`로 걸러 주므로 여기서 손댈 것이 없다 — FE가 지우는 것은
 * **지금 떠 있는 행**뿐이다. 고정 공지였으면 NOTICE_UNPINNED가, 대기 질문이었으면
 * QUESTION_REMOVED(DELETED)가 같이 오고 그건 기존 핸들러가 받는다.
 */
function handleChatDeleted(store: LiveStore, data: ChatDeletedData): void {
  store.chat = store.chat.filter((m) => m.id !== data.messageId)
}

/** NOTICE_* — 고정 공지 등록·교체·해제 (CHAT-02). content가 비면 해제로 본다 */
function handleNotice(store: LiveStore, data: NoticeData): void {
  // content가 있으면 등록/교체, 없으면 해제. messageId는 ULID 문자열이라 그대로 담는다 — 채팅 ChatMessage.id와
  // 같은 string 공간이라 고정공지↔원본 메시지 매칭이 성립한다(S15P11A704-416, 구 Number() 코어싱 제거).
  store.pinnedNotice = data.content ? { messageId: data.messageId, content: data.content } : null
}

/** 채팅 토픽 라우팅 — CHAT_MESSAGE · CHAT_DELETED 또는 NOTICE_* 프리픽스 */
export function dispatchChatTopic(store: LiveStore, event: RawEvent): void {
  if (event.type === 'CHAT_MESSAGE') {
    return handleChatMessage(store, event.data as ChatMessageData)
  }
  if (event.type === 'CHAT_DELETED') {
    return handleChatDeleted(store, event.data as ChatDeletedData)
  }
  if (event.type.startsWith('NOTICE_')) {
    return handleNotice(store, event.data as NoticeData)
  }
  console.warn('[live/socket] 미정의 채팅 토픽 이벤트 무시:', event.type)
}

// ─────────────────────────────────────────────────────────────
// 시청자 수 토픽  /topic/auctions/{auctionId}/viewers  (LIVE-03)
// ─────────────────────────────────────────────────────────────

/** VIEWER_COUNT — 활성 STOMP 세션 수(고유 사용자 아님, 멀티탭이면 중복) */
export function handleViewerCount(store: LiveStore, data: ViewerCountData): void {
  store.viewerCount = data.viewerCount
}

// ─────────────────────────────────────────────────────────────
// 질문 큐 토픽  /topic/auctions/{auctionId}/questions  (CHAT-06)
// ─────────────────────────────────────────────────────────────

/** 질문 추가 — 콘솔 큐에 실시간 반영. questionId는 REST와 같은 ULID 문자열이라 그대로 쓴다(계약 확정). */
function handleQuestionAdded(store: LiveStore, data: QuestionAddedData): void {
  const questionId = data.questionId
  // 재접속·에코로 같은 질문이 다시 와도 중복 추가하지 않는다(REST 시드와도 겹칠 수 있음).
  if (store.questions.some((q) => q.questionId === questionId)) return
  store.questions = [
    ...store.questions,
    { questionId, nickname: data.nickname, content: data.content },
  ]
}

// 콘솔 markAnswered가 서버 확정(204) 후 큐에서 제거한 질문 id — 뒤이어 올 QUESTION_REMOVED 에코는 정상이라 경고에서 뺀다.
// (세션 스코프 Set — 답변한 질문 수만큼만 쌓이고 에코가 소비하며 지운다.)
const expectedQuestionRemovals = new Set<string>()

/** 콘솔이 답변 완료로 큐에서 먼저 지운 질문을 예약 등록 — 뒤이어 올 QUESTION_REMOVED 에코를 조용히 처리한다(리뷰 nit). */
export function expectQuestionRemoval(questionId: string): void {
  expectedQuestionRemovals.add(questionId)
}

/**
 * 질문 제거 — 답변 완료(RESOLVED)·검열(FILTERED)·메시지 삭제(DELETED)로 큐에서 빠진다.
 * `data.reason`이 그 구분이지만 화면은 안 쓴다(셋 다 「큐에서 사라진다」로 같다).
 * questionId는 ADDED와 같은 ULID 문자열이라 코어싱하지 않는다(명세 chat-06 질문 토픽 예시).
 * ✅ BE 발행자 4곳이 실재한다(S15P11A704-680) — 아래 warn은 이제 questionId 정합의 실질 감지선이다.
 */
function handleQuestionRemoved(store: LiveStore, data: QuestionRemovedData): void {
  const questionId = data.questionId
  const next = store.questions.filter((q) => q.questionId !== questionId)
  if (next.length === store.questions.length) {
    // 이미 큐에 없음. 내가 답변 완료 확정(204) 후 제거한 것의 서버 에코면 정상이라 조용히 넘긴다.
    if (expectedQuestionRemovals.delete(questionId)) return
    // 그 외엔 서버가 보낸 questionId가 큐(REST 시드 포함)의 값과 다르다는 뜻이다.
    console.warn(
      '[live/socket] QUESTION_REMOVED가 큐에서 일치 항목을 못 찾음(questionId 정합 확인):',
      questionId,
    )
    return
  }
  expectedQuestionRemovals.delete(questionId) // 실제로 지웠으면 예약도 정리
  store.questions = next
}

/** 질문 큐 토픽 라우팅 (CHAT-06). 콘솔 큐 UI는 FE1이 store.questions를 읽어 그린다. */
export function dispatchQuestionTopic(store: LiveStore, event: RawEvent): void {
  if (event.type === 'QUESTION_ADDED') {
    return handleQuestionAdded(store, event.data as QuestionAddedData)
  }
  if (event.type === 'QUESTION_REMOVED') {
    return handleQuestionRemoved(store, event.data as QuestionRemovedData)
  }
  console.warn('[live/socket] 미정의 질문 토픽 이벤트 무시:', event.type)
}

// ─────────────────────────────────────────────────────────────
// 개인 큐  /user/queue/moderation  (AI-05)
// ─────────────────────────────────────────────────────────────

/**
 * 검열 개인 통지. CHAT_MUTED는 입력을 잠그고, 해제 시각은 **서버값 기준으로 표시**한다(자체 계산 금지, 규칙 5).
 * MODERATION_WARNING은 경고 배너용이라 스토어 상태를 바꾸지 않고 호출부(ChatPanel)가 토스트/배너로 띄운다.
 */
export function dispatchModeration(store: LiveStore, event: RawEvent): void {
  if (event.type === 'CHAT_MUTED') {
    const data = event.data as ChatMutedData
    // /user/queue/moderation은 개인 큐라 구독 중인 방과 무관하게 온다(방 이동 직후 지연 도착·다중 탭 등).
    // 다른 방의 뮤트가 지금 보는 방 채팅을 잠그지 않도록 auctionId를 현재 roomId와 대조해 다르면 무시한다(414 리뷰).
    if (store.roomId !== null && data.auctionId !== store.roomId) return
    store.muted = true
    store.mutedUntil = toEpochMs(data.mutedUntil)
    return
  }
  if (event.type === 'MODERATION_WARNING') {
    // 개인 검열 경고(AI-05) — 발신자에게만 일시 배너로 알린다(ChatPanel). 입력을 잠그지는 않는다(그건 CHAT_MUTED).
    // id를 증가시켜 같은 사유가 연달아 와도 값이 바뀌어 화면이 다시 뜨게 한다(S15P11A704-356).
    const data = event.data as ModerationWarningData
    store.moderationWarning = {
      id: (store.moderationWarning?.id ?? 0) + 1,
      reason: data.reason,
      // 최종 명세(414): message가 사용자 표시 문구다. 그대로 담고, 표시/폴백 분기는 ChatPanel이 한다.
      message: data.message,
      // messageId(ULID 문자열, 416)는 지금 표시엔 안 쓰지만 버리지 않고 담는다(향후 대상 메시지 강조 등).
      messageId: data.messageId,
    }
    return
  }
  console.warn('[live/socket] 미정의 검열 이벤트 무시:', event.type)
}

// ─────────────────────────────────────────────────────────────
// 재접속 스냅샷 (WS-03)  GET /api/v1/auctions/{id}/realtime-state
// ─────────────────────────────────────────────────────────────

/**
 * 서버 권위 스냅샷으로 스토어를 복원한다. 재접속 시 버퍼 재생 절차의 마지막 단계이며,
 * serverOffset은 여기서 건드리지 않는다 — 스냅샷엔 serverTime이 아예 없어 세울 근거가 없다.
 */
export function applySnapshot(store: LiveStore, snapshot: RealtimeState): void {
  store.stateVersion = snapshot.stateVersion
  store.auctionStatus = snapshot.auctionStatus
  store.endsAt = toEpochMs(snapshot.endAt)
  store.extensionCount = snapshot.extendCount
  // 남은 연장 횟수는 서버 값을 그대로 쓴다 — FE 상수(MAX_EXTENSIONS)에서 유도하면(구 S15P11A704-301 임시 대응)
  // 서버가 연장 상한 정책을 바꾼 순간 화면 숫자가 서버와 어긋난다. BE가 스냅샷에 실어 주면서 유도가 불필요해졌다.
  store.remainingExtensions = snapshot.remainingExtensions
  store.bidCount = snapshot.bidCount
  store.participantCount = snapshot.bidParticipantCount
  store.viewerCount = snapshot.viewerCount
  store.myBid = snapshot.myBidAmount
  // 내 예측 금액·스테이크 복원 — 이게 없으면 잠금만 살아나고 "얼마에 걸었는지"가 빈칸으로 남는다
  // (S15P11A704-388). 예측 미참여면 서버가 null을 주므로 입찰자·미참여자는 영향받지 않는다.
  store.myPredictedPrice = snapshot.myPredictedPrice
  store.myStakeAmount = snapshot.myStakeAmount
  // 배타 잠금(규칙 7) 복원 근거 — S15P11A704-201에서 store.myParticipationType으로 이관됨
  store.myParticipationType = snapshot.myParticipationType
  // 스냅샷·소켓·스토어 모두 messageId가 ULID 문자열이라 변환 없이 그대로 복원한다(S15P11A704-416, 구 Number() 제거)
  store.pinnedNotice = snapshot.pinnedNotice
    ? { messageId: snapshot.pinnedNotice.messageId, content: snapshot.pinnedNotice.content }
    : null
  store.muted = snapshot.amIMuted
  // mutedUntil 정합 — 이전 방/세션의 잔존값이 남지 않게 muted와 함께 복원한다(S15P11A704-301).
  // BE가 amIMuted와 mutedUntil의 동치를 강제하므로 아래 분기는 방어용이고, 값이 없으면 null →
  // ChatPanel이 "일시 제한" 폴백 문구로 표시한다(규칙 5 — 시각 자체 계산 금지).
  store.mutedUntil = snapshot.amIMuted ? toEpochMs(snapshot.mutedUntil) : null
}
