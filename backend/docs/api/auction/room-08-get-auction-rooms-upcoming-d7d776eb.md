# 곧 시작 경매 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [곧 시작 경매 조회](https://app.notion.com/d7d776ebfd688207b3b801bba018ef89)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `ROOM-08` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/auction-rooms/upcoming` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `UpcomingAuctionController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:14Z |

## 요약

현재부터 1시간 이내 시작 예정인 대기방을 예정 시각순으로 최대 5개 조회한다.

## 비고

최대 5개와 hiddenCount 반환

## Request
```javascript
GET /api/v1/auction-rooms/upcoming?limit=5
```
## Response
```json
{
  "items": [
    {
      "auctionId": 100,
      "productTitle": "키크론 Q1 Pro",
      "imageUrl": "https://cdn.example.com/products/abc.jpg",
      "scheduledAt": "2026-07-21T20:00:00+09:00",
      "waitingCount": 18,
      "status": "WAITING"
    }
  ],
  "totalCount": 8,
  "hiddenCount": 3
}
```
## 규칙
- 현재 서버 시각 이후 1시간 이내의 `WAITING` 방만 조회한다.
- `scheduledAt` 오름차순으로 정렬한다.
- 최대 5개를 반환하며 초과 개수는 `hiddenCount`로 제공한다.
- 대상이 없으면 빈 배열과 `hiddenCount=0`을 반환한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [곧 시작 경매 조회 원본](https://app.notion.com/d7d776ebfd688207b3b801bba018ef89)

