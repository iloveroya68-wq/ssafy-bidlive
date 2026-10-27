# 참여한 경매 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [참여한 경매 조회](https://app.notion.com/87d776ebfd68820dbcf98170ab485587)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `MEM-02` |
| 저장소 도메인 | `member` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/members/me/auctions/participated` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `ParticipatedAuctionQueryController` (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:13Z |

## 요약

로그인 사용자가 입찰 또는 예측 베팅으로 참여한 경매와 본인의 결과를 커서 기반으로 조회한다.

## 비고

낙찰 경매는 중복 방지를 위해 기본 제외

## 개요

로그인 사용자가 입찰 또는 예측 베팅으로 참여한 경매와 본인의 결과를 커서 기반으로 조회한다.

입찰과 예측 참여는 한 경매에서 상호 배타이므로 각 항목의 `participationType`은 하나만 가진다. 예측 결과값은 경매 결과 조회 API의 `myPredictionSettlement.result`와 동일한 타입을 사용한다.

## Request

```
GET /api/v1/members/me/auctions/participated?cursor={cursor}&size=8
Authorization: Bearer {accessToken}
```

## Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `cursor` | String | 선택 | 이전 응답의 `nextCursor`. 최초 조회 시 생략 |
| `size` | Integer | 선택 | 조회 개수. 기본 8, 최대 20 |

## Response

### 200 OK

```json
{
  "items": [
    {
      "auctionId": 101,
      "participationType": "BID",
      "productTitle": "헤드폰",
      "imageUrl": "https://cdn.example.com/products/headphone.jpg",
      "roomStatus": "ENDED",
      "resultType": "SOLD",
      "result": "LOST",
      "resultLabel": "미낙찰",
      "finalPrice": 74000,
      "myFinalBid": 61000,
      "holdReleasedAmount": 61000,
      "predictedPrice": null,
      "errorRate": null,
      "errorBand": null,
      "stakeAmount": null,
      "payoutAmount": null,
      "refundAmount": null,
      "endedAt": "2026-07-20T20:40:00+09:00"
    },
    {
      "auctionId": 102,
      "participationType": "PREDICTION",
      "productTitle": "한정판 피규어",
      "imageUrl": "https://cdn.example.com/products/figure.jpg",
      "roomStatus": "ENDED",
      "resultType": "SOLD",
      "result": "HIT",
      "resultLabel": "예측 적중",
      "finalPrice": 74000,
      "myFinalBid": null,
      "holdReleasedAmount": null,
      "predictedPrice": 72000,
      "errorRate": 2.70,
      "errorBand": "WITHIN_10",
      "stakeAmount": 300,
      "payoutAmount": 900,
      "refundAmount": null,
      "endedAt": "2026-07-19T21:30:00+09:00"
    },
    {
      "auctionId": 103,
      "participationType": "PREDICTION",
      "productTitle": "빈티지 앰프",
      "imageUrl": "https://cdn.example.com/products/amp.jpg",
      "roomStatus": "ENDED",
      "resultType": "NO_BID",
      "result": "REFUNDED",
      "resultLabel": "유찰 · 베팅 환불",
      "finalPrice": null,
      "myFinalBid": null,
      "holdReleasedAmount": null,
      "predictedPrice": 180000,
      "errorRate": null,
      "errorBand": null,
      "stakeAmount": 300,
      "payoutAmount": 0,
      "refundAmount": 300,
      "endedAt": "2026-07-18T20:00:00+09:00"
    }
  ],
  "nextCursor": "opaque-cursor",
  "hasNext": true
}
```

## 응답 필드

- `items`: 참여한 경매 목록. 결과가 없으면 빈 배열
- `participationType`: 참여 유형. `BID` 또는 `PREDICTION`
- `roomStatus`: 현재 경매방 상태
- `resultType`: 경매 결과. `SOLD`, `NO_BID`, `HOST_LEFT`, `FORCED`
- `result`: 현재 사용자의 입찰 또는 예측 결과
- `resultLabel`: 화면에 표시할 결과 문구
- `finalPrice`: 실제 낙찰가. 낙찰가 없이 종료되면 `null`
- `myFinalBid`, `holdReleasedAmount`: 입찰 참여일 때만 값이 존재
- `predictedPrice`, `errorRate`, `errorBand`, `stakeAmount`: 예측 참여일 때만 값이 존재
- `payoutAmount`: 예측 적중으로 지급된 금액. 미적중이면 `0`
- `refundAmount`: 유찰·이탈·강제 종료로 환불된 금액. 환불이 아니면 `null`
- `endedAt`: 실제 경매 종료 시각
- `nextCursor`, `hasNext`: 무한 스크롤용 커서 정보

## 결과값 표준

| 참여 유형 | 결과 | 설명 |
| --- | --- | --- |
| `BID` | `WON` | 낙찰 |
| `BID` | `LOST` | 미낙찰 |
| `PREDICTION` | `HIT` | 오차율이 20% 이내 |
| `PREDICTION` | `MISS` | 오차율이 20%를 초과 |
| `PREDICTION` | `REFUNDED` | 낙찰가 없이 종료되어 베팅 포인트 환불 |

- `PREDICTION` 결과에는 `WON`, `LOST`를 사용하지 않는다.
- `BID` 결과에는 `HIT`, `MISS`를 사용하지 않는다.
- 경매 결과 조회 API의 `myPredictionSettlement.result`도 동일하게 `HIT`, `MISS`, `REFUNDED`를 사용한다.

## 오차 밴드

| `errorBand` | 설명 | `result` |
| --- | --- | --- |
| `WITHIN_5` | 실제 낙찰가 대비 오차율 5% 이내 | `HIT` |
| `WITHIN_10` | 오차율 5% 초과 10% 이내 | `HIT` |
| `WITHIN_20` | 오차율 10% 초과 20% 이내 | `HIT` |
| `MISS` | 오차율 20% 초과 | `MISS` |
| `null` | 환불 또는 결과 미확정 | `REFUNDED` 또는 `null` |

## 규칙

- 입찰과 예측 참여는 같은 경매에서 상호 배타다.
- 최신 종료순인 `endedAt DESC, auctionId DESC`로 조회한다.
- 낙찰 받은 경매는 `낙찰 받은 경매` 탭과의 중복 방지를 위해 기본적으로 제외한다.
- 진행 중인 참여 내역을 포함하면 `result`, `resultLabel`, `finalPrice`, 결과 관련 금액은 `null`로 반환한다.
- 예측 적중 시 `result=HIT`과 해당 `errorBand`를 반환한다.
- 예측 미적중 시 `result=MISS`, `errorBand=MISS`, `payoutAmount=0`을 반환한다.
- 유찰·판매자 이탈·강제 종료로 환불되면 `result=REFUNDED`, `payoutAmount=0`, `refundAmount=stakeAmount`로 반환한다.
- 커서는 프론트가 해석하지 않고 다음 요청에 그대로 전달한다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_CURSOR` | 커서 형식 오류 |
| 401 | `UNAUTHORIZED` | Access Token이 없거나 유효하지 않음 |

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [참여한 경매 조회 원본](https://app.notion.com/87d776ebfd68820dbcf98170ab485587)

