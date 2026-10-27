# Common Scope

이 scope는 `common` 도메인 전체를 하나의 담당 범위가 소유할 때 사용하는 기본 scope다.
같은 도메인을 여러 팀원이 나누어 담당하면 이 파일을 기능별 scope로 분리한다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `common` |
| Notion 명세 담당자 | 김경민 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 공통 WebSocket 연결·인증·오류와 횡단 관심사 |
| API 색인 | [docs/api/common/README.md](../../api/common/README.md) |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/common/**`
- `src/test/java/com/ssafy/bidlive/common/**`
- `docs/api/common/**`

## Owned APIs

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `WS-01` | `WS` | `/ws` | 확정 | [소켓 연결·인증](../../api/common/ws-01-ws-ws-053776eb.md) |
| `WS-01` | `WS` | `/app/auth/renew` | 확정 | [소켓 토큰 재인증](../../api/common/ws-01-ws-app-auth-renew-e0d776eb.md) |
| `WS-02` | `INTERNAL` | 내부 처리 | 확정 | [구독 권한 검증](../../api/common/ws-02-internal-internal-146776eb.md) |
| `WS-03` | `GET` | `/api/v1/auctions/{auctionId}/realtime-state` | 확정 | [경매 상태 스냅샷](../../api/common/ws-03-get-auctions-by-auctionid-realtime-state-9b3776eb.md) |
| `WS-04` | `WS` | `/user/queue/errors` | 확정 | [WS 에러 수신](../../api/common/ws-04-ws-user-queue-errors-498776eb.md) |

소유 목록에 있어도 상태가 `확정`이 아닌 계약은 확정된 것으로 가정해 구현하지 않는다.

## Allowed Changes

- `Common` API Controller, WebSocket 진입점과 DTO
- `Common` Application Service와 유스케이스
- `Common` Domain Model, 정책과 이벤트
- `Common` Infrastructure Adapter와 Repository 구현
- `Common` 테스트
- `docs/api/common/**` 명세

## Restricted Changes

- `src/main/java/com/ssafy/bidlive/**` 중 `common` 밖의 도메인
- `src/test/java/com/ssafy/bidlive/**` 중 `common` 밖의 테스트
- `docs/api/**` 중 `common` 밖의 명세
- `AGENTS.md`, `docs/agents/**`, `docs/architecture/package-boundaries.md`
- `build.gradle`, `settings.gradle`, `src/main/resources/application.yml`
- 공용 인증·인가, 오류 응답과 직렬화 정책
- 다른 도메인의 DB 모델, Repository와 외부 연동

## Shared Changes

아래 대상은 수정 전에 파일, 이유, 영향과 최소 변경을 보고하고 승인을 받아야 한다.

| 대상 | 승인 조건 |
| --- | --- |
| `src/main/java/com/ssafy/bidlive/common/**`의 공개 계약 | 다른 도메인에 영향을 주는 공통 계약 변경 |
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
