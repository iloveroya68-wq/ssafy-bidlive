// ROOM-07 대기방 목 소켓 드라이버 (S15P11A704-64·65).
//
// **FE2의 실 STOMP 연동 전까지만 쓰는 임시 파일이다** (docs/03 §7의 목 드라이버와 같은 역할).
// 이벤트 이름·필드는 백엔드 명세를 그대로 따른다 — 목에서만 통하는 이름을 만들면
// 실소켓 전환 때 핸들러를 재사용한다는 전제가 깨진다(docs/03 §6-6).
//   - `AUCTION_STARTED` : 명세 「경매 방송 시작」 (ROOM-05) → `/topic/auctions/{auctionId}`
//   - `VIEWER_COUNT`    : 명세 「접속 세션 수 브로드캐스트」 (LIVE-03) → `/topic/auctions/{auctionId}/viewers`
//
// 파일명을 `waiting`으로 시작시킨 것은 같은 시기에 진행되는 다른 작업과 파일이 겹치지 않게 하기 위해서다.

import type { SocketEvent } from '@/mocks/bidFeed'
import type { ChatMessageData } from '@/features/live/socket/types'
import { ApiError } from '@/shared/api/errors'
import { toIsoDateTime, type IsoDateTime } from '@/shared/api/types'

/**
 * `AUCTION_STARTED.data` — 판매자가 수동으로 방송을 시작했다는 신호.
 * 대기방 전원을 라이브로 넘기는 유일한 트리거이며, `endAt`은 **서버가 확정한** 마감 시각이다(BID-05).
 *
 * ⚠️ 명세 「경매 방송 시작」의 예시 메시지에는 `auctionId`·`sequence`·`serverTime`이
 *    최상위에 함께 실려 있지만, docs/03 §6은 공통 규격을 `{ type, data }`로 못 박고
 *    "`auctionId`는 구독 destination에 이미 있으므로 본문에서 제외한다"고 정리해 두었다.
 *    둘이 어긋나므로 여기서는 **문서 쪽(= AGENTS.md 규칙 9)을 따르고** 차이는 리뷰에 남긴다.
 */
export interface AuctionStartedData {
  startedAt: IsoDateTime
  /** 서버 권위 마감 시각. 클라이언트가 durationMinutes로 다시 계산하지 않는다 */
  endAt: IsoDateTime
}

export type AuctionStartedEvent = SocketEvent<'AUCTION_STARTED', AuctionStartedData>

/** `VIEWER_COUNT.data` — 고유 회원 수가 아니라 활성 STOMP 세션 수다(멀티탭이면 중복 집계) */
export interface ViewerCountData {
  viewerCount: number
  countedBy: 'STOMP_SESSION'
  measuredAt: IsoDateTime
}

export type ViewerCountEvent = SocketEvent<'VIEWER_COUNT', ViewerCountData>

/** `CHAT_MESSAGE.data` — 대기 채팅(CHAT-01). 실 핸들러(handleChatMessage)와 같은 필드다(socket/types.ts) */
export type ChatMessageEvent = SocketEvent<'CHAT_MESSAGE', ChatMessageData>

/** 대기방이 처리하는 이벤트 합집합 — 실소켓 전환 시 핸들러 시그니처가 그대로 유지된다 */
export type WaitingRoomEvent = AuctionStartedEvent | ViewerCountEvent | ChatMessageEvent

/** 목 방의 기본 진행 시간. 실제로는 `auction_room.duration_minutes`를 서버가 쓴다 */
export const MOCK_DURATION_MINUTES = 15

export function makeAuctionStartedEvent(
  durationMinutes: number = MOCK_DURATION_MINUTES,
  now: number = Date.now(),
): AuctionStartedEvent {
  return {
    type: 'AUCTION_STARTED',
    data: {
      startedAt: toIsoDateTime(now),
      // 서버가 `startedAt + durationMinutes`로 계산해 내려주는 값을 흉내 낸다
      endAt: toIsoDateTime(now + durationMinutes * 60_000),
    },
  }
}

export function makeViewerCountEvent(
  viewerCount: number,
  now: number = Date.now(),
): ViewerCountEvent {
  return {
    type: 'VIEWER_COUNT',
    data: { viewerCount, countedBy: 'STOMP_SESSION', measuredAt: toIsoDateTime(now) },
  }
}

// 디자인 원본 wait 채팅 — 시작 전이라 실물 확인이 아니라 조건·기대를 묻는 결이다. [닉네임, 내용, 질문]
const WAIT_CHAT: Array<[string, string, boolean]> = [
  ['민수', '오 이거 기다렸어요', false],
  ['서연', '바디만 나오는 건가요? 렌즈는 별도?', true],
  ['준호', '셔터막 상태 어떤가요', true],
  ['하늘', '드디어 ㅜㅜ', false],
  ['도윤', '곧 시작하나요?', false],
  ['지아', '알림 받고 왔어요', false],
]

let mockChatSeq = 0
export function makeChatMessageEvent(
  nickname: string,
  content: string,
  isQuestion: boolean,
  now: number = Date.now(),
): ChatMessageEvent {
  mockChatSeq += 1
  return {
    type: 'CHAT_MESSAGE',
    data: {
      messageId: `mock-wait-${now + mockChatSeq}`, // 남이 보낸 메시지라 clientMessageId는 없다
      memberId: null,
      nickname,
      content,
      isQuestion,
      clientMessageId: null,
      maskedFields: [],
      createdAt: toIsoDateTime(now),
    },
  }
}

/**
 * 시연용 join 실패.
 *
 * 목 어댑터(`shared/api/rooms.ts`)의 `joinAuctionRoom`은 항상 `WAITING`을 돌려주도록 돼 있어
 * `ROOM_ACCESS_DENIED`·`AUCTION_ALREADY_ENDED` 분기를 화면에서 확인할 방법이 없다.
 * 그 파일은 이 작업의 소유 범위 밖이라, 대신 여기서 같은 모양의 오류를 만들어 준다.
 * 화면은 실제 실패와 **같은 경로**(`ApiError` → 코드 분기)로 처리하므로 분기 자체는 진짜다.
 */
export function makeMockJoinError(code: string, status: number): ApiError {
  return new ApiError('시연용 목 오류입니다', { status, code })
}

export interface MockWaitingRoomSubscription {
  /** 시연용 — 판매자가 시작 버튼을 눌렀을 때 방 전체에 도착하는 이벤트를 흘려보낸다 */
  emitAuctionStarted: (durationMinutes?: number) => void
  /** 화면 언마운트 시 반드시 호출해야 타이머가 남지 않는다 */
  unsubscribe: () => void
}

export interface MockWaitingRoomOptions {
  /** 첫 화면에 그릴 대기 인원. 실제로는 WS-03 스냅샷의 `viewerCount`가 기준값이다 */
  seedViewerCount?: number
  /** 대기 인원이 늘어나는 주기 (디자인 원본은 5.2초) */
  viewerIntervalMs?: number
  /** 대기 채팅이 흘러드는 주기. 0이면 채팅 미발행 */
  chatIntervalMs?: number
}

/**
 * 대기방 구독을 흉내 낸다. destination을 인자로 받는 이유는 하나다 —
 * ROOM-07 join 응답의 `destinations`가 **실제로 쓰이는 값**이어야
 * 실 STOMP로 갈아 끼울 때 이 호출부가 그대로 남기 때문이다.
 *
 * @param destinations join 응답의 `destinations.room`과 그 하위 `/viewers`
 */
export function subscribeMockWaitingRoom(
  destinations: { room: string; viewers: string },
  onEvent: (event: WaitingRoomEvent) => void,
  options: MockWaitingRoomOptions = {},
): MockWaitingRoomSubscription {
  const { seedViewerCount = 0, viewerIntervalMs = 5200, chatIntervalMs = 3800 } = options
  // destination은 실소켓에서 SUBSCRIBE 대상이 된다. 목에서는 쓰이지 않지만
  // 인자에서 빼면 호출부가 join 응답을 무시하게 되므로 남겨 둔다.
  void destinations

  let viewerCount = seedViewerCount
  let stopped = false

  // 시작이 가까울수록 대기 인원이 붙는 상황을 흉내 낸다 (디자인 원본과 같은 동작)
  const viewerTimer = setInterval(() => {
    viewerCount += 1
    onEvent(makeViewerCountEvent(viewerCount))
  }, viewerIntervalMs)

  // 시드 채팅 몇 개를 바로 흘려 화면이 비지 않게 하고, 이후 주기적으로 이어 붙인다
  WAIT_CHAT.slice(0, 4).forEach(([nickname, content, isQuestion]) =>
    onEvent(makeChatMessageEvent(nickname, content, isQuestion)),
  )
  let chatIndex = 4
  const chatTimer =
    chatIntervalMs > 0
      ? setInterval(() => {
          const [nickname, content, isQuestion] = WAIT_CHAT[chatIndex++ % WAIT_CHAT.length]
          onEvent(makeChatMessageEvent(nickname, content, isQuestion))
        }, chatIntervalMs)
      : null

  return {
    emitAuctionStarted(durationMinutes = MOCK_DURATION_MINUTES) {
      if (stopped) return
      onEvent(makeAuctionStartedEvent(durationMinutes))
    },
    unsubscribe() {
      stopped = true
      clearInterval(viewerTimer)
      if (chatTimer) clearInterval(chatTimer)
    },
  }
}

/**
 * 목 대기방 서버 시계 시드 (S15P11A704-448).
 *
 * 대기방 카운트다운(`시작까지`)은 `useCountdown`이 `serverOffset` 없이는 계산하지 않는다 —
 * 규칙 5(서버 기준 없이 남은 시간을 지어내지 않는다)를 지키는 정상 동작이다. 그런데 목 모드에서
 * `serverOffset`을 세울 수 있는 경로가 하나도 없다 — 소켓이 없어 `TIME_SYNC`가 안 오고, join 응답도
 * 목이라 `serverTime`이 없다. 그래서 오프셋이 영구히 null이고 칩이 빈 값으로 남았다.
 * (실 모드는 join 응답의 `serverTime`과 `clockOffset`의 REST 폴백이 이 자리를 메운다, S15P11A704-265.)
 *
 * 목에서는 서버 시각이 곧 로컬 시각이므로 오프셋 0이 **지어낸 값이 아니라 사실**이다
 * (목 라이브 피드도 같은 근거로 `TIME_SYNC`에 offset ≈ 0을 싣는다).
 *
 * ⚠️ 가짜 `TIME_SYNC`를 쏘지 않는 이유: `TimeSyncData.endAt`이 non-nullable인데(명세 WS 이벤트 표)
 * 대기방에는 마감 시각이 없다. 이벤트로 만들면 `handleTimeSync`가 `store.endsAt`에 **지어낸 마감 시각**을
 * 넣는다 — 서버 권위 값 자리에 거짓을 두는 쪽이 더 나쁘다. 대기방의 `TIME_SYNC`가 실제로 무엇을 싣는지는
 * BE 확인 대상이고(별도 티켓), 답이 오면 이 시드를 그 모양의 이벤트로 바꾼다.
 *
 * 이미 오프셋이 있으면(실 TIME_SYNC 수신 후 재입장 등) 덮지 않는다 — 목 시드가 서버 값을 밀어내면 안 된다.
 */
export function seedMockServerClock(store: { serverOffset: number | null }): void {
  if (store.serverOffset === null) store.serverOffset = 0
}
