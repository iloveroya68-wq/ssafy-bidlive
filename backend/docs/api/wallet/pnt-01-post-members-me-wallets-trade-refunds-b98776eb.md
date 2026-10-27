# 거래 포인트 환불

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [거래 포인트 환불](https://app.notion.com/b98776ebfd688361880281fc259c3382)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-01` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `POST` |
| Path / Destination | `/api/v1/members/me/wallets/trade/refunds` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `TradePointRefundController` (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | Front↔BE, BE 내부 |
| 의존 대상 | Front, PostgreSQL, Redis, 포인트 |
| 마지막 수정 | 2026-07-27 02:08:12Z |

## 요약

홀드 중 금액을 제외한 가용 거래 포인트를 환불 처리한다.

## 비고

실제 현금 환불이 아닌 프로젝트 내 포인트 차감

## 개요
거래 포인트 중 입찰로 홀드되지 않은 가용 금액을 환불 처리한다. 프로젝트 단계에서는 실제 현금 환불이 아니라 서비스 내 거래 포인트 차감으로 처리한다.
## Request
```javascript
POST /api/v1/members/me/wallets/trade/refunds
Authorization: Bearer {accessToken}
Content-Type: application/json
Idempotency-Key: {uuid}
```
```json
{
  "amount": 30000
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
<td>환불할 거래 포인트 금액</td>
</tr>
</table>
## Response
### 200 OK
```json
{
  "walletType": "TRADE",
  "refundedAmount": 30000,
  "balance": 170000,
  "heldAmount": 50000,
  "availableAmount": 120000,
  "ledgerId": 1003,
  "refundedAt": "2026-07-20T21:40:00+09:00"
}
```
## 검증 규칙
- 환불 금액은 양의 정수여야 한다.
- `amount <= availableAmount`여야 한다.
- 입찰로 홀드된 금액은 환불할 수 없다.
- 같은 `Idempotency-Key` 요청은 한 번만 처리한다.
- 지갑 차감과 `REFUND` 원장 생성은 하나의 DB 트랜잭션으로 처리한다.
- DB 트랜잭션이 커밋된 뒤 `ledgerId`를 멱등 키로 사용해 Redis TRADE 가용 잔액을 차감한다.
- Redis 지갑이 없으면 커밋 결과 스냅샷으로 복원하고, 일부 잔액 필드만 존재하거나 결과가 음수가 되는 갱신은 적용하지 않는다.
## 오류 응답
<table>
<tr>
<td>HTTP</td>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>400</td>
<td>`INVALID_REFUND_AMOUNT`</td>
<td>환불 금액 형식 또는 범위 오류</td>
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
<td>`INSUFFICIENT_AVAILABLE_AMOUNT`</td>
<td>환불 가능한 가용 포인트 부족</td>
</tr>
</table>
## 처리 흐름
```plain text
인증 사용자 확인
→ 환불 금액 검증
→ 중복 요청 확인
→ 가용 포인트 확인
→ TRADE 지갑 잔액 차감
→ REFUND 포인트 원장 생성
→ DB 트랜잭션 커밋
→ Redis 가용 잔액 원자 동기화
→ 변경된 지갑 상태 반환
```

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [거래 포인트 환불 원본](https://app.notion.com/b98776ebfd688361880281fc259c3382)
