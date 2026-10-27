# Chat Scope

이 scope는 `chat` 도메인 전체를 하나의 담당 범위가 소유할 때 사용하는 기본 scope다.
같은 도메인을 여러 팀원이 나누어 담당하면 이 파일을 기능별 scope로 분리한다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `chat` |
| Notion 명세 담당자 | 김경민 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 채팅·질문·공지·검열 |
| API 색인 | [docs/api/chat/README.md](../../api/chat/README.md) |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/chat/**`
- `src/test/java/com/ssafy/bidlive/chat/**`
- `docs/api/chat/**`

## Owned APIs

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `AI-05` | `INTERNAL` | 내부 처리 | 확정 | [LLM 비동기 문맥 판정](../../api/chat/ai-05-internal-internal-d3b776eb.md) |
| `AI-05` | `WS` | `/user/queue/moderation` | 확정 | [검열 경고·뮤트 통지](../../api/chat/ai-05-ws-user-queue-moderation-5bb776eb.md) |
| `CHAT-01` | `WS` | `/topic/auctions/{auctionId}/chat` | 확정 | [채팅 수신](../../api/chat/chat-01-ws-topic-auctions-by-auctionid-chat-533776eb.md) |
| `CHAT-01` | `WS` | `/app/auctions/{auctionId}/chat` | 확정 | [채팅 전송](../../api/chat/chat-01-ws-app-auctions-by-auctionid-chat-1eb776eb.md) |
| `CHAT-01` | `GET` | `/api/v1/auctions/{auctionId}/chats` | 확정 | [채팅 이력 조회](../../api/chat/chat-01-get-auctions-by-auctionid-chats.md) |
| `CHAT-01` | `DELETE` | `/api/v1/auctions/{auctionId}/chats/{messageId}` | 확정 | [채팅 메시지 삭제](../../api/chat/chat-01-delete-auctions-by-auctionid-chats-by-messageid.md) |
| `CHAT-02` | `POST` | `/api/v1/auctions/{auctionId}/chat/pin` | 확정 | [고정 공지 등록·교체](../../api/chat/chat-02-post-auctions-by-auctionid-chat-pin-516776eb.md) |
| `CHAT-02` | `DELETE` | `/api/v1/auctions/{auctionId}/chat/pin` | 확정 | [고정 공지 해제](../../api/chat/chat-02-delete-auctions-by-auctionid-chat-pin-b6b776eb.md) |
| `CHAT-03` | `INTERNAL` | 내부 처리 | 확정 | [룰 기반 채팅 필터](../../api/chat/chat-03-internal-internal-127776eb.md) |
| `CHAT-04` | `INTERNAL` | 내부 처리 | 확정 | [슬로우 모드](../../api/chat/chat-04-internal-internal-b00776eb.md) |
| `CHAT-06` | `WS` | `/app/auctions/{auctionId}/chat` | 확정 | [질문 채팅 전송](../../api/chat/chat-06-ws-app-auctions-by-auctionid-chat-781776eb.md) |
| `CHAT-06` | `WS` | `/topic/auctions/{auctionId}/questions` | 확정 | [질문 큐 수신](../../api/chat/chat-06-ws-topic-auctions-by-auctionid-questions-5ec776eb.md) |
| `CHAT-06` | `GET` | `/api/v1/auctions/{auctionId}/questions` | 확정 | [질문 큐 조회](../../api/chat/chat-06-get-auctions-by-auctionid-questions-e0c776eb.md) |
| `CHAT-06` | `DELETE` | `/api/v1/auctions/{auctionId}/questions/{questionId}` | 확정 | [질문 큐 항목 제거](../../api/chat/chat-06-delete-auctions-by-auctionid-questions-by-questionid-12a776eb.md) |

소유 목록에 있어도 상태가 `확정`이 아닌 계약은 확정된 것으로 가정해 구현하지 않는다.

## Allowed Changes

- `Chat` API Controller, WebSocket 진입점과 DTO
- `Chat` Application Service와 유스케이스
- `Chat` Domain Model, 정책과 이벤트
- `Chat` Infrastructure Adapter와 Repository 구현
- `Chat` 테스트
- `docs/api/chat/**` 명세

## Restricted Changes

- `src/main/java/com/ssafy/bidlive/**` 중 `chat` 밖의 도메인
- `src/test/java/com/ssafy/bidlive/**` 중 `chat` 밖의 테스트
- `docs/api/**` 중 `chat` 밖의 명세
- `AGENTS.md`, `docs/agents/**`, `docs/architecture/package-boundaries.md`
- `build.gradle`, `settings.gradle`, `src/main/resources/application.yml`
- 공용 인증·인가, 오류 응답과 직렬화 정책
- 다른 도메인의 DB 모델, Repository와 외부 연동

## Shared Changes

아래 대상은 수정 전에 파일, 이유, 영향과 최소 변경을 보고하고 승인을 받아야 한다.

| 대상 | 승인 조건 |
| --- | --- |
| `src/main/java/com/ssafy/bidlive/common/**` | 공통 코드 변경이 반드시 필요한 경우 |
| `src/main/resources/**` | 도메인 전용 리소스로 분리할 수 없는 경우 |
| `build.gradle`, `settings.gradle` | 승인된 의존성 또는 빌드 변경 |
| `src/main/resources/application.yml` | 공통 환경설정 계약 변경 |
| 다른 도메인의 공개 Application 계약 | 도메인 간 연동 계약 변경 |
| 다른 도메인의 `docs/api/**` | 여러 API 계약을 함께 변경해야 하는 경우 |
| DB migration | 다른 도메인이나 공용 스키마에 영향을 주는 경우 |

## Validation

```bash
./gradlew test
./gradlew build
```

## Completion Report

- 변경 파일이 모두 Owned Paths에 포함되는지
- 변경한 기능 ID와 API 명세
- 테스트와 빌드 결과
- 승인받은 Shared Changes
- scope 밖이라 수정하지 않은 문제
