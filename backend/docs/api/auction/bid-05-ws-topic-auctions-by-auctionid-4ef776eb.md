# 서버 타이머 동기화

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [서버 타이머 동기화](https://app.notion.com/4ef776ebfd6883d79f7981c08d9942d9)

> **2026-08-01 로컬 확정.** 프론트 실연동 계약에 맞춰 `TIME_SYNC.data`를
> `serverTime`, `endAt` 두 필드로 고정했다. 일시정지·재개 이벤트는 사용하지 않는다.

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-05` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 방송 |
| Method | `WS` |
| Path / Destination | `/topic/auctions/{auctionId}` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매 |
| 마지막 수정 | 2026-08-01 |

## 개요

클라이언트 로컬 시계와 무관하게 서버 기준 남은 시간을 표시하도록 시각 정보를 전달한다.

- **Destination**: `/topic/auctions/{auctionId}`
- **Event Type**: `TIME_SYNC`

## 메시지 예시

```json
{
  "type": "TIME_SYNC",
  "data": {
    "serverTime": "2026-07-20T21:12:44+09:00",
    "endAt": "2026-07-20T21:20:00+09:00"
  }
}
```

## data 필드

<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>설명</td>
</tr>
<tr>
<td>`serverTime`</td>
<td>DateTime</td>
<td>이벤트 생성 시점의 서버 시각</td>
</tr>
<tr>
<td>`endAt`</td>
<td>DateTime</td>
<td>현재 서버가 보유한 경매 마감 시각</td>
</tr>
</table>

`auctionId`는 destination에 있으므로 `data`에 중복하지 않는다. `status`는 경매 상태 이벤트와
WS-03 스냅샷이 담당하므로 포함하지 않는다. `TIME_SYNC`는 상태를 바꾸지 않는 보정 메시지이므로
`stateVersion`도 포함하지 않는다.

## 발생 시점

- 이미 `LIVE`인 경매의 `/topic/auctions/{auctionId}` 구독 완료 직후
- WebSocket 재연결 후 위 destination을 다시 구독했을 때
- `AUCTION_STARTED` 발행 직후
- `AUCTION_EXTENDED` 발행 직후
- 해당 destination에 구독자가 있는 `LIVE` 경매에 30초마다

`/chat`, `/viewers`, `/bids`, `/questions` 같은 하위 destination의 구독은 발생 조건이 아니다.
`WAITING`은 확정된 `endAt`이 없고 `ENDED`는 카운트다운 보정이 필요하지 않으므로 발행하지 않는다.

구독 직후 발행도 방 전체 topic 브로드캐스트다. 기존 구독자가 같은 값을 다시 받아도 최신
`serverTime`, `endAt`으로 덮어쓰므로 중복 수신을 허용한다.

## 이벤트 순서

```plain text
경매 시작: AUCTION_STARTED → TIME_SYNC
경매 연장: AUCTION_EXTENDED → TIME_SYNC
```

## 프론트 계산

```plain text
serverOffset = serverTime - clientReceivedAt
estimatedServerTime = clientNow + serverOffset
remainingMillis = max(0, endAt - estimatedServerTime)
```

프론트는 매초 서버에 요청하거나 남은 시간을 상태로 감소시키지 않는다. 현재 클라이언트 시각에
마지막으로 받은 서버 오프셋을 적용해 화면만 다시 계산한다. 새로운 `TIME_SYNC`가 오면
`serverOffset`과 `endAt`을 모두 최신 값으로 덮어쓴다.

## 서버 처리 규칙

- `serverTime`은 이벤트 payload를 생성하는 시점의 서버 시각이다.
- 구독 직후와 주기 보정은 Redis 실시간 경매 상태의 `status`, `endAt`을 같은 조회에서 확인한다.
- Redis 상태가 없거나 `status`가 `LIVE`가 아니거나 `endAt`이 불완전하면 발행하지 않는다.
- 시작·연장 직후에는 커밋된 사건의 `endAt`을 사용한다.
- 한 경매의 동기화 실패가 다른 경매의 주기 발행이나 STOMP 구독 성공을 취소하지 않는다.

## 주의사항

- Redis에서 남은 시간을 매초 감소시키지 않고 절대 시각 `endAt`을 저장한다.
- 화면의 타이머가 0이 되어도 프론트가 낙찰을 결정하지 않는다.
- 실제 입찰 접수와 마감 판정은 항상 서버 시각을 기준으로 한다.
- 판매자 연결 유예 동안에도 타이머와 `endAt`을 멈추거나 다시 계산하지 않는다.
- 판매자가 30초 안에 복귀하지 않을 때의 종료·유찰 처리는 BID-07의 범위다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [서버 타이머 동기화 원본](https://app.notion.com/4ef776ebfd6883d79f7981c08d9942d9)
