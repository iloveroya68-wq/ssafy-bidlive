# SETTLEMENT_PROGRESS

> Notion 원본이 없는 신규 로컬 이벤트 계약이다. BID-10 본문에만 있던 진행 이벤트를 등재된
> WebSocket 이벤트 계약으로 분리했다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `SETTLEMENT_PROGRESS` |
| 저장소 도메인 | `bid` |
| 연관 기능 ID | `BID-10` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | Medium |
| 발생 시점 | `SETTLING` 구간에서 입찰 검증·정렬이 진행되는 동안 |
| data 필드 | processedBidCount, totalBidCount |

## 설명

집계 중 진행률을 알린다. 프론트는 `processedBidCount / totalBidCount`로 봉투를 열며 입찰 건수를
0부터 올리는 진행률 연출을 구동한다.

`AUCTION_ENDED`가 `SETTLING` 구간을 열고, `AUCTION_SETTLED` 또는 `AUCTION_NO_SALE`이 닫는다.
그 사이에서만 발행된다.

## Payload

```json
{
  "type": "SETTLEMENT_PROGRESS",
  "data": {
    "processedBidCount": 8,
    "totalBidCount": 13
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `processedBidCount` | number | 검증·정렬을 마친 입찰 건수 |
| `totalBidCount` | number | 집계 대상 전체 입찰 건수 |

## 처리 규칙

- `auctionId`는 Destination에 포함되므로 메시지 본문에서 제외한다.
- `totalBidCount`는 이 이벤트가 들고 있다. `AUCTION_ENDED`에 중복해 넣지 않는다.
- `processedBidCount`는 `totalBidCount`를 넘지 않으며 감소하지 않는다.
- 집계 중 최고 입찰가, 2등 입찰가, 임시 낙찰자, 개별 입찰 금액과 순위는 포함하지 않는다.
  진행률 이외의 정보로 결과를 추측할 수 있게 만들지 않는다.
- 재시도 중에도 경매 상태는 `SETTLING`으로 유지하고 이 이벤트만 다시 발행한다.
- 집계가 실패하면 성공 결과를 임의로 공개하지 않는다. 종료 이벤트로만 구간을 닫는다.

## 출처

- [WebSocket 공통 메시지 형식](../../common/websocket-message-format.md)
- [집계 중 상태 브로드캐스트 (BID-10)](../bid-10-ws-topic-auctions-by-auctionid-09f776eb.md)
