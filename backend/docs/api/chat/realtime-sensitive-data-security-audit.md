# 실시간 채팅 민감정보 노출 점검

## 범위

- Jira: `S15P11A704-239`, `S15P11A704-602`
- 기능 ID: `CHAT-01`, `CHAT-03`, `AI-05`
- 대상: 채팅 이벤트, 외부 LLM 요청, retry·DLQ, 검열 감사 로그, 운영 로그

## 점검 결과

| 경로 | 허용 데이터 | 금지 데이터 | 검증 결과 |
| --- | --- | --- | --- |
| `CHAT_MESSAGE` 이벤트 | 마스킹된 `content`, 메시지·발신자 식별자 | 마스킹 전 연락처·계좌 | `StompChatMessageEventPublisherTest`로 마스킹 본문 전송 확인 |
| 외부 LLM 요청 | `messageId`, `filteredContent`, 질문 여부 | 감사 로그 원문, JWT, API key | `GmsChatLlmModerationGateway`가 필터 결과만 요청에 구성 |
| retry 상태 | 처리 중인 마스킹 본문과 실패 코드 | 마스킹 전 원문 | 작업 모델이 `filteredContent`만 보유 |
| DEAD_LETTER 상태 | `messageId`, 오류 코드, 시도 횟수 | `memberId`, 채팅 본문 | V24 이후 DLQ 전이 시 민감 필드를 `NULL` 처리 |
| 검열 감사 저장소 | AES-GCM 암호문, IV, key version, SHA-256 hash | 평문 원문 컬럼 | `JdbcChatModerationAuditPersistenceAdapterIntegrationTest`로 확인 |
| 채팅 운영 로그 | 처리 개수와 예외 유형 | JWT, 회원 ID, 채팅 원문, 금칙어 원문 | logger 호출부 정적 점검 및 관련 단위 테스트로 확인 |

## DEAD_LETTER 폐기 정책

- PENDING·PROCESSING 상태에서는 LLM 판정에 필요한 `memberId`와 `filteredContent`를 유지한다.
- 명시적 최종 실패와 최대 시도 횟수 초과 복구가 `DEAD_LETTER`로 전이될 때 두 필드를 제거한다.
- `messageId`, `attemptCount`, `lastErrorCode`는 장애 원인 추적과 멱등 처리를 위해 유지한다.
- 마스킹 전 원문은 작업 큐와 DEAD_LETTER에 저장하지 않는다.

## 자동 검증

```powershell
.\gradlew.bat test `
  --tests "com.ssafy.bidlive.chat.infrastructure.persistence.JdbcChatModerationJobQueueAdapterIntegrationTest" `
  --tests "com.ssafy.bidlive.chat.application.ChatModerationWorkerTest" `
  --tests "com.ssafy.bidlive.chat.infrastructure.StompChatMessageEventPublisherTest" `
  --tests "com.ssafy.bidlive.chat.infrastructure.gms.GmsChatLlmModerationGatewayTest" `
  --tests "com.ssafy.bidlive.chat.infrastructure.persistence.JdbcChatModerationAuditPersistenceAdapterIntegrationTest"
```

```powershell
git diff --check
```

Question moderation now also sends the queued room context snapshot
(`roomTitle`, `productDescription`, `productCategoryCode`) to the LLM. These
fields are cleared together with the masked message content when a job reaches
`DEAD_LETTER` (migration `V26`).

The chat send path now rejects messages when the synchronous filter detects a
configured prohibited word, phone number, messenger ID, or account number.
Those messages are not persisted or broadcast; the masked representation is
kept only as an in-process filtering result.
