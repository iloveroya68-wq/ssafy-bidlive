# AUCTION_SETTLED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_SETTLED](https://app.notion.com/f1e776ebfd6883e4bc010177ffdc447b)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_SETTLED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-07` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | 비크리 낙찰과 포인트 정산이 완료되었을 때 |
| data 필드 | winner, highestBidAmount, secondHighestBidAmount, finalPrice, resultType |

## 설명

낙찰자와 최종 낙찰가를 전원에게 동시에 공개한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_SETTLED 원본](https://app.notion.com/f1e776ebfd6883e4bc010177ffdc447b)

