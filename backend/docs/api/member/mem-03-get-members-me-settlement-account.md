# 정산 계좌 조회

> **구현 상태:** ✅ **구현됨** — `SettlementAccountQueryController` (S15P11A704-490) (`2026-07-30` 확인)

## 개요

로그인 사용자가 출금·판매 대금 정산에 사용할 계좌의 마스킹된 요약 정보를 조회한다.

계좌 원문은 응답하지 않으며 등록된 계좌가 없으면 `account=null`을 반환한다.

## Request

```http
GET /api/v1/members/me/settlement-account
Authorization: Bearer {accessToken}
```

## Response

### 200 OK — 등록된 계좌 있음

```json
{
  "account": {
    "bankCode": "004",
    "bankName": "KB국민은행",
    "maskedAccountNumber": "123-****-**789",
    "accountHolderName": "신희원",
    "verified": false,
    "updatedAt": "2026-07-27T18:00:00+09:00"
  }
}
```

### 200 OK — 등록된 계좌 없음

```json
{
  "account": null
}
```

## 지원 은행

| 은행 코드 | 은행명 |
| --- | --- |
| `004` | KB국민은행 |
| `088` | 신한은행 |
| `020` | 우리은행 |
| `081` | 하나은행 |
| `090` | 카카오뱅크 |
| `092` | 토스뱅크 |

## 검증 규칙

- Access Token의 `memberId`를 기준으로 본인 계좌만 조회한다.
- 계좌번호 원문은 응답하지 않는다.
- 은행명은 서버가 `bankCode`를 기준으로 반환한다.

## 오류 응답

| HTTP | 코드 | 설명 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `MEMBER_NOT_FOUND` | 회원이 존재하지 않음 |
| 503 | `SETTLEMENT_ACCOUNT_UNAVAILABLE` | 정산 계좌 조회 실패 |

## 보안 원칙

- 계좌번호 원문을 API 응답과 애플리케이션 로그에 기록하지 않는다.
- 마스킹된 값만 프론트에 전달한다.
