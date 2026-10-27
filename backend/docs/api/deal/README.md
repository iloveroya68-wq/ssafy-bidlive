# deal API

`src/main/java/com/ssafy/bidlive/deal/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `DEAL-01` | `GET` | `/api/v1/members/me/deals` | 확정 | [내 거래 목록 조회](./deal-01-get-members-me-deals-4a7776eb.md) |
| `DEAL-05` | `INTERNAL` | 내부 처리 | 확정 | [7일 자동 구매 확정 처리](./deal-05-internal-internal-a43776eb.md) |
| `DEAL-05` | `POST` | `/api/v1/deals/{dealId}/receipt-confirmation` | 확정 | [구매자 수령 확인](./deal-05-post-deals-by-dealid-receipt-confirmation-f26776eb.md) |
| `DEAL-05` | `PATCH` | `/api/v1/deals/{dealId}/shipping-address` | 확정 | [배송지 입력](./deal-05-patch-deals-by-dealid-shipping-address-620776eb.md) |
| `DEAL-05` | `GET` | `/api/v1/deals/{dealId}/shipping` | 확정 | [배송 정보 조회](./deal-05-get-deals-by-dealid-shipping.md) |
| `DEAL-05` | `POST` | `/api/v1/deals/{dealId}/shipment-confirmation` | 확정 | [판매자 발송 확인](./deal-05-post-deals-by-dealid-shipment-confirmation-255776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/deals/todos` | 확정 | [내 거래 할 일 조회](./mem-02-get-members-me-deals-todos-27e776eb.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

