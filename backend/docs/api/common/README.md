# common API

`src/main/java/com/ssafy/bidlive/common/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `WS-01` | `WS` | `/ws` | 확정 | [소켓 연결·인증](./ws-01-ws-ws-053776eb.md) |
| `WS-01` | `WS` | `/app/auth/renew` | 확정 | [소켓 토큰 재인증](./ws-01-ws-app-auth-renew-e0d776eb.md) |
| `WS-02` | `INTERNAL` | 내부 처리 | 확정 | [구독 권한 검증](./ws-02-internal-internal-146776eb.md) |
| `WS-03` | `GET` | `/api/v1/auctions/{auctionId}/realtime-state` | 확정 | [경매 상태 스냅샷](./ws-03-get-auctions-by-auctionid-realtime-state-9b3776eb.md) |
| `WS-04` | `WS` | `/user/queue/errors` | 확정 | [WS 에러 수신](./ws-04-ws-user-queue-errors-498776eb.md) |

## 검증 문서

- [WebSocket 인증·구독 권한 보안 회귀 검증](./websocket-security-regression.md)

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

