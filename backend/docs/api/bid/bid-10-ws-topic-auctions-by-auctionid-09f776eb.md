# 집계 중 상태 브로드캐스트

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [집계 중 상태 브로드캐스트](https://app.notion.com/09f776ebfd688381919181cb27af2571)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-10` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 입찰 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE, BE↔Redis, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

마감과 결과 공개 사이에 SETTLING 상태와 집계 진행 정보를 전원에게 브로드캐스트한다.

## 비고

P1, AUCTION_ENDED 후 결과 이벤트 전까지

## 개요
서버 마감 시각에 도달한 뒤 비크리 결과가 확정되기 전까지 전원 화면을 `집계 중` 상태로 전환한다. 프론트는 봉투를 열며 입찰 건수를 0부터 올리는 연출을 표시한다.
- **Destination**: `/topic/auctions/{auctionId}`
- **시작 이벤트**: `AUCTION_ENDED`
- **진행 이벤트**: `SETTLEMENT_PROGRESS`
- **종료 이벤트**: `AUCTION_SETTLED` 또는 `AUCTION_NO_SALE`
## 시작 메시지
`AUCTION_ENDED`의 정본 계약은 [이벤트 명세](../auction/events/auction-ended.md)이며 `data`는
`status`와 `endedAt`만 갖는다. `auctionId`는 Destination에 있으므로 본문에서 제외하고,
`totalBidCount`는 `SETTLEMENT_PROGRESS`가 매 진행 메시지에 담으므로 여기서 중복해 보내지 않는다.

```json
{
  "type": "AUCTION_ENDED",
  "data": {
    "status": "SETTLING",
    "endedAt": "2026-07-20T21:30:00+09:00"
  }
}
```
## 진행 메시지
독립 이벤트 계약은 [SETTLEMENT_PROGRESS 이벤트 명세](./events/settlement-progress.md)를 따른다.

```json
{
  "type": "SETTLEMENT_PROGRESS",
  "data": {
    "processedBidCount": 8,
    "totalBidCount": 13
  }
}
```
## 공개 금지 정보
- 집계 중 최고 입찰가와 2등 입찰가
- 임시 낙찰자 정보
- 개별 입찰 금액과 순위
## 처리 흐름
```plain text
서버 마감 시각 도달
→ BID-07이 LIVE → SETTLING 선점
→ AUCTION_ENDED 발행
→ 입찰 검증·정렬 진행 중 SETTLEMENT_PROGRESS 발행
→ 결과·포인트 정산 저장
→ AUCTION_SETTLED 또는 AUCTION_NO_SALE 발행
→ 화면을 결과 뷰로 전환
```
## 장애 처리
- 집계 실패 시 성공 결과를 임의로 공개하지 않는다.
- 재시도 중에도 경매 상태는 `SETTLING`으로 유지하고 `SETTLEMENT_PROGRESS`만 다시 발행한다.
- 중복 실행은 BID-07의 멱등 정산 키로 차단한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [집계 중 상태 브로드캐스트 원본](https://app.notion.com/09f776ebfd688381919181cb27af2571)

