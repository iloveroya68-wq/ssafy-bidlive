# wallet API

`src/main/java/com/ssafy/bidlive/wallet/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `PNT-01` | `GET` | `/api/v1/members/me/wallets` | 확정 | [거래 포인트 지갑 조회](./pnt-01-get-members-me-wallets-2e8776eb.md) |
| `PNT-01` | `POST` | `/api/v1/members/me/wallets/trade/charge` | 확정 | [거래 포인트 충전](./pnt-01-post-members-me-wallets-trade-charge-4b6776eb.md) |
| `PNT-01` | `POST` | `/api/v1/members/me/wallets/trade/refunds` | 확정 | [거래 포인트 환불](./pnt-01-post-members-me-wallets-trade-refunds-b98776eb.md) |
| `PNT-02` | `GET` | `/api/v1/members/me/point-ledgers` | 확정 | [포인트 원장 내역 조회](./pnt-02-get-members-me-point-ledgers-499776eb.md) |
| `PNT-03` | `INTERNAL` | 내부 처리 | 확정 | [포인트 홀드·해제·정산 처리](./pnt-03-internal-internal-556776eb.md) |
| `PNT-04` | `POST` | `/api/v1/payments/charge` | 확정 | [포인트 PG 테스트 결제 충전](./pnt-04-post-payments-charge-f44776eb.md) |
| `PNT-05` | `INTERNAL` | 내부 처리 | 확정 | [베팅 포인트 지급 처리](./pnt-05-internal-internal-fa7776eb.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

