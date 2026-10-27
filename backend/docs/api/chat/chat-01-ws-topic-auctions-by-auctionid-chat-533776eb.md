# 채팅 수신

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [채팅 수신](https://app.notion.com/533776ebfd6882dab7fd0176f5d6ec39)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-01` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}/chat` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 비고

CHAT_MESSAGE·NOTICE_* 수신; clientMessageId 포함

> **WS 수신 명세** — 경매방의 채팅·공지 이벤트를 구독한다.
## 구독 위치
```plain text
SUB /topic/auctions/{auctionId}/chat
```
## CHAT_MESSAGE 예시
```json
{
  "type": "CHAT_MESSAGE",
  "data": {
    "messageId": "01J8W3P2...",
    "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667788",
    "memberId": 7,
    "nickname": "서연",
    "content": "키캡 상태 어떤가요?",
    "isQuestion": false,
    "maskedFields": [],
    "createdAt": "2026-07-20T21:03:12+09:00"
  }
}
```
## 수신 이벤트
<table>
<tr>
<td>type</td>
<td>설명</td>
</tr>
<tr>
<td>`CHAT_MESSAGE`</td>
<td>신규 채팅</td>
</tr>
<tr>
<td>`NOTICE_PINNED`</td>
<td>고정 공지 등록·교체</td>
</tr>
<tr>
<td>`NOTICE_UNPINNED`</td>
<td>고정 공지 해제</td>
</tr>
<tr>
<td>`CHAT_DELETED`</td>
<td>판매자·운영자 삭제 또는 비동기 검열로 숨겨진 메시지</td>
</tr>
</table>

## CHAT_DELETED 예시

```json
{
  "type": "CHAT_DELETED",
  "data": {
    "messageId": "01J8W3P2N9KQ4T123456789ABC"
  }
}
```

- 수신자는 같은 `messageId`의 메시지를 화면과 로컬 버퍼에서 제거한다.
- 삭제 사유와 처리 회원 정보는 공개 payload에 포함하지 않는다.
## 클라이언트 처리 규칙
- `messageId`로 서버 중복 이벤트 제거
- 발신자는 `clientMessageId`로 optimistic UI 항목과 서버 결과 결합
- 알 수 없는 type은 연결을 끊지 않고 무시·기록
- 재접속 시 채팅 전체 재생은 보장하지 않으며 현재 공지는 WS-03으로 복원
## 필수 테스트
- CHAT_MESSAGE 필드와 마스킹 결과
- optimistic UI 중복 제거
- 공지 이벤트 분기
- 미지원 type 수신 시 연결 유지

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [채팅 수신 원본](https://app.notion.com/533776ebfd6882dab7fd0176f5d6ec39)

