# 채팅 메시지 삭제

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-01` |
| Method | `DELETE` |
| Path | `/api/v1/auctions/{auctionId}/chats/{messageId}` |
| 인증 | 필수 |
| 담당 | 김경민 |
| 관련 이슈 | `S15P11A704-418`, `S15P11A704-585` |

## 요청

- `auctionId`: 양의 정수 경매 ID
- `messageId`: 외부 공개 ULID
- body 없음
- 인증된 최신 회원 정보가 `ACTIVE`이고 다음 중 하나여야 한다.
  - 해당 경매의 판매자
  - `ADMIN`

## 성공 응답

```http
HTTP/1.1 204 No Content
```

이미 삭제된 같은 메시지를 다시 요청해도 `204 No Content`를 반환한다.

## 처리 규칙

- 메시지를 물리 삭제하지 않고 `is_deleted=true`로 전환한다.
- 내부 감사 정보로 삭제 시각, 삭제 회원 ID, 삭제 사유를 저장한다.
- 고정 공지였다면 같은 트랜잭션에서 고정을 해제한다.
- 대기 질문이었다면 질문 큐에서 `DELETED` 사유로 제거한다.
- 최초 상태 변경이 커밋된 경우에만 다음 이벤트를 발행한다.
  - `/topic/auctions/{auctionId}/chat`: `CHAT_DELETED`
  - 고정 공지였을 때: `NOTICE_UNPINNED`
  - 대기 질문이었을 때: `QUESTION_REMOVED(reason=DELETED)`
- 공개 이벤트에는 삭제 사유와 처리 회원 정보를 포함하지 않는다.

## CHAT_DELETED

```json
{
  "type": "CHAT_DELETED",
  "data": {
    "messageId": "01J8W3P2N9KQ4T123456789ABC"
  }
}
```

## 오류 응답

```json
{
  "code": "MESSAGE_NOT_FOUND",
  "message": "메시지를 찾을 수 없습니다."
}
```

| HTTP | code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_AUCTION_ID` | 경매 ID 형식 오류 |
| `400` | `INVALID_MESSAGE_ID` | ULID 형식 오류 |
| `401` | `UNAUTHORIZED` | 인증 없음, 최신 회원이 없거나 비활성 상태 |
| `403` | `CHAT_DELETE_FORBIDDEN` | 판매자나 운영자가 아님 |
| `404` | `MESSAGE_NOT_FOUND` | 메시지가 없거나 다른 경매에 속함 |

존재 여부를 통한 다른 경매 메시지 탐색을 막기 위해 없는 메시지와 경매 불일치를 같은 오류로 반환한다.
