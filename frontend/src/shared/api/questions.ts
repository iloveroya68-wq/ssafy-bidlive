// CHAT-06 질문 큐 API (S15P11A704-70)
//
// 계약 근거 — 명세 `backend/docs/api/chat/chat-06-{get,delete}-...-questions*`(둘 다 「확정」)가
// 1차 앵커이고, 2026-08-02에 BE 실물(`QuestionQueueController`·`QuestionQueueItemResponse`·
// 예외 핸들러)과 전 항목 대조했다 (S15P11A704-680).
//
// - 조회: GET    /api/v1/auctions/{auctionId}/questions?cursor=&size=
//         → `{ items, nextCursor, hasNext }`. PENDING만, `created_at ASC, message_id ASC` 안정 정렬.
//         size 기본 20·최대 100. 오류: 400 `INVALID_AUCTION_ID`·`INVALID_CURSOR`·`INVALID_PAGE_SIZE` ·
//         401 · 403 `NOT_AUCTION_HOST`.
// - 제거: DELETE /api/v1/auctions/{auctionId}/questions/{questionId} → 204 (PENDING → RESOLVED).
//         이미 RESOLVED·FILTERED인 질문도 204다(멱등). 오류: 401 · 403 `NOT_AUCTION_HOST` ·
//         404 `QUESTION_NOT_FOUND`.
//
// ⚠️ **목 분기는 없다** (S15P11A704-680). 목이 있던 시절엔 경로가 어긋나도 화면이 멀쩡했고,
//    지금은 조용한 404가 된다 — `questions.spec.ts`가 경로·쿼리를 잠그는 유일한 감지선이다.
// ⚠️ 질문은 채팅 메시지와 **다른 엔티티**다. 식별자가 `questionId`(ULID 문자열)이고 `chat_message.id`와
//    다르므로, 큐를 채팅 버퍼에서 필터링해 만들면 안 된다 — 이 모듈이 단일 소스다.
// ⚠️ 상태는 PENDING → RESOLVED(호스트 처리) / FILTERED(검열) / DELETED(메시지 삭제)로 갈린다.
//    조회는 PENDING만 돌려주므로 프론트가 상태를 다시 거를 필요가 없다.
// ⚠️ 「복원」 엔드포인트는 따로 없다 — 이 GET을 다시 부르는 것이 복원이다(재연결 재시드도 같은 경로).
//
// 실시간 추가·제거는 WS `/topic/auctions/{auctionId}/questions`(QUESTION_ADDED/REMOVED)가 담당한다.

import { http } from '@/shared/api/http'
import type { CursorPage, CursorPageQuery, Id, IsoDateTime } from '@/shared/api/types'

export const questionsPath = (auctionId: Id) => `/auctions/${auctionId}/questions`

export interface QuestionQueueItem {
  /** ULID 문자열이다 — 숫자 id가 아니다 */
  questionId: string
  memberId: Id
  nickname: string
  content: string
  createdAt: IsoDateTime
}

/** 기본 20건, 최대 100건 (`INVALID_PAGE_SIZE`) */
export interface QuestionQueueQuery extends CursorPageQuery {
  size?: number
}

/**
 * CHAT-06 질문 큐 조회 — `createdAt ASC, questionId ASC` 안정 정렬로 PENDING만 온다.
 * 호스트가 아니면 `NOT_AUCTION_HOST`로 막힌다.
 *
 * ⚠️ 커서 조회(`QuestionQueueQueryService.resolveCursor`)는 그 메시지가 **살아 있는지만** 본다 —
 *    `is_deleted = FALSE`만 걸고 `question_queue_status`는 안 본다. 그래서 커서로 쥔 질문이
 *    RESOLVED(답변 완료)·FILTERED(단순 검열)가 돼도 **커서는 그대로 산다**(그 항목만 다음 페이지에서 빠진다).
 *    `INVALID_CURSOR`가 나는 건 그 메시지가 **소프트 삭제**됐을 때뿐이다 — 호스트·관리자 삭제,
 *    모더레이션 BLOCKED. (그 외엔 ULID 형식이 깨진 경우.) 한번 죽은 커서는 영영 못 쓰므로
 *    복구는 커서 없이 다시 부르는 것뿐이다(`useQuestionQueue.loadMore`).
 */
export async function getQuestionQueue(
  auctionId: Id,
  query: QuestionQueueQuery = {},
): Promise<CursorPage<QuestionQueueItem>> {
  const { data } = await http.get<CursorPage<QuestionQueueItem>>(questionsPath(auctionId), {
    params: query,
  })
  return data
}

/**
 * CHAT-06 질문 답변 완료 — PENDING을 RESOLVED로 전이한다.
 * 이미 처리된 질문도 204로 성공하므로(멱등) 중복 클릭을 따로 막을 필요는 없다.
 */
export async function resolveQuestion(auctionId: Id, questionId: string): Promise<void> {
  await http.delete<void>(`${questionsPath(auctionId)}/${questionId}`)
}
