# AUCTION_CANCELED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_CANCELED](https://app.notion.com/09b776ebfd6883a7ae56010465dc8cc3)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_CANCELED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `BID-07, DEAL-09` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | Medium |
| 발생 시점 | 강제 종료 상태 반영과 포인트 환급 완료 후 |
| data 필드 | reason, endedAt, resultPath |

## 설명

낙찰 없이 경매를 종료하고 입찰 및 예측 베팅 홀드 반환을 안내한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_CANCELED 원본](https://app.notion.com/09b776ebfd6883a7ae56010465dc8cc3)

