# 경매 결과 조회

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [경매 결과 조회](https://app.notion.com/800776ebfd6882868c4e81299cb97d39)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `DEAL-06, DEAL-08` |
| 저장소 도메인 | `auction` |
| Notion 도메인 | 경매 |
| Method | `GET` |
| Path / Destination | `/api/v1/auction-rooms/{auctionId}/result` |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Public |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `AuctionResultController` (S15P11A704-295). ⚠️ 아래 「구현 메모」 필독 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, PostgreSQL, 경매 |
| 마지막 수정 | 2026-07-27 02:08:11Z |

## 요약

경매 종료 결과를 조회자 역할에 맞게 마스킹하여 반환하며 낙찰과 유찰 결과를 모두 처리한다.

## 비고

낙찰자·미낙찰자·예측자·관전자 역할 분기, 유찰 사유 분기

## 공통 Response
```json
{
  "auctionId": 100,
  "resultType": "SOLD",
  "viewerRole": "PREDICTOR",
  "winnerLabel": "김**",
  "finalPrice": 74000,
  "priceDecisionType": "SECOND_PRICE",
  "snapshotUrl": "https://cdn.example.com/snapshots/100.jpg",
  "myBidSettlement": null,
  "myPredictionSettlement": {
    "predictionId": 701,
    "predictedPrice": 74000,
    "stakeAmount": 300,
    "result": "WON",
    "payoutAmount": 900
  },
  "bids": [],
  "predictionBands": []
}
```
## SOLD Response
```json
{
  "auctionId": 100,
  "resultType": "SOLD",
  "viewerRole": "WINNING_BIDDER",
  "winnerLabel": "나",
  "finalPrice": 74000,
  "priceDecisionType": "SECOND_PRICE",
  "snapshotUrl": "https://cdn.example.com/snapshots/100.jpg",
  "myBidSettlement": {
    "result": "WON",
    "submittedAmount": 82000,
    "paymentAmount": 74000,
    "releasedAmount": 8000
  },
  "myPredictionSettlement": null,
  "bids": [],
  "predictionBands": []
}
```
## Prediction Response
```json
{
  "auctionId": 100,
  "resultType": "SOLD",
  "viewerRole": "PREDICTOR",
  "winnerLabel": "김**",
  "finalPrice": 74000,
  "priceDecisionType": "SECOND_PRICE",
  "snapshotUrl": "https://cdn.example.com/snapshots/100.jpg",
  "myBidSettlement": null,
  "myPredictionSettlement": {
    "predictionId": 701,
    "predictedPrice": 74000,
    "stakeAmount": 300,
    "result": "WON",
    "payoutAmount": 900
  },
  "bids": [
    {
      "bidderLabel": "김**",
      "amount": 82000,
      "submittedAt": "2026-07-21T20:10:00+09:00"
    },
    {
      "bidderLabel": "박**",
      "amount": 74000,
      "submittedAt": "2026-07-21T20:08:00+09:00"
    }
  ],
  "predictionBands": [
    {
      "minPrice": 70000,
      "maxPrice": 74999,
      "participantCount": 5,
      "selectedByMe": true,
      "winningBand": true
    }
  ]
}
```
## Spectator Response
```json
{
  "auctionId": 100,
  "resultType": "SOLD",
  "viewerRole": "SPECTATOR",
  "winnerLabel": "김**",
  "finalPrice": 74000,
  "priceDecisionType": "SECOND_PRICE",
  "myBidSettlement": null,
  "myPredictionSettlement": null,
  "bids": [
    {
      "bidderLabel": "김**",
      "amount": 82000,
      "submittedAt": "2026-07-21T20:10:00+09:00"
    },
    {
      "bidderLabel": "박**",
      "amount": 74000,
      "submittedAt": "2026-07-21T20:08:00+09:00"
    }
  ],
  "predictionBands": []
}
```
## No-sale Response
```json
{
  "auctionId": 101,
  "resultType": "HOST_LEFT",
  "viewerRole": "PREDICTOR",
  "message": "판매자 이탈로 종료됐어요",
  "myBidSettlement": null,
  "myPredictionSettlement": {
    "result": "REFUNDED",
    "stakeAmount": 300,
    "refundAmount": 300
  }
}
```
## 종료 사유
<table>
<tr>
<td>종료 사유</td>
<td>입찰 홀드</td>
<td>예측 베팅</td>
<td>판매자 재등록 CTA</td>
</tr>
<tr>
<td>`NO_BID`</td>
<td>해제할 홀드 없음</td>
<td>전액 환불</td>
<td>표시</td>
</tr>
<tr>
<td>`HOST_LEFT`</td>
<td>전액 해제</td>
<td>전액 환불</td>
<td>표시</td>
</tr>
<tr>
<td>`FORCED`</td>
<td>라이브면 전액 해제, 대기방이면 해제할 홀드 없음</td>
<td>전액 환불</td>
<td>숨김</td>
</tr>
</table>
## 규칙
- 결과 확정 전에는 조회할 수 없다.
- 본인의 행만 `나`로 표시하며 다른 참여자는 서버가 마스킹한다.
- `NO_BID`는 해제할 입찰 홀드가 없음을 표시한다.
- `HOST_LEFT`·`FORCED`는 모든 입찰 홀드와 예측 스테이크를 환불한다.
- S-12 화면 이름을 “유찰·강제 종료 안내”로 사용한다.
- 강제 종료에서는 재등록 버튼 대신 운영 검토 안내를 표시한다.
- 참여자는 기존 화면 위 종료 오버레이를 본 뒤 S-12로 이동한다.
- 판매자는 콘솔 통보 모달을 확인한 뒤 S-12로 이동한다.
- `viewerRole`은 `SELLER | WINNING_BIDDER | LOSING_BIDDER | PREDICTOR | SPECTATOR` 중 하나다.
- 입찰 참여자의 개인 정산 정보는 `myBidSettlement`에 반환한다.
- 예측 참여자의 개인 정산 정보는 `myPredictionSettlement`에 반환한다.
- 관전자는 두 개인 정산 필드를 모두 `null`로 반환한다.
- 예측 성공 여부, 베팅 금액, 지급 금액은 본인 정보만 반환한다.
- `predictionBands`에는 종료 후 공개 가능한 집계 분포만 포함하고 다른 사용자의 개별 예측가는 반환하지 않는다.
- `canRelist`는 판매자에게만 의미가 있으며, 일반 참여자에게는 `false`로 반환하거나 필드를 생략한다.
- `snapshotUrl`은 LIVE-06 낙찰 순간 스냅샷 URL이며 낙찰(`SOLD`)에만 담는다. 스냅샷이 없거나
  유찰이면 필드를 생략한다(위 「Spectator Response」가 그 경우다).
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 정보가 없거나 유효하지 않음</td>
</tr>
<tr>
<td>403</td>
<td>`AUCTION_RESULT_ACCESS_DENIED`</td>
<td>해당 경매 결과를 조회할 권한이 없음</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매가 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`PREDICTION_RESULT_NOT_AVAILABLE`</td>
<td>경매가 종료되지 않았거나 입찰·예측 정산 결과가 아직 확정되지 않음</td>
</tr>
<tr>
<td>503</td>
<td>`AUCTION_RESULT_UNAVAILABLE`</td>
<td>경매 결과 또는 정산 정보 조회에 실패함</td>
</tr>
</table>

## 구현 메모 (2026-07-30, S15P11A704-295)

> ### ⚠️ 이 문서의 `predictionBands`와 `result` 값은 폐기된 설계다 — 구현은 따르지 않았다
>
> 위 「Prediction Response」의
> `predictionBands: [{ minPrice, maxPrice, participantCount, selectedByMe, winningBand }]`는
> **사용자가 가격 구간을 고르는 파리뮤추얼 방식**이다. 현행은 **금액 직접 입력 + 오차율 고정 배당**
> (5% 이내 ×5 / 10% ×3 / 20% ×1.5 / 그 외 0)이고, `selectedByMe`("내가 고른 구간")라는 개념 자체가 없다.
> 프론트 절대 규칙(AGENTS.md 규칙 7)도 구간 선택 설계를 **"따라 만들지 않는다"**로 못 박았다.
>
> 같은 지적이 `323`에서 제기돼 **완료 처리**됐는데 이 사본에는 반영되지 않았다(Notion 원본은 확인 불가).
> 그래서 구현은 **PRED-02 「예측 결과 분포 조회」(확정)와 같은 오차 밴드 형태**로 맞췄다 —
> 두 화면이 다른 모양을 받으면 프론트가 변환을 하나 더 들고 있어야 한다.
>
> ```json
> "predictionBands": [
>   { "band": "WITHIN_5", "participantCount": 2, "multiplier": 5, "totalPayoutAmount": 2500 }
> ]
> ```
>
> `myPredictionSettlement.result`도 예시의 `"WON"`이 아니라 **`HIT` / `MISS` / `REFUNDED`**다.
> `WON`/`LOST`는 입찰 정산 값이고 예측과 섞지 않는다(`323`에서 통일 확정, 프론트 타입도 그 기준).

### 구현하며 정한 것

- **403 `AUCTION_RESULT_ACCESS_DENIED`는 쓰지 않는다.** 비공개 방은 존재 자체를 감춰야 하므로
  삭제(ROOM-04)·입장(ROOM-07)과 같이 **404**로 답한다. 403을 주면 "그 방은 있는데 네가 못 볼 뿐"이 샌다.
  그래서 판정 순서도 **404 → 409**다.
- **인증은 선택이다**(메타데이터 「인증: 불필요 / 공개 범위: Public」). 비로그인은 `SPECTATOR`이고
  개인 정산 두 필드가 `null`이다. 토큰이 있으면 역할을 판정한다.
- **역할 우선순위**: 판매자 → 낙찰자 → 미낙찰자 → 예측자 → 관전자. 판매자는 참여를 못 하므로 맨 앞이고,
  입찰·예측은 배타라 서로를 배제한다.
- **밴드·적중 여부는 저장된 `result_status`·`reward_multiplier`를 쓴다.** 오차율을 다시 계산하지 않는다 —
  정산 시점의 정책이 그 값에 박혀 있어서, 배수 정책이 바뀌어도 지난 경매 결과가 흔들리지 않는다.
  ⚠️ `prediction_result.error_rate`는 **퍼센트**다(`0.68` = 0.68%). 비율로 읽으면 전원 미적중이 된다.
- **집계는 `result_status`까지 묶는다.** `REFUNDED`는 오차 밴드가 아니며 환불 배수도 정산 밴드와 별도로 처리된다.
- **`predictionBands`는 0건 밴드도 채워 항상 4줄**이다 — PRED-02와 같다.
  빠뜨리면 화면이 "0명"과 "집계 안 됨"을 구분하지 못한다.
- **`bids`는 참여자당 최신 1건**이다(`DISTINCT ON`). 상향 입찰 이력을 다 주면 프론트가
  `bids.length`를 "N명"으로 쓰므로 인원과 순위가 틀어진다.
- **`FORCED`는 판매자에게도 `canRelist: false`**다(명세 「종료 사유」표).
  `termination_reason`에 CHECK 제약이 없어 모르는 값은 `FORCED`로 좁힌다.
- **본인 입찰은 최신 1건**(`sequence_no` 최대)만 본다. 상향 입찰로 행이 쌓이는데 홀드도 그 금액
  기준이라, 옛 행을 집으면 해제액이 틀린다.
- 유찰 `message`는 서버가 만든다 — 사유 3값에 문구를 매핑한다.

### ⚠️ 선행 조건: 이 API는 아직 데이터를 못 받는다

`auction_result`에 **INSERT하는 운영 코드가 없다**(테스트 픽스처만 넣는다). 비크리 낙찰 집계가
붙기 전까지 이 엔드포인트는 **항상 409 `PREDICTION_RESULT_NOT_AVAILABLE`**을 돌려준다.
읽기 계약과 마스킹·역할 판정을 먼저 고정해 두는 것이 이번 구현의 목적이다.

### 갱신: `snapshotUrl` 추가 (2026-08-05, S15P11A704-705)

- **응답에 `snapshotUrl`이 생겼다.** LIVE-06이 올린 낙찰 순간 캡처를 결과 화면(S-11)이 그리는데,
  그전까지 결과 응답에 URL이 없어 화면이 「스냅샷 준비 중」 플레이스홀더에 굳어 있었다.
  값은 `live_snapshot`에서 읽는다(`auction_id` UNIQUE라 경매당 1건, V9).
- **`live_snapshot`은 media 도메인 소유지만 auction 쪽 헤더 조회가 직접 조인한다.**
  MEM-02 낙찰 이력(`JpaWonAuctionHistoryQueryAdapter`)이 이미 같은 방식이라 관례를 따랐다.
  포트를 하나 더 파면 조회가 한 번 더 나가고, 그 조회가 실패하면 결과 화면까지 같이 죽는다 —
  LEFT JOIN이면 스냅샷이 없어도 결과는 그대로 나온다.
- **유찰에는 담지 않는다.** 「낙찰 순간」 캡처라 낙찰이 없으면 가리킬 순간이 없다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [경매 결과 조회 원본](https://app.notion.com/800776ebfd6882868c4e81299cb97d39)

