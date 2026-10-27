# report API

`src/main/java/com/ssafy/bidlive/report/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `DEAL-04` | `POST` | `/api/v1/auctions/{auctionId}/reports` | 확정 | [경매 신고 접수](./deal-04-post-auctions-by-auctionid-reports-b7d776eb.md) |
| `ADMIN-01` | `GET` | `/api/v1/admin/reports` | 확정 (로컬) | [운영자 신고 목록 조회](./admin-01-get-admin-reports.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

