# 소켓 토큰 재인증

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [소켓 토큰 재인증](https://app.notion.com/e0d776ebfd6882fda07781cd534a683f)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `WS-01` |
| 저장소 도메인 | `common` |
| Notion 도메인 | 공통 |
| Method | `WS` |
| Path / Destination | `/app/auth/renew` |
| 상태 | 확정 |
| 인증 | 필요 — `/ws` CONNECT 인증을 완료한 STOMP 세션 |
| 공개 범위 | 현재 STOMP 세션 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 기존 연결·Principal·구독 유지 |
| 의존 대상 | WS-01 CONNECT 인증, WS-04 개인 오류 채널 |
| 마지막 수정 | 2026-07-29 |

## 비고

토큰 만료 시 수신은 유지하고 발신만 차단한 뒤 재인증

> **WebSocket 재인증 명세** — 연결을 끊지 않고 동일 회원의 새 Access Token으로 세션 만료 시각을 갱신한다.

## 호출 위치

```plain text
SEND /app/auth/renew
receipt:{clientReceiptId}
```

```json
{ "accessToken": "eyJhbGci..." }
```

`receipt`는 성공 처리를 식별해야 하는 클라이언트가 함께 보내며, 서버는 성공 시 같은
값을 `receipt-id`로 갖는 STOMP `RECEIPT` frame을 원래 세션으로 전송한다.

## 요청 필드

| 필드 | 타입 | 필수 여부 | 규칙 |
| --- | --- | --- | --- |
| `accessToken` | String | 필수 | BidLive가 발급한 새 Access Token. body·query parameter의 `memberId`는 인증 근거로 사용하지 않음 |

## 처리 규칙

- 서버가 CONNECT에서 구성한 `SecurityPrincipal`만 현재 회원 식별에 사용한다.
- 새 토큰의 서명, issuer, token type, 필수 claim과 만료 시각을 검증한다.
- 새 토큰의 `memberId`는 기존 세션 Principal의 `memberId`와 일치해야 한다.
- 현재 회원 상태는 `MemberPersistence.findById()`로 다시 확인한다.
- `ACTIVE`와 `SUSPENDED` 회원은 재인증을 허용한다.
- `WITHDRAWN` 회원과 존재하지 않는 회원은 `AUTH_RENEW_INVALID`로 거부한다.
- 토큰의 `role`은 claim 형식 검증에만 사용하며 기존 Principal의 role을 교체하지 않는다.
- 성공 시 세션 속성의 `tokenExpiresAt`만 갱신한다. Principal, STOMP session ID,
  `connectedAt`과 기존 구독은 변경하지 않는다.
- 여러 갱신 요청이 겹치면 현재 값과 후보 값 중 가장 늦은 유효 만료 시각만 유지한다.
- 성공 시 요청의 `receipt`와 동일한 `receipt-id`를 가진 STOMP `RECEIPT` frame을
  원래 session ID로 전송한다.
- 실패 시 `/user/queue/errors`로 개인 `MESSAGE`를 전송하며 RECEIPT를 발행하거나
  연결을 종료하지 않는다.
- 실패한 요청은 기존 Principal과 `tokenExpiresAt`을 변경하지 않는다.

## 성공 응답

성공 응답은 JSON 메시지가 아니라 STOMP frame이다.

```plain text
RECEIPT
receipt-id:{clientReceiptId}
```

## 실패 코드

| 코드 | 발생 조건 | 공개 메시지 |
| --- | --- | --- |
| `AUTH_RENEW_INVALID` | 서명·issuer·token type·claim이 잘못된 토큰, 존재하지 않거나 탈퇴한 회원, 유효하지 않은 세션 인증 정보 | `재인증에 사용할 Access Token이 유효하지 않습니다.` |
| `AUTH_PRINCIPAL_MISMATCH` | 다른 회원의 토큰으로 갱신 시도 | `현재 연결의 사용자와 Access Token의 사용자가 일치하지 않습니다.` |
| `AUTH_RENEW_EXPIRED` | 새 Access Token이 이미 만료됨 | `재인증에 사용할 Access Token이 만료되었습니다.` |

실패 응답은 WS-04의 `{type, data}` 형식을 사용한다. `refDestination`은
`/app/auth/renew`, `correlationId`는 요청의 `receipt`다.

```json
{
  "type": "ERROR",
  "data": {
    "code": "AUTH_RENEW_EXPIRED",
    "message": "재인증에 사용할 Access Token이 만료되었습니다.",
    "refDestination": "/app/auth/renew",
    "correlationId": "renew-receipt-1"
  }
}
```

오류 payload와 로그에는 JWT 원문, memberId, 회원 상태 및 내부 예외를 포함하지 않는다.

## 후속 처리

재인증 성공 후 차단됐던 SEND를 재시도한다. SUBSCRIBE를 다시 만들 필요는 없다.

## 필수 테스트

- ACTIVE·SUSPENDED 회원 정상 갱신
- 만료·위조·Refresh Token 차단과 오류 코드 매핑
- 다른 회원 토큰 차단
- 동시 갱신의 만료 시각 역전 방지
- 성공 후 session ID·Principal·role·구독 유지
- 실패 시 기존 Principal과 `tokenExpiresAt` 불변
- 요청과 동일한 `receipt-id`의 RECEIPT 수신
- 개인 오류 수신 후 동일 연결에서 정상 재인증 가능
- 오류 응답의 JWT·memberId·회원 상태·내부 예외 비노출

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [소켓 토큰 재인증 원본](https://app.notion.com/e0d776ebfd6882fda07781cd534a683f)

