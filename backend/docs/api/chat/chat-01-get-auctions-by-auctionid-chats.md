# 채팅 이력 조회

> Jira S15P11A704-401에서 확정한 로컬 구현 계약이다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-01` |
| 저장소 도메인 | `chat` |
| Method | `GET` |
| Path | `/api/v1/auctions/{auctionId}/chats` |
| 상태 | 확정 |
| 구현 | 완료 |
| 인증 | 필요 — Access Token |
| 담당자 | 김경민 |

## 요청

```http
GET /api/v1/auctions/{auctionId}/chats?before={messageId}&size=30
Authorization: Bearer {accessToken}
```

- `before`는 서버가 발급한 26자 ULID `messageId`이며 해당 메시지는 결과에서 제외한다.
- `before`를 생략하면 최신 메시지부터 첫 페이지를 조회한다.
- `size` 기본값은 30, 허용 범위는 1~100이다.
- body, query parameter의 회원 ID는 인증 근거로 사용하지 않는다.

## 응답

```json
{
  "messages": [
    {
      "messageId": "01J8W3P2N9KQ4T123456789ABC",
      "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667788",
      "memberId": 7,
      "nickname": "서연",
      "content": "연락처 ******",
      "isQuestion": false,
      "maskedFields": ["CONTACT"],
      "createdAt": "2026-08-01T12:03:12Z"
    }
  ],
  "hasMore": true
}
```

- `messages`는 페이지 안에서 오래된 메시지부터 최신 메시지 순으로 정렬한다.
- `CHAT_MESSAGE.data`와 동일한 필드 구성을 사용한다.
- `hasMore`는 현재 페이지보다 오래된 메시지가 더 있는지를 나타낸다.
- `nextCursor`는 제공하지 않는다. 다음 요청의 `before`에는 현재 페이지 첫 메시지의 `messageId`를 사용한다.

## 조회 및 노출 정책

- 조회는 `(created_at, message_id)` 내림차순으로 `size + 1`건을 가져온 뒤 응답만 오름차순으로 뒤집는다.
- 같은 `createdAt`을 가진 메시지는 `messageId`로 정렬해 중복과 누락을 방지한다.
- `is_deleted = TRUE` 메시지는 제외한다.
- `moderation_status = BLOCKED` 메시지는 제외한다.
- `content`와 `maskedFields`는 동기 마스킹 후 저장된 값을 반환한다.
- 공개 경매는 ACTIVE 회원이 조회할 수 있다.
- 비공개 경매는 판매자만 조회할 수 있으며, 다른 회원에게는 경매 존재를 숨기기 위해 404를 반환한다.
- SUSPENDED·WITHDRAWN 회원은 조회할 수 없다.

## 실패 응답

```json
{
  "code": "INVALID_CURSOR",
  "message": "채팅 이력 커서가 올바르지 않습니다."
}
```

| HTTP | code | 설명 |
| --- | --- | --- |
| 400 | `INVALID_AUCTION_ID` | 경매 ID 형식 또는 범위 오류 |
| 400 | `INVALID_CURSOR` | ULID 형식 오류 또는 해당 경매에 없는 커서 |
| 400 | `INVALID_PAGE_SIZE` | size 형식 또는 범위 오류 |
| 401 | `UNAUTHORIZED` | 인증 정보 없음 또는 비정상 인증 사용자 |
| 403 | `ROOM_ACCESS_DENIED` | 회원 상태로 인한 조회 거부 |
| 404 | `AUCTION_NOT_FOUND` | 없거나 삭제·차단·비공개로 숨겨진 경매 |

## 필수 테스트

- 첫 페이지와 이전 페이지의 배타 커서 조회
- 페이지 내 오래된 순 정렬과 `hasMore`
- 동일 `createdAt`에서 `messageId` tie-breaker
- 삭제·BLOCKED 메시지 제외
- 저장된 `maskedFields` 복원
- 기본·최대 size 및 잘못된 cursor 처리
- 공개·비공개 경매와 회원 상태별 권한
- 인증 경로와 HTTP 오류 응답

## 출처

- [Jira S15P11A704-401](https://ssafy.atlassian.net/browse/S15P11A704-401)
