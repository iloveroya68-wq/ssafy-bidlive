# AUCTION_STARTED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_STARTED](https://app.notion.com/3fa776ebfd688333b0698107d358bf0b)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_STARTED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `ROOM-05` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | High |
| 발생 시점 | 예약 경매 또는 대기 중인 경매가 시작될 때 |
| data 필드 | startedAt, endAt |

## 설명

대기방 사용자를 라이브 상태로 전환하고 입찰과 예측 베팅을 활성화한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_STARTED 원본](https://app.notion.com/3fa776ebfd688333b0698107d358bf0b)

