# WS 에러 수신

> Notion 최종 종합 명세에서 동기화한 로컬 구현 계약이다.  
> 원본: [WS 에러 수신](https://app.notion.com/498776ebfd6883dea43001e47aa207fd)

## 메타데이터

| 항목 | 값 |
| --- | --- |
| 기능 ID | `WS-04` |
| 저장소 도메인 | `common` |
| Notion 도메인 | 공통 |
| Method | `WS` |
| Path / Destination | `/user/queue/errors` |
| 상태 | 확정 |
| 인증 | 필요 — WS-01에서 인증된 STOMP 세션 |
| 공개 범위 | 요청이 발생한 사용자 세션 |
| 담당자 | 김경민 |
| 우선순위 | High |
| 연결 구분 | 연결을 종료하지 않는 개인 `MESSAGE` |
| 의존 대상 | WS-01의 인증 `Principal`과 STOMP `sessionId` |
| 마지막 수정 | 2026-07-28 |

## 목적

SEND·SUBSCRIBE 처리 실패를 요청이 발생한 STOMP 세션의 개인 오류 큐로 회신한다.
공용 토픽과 동일 회원의 다른 세션에는 오류를 게시하지 않으며, 오류 수신 후에도
클라이언트가 연결을 유지하고 재시도할 수 있어야 한다.

## Destination

클라이언트는 다음 사용자 destination을 구독한다.

```plain text
SUB /user/queue/errors
```

서버는 `SimpMessagingTemplate.convertAndSendToUser()`를 사용할 때 다음 destination으로
전송한다.

```plain text
/queue/errors
```

서버 전송 시 `/user/queue/errors`를 직접 사용하지 않는다. `/user` prefix는 Spring의
사용자 destination 처리 과정에서 적용된다.

## 결과 예시

```json
{
  "type": "ERROR",
  "data": {
    "code": "SUBSCRIPTION_DENIED",
    "message": "해당 채널을 구독할 권한이 없습니다.",
    "refDestination": "/topic/auctions/100/questions",
    "correlationId": "client-message-or-receipt-id"
  }
}
```

## 필드 계약

| 필드 | 필수 여부 | 규칙 |
| --- | --- | --- |
| `type` | 필수 | 항상 `ERROR` |
| `data.code` | 필수 | 기능별로 정의한 안정적인 오류 코드 |
| `data.message` | 필수 | 사용자에게 공개 가능한 고정 문구 |
| `data.refDestination` | 조건부 | 실패한 SEND·SUBSCRIBE destination. 요청과 연결되지 않은 서버 내부 오류만 `null` 허용 |
| `data.correlationId` | 조건부 | 기능별 요청 ID 또는 STOMP `receipt`. 식별자를 확보할 수 없는 서버 내부 오류만 `null` 허용 |

## 처리 규칙

### 수신자와 세션 결정

- 수신자 이름은 WS-01에서 서버가 검증한 `Principal.getName()`으로만 결정한다.
- 클라이언트가 body, query parameter 또는 native header로 보낸 `memberId`는 사용하지 않는다.
- 오류가 발생한 STOMP `sessionId`를 전송 헤더에 포함하여 원래 요청 세션으로 한정한다.
- 동일 회원이 여러 브라우저나 탭으로 연결해도 다른 세션에는 해당 오류를 전달하지 않는다.
- 다른 사용자의 `/user/queue/errors` 메시지는 구독하거나 수신할 수 없다.

### correlationId 결정

correlationId는 다음 우선순위로 결정한다.

1. 기능이 명시적으로 제공한 요청 식별자
2. STOMP `receipt` ID
3. 식별자를 확보할 수 없는 서버 내부 오류는 `null`

채팅 SEND 실패에서는 `clientMessageId`를 명시적인 요청 식별자로 사용한다. 일반
SEND·SUBSCRIBE 실패에서는 STOMP `receipt`를 사용할 수 있다. 오류를 발생시킨 기능의
서버 로그도 가능한 경우 같은 correlationId를 사용해야 한다.

### 안전한 오류 응답

- `code`와 `message`는 기능별 `WebSocketErrorCode` 구현에서 함께 제공한다.
- `message`에 `Throwable.getMessage()`를 직접 사용하지 않는다.
- 내부 예외명, 스택 트레이스, JWT, Refresh Token, memberId, 회원 상태 및 개인정보를 포함하지 않는다.
- 상세 원인은 클라이언트 payload가 아니라 오류를 발생시킨 기능의 서버 로그에서 관리한다.

### 연결 유지

- 이 채널의 오류는 일반 사용자 destination `MESSAGE`로 전달한다.
- 오류 전송 후 STOMP 연결을 닫지 않는다.
- 클라이언트는 동일 세션에서 요청을 수정하여 다시 SEND·SUBSCRIBE할 수 있다.
- CONNECT 인증 실패에서 사용하는 STOMP `ERROR` frame은 WS-01의
  `StompConnectErrorHandler`가 담당하며 이 개인 오류 채널로 변환하지 않는다.

## 대표 코드

| 코드 | 발생 기능 |
| --- | --- |
| `AUTH_RENEW_EXPIRED` | WS-01 토큰 재인증 |
| `SUBSCRIPTION_DENIED` | WS-02 구독 권한 검증 |
| `CHAT_MUTED` | CHAT-01 채팅 전송 |
| `SLOW_MODE_LIMIT` | CHAT-04 슬로우 모드 |

대표 코드는 WS-04가 직접 판정하지 않는다. 각 기능이 자신의 오류 조건과 고정 메시지를
정의하고 공통 `UserWebSocketErrorPublisher`를 호출한다.

## 구현 경계

WS-04는 다음 공통 기반만 담당한다.

- `{type, data}` 개인 오류 메시지 계약
- SEND·SUBSCRIBE 요청의 Principal·sessionId·destination·correlationId 추출
- `/queue/errors` 사용자 destination 발행
- 사용자 및 세션 단위 격리

다음 정책은 WS-04에서 구현하지 않는다.

- WS-02의 경매방·역할별 SUBSCRIBE 권한 판정
- WS-01의 `/app/auth/renew` 토큰 재인증
- 채팅 뮤트 및 슬로우 모드 판정
- 경매 판매자·참여자 여부 조회

## 필수 테스트

- 요청이 발생한 사용자 세션만 오류 수신
- 동일 회원의 다른 세션 미수신
- 다른 회원 세션 미수신
- code·message·refDestination·correlationId 정확성
- 내부 예외·토큰·회원 정보 미노출
- 오류 수신 후 동일 연결에서 재요청 가능
- CONNECT 인증 오류 처리의 기존 동작 유지

## 출처

- [최종(종합) 명세서](https://app.notion.com/p/679776ebfd68834c9cc801883e05e8c7)
- [WS 에러 수신 원본](https://app.notion.com/498776ebfd6883dea43001e47aa207fd)
