# 질문 큐 수신

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [질문 큐 수신](https://app.notion.com/5ec776ebfd68823993bf811afb9592d6)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `CHAT-06` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | 채팅 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}/questions` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 비고

QUESTION_ADDED·QUESTION_REMOVED 이벤트

> **호스트 전용 WS 수신 명세** — 미답변 질문의 추가·제거 이벤트를 실시간으로 받는다.
## 구독 위치
```plain text
SUB /topic/auctions/{auctionId}/questions
```
## 권한
WS-02에서 해당 경매의 `hostId`와 Principal을 비교한다. 비호스트는 SUBSCRIBE 단계에서 `SUBSCRIPTION_DENIED` 처리한다.
## 이벤트
`QUESTION_ADDED`:
```json
{ "type": "QUESTION_ADDED", "data": { "questionId": "01J8...", "memberId": 7, "nickname": "서연", "content": "흠집을 보여주세요", "createdAt": "2026-07-20T21:03:12+09:00" } }
```
`QUESTION_REMOVED`:
```json
{ "type": "QUESTION_REMOVED", "data": { "questionId": "01J8...", "reason": "RESOLVED" } }
```
`reason`은 `RESOLVED` 또는 `FILTERED`다.
## 처리 규칙
- 이벤트는 질문 ID 기준으로 멱등 적용
- 재접속 시 누락 이벤트를 추측하지 않고 CHAT-06 GET으로 PENDING 목록을 복원
- 질문 본문과 작성자 정보는 호스트 토픽 외부에 추가 송출하지 않음
## 필수 테스트
- 호스트 성공·비호스트 구독 거부
- ADDED·REMOVED 멱등 적용
- RESOLVED·FILTERED reason 구분
- 재접속 REST 복원

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [질문 큐 수신 원본](https://app.notion.com/5ec776ebfd68823993bf811afb9592d6)

