# 질문 채팅 전송

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [질문 채팅 전송](https://app.notion.com/781776ebfd6883a2a18e011d97a2e029)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-06` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `WS` |
| Path / Destination | `/app/auctions/{auctionId}/chat` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

CHAT-01의 isQuestion=true 전송 계약; clientMessageId로 중복 방지

> **WS 질문 전송 명세** — CHAT-01 요청에서 `isQuestion=true`인 메시지를 미답변 질문으로 함께 등록한다.
## 호출 위치
```plain text
SEND /app/auctions/{auctionId}/chat
```
## 입력
```json
{
  "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667788",
  "content": "키캡 흠집을 보여주세요",
  "isQuestion": true
}
```
## 처리 규칙
- CHAT-01 인증·상태·mute·슬로우 모드·마스킹 규칙을 모두 적용
- 채팅 저장과 같은 트랜잭션에서 `question_status=PENDING`
- `(memberId, clientMessageId)` 멱등 처리로 질문 중복 적재 방지
- 커밋 후 일반 채팅 토픽에 `CHAT_MESSAGE`, 호스트 질문 토픽에 `QUESTION_ADDED` 송출
- AI-05가 중복·비질문으로 판정하면 이후 `FILTERED`로 전이
## QUESTION_ADDED 예시
```json
{
  "type": "QUESTION_ADDED",
  "data": {
    "questionId": "01J8W3P2N9KQ4T",
    "memberId": 7,
    "nickname": "서연",
    "content": "키캡 흠집을 보여주세요",
    "createdAt": "2026-07-20T21:03:12+09:00"
  }
}
```
## 필수 테스트
- WAITING·OPEN 적재, 종료 경매 거부
- 같은 clientMessageId 질문 한 건만 생성
- 채팅 저장 실패 시 질문 상태도 롤백
- CHAT_MESSAGE·QUESTION_ADDED 대상 토픽 분리

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [질문 채팅 전송 원본](https://app.notion.com/781776ebfd6883a2a18e011d97a2e029)

