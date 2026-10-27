# 입찰 기세(Vibe) 피드

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [입찰 기세(Vibe) 피드](https://app.notion.com/8a9776ebfd68829a8134819ea5df1642)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-04` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 방송 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}` |
| 상태 | 확정 |
| 구현 | ✅ **구현됨** — `BidAcceptedWebSocketPublisher` (`2026-07-31` 확인) |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 개요
입찰 금액과 입찰자를 공개하지 않고 입찰 발생 사실만 연출용 메시지로 전달한다.
- **Destination**: `/topic/auctions/{auctionId}`
- **Event Type**: `BID_ACCEPTED`
- **수신 대상**: 경매방 전체
> 별도의 `BID_VIBE` 이벤트를 추가하지 않고 `BID_ACCEPTED.data.vibeMessage`에 포함한다.
## 메시지 예시
```json
{
  "type": "BID_ACCEPTED",
  "data": {
    "bidCount": 13,
    "participantCount": 9,
    "vibeMessage": "누군가 베팅을 진행 중입니다!"
  }
}
```
## 프론트 처리
- `vibeMessage`가 존재하면 짧은 토스트 또는 애니메이션 표시
- 금액, 닉네임, 순위는 표시하지 않음
- 연속 입찰 시 화면이 과도하게 흔들리지 않도록 큐 또는 짧은 스로틀 적용 가능
## 발생 조건
Redis 원자적 입찰 처리와 입찰 이력 저장이 성공한 경우에만 전송한다. 실패한 입찰 요청에는 Vibe 피드를 발생시키지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [입찰 기세(Vibe) 피드 원본](https://app.notion.com/8a9776ebfd68829a8134819ea5df1642)

