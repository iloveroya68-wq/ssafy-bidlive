# AUCTION_NO_SALE

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_NO_SALE](https://app.notion.com/ab0776ebfd68832ab20501d575088f34)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_NO_SALE` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-07` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | 유효 입찰이 없거나 낙찰 없이 경매가 종료되었을 때 |
| data 필드 | reason, endedAt |

## 설명

유찰 사유와 포인트 홀드 반환 사실을 전달한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_NO_SALE 원본](https://app.notion.com/ab0776ebfd68832ab20501d575088f34)

