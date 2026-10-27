// 채팅 과거 조회 (CHAT, S15P11A704-379) — 위로 스크롤 시 이전 메시지를 REST로 불러온다.
//
// 계약 확정 (S15P11A704-679). 1차 근거는 명세 `backend/docs/api/chat/chat-01-get-auctions-by-auctionid-chats.md`
// (상태: 확정 / 구현: 완료)이고, BE 실물(`chat/api/ChatHistoryController`·`ChatHistoryResponse`)로 대조했다.
//   · `before` 생략 = 첫 페이지(가장 최신 size개), 주면 그 26자 ULID 메시지 **이전**만(배타).
//   · `size` 기본 30, 1..100 밖이면 400 INVALID_PAGE_SIZE.
//   · `messages`는 오래된→최신 오름차순, `hasMore`는 서버가 size+1 조회로 판단한다. `nextCursor`는 없다 —
//     다음 `before`는 현재 페이지 첫 메시지의 id다.
//   · 인증 필수(401). 오류 본문은 `{code, message}` — 400 INVALID_AUCTION_ID/INVALID_CURSOR/
//     INVALID_PAGE_SIZE · 403 ROOM_ACCESS_DENIED · 404 AUCTION_NOT_FOUND (호출부가 4xx를 종료로 본다).
//   · **비공개 경매는 판매자만 조회할 수 있고, 다른 회원에게는 경매 존재를 숨기려고 404를 준다**(명세 「조회 및
//     노출 정책」). 그래서 4xx는 "이력이 없다"가 아니다 — 스토어가 `chatOlderBlocked`로 갈라 화면이
//     「대화의 처음이에요」를 띄우지 않게 한다.
// 메시지 필드는 소켓 CHAT_MESSAGE.data와 같은 shape라 정규화(handlers.toChatMessage)를 그대로 재사용한다.
import { http } from '@/shared/api/http'
import { toChatMessage } from '@/features/live/socket/handlers'
import type { ChatMessageData } from '@/features/live/socket/types'
import type { ChatMessage } from '@/stores/live'

export interface ChatHistoryPage {
  /** before 커서보다 이전 메시지들 — 오래된→최신(오름차순). */
  messages: ChatMessage[]
  /** 더 과거가 남았는지(서버 판단). */
  hasMore: boolean
}

/** 응답 DTO — 소켓 CHAT_MESSAGE.data 배열 + hasMore (`ChatHistoryResponse`). */
interface ChatHistoryResponse {
  messages: ChatMessageData[]
  hasMore: boolean
}

/**
 * `beforeMessageId`보다 이전 메시지 한 페이지를 조회한다.
 * @param beforeMessageId 현재 가진 가장 오래된 **확정** 메시지의 서버 id(ULID 문자열).
 *   `undefined`면 커서를 안 보내 첫 페이지(최신 size개)를 받는다 — 방 입장 직후 빈 채팅창 시드.
 */
export async function fetchChatHistory(
  roomId: number,
  beforeMessageId?: string,
  size = 30,
): Promise<ChatHistoryPage> {
  const { data } = await http.get<ChatHistoryResponse>(`/auctions/${roomId}/chats`, {
    params: { before: beforeMessageId, size },
  })
  return { messages: data.messages.map(toChatMessage), hasMore: data.hasMore }
}
