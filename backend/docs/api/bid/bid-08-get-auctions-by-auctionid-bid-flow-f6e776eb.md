# 입찰 흐름 그래프 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [입찰 흐름 그래프 조회](https://app.notion.com/f6e776ebfd6882a7bfe8817e4a381401)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-08` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 입찰 |
| Method | `GET` |
| Path / Destination | `/api/v1/auctions/{auctionId}/bid-flow` |
| 상태 | 폐기 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 김택림 |
| 우선순위 | Low |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

종료된 경매의 전체 입찰 흐름을 마스킹된 입찰자·금액·접수 시각 기준으로 조회한다.

## 비고

P2, 진행 중 조회 금지

## 개요
종료된 경매의 전체 입찰 이력을 시간순으로 조회해 결과 리포트와 입찰 흐름 그래프를 구성한다. 진행 중에는 비공개 원칙 때문에 제공하지 않는다.
## Request
```javascript
GET /api/v1/auctions/{auctionId}/bid-flow
```
## Response
```json
{
  "auctionId": 100,
  "reservePrice": 50000,
  "finalPrice": 74000,
  "items": [
    {
      "bidderLabel": "김**",
      "amount": 62000,
      "submittedAt": "2026-07-21T20:04:10+09:00"
    },
    {
      "bidderLabel": "나",
      "amount": 82000,
      "submittedAt": "2026-07-21T20:08:22+09:00"
    }
  ]
}
```
## 검증 규칙
- 경매 상태가 `ENDED`여야 한다.
- 입찰자 식별 정보는 서버에서 마스킹한다.
- 인증 사용자가 본인인 행은 `나`로 표시할 수 있다.
- 진행 중에는 금액·순위·입찰자 정보를 반환하지 않는다.
## 처리 흐름
```plain text
auctionId 검증
→ ENDED 상태 및 auction_result 확인
→ 입찰 이력 시간순 조회
→ 요청자 기준 마스킹
→ 결과 리포트 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [입찰 흐름 그래프 조회 원본](https://app.notion.com/f6e776ebfd6882a7bfe8817e4a381401)

