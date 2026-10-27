# Member Scope

이 scope는 `member` 도메인 전체를 하나의 담당 범위가 소유할 때 사용하는 기본 scope다.
같은 도메인을 여러 팀원이 나누어 담당하면 이 파일을 기능별 scope로 분리한다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `member` |
| Notion 명세 담당자 | 신희원 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 회원 프로필·약관·마이페이지 |
| API 색인 | [docs/api/member/README.md](../../api/member/README.md) |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/member/**`
- `src/test/java/com/ssafy/bidlive/member/**`
- `docs/api/member/**`

## Owned APIs

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `MEM-03` | `GET` | `/api/v1/members/me/settlement-account` | 확정 | [정산 계좌 조회](../../api/member/mem-03-get-members-me-settlement-account.md) |
| `MEM-03` | `PUT` | `/api/v1/members/me/settlement-account` | 확정 | [정산 계좌 등록·변경](../../api/member/mem-03-put-members-me-settlement-account.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/hosted` | 확정 | [개최한 경매 조회](../../api/member/mem-02-get-members-me-auctions-hosted-bdc776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/won` | 확정 | [낙찰 받은 경매 조회](../../api/member/mem-02-get-members-me-auctions-won-a2d776eb.md) |
| `MEM-02` | `PATCH` | `/api/v1/members/me` | 확정 | [내 프로필 수정](../../api/member/mem-02-patch-members-me-2fc776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/nickname-availability` | 확정 | [닉네임 중복 확인](../../api/member/mem-02-get-members-nickname-availability-0de776eb.md) |
| `MEM-02` | `POST` | `/api/v1/members/me/terms-agreements` | 확정 | [약관 동의 API](../../api/member/mem-02-post-members-me-terms-agreements-662776eb.md) |
| `MEM-02` | `GET` | `/api/v1/terms?purpose=ONBOARDING` | 확정 | [약관 조회 API](../../api/member/mem-02-get-terms-purpose-onboarding-3af776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me/auctions/participated` | 확정 | [참여한 경매 조회](../../api/member/mem-02-get-members-me-auctions-participated-87d776eb.md) |
| `MEM-02` | `GET` | `/api/v1/members/me` | 확정 | [프로필 조회](../../api/member/mem-02-get-members-me-58a776eb.md) |

소유 목록에 있어도 상태가 `확정`이 아닌 계약은 확정된 것으로 가정해 구현하지 않는다.

## Allowed Changes

- `Member` API Controller, WebSocket 진입점과 DTO
- `Member` Application Service와 유스케이스
- `Member` Domain Model, 정책과 이벤트
- `Member` Infrastructure Adapter와 Repository 구현
- `Member` 테스트
- `docs/api/member/**` 명세

## Restricted Changes

- `src/main/java/com/ssafy/bidlive/**` 중 `member` 밖의 도메인
- `src/test/java/com/ssafy/bidlive/**` 중 `member` 밖의 테스트
- `docs/api/**` 중 `member` 밖의 명세
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
