# 포인트 PG 테스트 결제 충전

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [포인트 PG 테스트 결제 충전](https://app.notion.com/f44776ebfd688217b1e681bd42e5a2ec)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-04` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `POST` |
| Path / Destination | `/api/v1/payments/charge` |
| 상태 | 확정 |
| 인증 | 필요 |
| 공개 범위 | User |
| 담당자 | 이연주 |
| 우선순위 | Low |
| 연결 구분 | Front↔BE, BE↔BE |
| 의존 대상 | Front, 포인트, PostgreSQL |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

PG 테스트 결제 승인 후 사용자의 거래 포인트를 충전한다.

## 비고

포트원/토스 PG 샌드박스 연동 (P2)

## 개요
포트원 또는 토스페이먼츠 PG 샌드박스 결제를 승인하고 승인 금액만큼 사용자의 거래 포인트를 충전한다.
## Request
```javascript
POST /api/v1/payments/charge
Authorization: Bearer {accessToken}
Content-Type: application/json
Idempotency-Key: {uuid}
```
결제사 선택 후 `paymentKey`, 주문 식별자, 결제 금액을 포함하는 요청 본문을 확정해야 한다.
## Response
### 200 OK
```json
{
  "type": "POINT_CHARGE_SUCCESS",
  "data": {
    "paymentId": "pay_20260720_001",
    "chargedAmount": 50000,
    "currentTotalPoint": 150000,
    "createdAt": "2026-07-20T21:02:30+09:00"
  }
}
```
## 검증 규칙
- 인증된 사용자만 충전할 수 있다.
- 클라이언트가 전달한 금액과 PG 승인 금액이 일치해야 한다.
- 동일 결제 건과 동일 멱등성 키는 한 번만 포인트 원장에 반영한다.
- 충전 대상은 베팅 포인트가 아닌 거래 포인트로 명확히 구분한다.
## 처리 흐름
```plain text
결제 요청 검증
→ PG 승인 결과 확인
→ 결제 이력과 거래 포인트 원장 저장
→ 거래 포인트 잔액 증가
→ type·data 공통 응답 반환
```
## 확인 필요
- 결제 준비와 승인을 하나의 API로 둘지 분리할지 확정
- 최종 PG 제공사와 요청 필드 확정

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [포인트 PG 테스트 결제 충전 원본](https://app.notion.com/f44776ebfd688217b1e681bd42e5a2ec)

