# bid API

`src/main/java/com/ssafy/bidlive/bid/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `BID-01` | `INTERNAL` | 내부 처리 | 확정 | [원자적 입찰 처리](./bid-01-internal-internal-0d0776eb.md) |
| `BID-02` | `WS` | `/topic/auctions/{auctionId}` | 확정 | [입찰 상태 브로드캐스트](./bid-02-ws-topic-auctions-by-auctionid-0f1776eb.md) |
| `BID-04` | `WS` | `/topic/auctions/{auctionId}` | 확정 | [입찰 기세(Vibe) 피드](./bid-04-ws-topic-auctions-by-auctionid-8a9776eb.md) |
| `BID-06` | `POST` | `/api/v1/auctions/{auctionId}/bids` | 확정 | [금액 직접 입력 입찰](./bid-06-post-auctions-by-auctionid-bids-c39776eb.md) |
| `BID-08` | `GET` | `/api/v1/auctions/{auctionId}/bid-flow` | 확정 | [입찰 흐름 그래프 조회](./bid-08-get-auctions-by-auctionid-bid-flow-f6e776eb.md) |
| `BID-10` | `WS` | `/topic/auctions/{auctionId}` | 확정 | [집계 중 상태 브로드캐스트](./bid-10-ws-topic-auctions-by-auctionid-09f776eb.md) |

## WebSocket 이벤트

| 연관 기능 ID | Event Type | Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `BID-01, BID-02, BID-04` | `BID_ACCEPTED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/bid-accepted.md) |
| `BID-10` | `SETTLEMENT_PROGRESS` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/settlement-progress.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

