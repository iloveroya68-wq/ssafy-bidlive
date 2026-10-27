# PREDICTION_SETTLED

> Notion WebSocket 이벤트 명세에서 동기화한 로컬 이벤트 계약이다.  
> 원본: [PREDICTION_SETTLED](https://app.notion.com/e03776ebfd68831d90d2818a20c15517)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| Event Type | `PREDICTION_SETTLED` |
| 저장소 도메인 | `prediction` |
| 연관 기능 ID | `PRED-03` |
| Destination | `/topic/auctions/{auctionId}` |
| 수신 대상 | 경매방 전체 |
| 상태 | 확정 |
| 우선순위 | Medium |
| 발생 시점 | 예측 베팅 정산 배치가 종료되었을 때 |
| data 필드 | settlementStatus, settledCount |

## 설명

예측 결과·지갑·점수·등급·뱃지 반영이 끝났음을 경매방 전체에 알린다. 배치의 종료 상태와 반영
건수만 담고, 분포와 배당 내용은 수신 후 PRED-02 예측 결과 조회 API로 확인한다.

## Payload

```json
{
  "type": "PREDICTION_SETTLED",
  "data": {
    "settlementStatus": "COMPLETED",
    "settledCount": 128
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `settlementStatus` | String | `COMPLETED` \| `PARTIAL` \| `FAILED` |
| `settledCount` | number | 정산이 반영된 예측 건수 |

### `settlementStatus`

| 값 | 의미 |
| --- | --- |
| `COMPLETED` | 대상 전 건의 정산이 반영되었다. |
| `PARTIAL` | 일부만 반영되었고 나머지는 재시도 대상이다. |
| `FAILED` | 반영된 건이 없다. 배치는 종료됐지만 정산이 실패했다. |

## 처리 규칙

- 배치가 종료된 뒤 발행한다. 개별 정산 트랜잭션이 롤백된 건은 `settledCount`에 포함하지 않는다.
- 반영 건이 하나도 없으면 발행을 생략하지 않고 `FAILED`로 발행한다. 실패를 알리지 않은 채 화면이
  집계 중에 머무르게 두지 않는다.
- `auctionId`는 Destination에 포함되므로 메시지 본문에서 제외한다.
- `settledCount`는 정산 배치가 처리한 건수이며 분포·배당 정보가 아니다. 적중 구간과 지급 합계는
  이벤트에 포함하지 않는다.
- 실시간 예측 분포는 어떤 경우에도 이벤트로 내보내지 않는다.
- 클라이언트는 이벤트를 수신하면
  `GET /api/v1/auctions/{auctionId}/prediction-results`를 호출한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [PREDICTION_SETTLED 원본](https://app.notion.com/e03776ebfd68831d90d2818a20c15517)

