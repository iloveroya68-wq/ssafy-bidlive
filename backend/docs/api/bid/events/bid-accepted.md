# BID_ACCEPTED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [BID_ACCEPTED](https://app.notion.com/906776ebfd68833092ac0179ab0d56cf)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `BID_ACCEPTED` |
| 저장소 도메인 | `bid` |
| 연관 기능 ID | `BID-01, BID-02, BID-04` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 구현 | ✅ **구현됨** — `BidAcceptedWebSocketPublisher` (`2026-07-31` 확인) |
| 우선순위 | High |
| 발생 시점 | 사용자의 입찰이 정상 접수된 직후 |
| data 필드 | bidCount, participantCount, vibeMessage |

## 설명

입찰 건수와 참여자 수를 갱신한다. 입찰 금액, 입찰자 정보, 현재 순위는 공개하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [BID_ACCEPTED 원본](https://app.notion.com/906776ebfd68833092ac0179ab0d56cf)

