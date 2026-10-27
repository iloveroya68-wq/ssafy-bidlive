# LIVE_ENDED

> Notion 원본이 없는 신규 로컬 이벤트 계약이다. LIVE-02 구현에 필요한 시청자 종료 알림을
> `media` 도메인 계약으로 새로 정의했다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `LIVE_ENDED` |
| 저장소 도메인 | `media` |
| 연관 기능 ID | `LIVE-02` |
| Destination | `/topic/rooms/{roomId}/live` |
| 수신 대상 | 방 구독자 전체 |
| 상태 | 신규 제안 |
| 우선순위 | High |
| 발생 시점 | 방송 종료 트랜잭션이 커밋된 직후 |
| data 필드 | endedAt |

## 구독 위치

```plain text
SUB /topic/rooms/{roomId}/live
```

## 결과

```json
{
  "type": "LIVE_ENDED",
  "data": {
    "endedAt": "2026-07-20T21:40:00+09:00"
  }
}
```

## 설명

판매자가 방송을 종료해 미디어 세션이 닫혔음을 방 구독자에게 알린다. 프론트는 이 이벤트를 받으면
플레이어를 정리하고 종료 화면으로 전환한다.

## 처리 규칙

- envelope은 도메인 공용 `common.event.WsEvent<T>`를 사용하고, `data` 타입(`LiveEndedData`)은
  사건이 발생한 `media` 도메인이 소유한다.
- 방송은 경매가 아닌 방(room) 리소스이므로 destination을 경매 이벤트의
  `/topic/auctions/{auctionId}`와 분리한다. `roomId`(String)와 `auctionId`(Long)의 타입 차이는
  서로 다른 리소스 식별자이기 때문이며 통일하지 않는다.
- 커밋 이후에만 송출하므로 롤백된 종료는 시청자에게 전달되지 않는다.
- 조건부 상태 갱신에 성공한 요청만 이벤트를 발생시키므로 중복 종료 요청으로 두 번 나가지 않는다.
- `roomId`는 구독 Destination에 이미 있으므로 공통 메시지 형식 규칙대로 `data`에서 제외한다.
- 경매 마감 이벤트(`AUCTION_ENDED`)와는 별개이며, 방송 세션 종료만을 의미한다.

## 확인 필요

- `auction` 도메인의 `AUCTION_ENDED`와 역할이 겹치는지, 두 이벤트를 모두 유지할지 확정해야 한다.
- Destination을 `/topic/rooms/{roomId}/live`로 둘지, LIVE-03과 같은
  `/topic/auctions/{auctionId}/...` 체계로 맞출지 명세 담당자 확인이 필요하다.

## 출처

- [WebSocket 공통 메시지 형식](../../common/websocket-message-format.md)
- [라이브 스트리밍 방송 종료](../live-02-post-rooms-by-roomid-live-end-502776eb.md)
