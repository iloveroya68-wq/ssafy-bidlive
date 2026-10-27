# media API

`src/main/java/com/ssafy/bidlive/media/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `LIVE-01` | `POST` | `/api/v1/rooms/{roomId}/live/publisher` | 확정 | [스트리밍 방 세션 생성 및 발행 토큰 발급](./live-01-post-rooms-by-roomid-live-publisher-404776eb.md) |
| `LIVE-01` | `POST` | `/api/v1/rooms/{roomId}/live/subscriber` | 확정 | [스트리밍 시청 토큰 발급](./live-01-post-rooms-by-roomid-live-subscriber-310776eb.md) |
| `LIVE-02` | `POST` | `/api/v1/rooms/{roomId}/live/end` | 확정 | [라이브 스트리밍 방송 종료](./live-02-post-rooms-by-roomid-live-end-502776eb.md) |
| `LIVE-03` | `WS` | `/topic/auctions/{auctionId}/viewers` | 확정 | [접속 세션 수 브로드캐스트](./live-03-ws-topic-auctions-by-auctionid-viewers-f7f776eb.md) |
| `LIVE-03` | `INTERNAL` | 내부 처리 | 확정 | [접속 세션 수 집계](./live-03-internal-internal-3fa776eb.md) |
| `LIVE-06` | `POST` | `/api/v1/rooms/{roomId}/live/snapshot` | 확정 | [낙찰 순간 방송 스냅샷 캡처 및 저장](./live-06-post-rooms-by-roomid-live-snapshot-ef6776eb.md) |

## WebSocket 이벤트

| 연관 기능 ID | Event Type | Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `LIVE-02` | `LIVE_ENDED` | `/topic/rooms/{roomId}/live` | 신규 제안 | [이벤트 명세](./events/live-ended.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

