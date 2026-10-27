# auction API

`src/main/java/com/ssafy/bidlive/auction/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `AI-03` | `GET` | `/api/v1/auction-rooms/{auctionId}/catch-up-summary` | 확정 | [중간 입장 요약 조회](./ai-03-get-auction-rooms-by-auctionid-catch-up-summary-2ae776eb.md) |
| `BID-03` | `POST` | `/api/v1/auctions/{auctionId}/extensions` | 확정 | [판매자 방송 연장](./bid-03-post-auctions-by-auctionid-extensions-408776eb.md) |
| `BID-05` | `WS` | `/topic/auctions/{auctionId}` | 확정 | [서버 타이머 동기화](./bid-05-ws-topic-auctions-by-auctionid-4ef776eb.md) |
| `BID-07` | `INTERNAL` | 내부 처리 | 확정 | [비크리 엔진](./bid-07-internal-internal-c29776eb.md) |
| `DEAL-06, DEAL-08` | `GET` | `/api/v1/auction-rooms/{auctionId}/result` | 확정 | [경매 결과 조회](./deal-06-deal-08-get-auction-rooms-by-auctionid-result-800776eb.md) |
| `DEAL-09` | `POST` | `/api/v1/admin/auctions/{auctionId}/force-end` | 확정 | [운영 강제 종료](./deal-09-post-admin-auctions-by-auctionid-force-end-0c1776eb.md) |
| `ROOM-01` | `POST` | `/api/v1/auction-rooms` | 확정 | [경매방 생성](./room-01-post-auction-rooms-177776eb.md) |
| `ROOM-02` | `GET` | `/api/v1/auction-rooms` | 확정 | [경매방 목록 조회](./room-02-get-auction-rooms-98a776eb.md) |
| `ROOM-02` | `GET` | `/api/v1/auctions/categories` | 확정 | [경매방 카테고리 목록 조회](./room-02-get-auctions-categories-a23776eb.md) |
| `ROOM-03` | `GET` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 상세 조회](./room-03-get-auction-rooms-by-auctionid-323776eb.md) |
| `ROOM-03 → ROOM-02` | `GET` | `/api/v1/auction-rooms?categoryId=1&status=LIVE&size=20&cursor={cursor}` | 통합됨 | [카테고리별 경매방 목록 조회 통합 안내](./room-03-get-auctions-categoryid-1-status-live-size-20-cursor-by-cursor-293776eb.md) |
| `ROOM-04` | `DELETE` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 삭제](./room-04-delete-auction-rooms-by-auctionid-53b776eb.md) |
| `ROOM-04` | `PATCH` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 수정](./room-04-patch-auction-rooms-by-auctionid-a9b776eb.md) |
| `ROOM-05, LIVE-02` | `POST` | `/api/v1/auction-rooms/{auctionId}/start` | 확정 | [경매 방송 시작](./room-05-live-02-post-auction-rooms-by-auctionid-start-4bf776eb.md) |
| `ROOM-07` | `POST` | `/api/v1/auction-rooms/{auctionId}/join` | 확정 | [대기방 입장 및 상태 조회](./room-07-post-auction-rooms-by-auctionid-join-9e0776eb.md) |
| `ROOM-08` | `GET` | `/api/v1/auction-rooms/upcoming` | 확정 | [곧 시작 경매 조회](./room-08-get-auction-rooms-upcoming-d7d776eb.md) |
| `ROOM-09` | `POST` | `/api/v1/auctions/{auctionId}/end` | 확정 | [판매자 경매 종료](./room-09-post-auctions-by-auctionid-end.md) |

## WebSocket 이벤트

| 연관 기능 ID | Event Type | Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `BID-07, DEAL-09` | `AUCTION_CANCELED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-canceled.md) |
| `BID-07` | `AUCTION_ENDED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-ended.md) |
| `BID-03` | `AUCTION_EXTENDED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-extended.md) |
| `BID-07` | `AUCTION_NO_SALE` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-no-sale.md) |
| `—` | `AUCTION_PAUSED` | `/topic/auctions/{auctionId}` | 🚫 폐기 (2026-08-01) | [폐기 사유](./events/auction-paused.md) |
| `—` | `AUCTION_RESUMED` | `/topic/auctions/{auctionId}` | 🚫 폐기 (2026-08-01) | [폐기 사유](./events/auction-resumed.md) |
| `BID-07` | `AUCTION_SETTLED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-settled.md) |
| `ROOM-05` | `AUCTION_STARTED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/auction-started.md) |
| `BID-05` | `TIME_SYNC` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/time-sync.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

