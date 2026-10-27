# AUCTION_RESUMED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [AUCTION_RESUMED](https://app.notion.com/b45776ebfd6882cb888101c6fbfca52d)

> **폐기된 계약이다. 구현하거나 발행하지 않는다.**

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `AUCTION_RESUMED` |
| 저장소 도메인 | `auction` |
| 연관 기능 ID | `—` (`BID-05`에서 제외) |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 🚫 폐기 (2026-08-01) |
| 우선순위 | Medium |
| 발생 시점 | 없음 |
| data 필드 | 없음 |

## 설명

공개 `PAUSED` 상태를 사용하지 않으므로 재개 사건도 존재하지 않는다. 판매자가 30초 안에
복귀하면 기존 `endAt`으로 경매를 계속하며 `AUCTION_RESUMED`에 연쇄한 별도 `TIME_SYNC`는
발행하지 않는다. 다만 메인 topic 재구독이 성공하면 BID-05 구독 규칙에 따라 `TIME_SYNC`를 발행한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [AUCTION_RESUMED 원본](https://app.notion.com/b45776ebfd6882cb888101c6fbfca52d)
