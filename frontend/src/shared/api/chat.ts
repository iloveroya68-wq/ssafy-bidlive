// CHAT-02 고정 공지 등록·해제 (S15P11A704-559) · CHAT-01 메시지 삭제 (S15P11A704-681) API
//
// 명세 근거
// - 등록·교체: POST   /api/v1/auctions/{auctionId}/chat/pin  body `{ messageId }` → 200 `{ messageId, content }`
// - 해제:      DELETE /api/v1/auctions/{auctionId}/chat/pin                        → 204 (공지가 없어도 204, 멱등)
// - 삭제:      DELETE /api/v1/auctions/{auctionId}/chats/{messageId}               → 204 (이미 지워도 204, 멱등)
//   ⚠️ 삭제만 경로가 `chats`(복수)다 — 고정 공지의 `chat/pin`과 한 글자 차이라 오타가 404로 나온다.
//
// 고정 공지는 판매자(호스트) 전용이다 — 아니면 403 `NOT_AUCTION_HOST`.
// 목 분기는 없다(mock.ts 머리말) — 서버에 `PinnedNoticeController`(고정)와 `ChatMessageDeletionController`
// (삭제, `@RequestMapping("/api/v1/auctions/{auctionId}/chats/{messageId}")`)가 있어 목 모드에서도 실 호출로 나간다.
//
// 성공 응답으로 화면을 갱신하지 않는다. 서버가 커밋 후 채팅 토픽으로 `NOTICE_PINNED`/`NOTICE_UNPINNED`를
// 되쏘고, 그걸 `features/live/socket/handlers.ts`의 handleNotice가 받아 스토어를 갱신한다 —
// 응답으로도 갱신하면 같은 상태를 두 경로가 쓰게 된다. 그래서 등록도 응답 본문을 돌려주지 않는다
// (계약상 200 본문은 `{ messageId, content }`이지만 소비자가 없다 — 필요해지면 그때 타입을 세운다).
//
// ⚠️ **목 모드에서는 성공해도 배너가 안 바뀐다.** 목 소켓 드라이버가 `NOTICE_*`를 발행하지 않아
//    갱신 경로 자체가 비어 있다 — 목으로 눌러 보고 "고장 났다"고 읽지 말 것. 실 서버에서만 에코가 온다.
//
// `messageId`는 **ULID 문자열**을 보낸다. 문서 계약도, 소켓 `NOTICE_PINNED`도, WS-03 스냅샷도,
// FE 스토어(`ChatMessage.id`)도 전부 문자열이다. 한때 실서버 `PinnedNoticeController`가 이 값을
// `Long.parseLong`으로 검증해 400 `INVALID_MESSAGE_ID`가 났지만 **S15P11A704-613에서 고쳐졌다** —
// 이제 `ChatMessageId`가 26자 정규 ULID를 검증하고, 반대로 기존 숫자 id는 26자 미만이라 400이 된다.
// 문서 계약대로 보내면 된다(숫자로 바꾸지 말 것).

import { http } from '@/shared/api/http'
import type { Id } from '@/shared/api/types'

export const chatPinPath = (auctionId: Id) => `/auctions/${auctionId}/chat/pin`

/**
 * CHAT-02 고정 공지 등록·교체 — 이미 같은 메시지가 고정돼 있어도 성공한다(멱등).
 *
 * 응답 본문(200 `{ messageId: ULID 문자열, content }`)은 버린다 — 화면은 소켓 에코로만 갱신하므로(머리말)
 * 읽을 곳이 없다. 성공·실패만 알면 되는 자리라 반환을 void로 좁힌다.
 */
export async function pinNotice(auctionId: Id, messageId: string): Promise<void> {
  await http.post(chatPinPath(auctionId), { messageId })
}

/** CHAT-02 고정 공지 해제 — 고정된 공지가 없어도 204로 성공하므로 중복 클릭을 따로 막지 않는다. */
export async function unpinNotice(auctionId: Id): Promise<void> {
  await http.delete<void>(chatPinPath(auctionId))
}

/**
 * CHAT-01 채팅 메시지 삭제 (S15P11A704-681) — 판매자·운영자.
 *
 * **엔드포인트는 하나다.** 역할 판정은 서버가 하고(판매자 → `SELLER_DELETED`, ADMIN → `ADMIN_DELETED`,
 * 둘 다 아니면 403 `CHAT_DELETE_FORBIDDEN`) FE는 호출 주체에 따라 경로를 바꾸지 않는다.
 *
 * 고정 공지와 같은 이유로 **성공 응답으로 화면을 갱신하지 않는다** — 서버가 커밋 후 채팅 토픽으로
 * `CHAT_DELETED`를 되쏘고 handlers.handleChatDeleted가 행을 지운다(대상이 공지·대기 질문이었다면
 * `NOTICE_UNPINNED`·`QUESTION_REMOVED`도 함께 온다). 여기서 낙관적으로 지우면 403·404로 실패한 뒤
 * 되살릴 방법이 없다.
 *
 * 이미 지워진 메시지를 다시 요청해도 204라(멱등) 중복 클릭을 따로 막지 않는다.
 * messageId는 `pinNotice`와 같은 ULID 문자열 그대로 보낸다 — 26자가 아니면 400 `INVALID_MESSAGE_ID`.
 */
export async function deleteChatMessage(auctionId: Id, messageId: string): Promise<void> {
  await http.delete<void>(`/auctions/${auctionId}/chats/${messageId}`)
}
