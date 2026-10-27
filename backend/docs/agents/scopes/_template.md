# Scope: `{scope-id}`

> 이 파일을 복사하고 파일명을 `{scope-id}.scope.md`로 변경한 뒤 중괄호로 표시된 값을
> 프로젝트에 맞게 수정한다.

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Scope ID | `{scope-id}` |
| 실제 작업 담당자 | `{이름 또는 팀 역할}` |
| Notion 명세 담당자 | `{원본 명세 담당자 필드 값}` |
| 담당 기능 | `{이 scope가 책임지는 기능}` |
| 관련 도메인 | `{auth, member 등}` |

## 담당 책임

- `{구현하고 유지할 기능}`
- `{관리할 API 또는 유스케이스}`
- `{관리할 외부 연동 또는 데이터}`

## 담당 API 명세

API와 WebSocket 명세의 기준 및 확인 방법은 `docs/api/README.md`를 따른다.

| 항목 | 값 |
| --- | --- |
| 로컬 명세 파일 | `docs/api/{domain}/{api-file}.md` |
| 기능 ID | `{AUCTION-01, BID-01 등 또는 범위}` |
| 구현 가능한 상태 | `확정` |
| Notion 담당자 | `{원본 명세 담당자 필드 값}` |

- 위 조건에 해당하는 API와 WebSocket 이벤트만 구현하거나 변경한다.
- 여러 API를 담당하면 각 명세 파일을 표에 행으로 추가한다.
- 도메인 전체를 단독 소유할 때만 `docs/api/{domain}/**` glob을 사용할 수 있다.
- 다른 scope의 로컬 API 계약은 읽을 수 있지만 임의로 변경하지 않는다.
- API 계약 변경이 필요하면 코드와 해당 로컬 명세를 같은 작업에서 변경한다.
- 로컬 명세와 Notion 원본이 다르면 어느 한쪽을 추측하여 수정하지 않고 차이와
  영향을 먼저 보고한다.

## 수정 허용 경로

아래 경로에 포함된 파일만 생성·수정·삭제할 수 있다.

```text
src/main/java/com/ssafy/bidlive/{domain}/{담당 하위 경로 또는 파일}
src/test/java/com/ssafy/bidlive/{domain}/{담당 하위 경로 또는 파일}
docs/api/{domain}/{담당 API 명세 파일}.md
docs/api/{domain}/events/{담당 WebSocket 이벤트}.md
docs/{이 scope가 소유한 문서 경로}/**
```

도메인 전체를 한 scope가 단독 소유할 때만 Java와 테스트 경로에
`src/.../{domain}/**` glob을 사용할 수 있다.

필요한 경우 개별 리소스 경로를 추가한다.

```text
src/main/resources/{scope 전용 리소스 경로}/**
```

## 읽기 전용 경로

의존성 파악과 연동을 위해 읽을 수 있지만 수정할 수 없는 경로다.

```text
src/main/java/com/ssafy/bidlive/{연관 도메인}/**
```

읽기 전용 경로가 없으면 `없음`이라고 작성한다.

## 수정 금지 경로

허용 경로와 겹치더라도 아래 경로는 수정할 수 없다.

```text
build.gradle
settings.gradle
AGENTS.md
docs/agents/**
docs/architecture/package-boundaries.md
src/main/resources/application.yml
src/main/java/com/ssafy/bidlive/common/**
src/main/java/com/ssafy/bidlive/{다른 담당 도메인}/**
docs/api/{다른 담당 도메인}/**
```

프로젝트에서 모든 scope에 공통으로 금지할 파일과 해당 scope가 소유하지 않는 경로를
명시한다.

`AGENTS.md`, `docs/agents/**`, `docs/architecture/package-boundaries.md`는 작업
에이전트가 자신의 규칙이나 권한을 변경하지 못하도록 모든 구현 scope에서 금지한다.
이 문서들은 별도의 문서 관리 작업으로만 수정한다.

## 공유 경로와 수정 조건

| 경로 | 수정할 수 있는 조건 | 필요한 승인 |
| --- | --- | --- |
| `{공유 파일 또는 glob}` | `{수정이 필요한 상황}` | `{승인자 또는 담당 scope}` |

공유 경로가 없으면 `없음`이라고 작성한다. 공유 경로는 자동으로 수정 허용 경로가
되지 않으며, 표에 적힌 조건과 승인을 모두 충족해야 한다.

## 새 파일 생성 기준

- 새 파일도 `수정 허용 경로` 안에만 생성한다.
- 파일 위치는 `docs/architecture/package-boundaries.md`를 따른다.
- 기존 패키지로 책임을 설명할 수 없다면 새 패키지를 임의로 만들지 않는다.
- DB migration, 공통 설정 및 공용 계약을 새로 만들 때는 공유 경로 승인 절차를
  따른다.

## 범위 밖 변경 절차

범위 밖 변경이 필요하면 수정 전에 다음 형식으로 보고한다.

```text
범위 밖 변경 요청:
- 대상 파일:
- 필요한 이유:
- 현재 scope만으로 해결할 수 없는 이유:
- 예상 영향:
- 필요한 최소 변경:
- 요청하는 승인: 이번 작업에 한해 위 파일의 최소 변경 허용
```

승인을 받지 못하면 범위 밖 파일을 수정하지 않고 현재 범위에서 가능한 작업만
완료한다.

- 다른 도메인의 기존 공개 Application 계약을 호출하기만 하면 범위 확장이 아니다.
- 다른 도메인의 파일이나 공개 계약을 수정할 때는 사용자의 명시적인 승인을 받는다.
- 승인은 보고한 파일과 현재 작업에만 적용한다.
- 승인받아도 다른 도메인의 Infrastructure 또는 JPA Entity를 직접 참조하지 않는다.
- 여러 도메인 수정이 처음부터 예정되면 필요한 개별 경로를 소유하는 기능 scope를
  사용한다.

## 검증 명령

이 scope의 작업을 완료하기 위해 실행할 명령을 작성한다.

```bash
./gradlew test
./gradlew build
```

필요하면 특정 테스트 명령을 위 명령보다 먼저 추가한다.

## 완료 보고

완료 보고에는 다음 내용을 포함한다.

- 수정한 파일이 모두 허용 경로에 포함되는지
- 실행한 테스트와 빌드 결과
- 승인받아 수정한 공유 또는 범위 밖 파일
- 구현하거나 변경한 API 명세의 기능 ID와 상태
- 발견했지만 범위상 수정하지 않은 문제
