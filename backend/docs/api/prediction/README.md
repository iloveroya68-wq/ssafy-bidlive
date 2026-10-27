# prediction API

`src/main/java/com/ssafy/bidlive/prediction/` 도메인이 담당하는 로컬 API 계약이다.

## API 및 내부 계약

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `PRED-01` | `POST` | `/api/v1/auctions/{auctionId}/predictions` | 확정 | [낙찰가 예측 베팅 접수](./pred-01-post-auctions-by-auctionid-predictions-a9e776eb.md) |
| `PRED-02` | `GET` | `/api/v1/auctions/{auctionId}/prediction-results` | 확정 | [예측 결과 분포 조회](./pred-02-get-auctions-by-auctionid-prediction-results-60d776eb.md) |
| `PRED-03` | `INTERNAL` | 내부 처리 | 확정 | [예측 고정 배당 정산](./pred-03-internal-internal-49b776eb.md) |
| `PRED-04` | `GET` | `/api/v1/members/me/prediction-achievements` | 확정 | [명예 뱃지/등급 점수](./pred-04-get-members-me-prediction-achievements-a61776eb.md) |
| `PRED-05` | `GET` | `/api/v1/predictions/leaderboards/daily` | 확정 | [일일 낙찰가 예측 리더보드 조회](./pred-05-get-predictions-leaderboards-daily-0fc776eb.md) |

## WebSocket 이벤트

| 연관 기능 ID | Event Type | Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `PRED-01` | `PREDICTION_ACCEPTED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/prediction-accepted.md) |
| `PRED-03` | `PREDICTION_SETTLED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](./events/prediction-settled.md) |

## 출처

- [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

