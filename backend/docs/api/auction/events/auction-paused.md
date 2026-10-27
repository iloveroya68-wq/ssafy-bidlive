# AUCTION_PAUSED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_PAUSED](https://app.notion.com/f60776ebfd688267a6c6013c9bc74aed)

> **폐기된 계약이다. 구현하거나 발행하지 않는다.**

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_PAUSED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `—` (`BID-05`에서 제외) |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 🚫 폐기 (2026-08-01) |
| 우선순위 | Medium |
| 발생 시점 | 없음 |
| data 필드 | 없음 |

## 설명

판매자 연결이 끊겨도 공개 경매 상태와 타이머를 `PAUSED`로 전환하지 않는다. 기존 `endAt`은
계속 유효하며 프론트는 이 이벤트를 정의하거나 처리하지 않는다.

판매자가 30초 안에 복귀하지 않으면 BID-07 종료·유찰 흐름을 사용한다. 30초 안에 복귀하면
기존 `endAt`으로 경매를 계속하며 별도의 재개 이벤트를 발행하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_PAUSED 원본](https://app.notion.com/f60776ebfd688267a6c6013c9bc74aed)
