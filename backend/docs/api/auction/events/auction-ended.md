# AUCTION_ENDED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_ENDED](https://app.notion.com/72b776ebfd68838ca76d01b4b91ae0d4)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_ENDED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-07` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | 서버 기준 경매 마감 시각에 도달했을 때 |
| data 필드 | endedAt, status |

## 설명

신규 입찰과 예측 베팅을 차단하고 비크리 정산이 시작되었음을 알린다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_ENDED 원본](https://app.notion.com/72b776ebfd68838ca76d01b4b91ae0d4)

