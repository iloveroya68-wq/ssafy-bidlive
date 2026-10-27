# Redis Infrastructure Scope

이 scope는 여러 도메인이 공유하는 Redis 실행 환경, Spring 연결과 공통 직렬화
정책을 구성하는 인프라 설정 작업에 사용한다. 경매, 입찰, 정산 등 도메인별 Redis
비즈니스 구현은 이 scope가 직접 소유하지 않는다.

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Scope ID | `redis-infrastructure` |
| 작업 유형 | Redis 인프라 설정 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| Notion 명세 담당자 | 인프라 담당자 |
| 담당 기능 | Redis 컨테이너, 연결 설정, 공통 직렬화, 개발 환경과 기술 문서 |
| 관련 도메인 | `common` 및 Redis를 사용하는 모든 도메인 |

## 담당 책임

- Docker Compose 기반 Redis 실행과 데이터 영속화
- Spring Data Redis 의존성 및 환경변수 기반 접속 설정
- 공통 `RedisTemplate`과 `StringRedisTemplate` 사용 정책
- Redis Key·Value 직렬화 정책
- Redis Key 네이밍 원칙과 로컬 개발 절차 문서화
- Redis 연결 상태 및 저장·조회 검증

## 담당 API 명세

없음. 이 scope는 HTTP API 또는 WebSocket 계약을 구현하거나 변경하지 않는다. API나
이벤트 계약 변경이 필요하면 해당 기능 scope를 별도로 활성화한다.

## 수정 허용 경로

아래 경로에서도 현재 Redis 인프라 작업과 직접 관련된 부분만 변경할 수 있다.

```text
src/main/java/com/ssafy/bidlive/common/config/RedisConfig.java
src/main/java/com/ssafy/bidlive/common/config/redis/**
src/main/resources/redis/**
src/test/java/com/ssafy/bidlive/common/config/Redis*Test.java
src/test/java/com/ssafy/bidlive/common/config/redis/**
src/test/resources/redis/**
src/main/resources/application.yml
src/main/resources/application-local.yml
docker-compose.yml
docker-compose.local.yml
.env.example
README.md
docs/infrastructure/redis/**
```

- `application*.yml`에서는 `spring.data.redis`와 Redis 전용 애플리케이션 속성만
  변경한다.
- Compose 파일에서는 Redis 서비스, Redis 전용 Volume·Network·healthcheck와 Redis
  연결에 필요한 서비스 환경변수만 변경한다.
- `README.md`에서는 Redis 실행, 검증, 직렬화 및 Key 규칙 부분만 변경한다.
- 실제 비밀번호나 운영 Secret은 허용 경로에도 기록하지 않는다.

## 읽기 전용 경로

Redis 사용 방식과 호환성을 확인하기 위해 다음 경로를 읽을 수 있지만 자동으로
수정할 수는 없다.

```text
src/main/java/com/ssafy/bidlive/*/infrastructure/redis/**
src/test/java/com/ssafy/bidlive/*/infrastructure/redis/**
src/main/java/com/ssafy/bidlive/*/application/**
src/main/java/com/ssafy/bidlive/*/domain/**
docs/api/**
```

## 수정 금지 경로와 내용

- `src/main/java/com/ssafy/bidlive/common/security/**`
- Redis와 무관한 `common.config` 클래스
- Redis와 무관한 도메인 코드와 테스트
- JPA Entity, JPA Repository와 DB migration
- OpenVidu 및 다른 외부 연동 설정
- `docs/api/**`의 HTTP API와 WebSocket 계약
- `settings.gradle`
- 기존 운영 환경의 비밀값과 개인 로컬 설정값
- 다른 담당자의 비즈니스 규칙
- 테스트 삭제 또는 비활성화
- `AGENTS.md`, `docs/agents/**`, `docs/architecture/package-boundaries.md`

허용 경로와 금지 내용이 겹치면 금지 규칙을 우선한다. 구현 작업 중 이 scope 또는
상위 에이전트 규칙을 스스로 수정할 수 없다.

## 조건부 수정 경로

| 경로 | 수정할 수 있는 조건 | 필요한 승인 |
| --- | --- | --- |
| `build.gradle` | Redis Starter가 없거나 Redis 검증에 필요한 기존 승인 의존성을 구성해야 하는 경우 | 사용자 또는 빌드 담당자 |
| `src/main/resources/application-prod.yml` | 운영 Redis 연결 계약을 반드시 변경해야 하고 비밀값을 포함하지 않는 경우 | 사용자 및 운영 설정 담당자 |
| `scripts/redis/**` | 반복 가능한 Redis 로컬 실행·점검 명령이 문서만으로 충분하지 않은 경우 | 사용자 |
| `src/main/java/com/ssafy/bidlive/{domain}/infrastructure/redis/**`의 개별 파일 | 공통 Redis 설정 변경으로 기존 Adapter의 호환 수정이 반드시 필요한 경우 | 사용자 및 해당 도메인 담당자 |
| `src/test/java/com/ssafy/bidlive/{domain}/infrastructure/redis/**`의 관련 테스트 | 승인된 도메인 Redis Adapter 변경을 검증하는 경우 | 사용자 및 해당 도메인 담당자 |

조건부 수정 전에는 대상 파일, 이유, 현재 scope만으로 해결할 수 없는 이유, 예상 영향과
최소 변경안을 보고한다. 승인은 보고한 개별 파일과 현재 작업에만 적용한다.

## 새 파일 생성 기준

- 공통 Redis Bean과 기술 설정은 `common.config` 또는 `common.config.redis`에 둔다.
- Lua Script와 Redis 전용 리소스는 `src/main/resources/redis/**`에 둔다.
- 기술 환경 문서는 `docs/infrastructure/redis/**`에 둔다.
- 도메인 의미가 포함된 Key 생성기, Repository와 Adapter는 해당 도메인의
  `infrastructure.redis`에 두며 조건부 수정 승인을 먼저 받는다.
- Redis Key 문자열은 여러 파일에 하드코딩하지 않고 소유 도메인의 Key 생성 객체에
  모은다.
- 새 파일에도 실제 Secret, 임시 코드 또는 사용하지 않는 예제 코드를 남기지 않는다.

## 범위 밖 변경 절차

범위 밖 변경이 필요하면 다음 형식으로 먼저 보고한다.

```text
범위 밖 변경 요청:
- 현재 작업 유형: Redis 인프라 설정
- 활성 scope: docs/agents/scopes/redis-infrastructure.scope.md
- 대상 파일:
- 필요한 이유:
- 현재 scope만으로 해결할 수 없는 이유:
- 예상 영향:
- 필요한 최소 변경:
- 요청하는 승인: 이번 작업에 한해 위 파일의 최소 변경 허용
```

## 검증 명령

```bash
docker compose config --quiet
docker compose up -d redis
docker compose exec redis redis-cli ping
./gradlew test --tests '*Redis*'
./gradlew test
./gradlew build
```

- 연결 설정을 변경하면 Spring Data Redis를 통한 실제 저장·조회도 확인한다.
- 영속화 설정을 변경하면 컨테이너 재생성 후 데이터 유지 여부를 확인한다.
- Docker 또는 외부 Redis를 실행할 수 없으면 정적 검증으로 대체했다고 완료 처리하지
  않고 확인하지 못한 항목과 재현 명령을 보고한다.
- 검증을 위해 만든 Key, 컨테이너와 임시 코드는 작업 후 안전하게 정리한다.

## 완료 보고

- 변경 목적과 수정한 Redis 설정 파일의 역할
- 변경 파일이 모두 허용 또는 승인된 조건부 경로에 포함되는지
- 조건부 파일을 수정한 이유와 승인 내역
- Compose 구문, Redis `PING`, Spring 저장·조회 결과
- 실행한 Redis 관련 테스트, 전체 테스트와 빌드 결과
- 직렬화 및 Redis Key 정책 변경 내용
- 구현하거나 변경한 기능 ID와 API 명세가 없다는 사실
- 확인하지 못한 항목과 범위상 수정하지 않은 문제
