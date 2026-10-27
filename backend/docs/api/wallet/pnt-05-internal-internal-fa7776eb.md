# 베팅 포인트 지급 처리

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [베팅 포인트 지급 처리](https://app.notion.com/fa7776ebfd68828eb24481cfcebeeeec)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `PNT-05` |
| 저장소 도메인 | `wallet` |
| Notion 도메인 | 포인트 |
| Method | `INTERNAL` |
| Path / Destination | 내부 처리 |
| 상태 | 확정 |
| 인증 | 불필요 |
| 공개 범위 | Internal |
| 담당자 | 신희원 |
| 구현 | ✅ **구현됨** — `BettingPointGrantService` (S15P11A704-505) — 베팅 포인트 지급 (`2026-07-30` 확인) |
| 우선순위 | Medium |
| 연결 구분 | BE↔BE, BE 내부 |
| 의존 대상 | PostgreSQL, Redis, 포인트 |
| 마지막 수정 | 2026-07-27 02:08:10Z |

## 요약

가입·출석·라이브 시청 등 일반 보상에 따른 무료 베팅 포인트를 멱등하게 지급하고 GRANT 원장에 기록한다. 예측 적중 판정과 배당 정산은 PRED-03의 책임이다.

## 비고

결제·충전·거래 포인트 전환·현금 전환 금지

### 내부 인터페이스
```plain text
BettingPointGrantResult grant(BettingPointGrantCommand command)
```
```plain text
{
  "memberId": 25,
  "grantType": "LIVE_WATCH",
  "sourceId": "AUCTION:100",
  "amount": 100,
  "policyVersion": "2026-07-01",
  "occurredAt": "2026-07-22T10:30:00+09:00"
}
```
### 지급 유형
<table>
<tr>
<td>`grantType`</td>
<td>호출 주체</td>
<td>중복 방지 기준</td>
</tr>
<tr>
<td>`SIGNUP`</td>
<td>MEM-03</td>
<td>회원당 1회</td>
</tr>
<tr>
<td>`DAILY_ATTENDANCE`</td>
<td>출석 모듈</td>
<td>회원·날짜당 1회</td>
</tr>
<tr>
<td>`LIVE_WATCH`</td>
<td>LIVE-03 시청 판정</td>
<td>회원·경매당 1회</td>
</tr>
</table>
### 결과
```plain text
{
  "grantId": "019c...",
  "memberId": 25,
  "grantType": "LIVE_WATCH",
  "amount": 100,
  "balanceAfter": 2100,
  "ledgerId": "019d...",
  "duplicated": false,
  "grantedAt": "2026-07-22T10:30:00+09:00"
}
```
### 처리 규칙
- `(memberId, grantType, sourceId)`에 UNIQUE 제약을 둔다.
- 중복 요청은 다시 지급하지 않고 기존 결과를 성공으로 반환한다.
- 지갑 잔액 증가와 포인트 원장 생성은 하나의 트랜잭션으로 처리한다.
- 신규 GRANT 원장과 BETTING 지갑 변경이 커밋되면 지급액만큼 Redis의 BETTING
  가용 잔액을 증가시킨다.
- 가입·출석·시청 보상액은 서버 정책값으로 결정하며 클라이언트 입력을 신뢰하지 않는다.
- `PNT-05`는 예측의 적중 여부나 배당액을 계산하지 않는다.
- 가입·출석·시청 같은 일반 보상은 원장 유형 `GRANT`를 사용한다.
- 예측 배당·환불은 `PRED-03`이 결정하고 각각 `PAYOUT`·`REFUND` 원장으로 반영한다.
- `PNT-05` 일반 지급 경로에서는 `CHARGE`, `PAYOUT`, `REFUND` 및 거래 포인트 전환 유형을 허용하지 않는다.
### 실패 코드
<table>
<tr>
<td>코드</td>
<td>조건</td>
</tr>
<tr>
<td>`MEMBER_NOT_FOUND`</td>
<td>회원 없음</td>
</tr>
<tr>
<td>`BETTING_WALLET_NOT_FOUND`</td>
<td>베팅 지갑 없음</td>
</tr>
<tr>
<td>`INVALID_GRANT_TYPE`</td>
<td>허용되지 않은 지급 유형</td>
</tr>
<tr>
<td>`INVALID_GRANT_AMOUNT`</td>
<td>0 이하 또는 정책과 다른 금액</td>
</tr>
<tr>
<td>`MEMBER_INACTIVE`</td>
<td>정지·탈퇴 회원</td>
</tr>
<tr>
<td>`POINT_LEDGER_WRITE_FAILED`</td>
<td>원장·지갑 트랜잭션 실패</td>
</tr>
</table>

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [베팅 포인트 지급 처리 원본](https://app.notion.com/fa7776ebfd68828eb24481cfcebeeeec)

