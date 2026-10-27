# WebSocket 공통 메시지 형식

> [Notion 최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)에서 동기화했다.

## 공통 메시지 형식
WebSocket 경매 상태 이벤트는 공통적으로 `type`과 `data`만 사용한다.
```json
{
  "type": "AUCTION_EXTENDED",
  "data": {
    "endAt": "2026-07-20T15:30:00+09:00",
    "extensionCount": 2,
    "remainingExtensions": 1
  }
}
```
<table>
<tr>
<td>공통 필드</td>
<td>타입</td>
<td>필수 여부</td>
<td>설명</td>
</tr>
<tr>
<td>`type`</td>
<td>String</td>
<td>필수</td>
<td>수신한 WebSocket 이벤트의 종류</td>
</tr>
<tr>
<td>`data`</td>
<td>Object</td>
<td>필수</td>
<td>이벤트 종류별 상세 데이터</td>
</tr>
</table>
- `Destination`은 메시지 본문에 포함하지 않는다.
- 프론트는 `/topic/auctions/{auctionId}`를 구독한 상태에서 메시지를 수신한다.
- 프론트는 `type`을 기준으로 이벤트 처리 로직을 분기한다.
- `auctionId`는 구독 Destination에 이미 포함되어 있으므로 메시지 본문에서 제외한다.
- `serverTime`은 모든 이벤트의 공통 필드로 두지 않고, 시간 동기화가 필요한 `TIME_SYNC` 이벤트의 `data`에만 포함한다.
- `eventId`와 `sequence`는 초기 구현에서는 사용하지 않는다.
- 추후 이벤트 중복 처리나 메시지 누락 복구가 필요해지면 `eventId`와 `sequence`를 추가할 수 있다.
- ⚠️ `PREDICTION_UPDATED`는 **폐기된 이벤트다.** 실시간 예측 분포를 공개하지 않는 원칙에 따라
  삭제했고 등재된 WebSocket 이벤트 목록에도 없다. 아래 예시에 다시 추가하지 않는다. 예측 분포는
  정산 후 `GET /api/v1/auctions/{auctionId}/prediction-results`(PRED-02)로만 조회한다.
## 프론트 처리 예시
```javascript
client.subscribe(`/topic/auctions/${auctionId}`, message => {
  const event = JSON.parse(message.body);

  switch (event.type) {
    case "BID_ACCEPTED":
      updateBidState(event.data);
      break;

    case "AUCTION_EXTENDED":
      updateAuctionEndAt(event.data);
      break;

    case "TIME_SYNC":
      synchronizeTimer(event.data);
      break;

    case "SETTLEMENT_PROGRESS":
      updateSettlementProgress(event.data);
      break;

    case "AUCTION_SETTLED":
      showAuctionResult(event.data);
      break;
  }
});
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)

