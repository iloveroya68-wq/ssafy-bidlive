import { defineStore } from 'pinia'
import type { ParticipationType } from '@/shared/lib/participation'
import type {
  AuctionEndEvent,
  ChatSendPayload,
  PredictionSettledData,
} from '@/features/live/socket/types'
// 타입만 가져온다(컴파일 시 소거) — 목 번들이 liveSocket을 끌어오지 않는 성질을 깨지 않는다.
import type { LiveSocketOptions } from '@/features/live/socket/liveSocket'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import { toApiError } from '@/shared/api/errors'
import { newRequestId } from '@/shared/api/idempotency'
import { isMockApi } from '@/shared/api/mock'

// 오너: FE2 — 소켓(STOMP) 이벤트로 갱신되는 라이브 상태.
//
// state 모양은 FE1·FE2 합의(docs/03-역할-경계-합의.md §3)를 단일 소스로 하고, WS-03 스냅샷
// (features/live/socket/realtimeState.ts)·소켓 이벤트(features/live/socket/types.ts)가 실어오는 값을 담는다.
// FE1 화면(입찰·예측 패널·타이머·결과)도 구독하므로 state·action 시그니처 변경은
// 반드시 상호 공유 후 진행한다(AGENTS.md 규칙 4).
//
// 액션은 sendChat(CHAT-01·06, S15P11A704-204)과 connect/connectWaitingRoom/disconnect가 있다.
// **placeBid는 없고, 앞으로도 넣지 않는다.** BID-06이 REST 201로 개인 결과를 동기 반환하므로
// 제출도 결과 수신도 이 스토어를 거치지 않는다 — FE1 패널이 shared/api/bids.ts의 placeBid를 직접 await하고,
// 그 응답으로 myBid·myParticipationType을 patch한다. 소켓의 BID_ACCEPTED는 개인 결과가 아니라
// 집계(bidCount·participantCount·vibeMessage) 브로드캐스트다 — 규칙 6상 입찰자가 안 실려서
// 개인 결과 채널로 쓸 수도 없다. 근거는 docs/03 §3 (224 결론).
// state 갱신 주체는 소켓 이벤트 핸들러(features/live/socket/handlers.ts, S15P11A704-202)다.
//
// 초기값 규칙: 서버 스냅샷/이벤트를 아직 못 받은 값은 null로 둔다 — 0/false는 "서버가 그렇게 말함"과
// "아직 못 받음"을 구분하지 못해 화면이 거짓값(0건 0명, 시계 동기화됨)을 사실처럼 그린다.

/** 소켓 연결 상태 (WS-01) */
export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'closed'

/**
 * 고정 공지 (CHAT-02). 갱신 주체인 NOTICE_* 이벤트(socket/types.ts `NoticeData`)의 필드형에 맞춘다 —
 * messageId는 ULID 문자열|null(S15P11A704-416), content는 string|null.
 * (messageId를 채팅 ChatMessage.id·스냅샷 realtimeState.PinnedNotice와 같은 string으로 통일해,
 *  「고정공지↔원본 메시지 매칭」이 타입 수준에서 성립하고 소켓/스냅샷 경로 간 변환이 필요 없다.)
 */
export interface PinnedNotice {
  messageId: string | null
  content: string | null
}

/**
 * 채팅 메시지 (CHAT-01). 소켓 `CHAT_MESSAGE.data`(socket/types.ts)를 화면용으로 정규화한 모양이다.
 * 발신자는 탈퇴 회원이면 null이라 렌더러가 반드시 null을 처리한다.
 */
export interface ChatMessage {
  /** chat_message PK — 서버 확정은 ULID 문자열(messageId), 낙관 미확정 행은 임시키 `opt-N`(S15P11A704-416) */
  id: string
  /** 발신자 닉네임 — 탈퇴 회원이면 null */
  nickname: string | null
  text: string
  /** CHAT-06 질문 채팅 여부 */
  isQuestion: boolean
  /** 멱등 처리용 — 내가 보낸 메시지가 브로드캐스트로 돌아온 것을 걸러낸다 */
  clientMessageId: string | null
  /**
   * 서버가 발신 시점에 가린 필드 목록 (CHAT-03, S15P11A704-413). content는 이미 서버가 마스킹해 보내므로
   * 화면은 content를 그대로 렌더하고, 비어있지 않으면 「검열됨」 힌트만 붙인다. (구 moderationStatus/isDeleted 폐기)
   */
  maskedFields?: string[]
  /** 수신 시각 (ISO 8601) — 타임스탬프 표시·재접속 병합 정렬 기준 */
  createdAt: string
  /**
   * 전송 대기 — 낙관 추가 후 서버 에코(승격)가 오기 전까지 true (S15P11A704-376).
   * 에코가 오면 handlers.handleChatMessage가 이 행을 서버 값(pending 없음)으로 통째 교체해 자연히 해제된다.
   * 재연결 중 큐잉·상한 폐기·영구 미연결이면 계속 true로 남아 「전송 중」 표시가 유지된다(미전송 가시화).
   * 목 모드(전송 대상 서버 없음)는 에코가 없어 처음부터 false로 둔다 — 영구 「전송 중」을 피한다.
   */
  pending?: boolean
  /**
   * 전송 실패 — 재연결 대기 큐 상한 초과로 payload가 확정 폐기됐다(S15P11A704-383).
   * 다시 시도되지 않으므로 pending을 해제하고 이 값을 세워, 「전송 중…」 대신 「전송 실패」로 표시한다(재입력 신호).
   */
  failed?: boolean
  /**
   * 실패 사유 — 서버 에러 큐가 준 표시용 문구(WS-04 `publicMessage`, S15P11A704-558). 그대로 화면에 노출한다.
   * 큐 폐기·타임아웃처럼 서버 회신 없이 실패한 경우엔 없고, 화면은 기본 「전송 실패」로 떨어진다.
   */
  failReason?: string
  /**
   * 과거 조회로 불러온 이전 메시지 (S15P11A704-379). 상한 트림에서 보호한다 — 위로 읽는 중 라이브 한 건에
   * 방금 불러온 과거가 잘려나가(+스크롤이 튀어) 사라지는 것을 막는다(capChat이 보호 행으로 취급).
   */
  older?: boolean
}

/**
 * 질문 큐 항목 (CHAT-06). QuestionQueue.vue(FE1 콘솔)가 쓰는 필드만 담는다 —
 * REST(QuestionQueueItem)와 소켓(QUESTION_ADDED)이 함께 주는 필드의 부분집합 — 둘 다 memberId·createdAt도 준다.
 */
export interface QuestionItem {
  /** ULID 문자열. REST·소켓(QUESTION_ADDED) 모두 같은 ULID를 주므로 변환 없이 담는다. */
  questionId: string
  nickname: string
  content: string
}

/** 채팅 버퍼 상한 — 오래된 메시지는 버린다(과거 조회는 REST). sendChat·핸들러(handlers.ts)가 공유한다. */
export const CHAT_KEEP = 200

/**
 * CHAT-01 확정 입력 상한 250자. 서버 상한이 바뀌면 여기만 고친다.
 * 세는 단위가 둘로 갈리는데도 같은 상수를 쓴다 — sendChat 가드는 코드 포인트(`[...text].length`),
 * ChatPanel textarea `maxlength`와 잔여 카운터는 UTF-16(`String.length`)이다. 명세 상한이 250 하나고,
 * UTF-16 길이는 코드 포인트 길이보다 항상 같거나 크므로 maxlength 쪽이 항상 같거나 더 엄격하다
 * (이모지 등 astral 문자에서만 더 엄격) — 즉 입력 캡을 통과한 값은 가드도 반드시 통과한다.
 */
export const CHAT_MAX_LENGTH = 250

/**
 * 과거 조회로 보관할 이전 메시지(older) 총량 상한 (S15P11A704-406). capChat이 보호 행(older/pending/failed)을
 * 상한에서 제외하면서 older는 위로 스크롤할수록 무한히 쌓일 수 있다 — 메모리 방어로 여기까지만 불러온다.
 * 하드-드롭(오래된 older를 버림)은 "드롭→상단 재조회→또 드롭"으로 thrash가 나므로, 대신 **로드를 멈춘다**
 * (loadOlderChat 가드). live(CHAT_KEEP=200) + older(이 값)만큼이 클라이언트 보관 총량의 대략적 상한이 된다.
 */
export const CHAT_OLDER_MAX = 400

/**
 * 보호 행 — 상한 트림에서 제외하고 **상한 계산에도 포함하지 않는다**:
 *  - pending(376)·failed(383): 내 미확정/실패 낙관 행. 잘리면 미전송인데 흔적도 없어져 재입력을 못 안다.
 *  - older(379): 과거 조회로 불러온 이전 메시지. 잘리면 위로 읽는 중 라이브 한 건에 방금 불러온 게 사라진다.
 */
function isProtectedChat(m: ChatMessage): boolean {
  return m.pending === true || m.failed === true || m.older === true
}

/**
 * 채팅 상한 적용 (S15P11A704-399, 379). 상한은 **트림 대상(확정·비보호) 메시지**에만 적용한다 — 보호 행은
 * 상한에 포함하지 않고 항상 남긴다. 그래서 상한 초과분은 오래된 확정 메시지부터만 버리고, 낙관 행(pending/failed)과
 * 불러온 과거(older)는 잘리지 않는다. 보호 행이 없으면 종전 slice(-CHAT_KEEP)와 동일하게 동작한다.
 * (보호 행이 상한을 안 먹으므로, 과거를 불러와도 최근 라이브 메시지가 밀려나지 않는다 — displacement 방지, 379 리뷰 1.)
 */
export function capChat(chat: ChatMessage[], keep = CHAT_KEEP): ChatMessage[] {
  const trimmable = chat.reduce((n, m) => (isProtectedChat(m) ? n : n + 1), 0)
  let excess = trimmable - keep
  if (excess <= 0) return chat
  return chat.filter((m) => {
    if (excess > 0 && !isProtectedChat(m)) {
      excess -= 1
      return false // 오래된 확정(비보호) 메시지부터 버린다
    }
    return true // 보호 행과 남길 확정 메시지는 유지
  })
}

/**
 * 개인 검열 경고 (AI-05, MODERATION_WARNING). 발신자에게만 일시 배너로 알린다.
 * `id`는 단조 증가 — 같은 `reason`이 연달아 와도 값이 바뀌어 화면(ChatPanel)이 다시 띄우게 하는 트리거다.
 */
export interface ModerationWarning {
  id: number
  /** 공개 enum(예: OFF_PLATFORM_TRADE) — 표시하지 않고 로깅·분기용 */
  reason: string
  /** 사용자에게 표시할 문구(MODERATION_WARNING.message, S15P11A704-414). 없으면 ChatPanel이 폴백 문구로 대체 */
  message: string
  /** 경고 대상 메시지 id (ULID 문자열, S15P11A704-416) — 지금은 표시에 안 쓰지만 향후 "해당 메시지 강조" 등을 위해 담아 둔다. */
  messageId: string | null
}

/**
 * BID-03 방송 연장 상한 (`auction_room.extension_count BETWEEN 0 AND 3`, docs/04 §3.4).
 * 남은 횟수는 서버 값(스냅샷·AUCTION_EXTENDED)을 쓰고 이 상수로 유도하지 않는다 — 여긴 표시용 분모다.
 * 목 피드(liveFeed)도 같은 상수를 공유한다 — 매직넘버가 두 곳에서 어긋나지 않게.
 */
export const MAX_EXTENSIONS = 3

// 채팅 SEND 전송 함수 — 실 모드에서 connect()가 STOMP publishChat을 주입하고 disconnect()가 비운다.
// 목 모드(미주입)에선 null이라 sendChat은 낙관 반영만 한다(전송 대상 서버가 없으므로).
let chatPublish: ((payload: ChatSendPayload) => void) | null = null

/**
 * 연결 세대 (S15P11A704-329).
 *
 * `connect`·`connectWaitingRoom`·`disconnect`는 셋 다 **동적 import를 await한 뒤에** 실제 정리·연결과
 * 상태 쓰기를 한다. 그런데 라우터는 이전 화면의 `onUnmounted`가 끝나기를 기다리지 않는다 — 다음 화면이
 * 먼저 마운트되는 경우가 있어서, **A의 꼬리가 B가 연결을 세운 뒤에 도착**할 수 있다.
 *
 * 그때 벌어지는 일이 둘이다.
 *  ① A의 `connectionStatus = 'closed'`가 B의 `'connecting'`/`'connected'`를 덮는다.
 *  ② 더 나쁜 쪽 — A의 `disconnectLiveSocket()`이 **B가 만든 연결을 끊는다.**
 *     `liveSocket`의 `active`는 누가 만든 것인지 보지 않는다.
 *
 * 그래서 각 진입점이 시작할 때 번호를 하나 올리고, await를 건넌 뒤 **자기 번호가 아직 최신일 때만**
 * 손을 댄다. `stores/room.ts`의 `requestSeq`·`stores/wallet.ts`의 `walletGeneration`과 같은 관용구다.
 *
 * 방 id로 대조하지 않는 이유: **같은 방을 다시 여는 경로**(콘솔 → 같은 방 라이브)에서 id가 같아 통과해
 * 버린다. 번호에는 그 구멍이 없다.
 *
 * ⚠️ 이 가드가 서는 전제는 **떠나는 쪽이 먼저 번호를 받는다**는 것이다. Vue는 컴포넌트를 갈아 끼울 때
 * 이전 것을 unmount한 뒤 새 것을 mount하고, 두 훅 모두 post-flush 큐에 그 순서로 들어간다 — 그래서
 * `onUnmounted`의 `disconnect()`가 `onMounted`의 `connect()`보다 **먼저 호출**된다. 늦는 것은 호출이 아니라
 * await 뒤의 꼬리이고, 그 꼬리를 막는 것이 이 번호다.
 *
 * 만약 반대 순서로 부르는 호출부가 생기면(다음 화면을 먼저 연결하고 이전 화면을 나중에 정리)
 * **번호만으로는 못 막는다.** 이 가드는 await **뒤**만 지키는데, `disconnect()`의 앞부분은 동기라
 * `chatPublish = null`이 그대로 실행돼 **다음 화면의 전송 함수를 지운다.** 증상은 "채팅이 안 나감"이고,
 * 소켓은 멀쩡히 붙어 있어서 원인을 찾기 어렵다. 지금은 그 순서로 부르는 곳이 없다.
 * 생기면 번호가 아니라 호출부가 자기 연결을 식별할 수단이 필요하다.
 */
let connectionGeneration = 0

/** 방 입장 일련번호의 발급기 (S15P11A704-367). `joinRoom`만 올린다 — 자세한 이유는 state의 `roomSession` 주석. */
let roomSessionSeq = 0

// 목 모드에서 connect()가 돌린 목 라이브 피드의 정지 함수. disconnect()가 호출해 타이머를 정리한다.
let stopMockFeed: (() => void) | null = null
// 목 모드 전용 — 내 입찰을 집계에 즉시 반영하는 진입점(bumpMyBid). 실 모드는 서버 BID_ACCEPTED가 담당해 null.
let bumpMockBid: (() => void) | null = null

/**
 * 낙관 행 전송 타임아웃 (S15P11A704-424).
 *
 * pending은 **서버 에코 승격**이나 **큐 폐기 신호**(markChatFailed)로만 풀린다. 그래서 둘 다 오지 않는 경우
 * — 순단으로 SEND가 새거나 에코만 유실되면 — 「전송 중…」이 영구히 남고, pending은 capChat 보호 행이라
 * 트림도 안 되며 재시도·닫기(400)는 failed 행만 대상이라 그 경로로도 못 들어간다. 클라이언트 타임아웃이
 * 없던 것이 원인이다.
 *
 * 값은 STOMP heartbeat(10초)와 같은 자릿수로 둔다. 정상 왕복은 1초 안이라 오탐이 아니고, 재연결 큐가
 * 이보다 늦게 flush돼 뒤늦게 성공하면 **에코가 failed 행을 확정 행으로 통째 교체해 스스로 바로잡는다**
 * (handlers.handleChatMessage는 병합이 아니라 교체다) — 잠깐 「전송 실패」가 보였다 확정되는 것은
 * 영구 대기보다 낫다고 보고 수용한다.
 */
export const CHAT_SEND_TIMEOUT_MS = 10_000

/** clientMessageId → 대기 타이머. 승격·폐기·퇴장에서 정리한다. */
const sendTimers = new Map<string, ReturnType<typeof setTimeout>>()

function clearChatSendTimer(clientMessageId: string): void {
  const t = sendTimers.get(clientMessageId)
  if (t === undefined) return
  clearTimeout(t)
  sendTimers.delete(clientMessageId)
}

/**
 * 전송 타임아웃을 걸어 pending 행을 failed로 승격시킨다.
 *
 * 스토어 액션이 아니라 모듈 함수인 이유: `stores/live.ts`의 state·action 시그니처는 FE1·FE2 합의 사항이라
 * (AGENTS.md 규칙 4) 내부 사정으로 액션을 늘리지 않는다. 호출부가 이 파일 안뿐이다.
 */
function armChatSendTimeout(
  store: { chat: ChatMessage[]; markChatFailed: (clientMessageId: string) => void },
  clientMessageId: string,
): void {
  clearChatSendTimer(clientMessageId)
  sendTimers.set(
    clientMessageId,
    setTimeout(() => {
      sendTimers.delete(clientMessageId)
      // **아직 pending인 행만** 승격한다. 서버 에코는 clientMessageId를 유지한 채 행을 교체하므로,
      // 가드 없이 markChatFailed를 부르면 이미 전송된 확정 메시지를 「전송 실패」로 만든다.
      const row = store.chat.find((m) => m.clientMessageId === clientMessageId)
      if (row?.pending === true) store.markChatFailed(clientMessageId)
    }, CHAT_SEND_TIMEOUT_MS),
  )
}

// 낙관적 렌더용 임시 id — 서버 messageId(ULID 문자열)와 겹치지 않게 `opt-` 프리픽스 문자열로 발급한다(S15P11A704-416).
// 서버 브로드캐스트 에코가 오면 handlers.handleChatMessage가 이 행을 서버 값으로 교체(승격)한다.
let optimisticIdSeq = 0
function nextOptimisticId(): string {
  optimisticIdSeq += 1
  return `opt-${optimisticIdSeq}`
}

export const useLiveStore = defineStore('live', {
  state: () => ({
    // ── 연결·방 ──
    roomId: null as number | null,
    /**
     * 이 방 **입장**의 일련번호 (S15P11A704-367). `joinRoom`이 들어올 때마다 새 값을 받는다.
     *
     * 화면이 언마운트 정리에서 **"내가 열었던 그 입장이 아직 살아 있는가"**를 물을 때 쓴다.
     * `roomId` 대조로는 **같은 방을 다시 여는 경로**에서 값이 같아 통과해 버린다 —
     * 콘솔의 「라이브로 이동」이 실제로 그 경로이고, 그때 지연 `$reset()`이 라이브가 방금 세운
     * 상태를 지운다. 방 번호가 아니라 입장 번호를 봐야 구분된다.
     */
    roomSession: 0,
    connectionStatus: 'idle' as ConnectionStatus,
    /**
     * WS-03 단조 증가 버전 — 재접속 시 이벤트 역전·누락 판정에 쓸 값.
     * ⚠️ 아직 각 이벤트 data에 stateVersion이 없고(socket/types.ts §6-8) 버퍼 재생도 미구현이라,
     * 현재는 스냅샷에서만 채워진다. null = 스냅샷 미수신 (버전 0 스냅샷과 구분).
     */
    stateVersion: null as number | null,

    // ── 경매 상태·타이머 (BID-05) ──
    /**
     * 경매 진행 상태 문자열. 대기/라이브/종료는 rooms.ts의 AuctionStatus(WAITING|LIVE|ENDED)와 같은 개념이나,
     * 스냅샷·소켓엔 SETTLING/SETTLED/NO_SALE 등 진행 세부 상태가 더 온다(목록 API 3값과 동일 개념인지
     * S15P11A704-227 확인 대기) → 좁히지 않고 string으로 둔다.
     */
    auctionStatus: 'WAITING' as string,
    /**
     * 마지막 종료 계열 이벤트 원본 (AUCTION_ENDED·SETTLED·NO_SALE·CANCELED). null = 아직 진행 중.
     *
     * 상태 문자열(`auctionStatus`)과 **따로 두는 이유**: 마감·공개 오버레이(BID-10)는 낙찰자·최종가·
     * 유찰 사유가 있어야 그려지는데 그 값들이 이 payload에만 있다. `predictionSettlement`과 같은
     * 「신호를 스토어에 남기고 화면이 watch」 패턴이고, 소비처는 `LiveView` → `reveal.apply` 하나다.
     *
     * ⚠️ 예전 주석은 "소켓이 실어오는 결과값은 스토어에 보관하지 않는다 — 결과 화면이 REST로 받는다"
     * 였는데, 그게 실서버에서 오버레이가 아예 안 뜨던 원인이다. 결과 화면(DEAL-06 REST)은 종료 **후**
     * 상세이고, 오버레이는 종료 **순간**의 전원 동시 공개라 소켓 payload 말고는 소스가 없다.
     * 방 스코프라 joinRoom($reset)에서 함께 지워진다.
     */
    endEvent: null as AuctionEndEvent | null,
    /**
     * 서버 권위 마감 시각 (epoch ms, BID-05).
     *
     * 쓰는 곳은 다섯이다. `socket/handlers.ts`의 넷 — `handleTimeSync`(BID-05),
     * `handleAuctionStarted`(ROOM-05), `handleAuctionExtended`(+5분, BID-03), `applySnapshot`(WS-03) —
     * 과 **콘솔의 방송 시작 응답**(`ConsoleView.startNow`, ROOM-05가 `startedAt + durationMinutes`를
     * 계산해 준다). 마지막 하나가 소켓 밖이라 「소켓이 세운다」로 적으면 안 된다 (S15P11A704-523).
     *
     * ⚠️ 화면이 카운트다운에 넘길 값은 이게 아니라 **`deadlineAt` 게터**다 —
     * 위 경로가 하나도 없는 구간을 목록 응답의 `endAt`으로 메운다.
     */
    endsAt: null as number | null,
    /**
     * 서버-클라이언트 시각 보정값 (ms). null이면 아직 서버 시각을 못 받았다는 뜻이고,
     * 그때 남은 시간을 계산하면 Date.now() 단독이 되어 규칙 5 위반이라 표시를 비운다('--:--').
     *
     * 쓰는 곳은 셋이다 — `socket/handlers.ts`의 `handleTimeSync`(TIME_SYNC),
     * `WaitingRoomView`의 join 응답 `serverTime`, 목 대기방의 `seedMockServerClock`.
     * (예전 주석은 「TIME_SYNC로만 갱신」이었는데 뒤의 둘이 붙으면서 사실이 아니게 됐다.)
     *
     * ⚠️ 화면이 카운트다운에 넘길 값은 이게 아니라 **`clockOffset` 게터**다 —
     * 위 경로가 하나도 없는 구간을 REST 응답의 `serverTime`으로 메운다 (S15P11A704-265).
     */
    serverOffset: null as number | null,
    extensionCount: 0, // 판매자 연장 횟수 (BID-03)
    remainingExtensions: null as number | null, // 남은 연장 횟수 (서버 권위 — 스냅샷·AUCTION_EXTENDED가 직접 준다)

    // ── 비크리 공개 집계 (규칙 6 — 공개 허용값은 이 둘뿐) ──
    // null = 스냅샷/이벤트 미수신. "0건 0명"을 사실로 렌더하지 않기 위함.
    // 갱신 주체는 핸들러(handlers.ts, 202)의 BID_ACCEPTED·스냅샷이며, FE1 LiveView가 로컬 ref 대신
    // 이 값을 읽도록 바꾸는 것은 후속 필수 작업이다(현재 LiveView는 로컬 ref 사용).
    bidCount: null as number | null, // 누적 입찰 건수
    participantCount: null as number | null, // 입찰 참여자 수 (고유성은 스냅샷 bidParticipantCount만 문서 보장, BID_ACCEPTED엔 명시 없음)
    viewerCount: null as number | null, // LIVE-03 활성 STOMP 세션 수 (멀티탭 중복)

    // ── 내 입찰 (BID-06) ──
    myBid: null as number | null, // 비크리 — 내 입찰가만 보임
    /** 배타 잠금(규칙 7) 복원 근거 — WS-03 스냅샷 myParticipationType. 첫 액션 시 확정, 이후 불변 */
    myParticipationType: 'NONE' as ParticipationType,

    // ── 내 예측 (PRED-01) — 재접속 복원 전용 (S15P11A704-388) ──
    //
    // 필드명을 스냅샷 와이어명 그대로 둔 이유: 이 둘은 여기서 소비되지 않고
    // `prediction.restoreSubmitted(...)`로 그대로 넘어가는 통과값이다. 이름을 줄이면
    // (myBid처럼) 스냅샷→스토어→prediction 경로에 매핑표가 하나 생긴다.
    // 제출 직후의 값은 prediction 스토어(myPrediction·myStake)가 소유하고, 여기 두 값은
    // **스냅샷이 유일한 갱신 주체**다 — 제출 시 여기를 patch하지 않는다.
    myPredictedPrice: null as number | null,
    myStakeAmount: null as number | null,
    /**
     * PRED-03 `PREDICTION_SETTLED` 수신 사실 (S15P11A704-543). null = 미수신.
     *
     * 명세 처리 규칙이 "수신하면 PRED-02를 호출한다"라 **신호를 스토어에 남긴다** —
     * 화면이 이걸 watch해서 예측 결과를 재조회한다(폴링 금지, 규칙 5의 자체 계산 금지와 같은 취지).
     * 페이로드 자체는 분포·배당이 아니다(규칙 7): 배치 종료 상태와 반영 건수뿐이고,
     * 실서버는 지금 그마저도 `data: {}`로 비워 보낸다(`PredictionSettledMessage.completed()`) —
     * 그래서 **필드 값으로 분기하지 않고 "왔다"만 쓴다.**
     * 방 스코프라 joinRoom($reset)에서 함께 지워진다.
     */
    predictionSettlement: null as PredictionSettledData | null,

    // ── Vibe (BID-04) ──
    vibeFeed: [] as string[], // "누군가 베팅 중...🔥". 상한 유지는 핸들러(202)가 한다 — 실소켓이면 무한 증가 주의
    /**
     * 예측 참여 피드 (PRED-01). **`vibeFeed`와 합치지 않는다** — 그 배열은 경매봉을 내려치는
     * 입력이라(`media/useBidPulse.ts`) 섞이면 입찰 연출이 통째로 예측에 울린다. 이유는 핸들러 주석.
     */
    predictionFeed: [] as string[],

    // ── 채팅 (CHAT) ──
    chat: [] as ChatMessage[],
    // 과거 조회(S15P11A704-379) — 위로 스크롤 시 이전 메시지 REST 로드. hasMore=더 과거 남음, loadingOlder=요청 중(중복 방지).
    chatHasMore: true,
    chatLoadingOlder: false,
    /**
     * 과거 조회가 **막혀서** 끊겼는지 (S15P11A704-679).
     *
     * 4xx는 다시 물어도 같은 답이라 `chatHasMore`를 내려 재요청을 끊는데, 그러면 화면이
     * 「대화의 처음이에요」를 띄운다 — 401(비로그인)·403·비공개 방 404에서 그건 **거짓말**이다.
     * 서버가 실제로 마지막 페이지를 준 경우(`hasMore:false`)와 구분하려고 플래그를 하나 든다.
     * 방 스코프라 joinRoom($reset)에서 함께 지워진다.
     */
    chatOlderBlocked: false,
    pinnedNotice: null as PinnedNotice | null, // CHAT-02 고정 공지
    muted: false, // AI-05 CHAT_MUTED (muted/mutedUntil 정합·영구제한 의미는 후속)
    mutedUntil: null as number | null, // 제한 해제 시각 (서버값 기준 표시, epoch ms)
    // AI-05 MODERATION_WARNING — 개인 검열 경고. null=경고 없음. 갱신 주체는 handlers.dispatchModeration (S15P11A704-356).
    moderationWarning: null as ModerationWarning | null,

    // ── 질문 큐 (CHAT-06, S15P11A704-364) ──
    // 콘솔이 REST로 시드하고 소켓 /questions(QUESTION_ADDED/REMOVED)가 실시간 유지한다(handlers.dispatchQuestionTopic).
    questions: [] as QuestionItem[],
  }),
  getters: {
    /**
     * 카운트다운이 쓸 서버 시각 보정값 (BID-05, S15P11A704-265).
     *
     * 이 방에서 얻은 `serverOffset`이 **우선**이고, 아직 없으면 REST가 준 값
     * (`room.serverOffset` — 목록·상세 응답의 `serverTime`)으로 대신한다. 소켓이 서는 데 걸리는
     * 동안 라이브·콘솔의 타이머가 `--:--`로 굳어 있던 구간을 메우는 게 전부다.
     *
     * 둘 다 없으면 여전히 null이고, 그때 useCountdown은 남은 시간을 지어내지 않는다(규칙 5).
     * 이 게터는 **쓰지 않는다** — `serverOffset`의 쓰기 경로(위 세 곳)는 그대로 두고 읽기만 폴백한다.
     */
    clockOffset: (state): number | null => state.serverOffset ?? useRoomStore().serverOffset,
    /**
     * 카운트다운이 쓸 마감 시각 (BID-05, S15P11A704-523). `clockOffset`과 같은 모양이다 —
     * 이 방에서 얻은 `endsAt`(위 다섯 경로)이 우선이고, 아직 없으면 목록에서 받아둔 `endAt`으로 대신한다.
     *
     * 이게 없으면 `clockOffset`만 채워도 소용이 없다: `useCountdown`은 마감 시각이 없으면
     * 오프셋과 무관하게 `--:--`를 낸다. 265에서 오프셋만 메웠을 때 라이브 칩이 그대로였던 이유다.
     */
    deadlineAt: (state): number | null => state.endsAt ?? useRoomStore().deadlineOf(state.roomId),
    /**
     * 과거 조회 보관 상한 도달 여부 (S15P11A704-406). loadOlderChat 가드와 ChatPanel 안내·스크롤 게이트가
     * **같은 판정**을 읽도록 한 곳으로 뺀다 — 두 곳에서 따로 세면 상한 의미가 바뀔 때 한쪽만 고쳐 어긋난다(리뷰 nit).
     */
    atOlderChatCap: (state): boolean =>
      state.chat.reduce((n, m) => (m.older ? n + 1 : n), 0) >= CHAT_OLDER_MAX,
  },
  actions: {
    /**
     * 방 입장 — 이전 방의 상태(채팅·집계·타이머 등)를 비우고 roomId를 잡는다.
     * 화면 재진입·방 전환 시 이전 방 채팅이 남지 않게 한다 — 실소켓 재구독에도 필요한 동작이라 여기 둔다.
     */
    joinRoom(roomId: number): void {
      this.$reset()
      this.roomId = roomId || null
      // `$reset()`이 0으로 되돌린 뒤에 새 번호를 받는다 — 같은 방을 다시 열어도 값이 달라진다.
      this.roomSession = ++roomSessionSeq
    },

    /**
     * 방 입장 — 상태 초기화 후 라이브 소스에 연결한다 (S15P11A704-208).
     * 모드 인식 단일 진입점: 실 모드는 STOMP(liveSocket, 207), 목 모드는 목 라이브 피드(liveFeed, 203).
     * 두 소스 모두 동적 import로 로드한다 — 모드별 번들 분리 + 정적 순환참조 회피.
     * (화면이 목 드라이버 대신 이 액션을 호출하도록 바꾸는 것은 FE1 LiveView 전환, S15P11A704-299.)
     *
     * `opts.questions`는 **판매자 화면(콘솔)만** 켠다 — 질문 큐 구독은 BE 인가가 판매자 전용이라
     * 참가자가 켜면 입장마다 SUBSCRIPTION_DENIED를 받는다(S15P11A704-541). 목 모드와는 무관하다.
     */
    async connect(roomId: number, opts?: LiveSocketOptions): Promise<void> {
      const generation = ++connectionGeneration
      // 이전 소스 정리 — disconnect 없이 재호출(방 전환)돼도 목 피드가 이중으로 돌지 않게(리뷰 1).
      // 실 모드는 connectLiveSocket 선두에서 자체 정리하므로 여기선 목 피드·전송함수만 비운다.
      stopMockFeed?.()
      stopMockFeed = null
      bumpMockBid = null
      chatPublish = null
      this.joinRoom(roomId)
      this.seedChatHistory()
      this.connectionStatus = 'connecting' // 모드 무관 연결 상태 UI (리뷰 3)
      if (isMockApi()) {
        // 목 모드 — 실 WS 서버가 없으므로 목 피드가 스토어를 구동한다. 채팅은 낙관 반영만.
        // ⚠️ 목도 await한다 — 여기도 늦은 꼬리가 가능하다. 가드 없이 두면 떠난 화면의 피드가 되살아난다.
        const { startMockLiveFeed } = await import('@/mocks/liveFeed')
        if (generation !== connectionGeneration) return
        const feed = startMockLiveFeed(this)
        stopMockFeed = feed.stop
        bumpMockBid = feed.bump
        this.connectionStatus = 'connected'
      } else {
        const socket = await import('@/features/live/socket/liveSocket')
        if (generation !== connectionGeneration) return
        socket.connectLiveSocket(this, roomId, opts)
        chatPublish = socket.publishChat
        // 채팅 실패 통지 — 폐기된 낙관 행(383)과 서버가 거절한 행(사유 포함, 558)을 '전송 실패'로 표시한다.
        socket.setChatDroppedHandler((id, reason) => this.markChatFailed(id, reason))
      }
    },

    /**
     * 대기방 실시간 연결 (ROOM-07 / CHAT-01, S15P11A704-270).
     * 라이브와 **같은 STOMP 토픽**(채팅·AUCTION_STARTED·viewers)에 붙는다 — connectLiveSocket(207)을 재사용해
     * 대기 채팅 송수신·전원 라이브 전환·시청자 수를 한 연결로 처리한다. 전환은 handleAuctionStarted가 세팅하는
     * auctionStatus='LIVE'를 화면이 구독해 감지한다(단일 소스).
     *
     * 방 스코프(joinRoom)는 호출부(WaitingRoomView.enter)가 이미 잡았다고 보고 여기서 재초기화하지 않는다
     * — enter()가 시드한 viewerCount 등을 지우지 않기 위함. 정리는 공용 disconnect()가 담당한다.
     * 목 모드 대기방은 컴포넌트가 mocks/waitingRoom 드라이버로 구동하므로 이 액션은 **실 모드에서만** 호출된다.
     */
    async connectWaitingRoom(roomId: number): Promise<void> {
      // 대기방 → 라이브는 실제로 제일 자주 밟는 전환이라 여기에도 세대 가드가 필요하다 (S15P11A704-329)
      const generation = ++connectionGeneration
      chatPublish = null
      this.seedChatHistory()
      this.connectionStatus = 'connecting'
      const socket = await import('@/features/live/socket/liveSocket')
      if (generation !== connectionGeneration) return
      socket.connectLiveSocket(this, roomId)
      chatPublish = socket.publishChat
      socket.setChatDroppedHandler((id, reason) => this.markChatFailed(id, reason))
    },

    /**
     * 데모 전용 — 내 입찰을 집계(bidCount)에 즉시 반영한다 (S15P11A704-355 리뷰 3).
     * 목 모드에선 목 피드의 카운터를 올려 브로드캐스트하고, 실 모드에선 서버 BID_ACCEPTED가 담당하므로 무동작(null).
     */
    bumpMyBid(): void {
      bumpMockBid?.()
    },

    /** 퇴장 — 목 피드/실 연결 중 돌던 쪽을 정리하고 전송 함수를 비운다 */
    async disconnect(): Promise<void> {
      const generation = ++connectionGeneration
      chatPublish = null
      bumpMockBid = null
      // 대기 타이머 전량 정리 (S15P11A704-424) — 방을 나간 뒤 뒤늦게 도는 타이머를 남기지 않는다.
      // 남아도 pending 가드에 걸려 오작동은 없지만, 정리 지점이 있는데 미루지 않는다.
      sendTimers.forEach((t) => clearTimeout(t))
      sendTimers.clear()
      if (isMockApi()) {
        // 목 모드 — 목 번들 순수성 유지를 위해 liveSocket을 import하지 않는다(리뷰 2)
        // 여기에 세대 가드가 없는 것은 **await가 없어 꼬리가 생기지 않기** 때문이다.
        // ⚠️ 이 분기에 await를 추가하게 되면(예: 목 모듈 동적 import) `connect()`의 목 분기처럼
        //    가드도 같이 넣어야 한다. 안 넣으면 가드가 조용히 사라진다.
        stopMockFeed?.()
        stopMockFeed = null
      } else {
        const socket = await import('@/features/live/socket/liveSocket')
        // 그사이 다음 화면이 연결을 세웠으면 **그 연결을 끊으면 안 된다.**
        // `disconnectLiveSocket`은 `active`를 누가 만들었는지 보지 않아서, 여기서 막지 않으면
        // 남의 STOMP 연결이 끊긴다. 상태 쓰기도 같이 건너뛴다 — 지금 상태의 주인은 다음 화면이다.
        if (generation !== connectionGeneration) return
        socket.setChatDroppedHandler(null)
        socket.disconnectLiveSocket()
      }
      this.connectionStatus = 'closed'
    },

    /**
     * CHAT-01·06 채팅 전송. 닉네임은 auth 스토어에서 연동하고, 멱등키(clientMessageId)를 붙인다.
     *
     * 낙관적 반영: 보낸 즉시 내 메시지를 목록에 넣는다(임시키 `opt-N`). 서버가 브로드캐스트로 되돌려주는
     * 에코는 같은 clientMessageId라 핸들러(handlers.ts handleChatMessage)가 걸러 중복 렌더를 막는다 —
     * 이것이 멱등의 핵심이다. 재전송(네트워크 실패) 시에도 같은 키를 재사용하면 서버가 중복 저장을 막는다.
     * 실제 전송(/app/auctions/{id}/chat SEND)은 STOMP 클라이언트 티켓에서 이 payload를 그대로 쏜다.
     */
    sendChat(content: string, isQuestion = false): void {
      // 뮤트(AI-05) 중엔 전송하지 않는다 — UI 잠금 전 경합(뮤트 수신 직전 전송)으로 낙관 행이 올라갔다
      // 서버 거절로 유령 메시지가 되는 걸 막는다.
      if (this.muted) return
      const text = content.trim()
      if (!text) return
      // CHAT-01 확정 상한 — 서버가 초과를 거절하므로 왕복 전에 막는다(단위는 코드 포인트).
      // 닉네임(auth.ts `completeOnboarding`)·방 제목(RoomCreateView)과 같은
      // 「입력 캡(maxlength) + 제출 시 검증」 이중 구조다.
      if ([...text].length > CHAT_MAX_LENGTH) return
      const payload: ChatSendPayload = {
        content: text,
        isQuestion,
        clientMessageId: newRequestId(),
      }
      // 닉네임 연동 — 내 프로필 닉네임(미설정이면 null, 렌더러가 null을 처리한다)
      const nickname = useAuthStore().user?.nickname ?? null
      // 상한 적용은 capChat — 낙관 행(pending/failed)은 밀려나지 않게 예외 처리한다(S15P11A704-399).
      this.chat = capChat([
        ...this.chat,
        {
          id: nextOptimisticId(),
          nickname,
          text: payload.content,
          isQuestion: payload.isQuestion,
          clientMessageId: payload.clientMessageId,
          // 낙관 행은 아직 서버 마스킹 전 — 비움. 서버 에코가 오면 maskedFields 포함해 통째 교체된다.
          maskedFields: [],
          createdAt: new Date().toISOString(),
          // 실 전송(chatPublish 주입됨)이면 에코 승격 전까지 대기 표시. 목 모드(null)는 에코가 없어 대기로 안 둔다.
          pending: chatPublish !== null,
        },
      ])
      // 실 STOMP 연결 중이면 SEND(연결 시 주입됨). 미연결(목 모드)이면 무동작 — 위 낙관 반영만 남는다.
      chatPublish?.(payload)
      // 목 모드는 pending을 세우지 않으므로 타이머도 걸지 않는다(승격시킬 대상이 없다).
      if (chatPublish !== null) armChatSendTimeout(this, payload.clientMessageId)
    },

    /**
     * 전송 실패 표시 (S15P11A704-383). 큐 폐기·타임아웃·서버 거절(558)로 못 나간 낙관 행을 clientMessageId로 찾아
     * pending을 해제하고 failed를 세운다 — 「전송 중…」이 무한히 남지 않고 「전송 실패」로 바뀌어 재입력을 알린다.
     * reason(서버 표시용 문구)이 있으면 그 사유를 함께 남긴다. 이미 상한(CHAT_KEEP) 밖으로 밀려 없으면 조용히 무시한다.
     */
    markChatFailed(clientMessageId: string, reason?: string): void {
      // 이미 failed로 갈 행이라 대기 타이머는 쓸모가 없다(S15P11A704-424).
      clearChatSendTimer(clientMessageId)
      const idx = this.chat.findIndex((m) => m.clientMessageId === clientMessageId)
      if (idx === -1) return
      // **에코로 확정된 행은 실패로 만들지 않는다** — 에코는 clientMessageId를 유지한 채 행을 서버 값으로
      // 교체하므로(handlers.handleChatMessage) 키만 보면 이미 전송된 메시지가 「전송 실패」가 된다.
      // 판별은 임시키 프리픽스다(loadOlderChat 커서와 같은 기준) — pending으로 보면 낙관 행을 pending:false로
      // 두는 목 모드가 통째로 걸린다.
      if (!this.chat[idx].id.startsWith('opt-')) return
      const next = [...this.chat]
      next[idx] = { ...next[idx], pending: false, failed: true, failReason: reason }
      this.chat = next
    },

    /**
     * 전송 실패 행 재시도 (S15P11A704-400) — 같은 clientMessageId로 다시 전송한다(멱등이라 서버가 중복 저장을 막는다).
     * 행을 pending으로 되돌리고 payload를 재구성해 SEND한다. 연결돼 있으면 즉시, 재연결 중이면 큐잉(373).
     * 또 실패하면 markChatFailed가 다시 failed로 되돌린다(재시도 루프 성립). 없거나 실패 상태가 아니면 무시.
     */
    retryChat(clientMessageId: string): void {
      // 뮤트(AI-05) 중엔 재시도하지 않는다 — sendChat과 동일 가드(리뷰 1). 없으면 pending으로 바뀐 뒤 서버 거절이
      // 에코도 큐 폐기도 아니라 markChatFailed가 안 불려 영구 「전송 중」이 되고, pending은 capChat 보호 행이라 트림도 안 된다.
      if (this.muted) return
      const idx = this.chat.findIndex((m) => m.clientMessageId === clientMessageId && m.failed)
      if (idx === -1) return
      const row = this.chat[idx]
      const payload: ChatSendPayload = {
        content: row.text,
        isQuestion: row.isQuestion,
        clientMessageId, // 같은 키 재사용 — 멱등
      }
      const next = [...this.chat]
      // 목 모드(chatPublish null)는 에코가 없어 영구 대기를 피하려 pending을 안 세운다(sendChat과 동일, 리뷰 3).
      // 지난 실패 사유(558)는 여기서 지우지 않는다 — 다시 실패하는 유일한 경로인 markChatFailed가 failReason을
      // 항상 덮어쓰므로(사유 없으면 undefined) 낡은 문구가 남을 수 없다. 지우는 코드는 도달 불가였다.
      next[idx] = { ...row, failed: false, pending: chatPublish !== null }
      this.chat = next
      chatPublish?.(payload)
      // 재시도도 pending으로 돌아가므로 같은 타임아웃을 다시 건다 — 없으면 재시도만 영구 대기에 빠진다(424).
      if (chatPublish !== null) armChatSendTimeout(this, clientMessageId)
    },

    /**
     * 전송 실패 행 닫기 (S15P11A704-400) — 사용자가 재시도 대신 지운다. 실패 행만 대상(확정 메시지는 안 건드림).
     *
     * 대기 타이머(424)를 여기서 정리하지 않는 이유: 이 액션은 failed 행만 지우고, failed가 되는 두 경로가
     * 이미 타이머를 없앤다 — 큐 폐기 신호는 markChatFailed가 clearChatSendTimer를 부르고, 타임아웃 자신은
     * 발화 시 맵에서 스스로 지운다. 즉 "failed인데 타이머가 살아 있는" 상태가 없다.
     * (정리 코드를 넣어 뒀다가 변형 테스트로 도달 불가를 확인해 걷어냈다.)
     */
    dismissChatFailed(clientMessageId: string): void {
      this.chat = this.chat.filter((m) => !(m.clientMessageId === clientMessageId && m.failed))
    },

    /**
     * 빈 채팅창 이력 시드 (S15P11A704-679) — 방 입장 직후 한 번, 최신 한 페이지를 채운다.
     *
     * 빈 목록은 넘칠 게 없어 스크롤 이벤트가 아예 안 나므로 ChatPanel의 `onScroll`이 못 도는
     * 유일한 구간이다. 여기만 채워 주면 이후는 기존 상단 스크롤이 잇는다.
     *
     * **방을 잡는 쪽(연결 경로)에서 부른다.** 패널의 `onMounted`에 두면 Vue가 자식을 부모보다 먼저
     * 마운트해서 LiveView의 `connect()`(→ `joinRoom()` → `$reset()`)보다 **앞서** 돈다 — 이전 방의
     * `roomId`(또는 null→0)로 요청이 나가고 응답은 `$reset()` 뒤에 도착한다. 여기서 부르면 그
     * 순서 의존이 없다.
     *
     * 이미 채팅이 있으면 부르지 않는다. 지금 두 호출부는 앞에서 늘 비우므로(`connect`는 `joinRoom`,
     * `connectWaitingRoom`은 호출부 `WaitingRoomView.enter`가) 항상 시드가 돌지만, 채팅을 넘겨받는
     * 경로가 생기면 그때 이 조건이 중복 조회를 막는다.
     * 중복·끝·상한·차단 판정은 전부 `loadOlderChat`이 한다.
     *
     * ⚠️ 알려진 비용: `connect()`는 채팅을 그리지 않는 화면(ConsoleView·ResultView)도 부른다 —
     * 그 둘에서 읽는 이가 없는 요청이 한 번씩 나간다. 화면당 1회·최대 30행이라 그대로 뒀다.
     * 거슬리면 채팅을 그리는 화면(LiveView·WaitingRoomView)이 직접 부르게 옮긴다 —
     * `connect()`는 `joinRoom()`을 **동기로** 끝내고 첫 await로 가므로 호출 직후에 불러도 방은 잡혀 있다.
     */
    seedChatHistory(): void {
      // 이력 조회는 인증이 필수다 — 비로그인 시드는 확정 401 왕복 + warn 노이즈일 뿐이고,
      // 그 401이 세운 `chatOlderBlocked`가 남으면 **로그인한 뒤에도 그 방에선 이력이 안 붙는다**.
      // 아예 안 쏘면 차단 흔적이 없어 로그인 후 상단 스크롤이 정상 동작한다.
      if (!useAuthStore().isLoggedIn) return
      if (this.chat.length === 0) void this.loadOlderChat()
    },

    /**
     * 채팅 과거 조회 (CHAT, S15P11A704-379) — 현재 가진 가장 오래된 확정 메시지 이전 한 페이지를 REST로 불러와 앞에 붙인다.
     * 중복 요청(loadingOlder)·끝(!hasMore)이면 무동작. 커서는 오래된 **확정** 메시지의 서버 id(ULID 문자열).
     * 확정 판별은 `!id.startsWith('opt-')` — 서버 id(ULID)를 받은 행(라이브·older)만 커서 후보다. 낙관 임시키(`opt-N`)는 건너뛴다(S15P11A704-416).
     * (pending 대신 id로 보는 이유: 목 모드는 낙관 행이 pending:false라 pending 기준이면 임시키가 커서로 새어든다.)
     * 커서 후보가 없으면(빈 채팅창·낙관 행뿐) **커서 없이** 부른다 — 서버가 첫 페이지(최신 30개)를 준다(S15P11A704-679).
     * dedup: 이미 있는 id는 제외한다(재접속·경계 겹침 방어).
     * 실패 처리는 상태로 갈린다: 5xx·네트워크는 조용히 두고 재스크롤 시 재시도(hasMore 유지),
     * 4xx(INVALID_CURSOR·ROOM_ACCESS_DENIED·404·401 등)는 다시 물어도 같은 답이라 hasMore를 내려 영구 재요청을
     * 끊고 `chatOlderBlocked`를 세운다 — 화면이 「대화의 처음이에요」를 띄우면 안 되는 종료이기 때문이다.
     */
    async loadOlderChat(): Promise<void> {
      if (this.chatLoadingOlder || !this.chatHasMore) return
      // 보관 중인 older가 상한이면 더 안 불러온다 — 무한 누적 방어(S15P11A704-406). 화면 안내와 같은 getter를 읽는다.
      if (this.atOlderChatCap) return
      const oldest = this.chat.find((m) => !m.id.startsWith('opt-'))
      // 이 **입장**을 캡처해 둔다 — 응답을 기다리는 사이 방이 바뀌면 이전 방의 이력을 새 방에 붙이게 된다
      // (LiveView·WaitingRoomView의 언마운트 정리와 같은 관용구, S15P11A704-367).
      // 로딩 플래그도 같이 지킨다: 새 방이 이미 자기 조회를 걸었는데 이 꼬리가 false로 덮으면 중복 요청이 샌다.
      const session = this.roomSession
      this.chatLoadingOlder = true
      try {
        const { fetchChatHistory } = await import('@/features/chat/history')
        const page = await fetchChatHistory(this.roomId ?? 0, oldest?.id)
        if (session !== this.roomSession) return
        const existing = new Set(this.chat.map((m) => m.id))
        // older 표시 — capChat이 상한 트림에서 보호한다(라이브 한 건에 방금 불러온 과거가 잘려나가지 않게, 리뷰 1).
        // 시드(첫 페이지)도 같은 표시를 받는다: 스크롤로 불러온 것이 아니어도 REST로 받은 과거라
        // 보호·상한 근거가 그대로 적용된다(30건이 CHAT_OLDER_MAX 400에서 차지하는 몫은 무시할 수준).
        const fresh = page.messages
          .filter((m) => !existing.has(m.id))
          .map((m) => ({ ...m, older: true }))
        this.chat = [...fresh, ...this.chat]
        this.chatHasMore = page.hasMore
      } catch (error) {
        if (session !== this.roomSession) return
        // 코드가 아니라 상태로 가른다 — 4xx는 요청이 틀렸다는 뜻이라 같은 요청을 반복할 이유가 없다.
        const { status, code } = toApiError(error)
        if (status !== null && status >= 400 && status < 500) {
          this.chatHasMore = false
          this.chatOlderBlocked = true
          // 조용히 끊으면 화면상 "이력이 원래 없는 방"과 구분되지 않는다 — 원인 코드를 흔적으로 남긴다.
          console.warn(`[chat] 이력 조회가 막혀 과거 조회를 멈춘다 — ${status} ${code}`)
        }
      } finally {
        if (session === this.roomSession) this.chatLoadingOlder = false
      }
    },
  },
})
