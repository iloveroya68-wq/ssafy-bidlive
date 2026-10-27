# OpenAPI Infrastructure Scope

이 scope는 Springdoc OpenAPI 문서 생성과 Swagger UI 실행 환경을 구성하는 공통
인프라 작업에 사용한다. 개별 도메인의 API 계약이나 비즈니스 동작은 직접 변경하지
않는다.

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Scope ID | `openapi-infrastructure` |
| 작업 유형 | OpenAPI·Swagger 인프라 설정 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| Notion 명세 담당자 | 인프라 담당자 |
| 담당 기능 | OpenAPI 생성, Swagger UI, 공통 API 메타데이터와 개발 문서 |
| 관련 도메인 | `common` 및 문서화 대상 API를 가진 모든 도메인 |

## 담당 책임

- Spring Boot 버전에 맞는 Springdoc OpenAPI 의존성 구성
- 공통 OpenAPI 제목, 설명과 버전 정보 구성
- OpenAPI JSON과 Swagger UI 경로 설정
- Swagger UI와 OpenAPI 문서 엔드포인트 동작 검증
- 로컬 실행 및 접속 방법 문서화

## 담당 API 명세

없음. `/v3/api-docs`와 `/swagger-ui.html`은 개발 도구 엔드포인트이며 도메인 HTTP
API 또는 WebSocket 계약으로 등록하지 않는다. 개별 API 설명이나 계약을 변경하려면
해당 도메인 기능 scope를 별도로 활성화한다.

## 수정 허용 경로

아래 경로에서도 현재 OpenAPI·Swagger 설정과 직접 관련된 부분만 변경할 수 있다.

```text
build.gradle
src/main/java/com/ssafy/bidlive/common/config/OpenApiConfig.java
src/main/java/com/ssafy/bidlive/common/config/openapi/**
src/test/java/com/ssafy/bidlive/common/config/OpenApi*Test.java
src/test/java/com/ssafy/bidlive/common/config/openapi/**
src/main/resources/application.yml
README.md
docs/infrastructure/openapi/**
```

- `build.gradle`에서는 승인된 Springdoc OpenAPI 의존성만 추가하거나 조정한다.
- `application.yml`에서는 `springdoc` 속성만 변경한다.
- `README.md`에서는 OpenAPI·Swagger 실행과 접속 방법만 변경한다.
- 공통 설정은 특정 도메인의 DTO, Entity 또는 비즈니스 서비스에 의존하지 않는다.

## 읽기 전용 경로

문서 생성 대상과 접근 정책을 파악하기 위해 다음 경로를 읽을 수 있지만 자동으로
수정할 수는 없다.

```text
src/main/java/com/ssafy/bidlive/*/api/**
src/main/java/com/ssafy/bidlive/common/config/SecurityConfig.java
docs/api/**
```

## 수정 금지 경로와 내용

- `src/main/java/com/ssafy/bidlive/common/security/**`
- 개별 도메인의 Controller, DTO, Application, Domain과 Infrastructure 코드
- JPA Entity, Repository와 DB migration
- Redis와 OpenVidu 설정
- `docs/api/**`의 HTTP API와 WebSocket 계약
- 기존 운영 환경의 비밀값
- 테스트 삭제 또는 비활성화
- 승인되지 않은 의존성 추가
- `AGENTS.md`, `docs/agents/**`, `docs/architecture/package-boundaries.md`

허용 경로와 금지 내용이 겹치면 금지 규칙을 우선한다. 구현 작업 중 이 scope 또는
상위 에이전트 규칙을 스스로 수정할 수 없다.

## 조건부 수정 경로

| 경로 | 수정할 수 있는 조건 | 필요한 승인 |
| --- | --- | --- |
| `src/main/java/com/ssafy/bidlive/common/config/SecurityConfig.java` | 기존 보안 정책이 OpenAPI JSON 또는 Swagger UI 접근을 차단하고 정확한 문서 경로만 허용해야 하는 경우 | 사용자 및 인증 담당자 |
| `src/main/resources/application-prod.yml` | 운영 환경의 OpenAPI 노출 여부를 명시적으로 변경해야 하고 비밀값을 포함하지 않는 경우 | 사용자 및 운영 설정 담당자 |
| 개별 도메인의 API 코드와 테스트 | 확정된 API 계약에 OpenAPI Annotation을 추가해야 하는 경우 | 사용자 및 해당 도메인 담당자 |

조건부 수정 전에는 대상 파일, 이유, 예상 영향과 최소 변경안을 보고한다. 승인은
보고한 개별 파일과 현재 작업에만 적용한다.

## 새 파일 생성 기준

- 전역 OpenAPI Bean은 `common.config.OpenApiConfig`에 둔다.
- 설정 책임이 커지면 기술 전용 코드를 `common.config.openapi`에 둔다.
- 공통 설정 테스트는 `src/test/java/.../common/config`의 OpenAPI 전용 테스트로 둔다.
- 개별 도메인의 설명, 응답과 요청 Schema는 이 scope에서 새로 정의하지 않는다.
- 구현 클래스와 테스트 클래스에는 책임을 설명하는 한 줄 JavaDoc을 작성한다.
- 임시 Controller, 예제 API 또는 사용하지 않는 설정 코드를 남기지 않는다.

## 범위 밖 변경 절차

범위 밖 변경이 필요하면 다음 형식으로 먼저 보고한다.

```text
범위 밖 변경 요청:
- 현재 작업 유형: OpenAPI·Swagger 인프라 설정
- 활성 scope: docs/agents/scopes/openapi-infrastructure.scope.md
- 대상 파일:
- 필요한 이유:
- 현재 scope만으로 해결할 수 없는 이유:
- 예상 영향:
- 필요한 최소 변경:
- 요청하는 승인: 이번 작업에 한해 위 파일의 최소 변경 허용
```

## 검증 명령

```bash
./gradlew test --tests '*OpenApi*'
./gradlew test
./gradlew build
```

- `/v3/api-docs`가 OpenAPI JSON과 공통 메타데이터를 반환하는지 확인한다.
- `/swagger-ui.html`이 Swagger UI로 정상 연결되는지 확인한다.
- 보안 설정을 변경하지 않았다면 기존 접근 정책 아래에서 두 경로가 노출되는지
  테스트한다.

## 완료 보고

- 변경한 Springdoc 의존성과 선택 이유
- 공통 OpenAPI 메타데이터와 Swagger 경로
- 수정한 파일이 모두 허용 또는 승인된 조건부 경로에 포함되는지
- OpenAPI JSON, Swagger UI, 전체 테스트와 빌드 결과
- 구현하거나 변경한 도메인 기능 ID와 API 명세가 없다는 사실
- 확인하지 못한 항목과 범위상 수정하지 않은 문제
