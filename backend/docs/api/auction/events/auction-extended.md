# AUCTION_EXTENDED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_EXTENDED](https://app.notion.com/426776ebfd6883dfb0dc81b41ac9c5e2)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_EXTENDED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-03` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | 판매자의 연장 요청이 서버 검증에 성공했을 때 |
| data 필드 | endAt, extensionCount, remainingExtensions |

## 설명

서버 마감 시각을 5분 연장하고 모든 사용자의 타이머를 갱신한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_EXTENDED 원본](https://app.notion.com/426776ebfd6883dfb0dc81b41ac9c5e2)

