# 거래 포인트 충전

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [거래 포인트 충전](https://app.notion.com/4b6776ebfd68824893f38196a75e3258)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-01` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `POST` |
| Path / Destination | `/api/v1/members/me/wallets/trade/charge` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `TradePointChargeController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, PostgreSQL, Redis, 포인트 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

실결제 없이 거래 포인트를 원클릭 충전하고 원장에 기록한다.

## 비고

PNT-04 PG 샌드박스와 별개인 P0 충전

## 개요
P0 데모용 원클릭 충전 API다. 실제 결제 없이 거래 포인트를 지급하고 포인트 원장에 `CHARGE` 유형으로 기록한다.
## Request
```javascript
POST /api/v1/members/me/wallets/trade/charge
Authorization: Bearer {accessToken}
Content-Type: application/json
Idempotency-Key: {uuid}
```
```json
{
  "amount": 100000
}
```
### Request Body
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
<td>충전할 거래 포인트 금액</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "walletType": "TRADE",
  "chargedAmount": 100000,
  "balance": 200000,
  "heldAmount": 50000,
  "availableAmount": 150000,
  "ledgerId": 1002,
  "chargedAt": "2026-07-20T21:35:00+09:00"
}
```
## 검증 규칙
- 충전 금액은 양의 정수여야 한다.
- 1회 충전 한도는 `1,000,000` 포인트다.
- 일일 충전 한도는 `3,000,000` 포인트다.
- 일일 한도는 한국 시간(`Asia/Seoul`)의 00:00:00부터 다음 날 00:00:00 직전까지 생성된 `CHARGE` 원장의 합계로 계산한다.
- 같은 `Idempotency-Key` 요청은 한 번만 처리한다.
- `Idempotency-Key`는 UUID 형식이어야 하며 전체 충전 요청에서 유일하게 사용한다.
- 지갑 잔액 증가와 `CHARGE` 원장 생성은 하나의 DB 트랜잭션으로 처리한다.
- DB 트랜잭션이 커밋된 뒤 `ledgerId`를 멱등 키로 사용해 Redis TRADE 가용 잔액을 증가시킨다.
- Redis 지갑이 없으면 커밋 결과 스냅샷으로 복원하고, 일부 잔액 필드만 존재하거나 결과가 음수가 되는 갱신은 적용하지 않는다.
- P0 원클릭 충전은 실제 결제가 아니므로 `point_charge` 결제 행을 생성하지 않는다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_CHARGE_AMOUNT`</td>
<td>충전 금액 형식 또는 범위 오류</td>
</tr>
<tr>
<td>401</td>
<td>`UNAUTHORIZED`</td>
<td>인증 실패</td>
</tr>
<tr>
<td>404</td>
<td>`TRADE_WALLET_NOT_FOUND`</td>
<td>거래 포인트 지갑이 존재하지 않음</td>
</tr>
<tr>
<td>409</td>
<td>`DUPLICATE_REQUEST`</td>
<td>이미 처리된 요청</td>
</tr>
<tr>
<td>422</td>
<td>`CHARGE_LIMIT_EXCEEDED`</td>
<td>1회 또는 일일 충전 한도 초과</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ 충전 금액과 한도 검증
→ 중복 요청 확인
→ TRADE 지갑 잔액 증가
→ CHARGE 포인트 원장 생성
→ DB 트랜잭션 커밋
→ Redis 가용 잔액 원자 동기화
→ 변경된 지갑 상태 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [거래 포인트 충전 원본](https://app.notion.com/4b6776ebfd68824893f38196a75e3258)
