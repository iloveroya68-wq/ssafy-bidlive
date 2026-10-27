# 채팅 전송

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [채팅 전송](https://app.notion.com/1eb776ebfd68838fb5468168021f3812)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-01` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `WS` |
| Path / Destination | `/app/auctions/{auctionId}/chat` |
| 상태 | 확정 |
| 인증 | 필수 (`SecurityPrincipal`) |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 구현 | ✅ `StompChatSendController` |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 비고

clientMessageId 멱등 처리 후 저장·브로드캐스트

> **WS 전송 명세** — 경매방 채팅을 검증·마스킹·멱등 저장한 뒤 채팅 토픽으로 송출한다.
## 호출 위치
```plain text
SEND /app/auctions/{auctionId}/chat
```
## 입력
```json
{
  "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667788",
  "content": "키캡 상태 어떤가요?",
  "isQuestion": false
}
```
- `clientMessageId`: 클라이언트가 생성한 UUID 또는 ULID, 재시도에도 같은 값 사용
- `content`: 앞뒤 공백 제거 후 1자 이상, Unicode 코드 포인트 기준 최대 250자
- `isQuestion`: 기본 `false`; `true`이면 CHAT-06 계약 추가 적용
## 처리 규칙
1. Principal, 경매 소속과 `WAITING|LIVE` 상태 검증
2. `(memberId, clientMessageId)` 기존 처리 결과 확인
3. 신규 요청만 mute·CHAT-04 슬로우 모드 검사
4. CHAT-03 마스킹 적용
5. `CHAT_MESSAGE` 저장; `(memberId, clientMessageId)` UNIQUE
6. 커밋 후 `/topic/auctions/{auctionId}/chat` 송출
- 동일 키 재요청은 다시 저장·차감·브로드캐스트하지 않음
- 서버 `messageId`는 ULID로 발급
- 발신자 ID·닉네임은 세션과 회원 조회 결과 사용
## 실패 코드
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`AUCTION_CHAT_CLOSED`</td>
<td>채팅 불가 상태</td>
</tr>
<tr>
<td>`CHAT_MUTED`</td>
<td>mute 중 전송</td>
</tr>
<tr>
<td>`SLOW_MODE_LIMIT`</td>
<td>전송 간격 위반</td>
</tr>
<tr>
<td>`CHAT_CONTENT_INVALID`</td>
<td>공백 또는 길이 정책 위반</td>
</tr>
</table>
## 후속 처리
질문이면 CHAT-06 적재, 모든 신규 메시지는 AI-05 비동기 판정 요청. 마스킹 감사 로그 실패는 채팅 커밋을 롤백하지 않는다.
## 필수 테스트
- 대기방·진행 중 성공, 종료 방 거부
- 같은 clientMessageId 재전송 시 한 건만 저장·송출
- 서로 다른 사용자의 같은 키 격리
- mute·슬로우 모드·마스킹
- 동시 전송 유실 여부

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [채팅 전송 원본](https://app.notion.com/1eb776ebfd68838fb5468168021f3812)

