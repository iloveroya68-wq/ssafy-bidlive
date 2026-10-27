# WebSocket 인증·구독 권한 보안 회귀 검증

## 범위

- Jira: `S15P11A704-239`, `S15P11A704-601`
- 기능 ID: `WS-01`, `WS-02`, `WS-04`
- 대상: STOMP CONNECT 인증, SUBSCRIBE 권한, 개인 오류 응답

## 보안 기준

- 인증은 CONNECT의 단일 `Authorization: Bearer {accessToken}` 헤더만 사용한다.
- body, query parameter, `memberId`, `user`, `simpUser` native header를 인증 근거로 사용하지 않는다.
- Principal은 서버가 검증한 Access Token과 최신 회원 정보를 기준으로 구성한다.
- 일반 경매 토픽은 인증 사용자에게 허용하고, 질문 토픽은 해당 경매 판매자에게만 허용한다.
- 개인 큐는 `/user/queue/**` 형식만 허용하며 회원 ID가 포함된 임의 사용자 경로는 거부한다.
- 등록되지 않은 Destination은 기본 거부한다.
- 구독 거부는 해당 SUBSCRIBE frame만 폐기하고 연결은 유지한다.
- 세션 토큰 만료 이후의 SEND·SUBSCRIBE는 유예 시간(`app.websocket.token-expiry-grace`)이 지나면
  거부하고, 재인증 destination만 예외로 통과시켜 같은 세션에서 회복할 수 있게 한다.
- 인증·구독 오류 응답에는 JWT, memberId, 회원 상태, 내부 예외명을 포함하지 않는다.

## 자동 검증 매트릭스

| 공격·경계 조건 | 기대 결과 | 자동 검증 |
| --- | --- | --- |
| 위조·만료·Refresh Token CONNECT | `AUTH_INVALID` 또는 `AUTH_EXPIRED` ERROR 후 연결 종료 | `StompConnectIntegrationTest` |
| Authorization 누락·중복 | `AUTH_INVALID` ERROR 후 연결 종료 | `StompConnectIntegrationTest` |
| 클라이언트가 신원 native header 주입 | header를 무시하고 검증된 token Principal 사용 | `StompConnectIntegrationTest` |
| 비판매자가 판매자 ID header를 주입해 질문 구독 | `SUBSCRIPTION_DENIED`, 구독 미등록 | `StompSubscriptionAuthorizationIntegrationTest` |
| 다른 회원 ID를 포함한 개인 큐 경로 구독 | `SUBSCRIPTION_DENIED`, 구독 미등록 | `StompSubscriptionAuthorizationIntegrationTest` |
| 미등록 Destination 구독 | `SUBSCRIPTION_DENIED`, 구독 미등록 | `StompSubscriptionAuthorizationIntegrationTest` |
| 구독 거부 후 정상 공개 토픽 구독 | 기존 연결에서 정상 구독 | `StompSubscriptionAuthorizationIntegrationTest` |
| 오류 payload의 민감정보 노출 | JWT·memberId·회원 상태·내부 구현명 미노출 | CONNECT·SUBSCRIBE 통합 테스트 |
| 토큰 만료 후 SUBSCRIBE·SEND | `AUTH_EXPIRED`, frame 폐기, 연결 유지 | `StompSessionExpiryIntegrationTest` |
| 유예 시간 안의 SEND·SUBSCRIBE | 정상 처리 | `StompSessionExpiryIntegrationTest` |
| 만료된 세션의 재인증 SEND | 통과 후 이어지는 정상 frame도 복구 | `StompSessionExpiryIntegrationTest` |

## 실행 명령

```powershell
.\gradlew.bat test `
  --tests "com.ssafy.bidlive.common.security.StompConnectIntegrationTest" `
  --tests "com.ssafy.bidlive.common.websocket.subscription.StompSubscriptionAuthorizationIntegrationTest" `
  --tests "com.ssafy.bidlive.common.websocket.expiry.StompSessionExpiryIntegrationTest"
```

```powershell
git diff --check
```

## 후속 점검

로그·채팅 이벤트·검열 DLQ의 원문 및 개인정보 비노출 검증은 `S15P11A704-602` 범위에서 별도로 수행한다.
