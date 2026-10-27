# 금액 직접 입력 입찰

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [금액 직접 입력 입찰](https://app.notion.com/c39776ebfd688361b19f81ccb9966939)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `BID-06` |
| 저장소 도메인 | `bid` |
| Notion 도메인 | 입찰 |
| Method | `POST` |
| Path / Destination | `/api/v1/auctions/{auctionId}/bids` |
| 상태 | 확정 |
| 구현 | ✅ **구현됨** — `BidController`, `BidCommandService`, `BidAcceptedWebSocketPublisher` (`2026-07-31` 확인) |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 김택림 |
| 우선순위 | High |
| 연결 구분 | Front↔BE |
| 의존 대상 | Front, 경매, PostgreSQL |
| 마지막 수정 | 2026-07-31 |

## 개요
사용자가 비공개로 자신의 최대 지불 가능 금액을 직접 입력해 입찰한다. 본인의 기존 최고 입찰가보다 높은 금액만 허용하며, 모든 경매는 동일 회원의 전역 거래 포인트 가용액을 공유한다.
## Request
```javascript
POST /api/v1/auctions/{auctionId}/bids
Authorization: Bearer {accessToken}
Content-Type: application/json
Idempotency-Key: {uuid}
```
```json
{
  "amount": 82000
}
```
`Idempotency-Key`: 같은 요청이 여러 번 전송돼도 서버가 실제 처리는 한 번만 하도록 구분하는 요청 고유값이다.
동일 키와 동일한 회원·경매·금액으로 재요청하면 기존 입찰 결과를 `201 Created`로 반환하고 입찰 이력과
포인트 원장을 다시 생성하지 않는다. 동일 키를 다른 요청에 재사용하면 `IDEMPOTENCY_KEY_REUSED`로
거절한다.
## Request Body
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`amount`</td>
<td>Long</td>
<td>필수</td>
<td>사용자가 새로 제출하는 최종 입찰 금액</td>
</tr>
</table>
## Response
### 201 Created
```json
{
  "auctionId": 100,
  "myBidAmount": 82000,
  "previousBidAmount": 70000,
  "additionalHoldAmount": 12000,
  "totalHeldAmount": 82000,
  "availableBalance": 118000,
  "submittedAt": "2026-07-20T21:12:44+09:00"
}
```

- `totalHeldAmount`는 해당 회원이 진행 중인 모든 경매에서 홀드한 거래 포인트 합계다.
- `availableBalance`는 다른 경매의 입찰 홀드까지 반영한 회원 전역 잔여 가용액이다.

## 검증 규칙
- 경매 상태가 `LIVE`여야 한다.
- 서버 기준 마감 전이어야 한다.
- 판매자는 자기 경매에 입찰할 수 없다.
- 입찰 금액은 하한가 이상이어야 한다.
- 기존 본인 입찰가보다 높아야 한다.
- 회원 전역 가용 거래 포인트가 추가 홀드 금액 이상이어야 한다.
- 다른 경매에서 이미 홀드한 금액은 현재 경매에서 다시 사용할 수 없다.
- 같은 방에서 예측 베팅에 먼저 참여한 사용자는 입찰할 수 없다.
- 상향 입찰 시 접수 시각을 현재 서버 시각으로 갱신한다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_BID_AMOUNT`</td>
<td>금액 형식 또는 범위 오류</td>
</tr>
<tr>
<td>403</td>
<td>`SELLER_CANNOT_BID`</td>
<td>판매자 자기 방 입찰</td>
</tr>
<tr>
<td>404</td>
<td>`AUCTION_NOT_FOUND`</td>
<td>경매가 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_NOT_LIVE`</td>
<td>입찰 가능한 LIVE 상태가 아님</td>
</tr>
<tr>
<td>409</td>
<td>`AUCTION_ALREADY_ENDED`</td>
<td>서버 기준 마감 이후 요청</td>
</tr>
<tr>
<td>409</td>
<td>`BID_BELOW_RESERVE_PRICE`</td>
<td>하한가 미만</td>
</tr>
<tr>
<td>409</td>
<td>`BID_NOT_HIGHER_THAN_PREVIOUS`</td>
<td>기존 본인 입찰가 이하</td>
</tr>
<tr>
<td>409</td>
<td>`PREDICTION_ALREADY_JOINED`</td>
<td>해당 방에서 예측 베팅에 이미 참여함</td>
</tr>
<tr>
<td>409</td>
<td>`IDEMPOTENCY_KEY_REUSED`</td>
<td>동일 Idempotency-Key를 다른 회원·경매·금액 요청에 재사용함</td>
</tr>
<tr>
<td>422</td>
<td>`INSUFFICIENT_AVAILABLE_BALANCE`</td>
<td>가용 거래 포인트 부족</td>
</tr>
</table>
## 처리 흐름
```plain text
Controller 인증 사용자 확인
→ Redis 회원 전역 TRADE 지갑이 없으면 PostgreSQL 스냅샷 최초 적재
→ BID-01 Redis Lua 원자 처리(경매별 입찰 갱신 + 회원 전역 지갑 차액 홀드)
→ 입찰 이력 및 포인트 원장 저장
→ 요청자에게 개인 결과 REST 응답
→ BID_ACCEPTED WebSocket 이벤트 브로드캐스트
```

## 현재 구현 범위

- Redis Lua가 경매 상태, 상향 입찰, 참여 유형과 회원 전역 가용액을 원자적으로 검증하고 갱신한다.
- 신규 입찰 전에 PostgreSQL TRADE 지갑 행을 잠그고, Redis 가용액과 홀드액이 모두 없으면
  잠근 지갑 스냅샷을 원자적으로 최초 적재한다. 기존 Redis 잔액은 덮어쓰지 않고 부분 초기화
  상태는 오류로 중단한다.
- Redis 승인 후 같은 PostgreSQL 트랜잭션에서 `BIDDER` 참여자와 모든 상향 입찰 이력을 저장하고,
  `TradePointOperationService`로 추가 홀드 금액만 TRADE 지갑과 불변 원장에 반영한다.
- PostgreSQL advisory lock으로 Idempotency-Key와 경매·회원별 입찰 순서를 직렬화한다.
- 신규 입찰 트랜잭션이 커밋되면 `BID_ACCEPTED`를 경매방 토픽에 발행한다. 동일 Idempotency-Key의
  멱등 재요청과 실패하거나 롤백된 입찰에는 발행하지 않는다.
- 충전·출금·환불·정산에 따른 DB와 Redis 동기화, Redis Stream 기반 재처리와 비동기 영속화는
  후속 범위다.

## 공개 원칙
REST 응답에는 본인의 입찰 금액과 홀드 정보만 포함한다. 다른 입찰자의 금액, 최고가, 2등가 및 순위는 반환하지 않는다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [금액 직접 입력 입찰 원본](https://app.notion.com/c39776ebfd688361b19f81ccb9966939)
