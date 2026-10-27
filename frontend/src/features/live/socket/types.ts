// 라이브 소켓 이벤트 타입 — 실 WS 명세 기준 전체 정의 (FE2, 지라 [FE2] 소켓 이벤트 타입 정의)
//
// 단일 소스: 백엔드 「최종(종합) 명세서」의 API 명세 테이블 + WebSocket 이벤트 명세.
//   docs/03-역할-경계-합의.md §6이 그 사본이고, 이 파일은 그 계약을 TS 타입으로 옮긴 것이다.
//   이름·필드는 **백엔드 명세가 단일 소스**다 — 임의로 추가·개명하지 않는다(§6).
//
// 폐기된 초안 이름(ENDS_AT_UPDATED · BID_PLACED · PRESENCE · AUCTION_CLOSED)은 정의하지 않는다.
//   → 각각 AUCTION_EXTENDED · BID_ACCEPTED · VIEWER_COUNT · AUCTION_ENDED/SETTLED/NO_SALE로 대체됐다(§6-7).
//
// "확정"과 "가정"을 구분해 적었다. 메인 토픽 경매 이벤트와 개인 큐(AI-05 검열 · WS-04 에러)는
// 명세·BE 구현으로 필드까지 확정이고, 아직 가정으로 남은 곳만 해당 자리에 표시해 뒀다.

import type { Id, IsoDateTime } from '@/shared/api/types'

// ─────────────────────────────────────────────────────────────
// 공통 래퍼 — 모든 WS 메시지는 `{ type, data }` (백엔드 공통 규격, AGENTS.md 규칙 9)
// ─────────────────────────────────────────────────────────────

/**
 * 백엔드 공통 메시지 형식. `destination`·`auctionId`·`serverTime`은 본문에 넣지 않는다
 * (auctionId는 구독 destination에, serverTime은 TIME_SYNC.data에만 — §6).
 *
 * ⚠️ `mocks/bidFeed.ts`에도 같은 모양의 로컬 복사본이 있다(FE1의 임시 목 파일).
 * 그 목이 실소켓으로 교체되며 삭제되면 이 정의가 유일본이 된다.
 */
export interface SocketEvent<TType extends string, TData> {
  type: TType
  data: TData
}

// ─────────────────────────────────────────────────────────────
// 메인 토픽  /topic/auctions/{auctionId}   (필드 확정 — 명세 WebSocket 이벤트 표)
// ─────────────────────────────────────────────────────────────

/** TIME_SYNC (BID-05) — serverOffset을 얻는 유일한 이벤트. 최초 입장·재연결·연장·주기 보정 시 발행 */
export interface TimeSyncData {
  serverTime: IsoDateTime
  endAt: IsoDateTime
}
export type TimeSyncEvent = SocketEvent<'TIME_SYNC', TimeSyncData>

/** AUCTION_STARTED (ROOM-05) — 판매자 수동 시작 → 대기방 전원 라이브 전환 트리거 */
export interface AuctionStartedData {
  startedAt: IsoDateTime
  endAt: IsoDateTime
}
export type AuctionStartedEvent = SocketEvent<'AUCTION_STARTED', AuctionStartedData>

/** AUCTION_EXTENDED (BID-03) — +5분 연장. 타이머 틱 이벤트는 없고 endAt 기준 클라이언트 렌더링 */
export interface AuctionExtendedData {
  endAt: IsoDateTime
  extensionCount: number
  /** 남은 연장 횟수 (extension_count는 0~3, docs/04 §3.4) */
  remainingExtensions: number
}
export type AuctionExtendedEvent = SocketEvent<'AUCTION_EXTENDED', AuctionExtendedData>

/**
 * BID_ACCEPTED (BID-01·02·04) — 비크리 공개 범위가 곧 이 세 필드다.
 * 입찰 금액·입찰자·순위·최고가는 **오지 않는다**(규칙 6). Vibe는 별도 이벤트가 아니라 이 필드.
 */
export interface BidAcceptedData {
  bidCount: number
  participantCount: number
  vibeMessage: string
}
export type BidAcceptedEvent = SocketEvent<'BID_ACCEPTED', BidAcceptedData>

/**
 * PREDICTION_ACCEPTED (PRED-01) — 신규 예측 참여의 공개 집계.
 *
 * `BID_ACCEPTED`에서 `bidCount`만 빠진 모양이고, `participantCount`는 **같은 값**이다 —
 * BE의 입찰·예측 두 Lua가 동일한 경매 상태 Hash의 `participantCount` 필드를 각각 HINCRBY 한다
 * (`bid-process.lua` · `prediction-participation-process.lua`). 입찰과 예측은 배타적이라(규칙 7)
 * 중복 계수도 없다. 그래서 두 이벤트가 같은 스토어 필드를 갱신해도 숫자가 튀지 않는다.
 *
 * `predictedPrice`·`stakeAmount`·실시간 분포는 **오지 않는다**(규칙 7, 비공개 예측 정책).
 * 없어서 못 쓰는 게 아니라 있으면 안 되는 값이다 — 연출도 이 값들에 기대지 않는다.
 */
export interface PredictionAcceptedData {
  participantCount: number
  /** `누군가 예측에 참여했습니다!` 고정 문구 (BE가 상수로 발행) */
  vibeMessage: string
}
export type PredictionAcceptedEvent = SocketEvent<'PREDICTION_ACCEPTED', PredictionAcceptedData>

/** AUCTION_ENDED (BID-07) — 서버 기준 마감. 신규 입찰·예측 차단 + 집계(비크리 정산) 시작 */
export interface AuctionEndedData {
  endedAt: IsoDateTime
  /** 종료 시점 상태 문자열. 값 목록은 명세에 없다(docs/04 §6.1) */
  status: string
}
export type AuctionEndedEvent = SocketEvent<'AUCTION_ENDED', AuctionEndedData>

/**
 * AUCTION_SETTLED (BID-07) — 낙찰·정산 완료. 전원 동시 공개.
 * 종료 후이므로 금액 공개가 규칙 6 위반이 아니다(비크리 최고가 vs 2등가 vs 최종가 구분 표기).
 */
export interface AuctionSettledData {
  /** 낙찰자 표시값(결과 화면 마스킹 정책은 DEAL-06 — 표시는 FE1 result 소관) */
  winner: string
  highestBidAmount: number
  secondHighestBidAmount: number
  finalPrice: number
  /** 최종가 결정 근거(2등가/하한가 등). 값 목록 미확인 */
  resultType: string
}
export type AuctionSettledEvent = SocketEvent<'AUCTION_SETTLED', AuctionSettledData>

/** AUCTION_NO_SALE (BID-07) — 유찰 + 홀드 반환 안내 */
export interface AuctionNoSaleData {
  /** 유찰 사유. 값 목록 미확인(docs/04 §6.1 termination_reason) */
  reason: string
  endedAt: IsoDateTime
}
export type AuctionNoSaleEvent = SocketEvent<'AUCTION_NO_SALE', AuctionNoSaleData>

/**
 * AUCTION_CANCELED (BID-07·DEAL-09) — 강제 종료 + 환급 완료.
 * 서버가 `AdminAuctionForceEndController`로 붙었다(S15P11A704-522) — 아래 3필드가 발행되는 값이다.
 */
export interface AuctionCanceledData {
  /**
   * **이 값은 방 전체에 브로드캐스트된다** — `/topic/auctions/{id}`는 그 방 시청자 전원이 구독한다.
   * 화면에 그릴 때는 서버가 실어 보낼 수 있는 값이 시청자에게 공개돼도 되는지부터 본다.
   * 신고 사유(`PROHIBITED_ITEM` 등)는 판매자에 대한 혐의라 여기 실리면 안 되고, 그래서
   * 운영자 콘솔은 고정값을 보낸다(`features/admin/useAdminForceEnd.ts`).
   * 모르는 값을 원문 그대로 노출하지 않는 처리는 `features/result/RevealOverlay.vue` 참고.
   */
  reason: string
  endedAt: IsoDateTime
  /** 종료 후 이동 경로(유찰 안내 화면 등) */
  resultPath: string
}
export type AuctionCanceledEvent = SocketEvent<'AUCTION_CANCELED', AuctionCanceledData>

/**
 * 종료 계열 4종. 마감·공개 오버레이(BID-10)가 소비하는 이벤트가 정확히 이 넷이다 —
 * 핸들러가 이 유니온 그대로 `live.endEvent`에 담고 화면이 그걸 `reveal.apply`에 넘긴다.
 * (`features/result/revealState.ts`가 같은 4종을 따로 선언하고 있었는데, 그 사본은 지웠다:
 *  두 벌이 있으면 화면이 스토어 값을 그대로 못 받아 캐스팅이 필요해진다.)
 */
export type AuctionEndEvent =
  AuctionEndedEvent | AuctionSettledEvent | AuctionNoSaleEvent | AuctionCanceledEvent

// AUCTION_PAUSED·AUCTION_RESUMED는 **정의하지 않는다** (S15P11A704-429).
// 판매자 연결이 끊기면 유예 없이 30초 무응답 시 즉시 유찰이라 일시정지 상태 자체가 없다.
// 유찰은 기존 AUCTION_ENDED/AUCTION_NO_SALE 경로로 온다.

/**
 * PREDICTION_SETTLED (PRED-03) — 정산 배치 종료 **신호만**. 개인 배당은 PRED-02 REST로 조회.
 *
 * 확정 명세(`backend/docs/api/prediction/events/prediction-settled.md`) 기준이다.
 * 명세 처리 규칙이 "수신하면 PRED-02를 호출한다"라, 핸들러는 이 payload를
 * `live.predictionSettlement`에 담고 화면이 그걸 watch해 재조회한다(S15P11A704-543).
 * ⚠️ 실서버는 아직 `data: {}`를 보낸다(`PredictionSettledMessage.completed()`) — 그래서 두 필드를
 * **optional로 선언한다.** 명세에 있다고 필수로 두면 타입이 "항상 온다"고 거짓말을 해서
 * 값 분기 코드가 컴파일에 막히지 않고 통과한다. 이 이벤트는 **값으로 분기하지 말고 "수신했다"만 쓴다.**
 * 폐기된 구간(bucket) 설계 어휘였던 `winningBucket`·`totalPool`은 명세에서 사라졌다 —
 * 분포·배당은 이 페이로드에 실리지 않고(규칙 7) 배치 종료 상태와 반영 건수만 온다.
 * (구 명세의 `PREDICTION_UPDATED`는 실시간 분포 비공개 원칙에 따라 삭제됐다 — 정의하지 않는다.)
 */
export interface PredictionSettledData {
  /** 배치 종료 상태. 반영 건이 0이어도 발행을 생략하지 않고 FAILED로 온다(화면이 집계 중에 굳지 않게) */
  settlementStatus?: 'COMPLETED' | 'PARTIAL' | 'FAILED'
  /** 정산이 반영된 예측 건수 — 분포·배당 정보가 아니다 */
  settledCount?: number
}
export type PredictionSettledEvent = SocketEvent<'PREDICTION_SETTLED', PredictionSettledData>

// ⚠️ 아래 두 가지는 이 토픽으로 오지만 명세 미확정이라 정의하지 않는다 — §6-8 BE 확정 대기:
//   1) BID-10 집계중(SETTLING) 상태: destination·설명만 있고 type 문자열이 명세에 없다.
//   2) stateVersion: 상태 변경 이벤트가 실어야 하는 값이지만 아직 각 data에 없다.
//      §6-5 재접속 버퍼 재생에서 event.stateVersion <= snapshot.stateVersion 폐기 판정의 전제라,
//      확정되면 각 *Data에 필드를 추가한다.
/** 메인 토픽으로 오는 모든 이벤트의 판별 유니온 — 핸들러가 `event.type`으로 좁힌다 */
export type AuctionTopicEvent =
  | TimeSyncEvent
  | AuctionStartedEvent
  | AuctionExtendedEvent
  | BidAcceptedEvent
  | AuctionEndedEvent
  | AuctionSettledEvent
  | AuctionNoSaleEvent
  | AuctionCanceledEvent
  | PredictionSettledEvent

// ─────────────────────────────────────────────────────────────
// 채팅 토픽  /topic/auctions/{auctionId}/chat
// ⚠️ data 필드는 명세 CSV에 비어 있어 DB 스키마(docs/04 §3.7)에서 끌어낸 FE 가정이다.
// ─────────────────────────────────────────────────────────────

/** CHAT_MESSAGE — 일반/질문 채팅 수신 (최종 명세 정합, S15P11A704-413) */
export interface ChatMessageData {
  /** chat_message PK — ULID 문자열(예: "01J8W3P2N9KQ4T"). 서버 중복 제거·과거조회 커서 키로 쓰인다(S15P11A704-416) */
  messageId: string
  /** 발신자 — 탈퇴 회원이면 null(sender_member_id ON DELETE SET NULL) */
  memberId: Id | null
  /** 발신자 닉네임 — 탈퇴 회원이면 null. 렌더러는 null을 반드시 처리한다 */
  nickname: string | null
  content: string
  /** CHAT-06 질문 채팅 여부 */
  isQuestion: boolean
  /** 멱등 처리용 — 내가 보낸 메시지가 브로드캐스트로 돌아온 것을 이 값으로 걸러낸다 */
  clientMessageId: string | null
  /**
   * 서버가 CHAT-03 마스킹을 발신 시점에 적용해 content를 이미 가린 뒤, 어느 필드를 가렸는지 알려준다.
   * (구 명세의 per-message moderationStatus/isDeleted는 폐기 — 최종 명세엔 없다. 개인 검열은 /user/queue/moderation.)
   */
  maskedFields: string[]
  createdAt: IsoDateTime
}
export type ChatMessageEvent = SocketEvent<'CHAT_MESSAGE', ChatMessageData>

/**
 * CHAT_DELETED — 이미 뿌려진 메시지가 사라진다(CHAT-01). 필드 확정이다:
 * 명세(`chat/chat-01-delete-auctions-by-auctionid-chats-by-messageid.md` :43-52,
 * `chat/chat-01-ws-topic-auctions-by-auctionid-chat-533776eb.md` :73-85)와
 * BE `StompChatDeletedEventPublisher`가 일치한다.
 *
 * **판매자·운영자 수동 삭제와 AI 사후 차단이 같은 이벤트로 온다** — 수신 처리는 하나면 둘 다 덮는다.
 * payload는 `messageId` 하나뿐이다: 삭제 사유와 처리 회원은 공개 payload에 싣지 않는 것이 명세 확정이라
 * 「누가 왜 지웠는지」를 화면이 말할 재료가 애초에 없다.
 *
 * 처리 규칙도 명세가 정한다 — 같은 messageId를 **화면과 로컬 버퍼에서 제거**한다(자리표시 tombstone이 아니다).
 * 이력·스냅샷·질문 큐는 서버가 `is_deleted`로 이미 걸러 주므로 재접속·과거 조회로 되살아나지 않는다.
 */
export interface ChatDeletedData {
  /** 삭제된 메시지 — ULID 문자열(CHAT_MESSAGE.messageId·ChatMessage.id와 같은 공간) */
  messageId: string
}
export type ChatDeletedEvent = SocketEvent<'CHAT_DELETED', ChatDeletedData>

/**
 * NOTICE_* — 고정 공지 등록·해제 계열(CHAT-02). 명세는 `NOTICE_*` 프리픽스만 준다.
 * 정확한 하위 타입명·필드가 미확정이라 프리픽스 매칭으로 처리한다(핸들러에서 startsWith).
 */
export interface NoticeData {
  /** 공지 원본 메시지 PK — ULID 문자열(채팅 messageId와 같은 공간, S15P11A704-416). 해제 이벤트 등에선 null */
  messageId: string | null
  content: string | null
}
// 판별자를 살리려 템플릿 리터럴을 쓴다 — TType이 `string`이면 유니온에서 좁혀지지 않아
// 채팅 토픽에서 CHAT_MESSAGE와 섞인다(리뷰 반영). 이제 'NOTICE_' 프리픽스가 타입으로 강제된다.
export type NoticeEvent = SocketEvent<`NOTICE_${string}`, NoticeData>

// ─────────────────────────────────────────────────────────────
// 질문 큐 토픽  /topic/auctions/{auctionId}/questions   (CHAT-06)
// ─────────────────────────────────────────────────────────────

/**
 * QUESTION_ADDED — 신규 질문이 커밋된 뒤 판매자 질문 토픽으로 발행된다.
 * 필드는 확정이다: 명세(`chat/chat-06-ws-topic-...-questions`)의 예시와
 * BE `StompQuestionAddedEventPublisher.QuestionAddedData`가 일치한다.
 */
export interface QuestionAddedData {
  /** chat_message PK — ULID 문자열. CHAT_MESSAGE.messageId와 같은 공간이라 별도 messageId 필드는 없다 */
  questionId: string
  /** 작성자 — 발행 시점 값이라 null이 아니다(`ChatMessage.memberId`는 양수 검증) */
  memberId: Id
  /** 작성자 닉네임 — `ChatMessage`가 non-null·non-blank로 검증하므로 null이 오지 않는다 */
  nickname: string
  content: string
  createdAt: IsoDateTime
}
export type QuestionAddedEvent = SocketEvent<'QUESTION_ADDED', QuestionAddedData>

/**
 * QUESTION_REMOVED — 답변 완료(RESOLVED)·검열(FILTERED)·메시지 삭제(DELETED)로 큐에서 빠진다.
 * 같은 명세 화면(`chat/chat-06-ws-topic-...-questions` :42)의 예시가 필드 소스다:
 * `{ "questionId": "01J8...", "reason": "RESOLVED" }` — questionId는 ADDED와 같은 ULID 문자열 공간.
 * ✅ BE 발행자가 실재한다 (S15P11A704-680에서 확인) — `QuestionQueueCommandService`(RESOLVED) ·
 *    `ChatAsyncModerationOutcomeHandler`(FILTERED, 2곳) · `ChatMessageDeletionService`(DELETED).
 */
export interface QuestionRemovedData {
  questionId: string
  /**
   * BE `QuestionRemovalReason` 그대로 — 'RESOLVED' | 'FILTERED' | 'DELETED'.
   * FE는 읽지 않지만 계약을 기록해 둔다. 값이 늘어도 화면이 안 깨지게 타입은 string으로 둔다.
   */
  reason: string
}
export type QuestionRemovedEvent = SocketEvent<'QUESTION_REMOVED', QuestionRemovedData>

// ─────────────────────────────────────────────────────────────
// 시청자 수 토픽  /topic/auctions/{auctionId}/viewers   (LIVE-03)
// ─────────────────────────────────────────────────────────────

/** VIEWER_COUNT — 고유 사용자 수가 아니라 **활성 STOMP 세션 수**다(멀티탭이면 중복, §6-3) */
export interface ViewerCountData {
  viewerCount: number
}
export type ViewerCountEvent = SocketEvent<'VIEWER_COUNT', ViewerCountData>

// ─────────────────────────────────────────────────────────────
// 개인 큐  /user/queue/moderation (AI-05) · /user/queue/errors (WS-04)
// 필드 확정 — AI-05는 명세에 data 예시가 전부 있고, WS-04는 BE 구현 확정분이다(아래 ERROR 주석).
// ─────────────────────────────────────────────────────────────

/** MODERATION_WARNING — 검열 경고(개인 수신, AI-05). 최종 명세 정합(S15P11A704-414) */
export interface ModerationWarningData {
  /** 경고 대상 메시지 (ULID 문자열, S15P11A704-416). 대상 메시지가 없으면 null */
  messageId: string | null
  /** 공개 허용 enum(예: OFF_PLATFORM_TRADE). 사용자에게 표시하지 않고 로깅·분기용 */
  reason: string
  /** 사용자에게 표시할 문구(예: 「외부 거래 유도는 제한됩니다.」) — 화면에 그대로 노출 */
  message: string
}
export type ModerationWarningEvent = SocketEvent<'MODERATION_WARNING', ModerationWarningData>

/** CHAT_MUTED — 채팅 제한(개인 수신, AI-05). amIMuted 스냅샷 필드와 대응(WS-03). 최종 명세 정합(S15P11A704-414) */
export interface ChatMutedData {
  /** 대상 경매 — 현재 구독 방과 대조용 */
  auctionId: Id
  /** 뮤트 지속 시간(초). 표시는 mutedUntil(서버 절대시각) 우선이고, 이 값은 자체 계산에 쓰지 않는다(규칙 5) */
  durationSeconds: number
  /** 제한 해제 시각 — 서버 값 기준으로 표시(자체 계산 금지, 규칙 5) */
  mutedUntil: IsoDateTime | null
}
export type ChatMutedEvent = SocketEvent<'CHAT_MUTED', ChatMutedData>

/** WS 에러 큐 — SEND·SUBSCRIBE 처리 실패 회신(WS-04). AUTH_EXPIRED는 재인증, AUTH_INVALID는 재시도 무의미 */
export interface WsErrorData {
  code: string
  /** 서버가 만든 **사용자 표시용** 문구(`WebSocketErrorCode.publicMessage()`) — 화면에 그대로 노출한다 */
  message: string | null
  /** 실패한 원 프레임의 destination(예: `/app/auctions/1/chat`). 어느 요청이 거절됐는지 구분용 */
  refDestination: string | null
  /** 요청이 실어 보낸 상관키 — 채팅 SEND면 clientMessageId다. 낙관 행을 찾아 실패 표시하는 열쇠(S15P11A704-558) */
  correlationId: string | null
}
// 타입명 'ERROR'·필드 모두 BE 확정분이다(`common/websocket/UserWebSocketErrorMessage`·`UserWebSocketErrorData`).
export type WsErrorEvent = SocketEvent<'ERROR', WsErrorData>

// ─────────────────────────────────────────────────────────────
// 클라이언트 → 서버 SEND 페이로드
// ─────────────────────────────────────────────────────────────

/**
 * SEND /app/auctions/{auctionId}/chat (CHAT-01·06). isQuestion=true면 질문 큐로 적재된다.
 * clientMessageId로 멱등 처리한다 — 재전송 시 같은 키를 재사용하면 서버가 중복 저장을 막고,
 * 브로드캐스트 에코도 이 값으로 걸러진다(handlers.ts handleChatMessage).
 * ⚠️ 명세 CSV에 body 필드 표가 없어 CHAT_MESSAGE 수신 필드에서 끌어낸 FE 가정이다(§6-4).
 */
export interface ChatSendPayload {
  content: string
  isQuestion: boolean
  clientMessageId: string
}
