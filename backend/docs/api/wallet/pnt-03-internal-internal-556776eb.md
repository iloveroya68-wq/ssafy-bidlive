# 포인트 홀드·해제·정산 처리

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [포인트 홀드·해제·정산 처리](https://app.notion.com/556776ebfd68837fb96c81286be9a8e0)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-03` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `TradePointOperationService` (S15P11A704-498) — 홀드·해제·정산 (`2026-07-30` 확인) |
| 우선순위 | High |
| 연결 구분 | BE↔BE, BE 내부 |
| 의존 대상 | PostgreSQL, Redis, 포인트, 경매 |
| 마지막 수정 | 2026-07-27 02:08:14Z |

## 요약

입찰과 거래 정산에 필요한 포인트 홀드·해제·차감을 원장과 함께 처리한다.

## 개요
입찰·경매 종료·거래 정산 모듈에서 거래 포인트의 홀드, 해제와 최종 차감을 처리한다. 프론트가 직접 호출하지 않는 백엔드 내부 서비스다.
## 주요 명령
```java
hold(referenceType, referenceId, memberId, amount, idempotencyKey)
release(referenceType, referenceId, memberId, amount, idempotencyKey)
settle(referenceType, referenceId, memberId, amount, idempotencyKey)
```
## 입력
<table>
<tr>
<td>필드</td>
<td>타입</td>
<td>필수</td>
<td>설명</td>
</tr>
<tr>
<td>`referenceType`</td>
<td>Enum</td>
<td>필수</td>
<td>금액 변경의 원인 종류. 예: `BID`, `DEAL`</td>
</tr>
<tr>
<td>`referenceId`</td>
<td>Long</td>
<td>필수</td>
<td>입찰 또는 거래 등 원인 데이터 ID</td>
</tr>
<tr>
<td>`memberId`</td>
<td>Long</td>
<td>필수</td>
<td>처리 대상 회원 ID</td>
</tr>
<tr>
<td>`amount`</td>
<td>Long</td>
<td>필수</td>
<td>홀드·해제·정산할 금액</td>
</tr>
<tr>
<td>`idempotencyKey`</td>
<td>String</td>
<td>필수</td>
<td>중복 실행을 방지하는 처리 고유값</td>
</tr>
</table>
## 출력
```json
{
  "walletType": "TRADE",
  "operationType": "HOLD",
  "amount": 82000,
  "balance": 200000,
  "heldAmount": 82000,
  "availableAmount": 118000,
  "ledgerId": 1001
}
```
## 검증 규칙
- 모든 금액은 양의 정수여야 한다.
- 홀드 요청 금액은 현재 가용 금액을 초과할 수 없다.
- 해제·정산 금액은 해당 참조에 남아 있는 홀드 금액을 초과할 수 없다.
- 지갑 잔액과 홀드액은 음수가 될 수 없다.
- 동일 `idempotencyKey` 요청은 한 번만 처리한다.
- 지갑 갱신과 포인트 원장 생성은 하나의 DB 트랜잭션으로 처리한다.
- 입찰 `HOLD`는 원자적 입찰 처리에서 Redis 잔액을 먼저 변경하므로 DB 커밋 후 같은 금액을 다시 반영하지 않는다.
- `RELEASE`, `SETTLEMENT`는 DB 커밋 뒤 `ledgerId` 기준으로 Redis 가용·홀드 잔액을 원자적으로 동기화한다.
- Redis 지갑이 없으면 커밋 결과 스냅샷으로 복원하고, 중복되거나 복원 기준 원장보다 오래된 이벤트는 적용하지 않는다.
## 오류 응답
<table>
<tr>
<td>코드</td>
<td>설명</td>
</tr>
<tr>
<td>`INSUFFICIENT_AVAILABLE_BALANCE`</td>
<td>홀드 가능한 가용 포인트 부족</td>
</tr>
<tr>
<td>`HOLD_NOT_FOUND`</td>
<td>해제·정산할 홀드 내역이 없음</td>
</tr>
<tr>
<td>`INVALID_RELEASE_AMOUNT`</td>
<td>해제 금액이 남은 홀드액을 초과함</td>
</tr>
<tr>
<td>`INVALID_SETTLEMENT_AMOUNT`</td>
<td>정산 금액이 허용 범위를 벗어남</td>
</tr>
<tr>
<td>`DUPLICATE_LEDGER_REFERENCE`</td>
<td>동일 처리 키 또는 참조로 이미 원장이 생성됨</td>
</tr>
<tr>
<td>`WALLET_NOT_FOUND`</td>
<td>거래 포인트 지갑이 존재하지 않음</td>
</tr>
</table>
## 처리 흐름
```plain text
내부 명령 수신
→ idempotencyKey 중복 확인
→ 지갑 행 잠금 및 현재 잔액 조회
→ 작업별 금액 검증
→ balance/heldAmount 원자적 변경
→ HOLD/RELEASE/SETTLEMENT 원장 생성
→ 트랜잭션 커밋
→ RELEASE/SETTLEMENT Redis 잔액 원자 동기화
→ 변경된 지갑 상태 반환
```
## 원장 원칙
- 기존 원장 행을 수정하거나 삭제하지 않는다.
- 실패한 트랜잭션에서는 지갑 변경과 원장 생성이 모두 롤백되어야 한다.
- 정정이 필요하면 반대 방향의 새 원장 행을 추가한다.

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [포인트 홀드·해제·정산 처리 원본](https://app.notion.com/556776ebfd68837fb96c81286be9a8e0)
