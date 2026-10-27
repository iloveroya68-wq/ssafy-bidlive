# Observability Scope

이 scope는 실시간 기능의 구조화 로그, correlationId, Actuator와 Micrometer 운영 지표를
구성하는 `S15P11A704-243` 전용 범위다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `observability` |
| Notion 명세 담당자 | 김경민 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 실시간 요청 추적, 구조화 로그와 운영 메트릭 |
| Jira | `S15P11A704-243`, `S15P11A704-592`, `S15P11A704-593` |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/common/observability/**`
- `src/test/java/com/ssafy/bidlive/common/observability/**`
- `docs/operations/observability.md`

## Allowed Existing Files

- `src/main/java/com/ssafy/bidlive/common/config/SecurityConfig.java`
- `src/main/java/com/ssafy/bidlive/common/config/WebSocketConfig.java`
- `src/main/java/com/ssafy/bidlive/common/logging/RequestLoggingFilter.java`
- `src/main/java/com/ssafy/bidlive/common/security/StompConnectAuthenticationInterceptor.java`
- `src/main/java/com/ssafy/bidlive/common/websocket/subscription/StompSubscriptionAuthorizationInterceptor.java`
- `src/main/java/com/ssafy/bidlive/chat/api/StompChatSendController.java`
- `src/main/java/com/ssafy/bidlive/chat/application/ChatModerationWorker.java`
- `src/main/java/com/ssafy/bidlive/chat/infrastructure/ChatAsyncModerationConfiguration.java`
- `src/main/java/com/ssafy/bidlive/media/application/ViewerSessionTrackingService.java`
- 위 파일의 대응 테스트
- `build.gradle`
- `src/main/resources/application.yml`

## Read-only Paths

- `src/main/java/com/ssafy/bidlive/**`
- `src/test/java/com/ssafy/bidlive/**`
- `docs/api/**`

## Restricted Changes

- `AGENTS.md`
- `docs/architecture/package-boundaries.md`
- API 및 WebSocket 요청·응답 계약
- DB migration과 Redis Key
- 인증·구독·채팅·검열·접속 세션의 비즈니스 정책
- 로그나 메트릭에 JWT, 메시지 원문, 개인정보 또는 고카디널리티 식별자 추가

## Validation

```bash
./gradlew test --tests "com.ssafy.bidlive.common.observability.*"
./gradlew test
./gradlew build
git diff --check
```

## Completion Report

- 수집하는 로그 필드와 메트릭 이름
- 저카디널리티 태그와 민감정보 비노출 근거
- Actuator 노출·접근 정책
- 실행한 테스트와 빌드 결과
- 승인받은 공유 파일 변경
