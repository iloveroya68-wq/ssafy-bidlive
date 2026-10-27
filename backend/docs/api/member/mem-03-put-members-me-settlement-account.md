# 정산 계좌 등록·변경

> **구현 상태:** ✅ **구현됨** — `SettlementAccountUpdateController` (S15P11A704-490) (`2026-07-30` 확인)

## 개요

로그인 사용자가 출금·판매 대금 정산에 사용할 계좌를 등록하거나 변경한다.
같은 엔드포인트의 `PUT` 요청으로 등록과 변경을 멱등하게 처리한다.

## Request

```http
PUT /api/v1/members/me/settlement-account
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "bankCode": "004",
  "accountNumber": "123456789012",
  "accountHolderName": "신희원"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `bankCode` | String | 필수 | 서버가 지원하는 은행 코드 |
| `accountNumber` | String | 필수 | 공백과 하이픈을 제거했을 때 10~14자리인 계좌번호 |
| `accountHolderName` | String | 필수 | 공백이 아닌 50자 이하의 예금주명 |

## Response

### 200 OK

```json
{
  "bankCode": "004",
  "bankName": "KB국민은행",
  "maskedAccountNumber": "123-****-**012",
  "accountHolderName": "신희원",
  "verified": false,
  "updatedAt": "2026-07-27T18:00:00+09:00"
}
```

## 검증 규칙

- Access Token의 `memberId`를 기준으로 본인 계좌만 등록·변경한다.
- 지원하는 은행 코드와 계좌번호 형식을 검증한다.
- 계좌번호의 공백과 하이픈은 제거한 뒤 검증·암호화한다.
- 동일한 은행·계좌번호·예금주명으로 다시 요청하면 기존 행과 `updatedAt`을 유지한다.
- 변경 요청은 같은 회원의 기존 행을 갱신하며 중복 계좌 행을 만들지 않는다.
- 실명·계좌 검증 기능을 도입하기 전에는 `verified=false`를 반환한다.
- 출금 처리 중 계좌 변경 제한은 정산 정책 확정 후 별도로 적용한다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 400 | `INVALID_BANK_CODE` | 지원하지 않는 은행 코드 |
| 400 | `INVALID_ACCOUNT_NUMBER` | 계좌번호 또는 예금주명 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `MEMBER_NOT_FOUND` | 회원이 존재하지 않음 |
| 409 | `SETTLEMENT_ACCOUNT_CHANGE_BLOCKED` | 진행 중 정산 등으로 계좌를 변경할 수 없음 |
| 503 | `SETTLEMENT_ACCOUNT_UPDATE_FAILED` | 정산 계좌 저장 또는 암호화 실패 |

## 암호화·키 관리

- 계좌번호는 Java 표준 `AES/GCM/NoPadding`과 256비트 키로 암호화한다.
- 암호화할 때마다 12바이트 IV를 새로 생성한다.
- DB에는 암호문, IV, 키 버전과 마스킹된 계좌번호만 저장한다.
- 암호화 키는 `SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_BASE64` 환경변수로 주입한다.
- 키 버전은 `SETTLEMENT_ACCOUNT_ENCRYPTION_KEY_VERSION`으로 주입하며 기본값은 `v1`이다.
- 계좌번호 원문은 응답, 예외 메시지, 접근 로그와 SQL 로그에 기록하지 않는다.

## 처리 흐름

```text
인증 사용자 확인
→ 은행 코드·계좌번호·예금주명 검증
→ 회원과 기존 정산 계좌 잠금 조회
→ 동일 요청이면 기존 요약 반환
→ 계좌번호 AES-GCM 암호화
→ 신규 등록 또는 기존 정보 변경
→ 마스킹된 계좌 요약 반환
```
