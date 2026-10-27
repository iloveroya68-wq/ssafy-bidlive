# 검열 경고·뮤트 통지

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [검열 경고·뮤트 통지](https://app.notion.com/5bb776ebfd68822b81be0179f8e43978)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `AI-05` |
| 저장소 도메인 | `chat` |
| Notion 도메인 | AI |
| Method | `WS` |
| Path / Destination | `/user/queue/moderation` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | 미지정 |
| 담당자 | 김경민 |
| 우선순위 | Medium |
| 연결 구분 | 미지정 |
| 의존 대상 | 미지정 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 비고

MODERATION_WARNING·CHAT_MUTED 개인 이벤트 전용 수신 계약

> **개인 알림 WS 명세** — AI-05 판정 결과 중 사용자에게 공개할 경고·뮤트 상태만 수신한다.
## 구독 위치
```plain text
SUB /user/queue/moderation
```
## 이벤트 예시
```json
{
  "type": "MODERATION_WARNING",
  "data": {
    "messageId": "01J8W3P2N9KQ4T",
    "reason": "OFF_PLATFORM_TRADE",
    "message": "외부 거래 유도는 제한됩니다."
  }
}
```
```json
{
  "type": "CHAT_MUTED",
  "data": {
    "auctionId": 100,
    "durationSeconds": 600,
    "mutedUntil": "2026-07-20T21:13:12+09:00"
  }
}
```
## 처리 규칙
- 판정 대상 사용자 개인 큐에만 송출
- LLM prompt·원문·점수·내부 판정 근거는 노출하지 않음
- `reason`은 공개 허용 enum만 사용
- 중복 비동기 소비로 동일 messageId 경고를 반복 송출하지 않음
- `durationSeconds`와 `mutedUntil`은 서버 설정과 Redis TTL 기준
## 필수 테스트
- 대상 사용자만 수신
- 원문·내부 판정 정보 미노출
- 동일 messageId 중복 알림 방지
- Redis TTL과 mutedUntil 일치

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [검열 경고·뮤트 통지 원본](https://app.notion.com/5bb776ebfd68822b81be0179f8e43978)

