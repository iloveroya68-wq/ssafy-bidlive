# 낙찰가 예측 베팅 접수

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [낙찰가 예측 베팅 접수](https://app.notion.com/a9e776ebfd68836aa5c781d86a47648f)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PRED-01` |
| 저장소 도메인 | `prediction` |
| Notion 도메인 | 예측 |
| Method | `POST` |
| Path / Destination | `/api/v1/auctions/{auctionId}/predictions` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | Medium |
| 연결 구분 | Front↔BE, BE↔Redis, BE 내부 |
| 의존 대상 | Front, Redis, PostgreSQL, 포인트, 경매 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

입찰하지 않은 사용자가 예측 낙찰가와 베팅 포인트 스테이크를 직접 입력한다.

## 비고

구간·상한·프리셋 없음, 입찰과 상호 배타

## Request
```json
{
  "predictedPrice": 74000,
  "stakeAmount": 300,
  "clientRequestId": "2b8bb687-6d5e-4e09-b651-b88727266a51"
}
```
## Response
```json
{
  "predictionId": 701,
  "auctionId": 100,
  "predictedPrice": 74000,
  "stakeAmount": 300,
  "status": "PLACED",
  "submittedAt": "2026-07-21T20:08:00+09:00"
}
```
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_PREDICTED_PRICE`</td>
<td>예상 낙찰가가 0 이하이거나 형식이 잘못됨</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_STAKE_AMOUNT`</td>
<td>베팅 포인트가 0 이하이거나 형식이 잘못됨</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_CLIENT_REQUEST_ID`</td>
<td>`clientRequestId` 형식이 잘못됨</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>403</td>
<td>`SELLER_CANNOT_PREDICT`</td>
<td>해당 경매의 판매자가 예측을 요청함</td>
</tr>
<tr>
<td>403</td>
<td>`BIDDER_CANNOT_PREDICT`</td>
<td>해당 경매에 입찰한 사용자가 예측을 요청함</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매가 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_NOT_LIVE`</td>
<td>경매가 `LIVE` 상태가 아님</td>
</tr>
<tr>
<td>409</td>
<td>`PREDICTION_CLOSED`</td>
<td>서버 기준 예측 접수 시간이 종료됨</td>
</tr>
<tr>
<td>409</td>
<td>`PREDICTION_ALREADY_EXISTS`</td>
<td>동일 사용자가 해당 경매에 이미 예측함</td>
</tr>
<tr>
<td>409</td>
<td>`INSUFFICIENT_BETTING_POINTS`</td>
<td>가용 베팅 포인트가 부족함</td>
</tr>
<tr>
<td>409</td>
<td>`IDEMPOTENCY_KEY_REUSED`</td>
<td>동일한 `clientRequestId`를 다른 요청 내용으로 재사용함</td>
</tr>
</table>
## 검증 규칙
- 경매가 `LIVE` 상태이고 마감 전이어야 한다.
- 판매자와 해당 방 입찰 참여자는 예측할 수 없다.
- 한 사용자는 한 방에서 한 번만 예측한다.
- 예측 금액과 스테이크는 모두 0보다 커야 한다.
- Redis에 베팅 가용 잔액이 없으면 예측 처리 전에 PostgreSQL의 BETTING 지갑 잔액을 최초 적재한다.
- 가용 베팅 포인트가 스테이크 이상이어야 한다.
- `clientRequestId`로 중복 요청을 방지한다.
- 진행 중에는 다른 사용자의 예측 정보나 분포를 반환하지 않는다.

## 연계 WebSocket 이벤트

신규 예측 접수 트랜잭션이 커밋되면 경매방 전체에
[`PREDICTION_ACCEPTED`](./events/prediction-accepted.md)를 발행한다. 이벤트에는 입찰·예측 전체
고유 참여자 수와 고정 안내 문구만 포함하며, 동일한 `clientRequestId`의
멱등 재시도에는 다시 발행하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [낙찰가 예측 베팅 접수 원본](https://app.notion.com/a9e776ebfd68836aa5c781d86a47648f)

