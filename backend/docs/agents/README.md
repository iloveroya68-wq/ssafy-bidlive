# Agent Work Scopes

이 문서는 에이전트 작업 범위의 적용 방법과 등록 위치를 정의한다.

## 적용 원칙

- 모든 코드 작업에는 하나의 scope 문서를 지정한다.
- `docs/agents/README.md`는 적용 방법과 등록 목록이며 활성 scope가 아니다.
- 실제 작업에는 `docs/agents/scopes/{scope-id}.scope.md` 하나를 활성 scope로 지정한다.
- 에이전트는 저장소 전체를 읽고 분석할 수 있지만, 지정된 scope의 `수정 허용 경로`
  안에서만 파일을 생성·수정·삭제할 수 있다.
- `수정 금지 경로`는 `수정 허용 경로`보다 우선한다.
- 관련 테스트, 리소스 및 문서도 자동으로 허용되지 않는다. scope 문서에 경로를
  명시해야 한다.
- 범위 밖 수정이 필요하면 파일, 이유 및 영향을 먼저 보고하고 명시적인 승인을
  받은 후 진행한다.
- 승인받은 범위 확장은 해당 작업에만 적용된다. 이후 작업의 기본 scope를 변경하지
  않는다.
- 두 scope가 같은 파일을 수정할 수 있다면 해당 파일을 `공유 경로`로 선언하고
  수정 조건과 승인자를 명시한다.

## 작업 유형별 범위

에이전트는 작업을 시작하기 전에 요청의 주된 목적을 기준으로 작업 유형을 판단한다.
작업 유형은 활성 scope를 대체하지 않으며, 어떤 scope를 활성화할지 선택하는 기준으로
사용한다. 하나의 작업에는 작업 유형과 관계없이 하나의 활성 scope만 적용한다.

### 1. 기능 구현

특정 도메인의 API, Application, Domain 또는 Infrastructure 구현을 변경하는
작업이다.

기본 수정 범위는 다음과 같다.

- 해당 도메인의 프로덕션 코드와 테스트 코드
- 해당 도메인의 전용 리소스
- 활성 기능 ID의 API 또는 WebSocket 명세

공용 설정 파일은 활성 scope의 허용 또는 공유 경로에 명시된 경우에만 수정한다.

### 2. 인프라 설정

Redis, PostgreSQL, OpenVidu, Docker와 같이 여러 도메인이 사용하는 기술 환경과
공통 설정을 구성하는 작업이다.

인프라 설정 작업에는 도메인 scope 대신 해당 기술의 인프라 scope를 사용한다. 기술
인프라 scope가 허용한 공용 파일이라도 현재 요청의 기술 영역과 직접 관련된 부분만
수정한다. 특정 도메인의 비즈니스 구현 변경이 필요하면 해당 파일과 이유를 보고하고
도메인 담당 승인을 받은 뒤 최소 범위만 변경한다.

Redis 컨테이너, Spring Data Redis 연결, 공통 직렬화, Redis 리소스 및 로컬 개발
환경 구성에는 다음 scope를 사용한다.

```text
작업 유형: Redis 인프라 설정
작업 범위: docs/agents/scopes/redis-infrastructure.scope.md
```

### 3. 운영 설정

로그 형식, 추적 ID, 모니터링, CI/CD와 배포 설정을 변경하는 작업이다. 등록된 운영
설정 scope의 허용 경로와 승인 규칙을 따른다. 적합한 운영 scope가 없으면 파일을
수정하지 않고 필요한 scope와 예상 변경 경로를 먼저 보고한다.

## Scope 지정 방법

작업 요청에 다음과 같이 사용할 scope 문서의 경로를 포함한다.

```text
작업 범위: docs/agents/scopes/auth.scope.md
대상 명세:
- docs/api/auth/mem-01-post-auth-oauth2-token-769776eb.md
기능 ID: MEM-01
요구사항: 카카오 로그인 성공 시 회원 생성 로직을 구현한다.
```

scope가 지정되지 않았거나 문서가 존재하지 않으면 에이전트는 코드를 수정하지 않고
필요한 scope 후보와 예상 수정 경로를 먼저 보고한다.

API 또는 WebSocket 작업에서는 대상 명세 파일과 기능 ID도 함께 지정한다. 활성
scope에 같은 도메인의 디렉터리가 포함되어 있더라도 등록되지 않은 기능 ID의 계약을
임의로 변경하지 않는다.

## 범위 판정 규칙

경로만으로 수정 가능 여부를 판단하지 않고 다음 조건을 모두 확인한다.

1. 현재 작업 유형에 적합한 활성 scope의 허용 경로인가
2. 현재 작업 목적과 직접 관련된 변경인가

경로가 허용되어 있어도 요청과 관련 없는 파일이나 설정은 수정하지 않는다. 예를 들어
Redis 설정 작업에서 `application.yml`을 수정할 수 있어도 PostgreSQL, JWT 또는
OpenVidu 설정은 함께 변경하지 않는다.

## 범위 밖 변경 대응

필요한 파일이 활성 scope에 포함되지 않으면 작업 전체를 즉시 종료하지 않고 다음
내용을 먼저 보고한다.

- 현재 작업 유형과 활성 scope
- 현재 수정 허용 범위
- 추가 수정이 필요한 파일
- 수정이 필요한 이유
- 예상 영향 범위
- 필요한 최소 변경안

사용자가 보고된 파일의 최소 변경을 명시적으로 승인하면 작업을 계속한다. 단, 보호
브랜치 작업, 비밀값 노출, 테스트 삭제·비활성화 또는 금지된 의존성 추가가 필요한
경우에는 승인 요청 없이 즉시 중단한다.

## 다른 도메인 변경

활성 scope가 소유하지 않은 다른 도메인의 파일을 수정해야 하면 수정 전에 사용자
승인을 받는다. 다른 도메인의 코드를 읽거나 이미 공개된 Application 계약을 호출하는
것만으로는 범위 확장이 필요하지 않다.

승인 요청은 다음 형식을 사용한다.

```text
범위 밖 변경 요청:
- 대상 파일:
- 필요한 이유:
- 현재 scope만으로 해결할 수 없는 이유:
- 예상 영향:
- 필요한 최소 변경:
- 요청하는 승인: 이번 작업에 한해 위 파일의 최소 변경 허용
```

- 대상은 다른 도메인 전체가 아니라 수정할 개별 파일 또는 최소 경로로 적는다.
- 사용자가 명시적으로 승인하기 전에는 대상 파일을 수정하지 않는다.
- 승인은 현재 작업과 보고한 대상에만 적용하며 이후 작업으로 승계하지 않는다.
- 승인받지 않은 추가 파일이 필요해지면 다시 보고하고 승인받는다.
- 사용자 승인은 scope 경계만 확장한다. 의존성 방향과 Infrastructure 직접 참조 같은
  아키텍처 금지 사항을 해제하지 않는다.

여러 도메인의 수정이 API 명세에서 처음부터 예상되는 경우에는 매번 범위 밖 승인을
추가하기보다 API 또는 유스케이스 단위의 기능 scope를 먼저 만든다.

```text
docs/agents/scopes/oauth-signup.scope.md
docs/agents/scopes/auction-settlement.scope.md
```

기능 scope에도 관련 도메인 전체 glob을 넣지 않고 필요한 API 명세, Application
계약, 구현 파일과 테스트 경로만 등록한다. 하나의 작업에는 이 기능 scope 하나만
활성화한다.

## Scope 문서 관리

scope 문서는 `docs/agents/scopes/` 아래에서 관리한다.

새 scope를 만들 때
[`_template.md`](./scopes/_template.md)를 복사한 뒤 다음 항목을 채운다.

- 담당 기능과 책임
- 수정 허용 경로
- 읽기 전용 경로
- 수정 금지 경로
- 조건부로 수정 가능한 공유 경로
- 담당 API와 WebSocket 이벤트
- 새 파일 생성 기준
- 검증 명령

사람 이름보다 장기간 유지되는 도메인이나 역할을 scope 이름으로 사용한다. 담당자
변경은 scope의 경로 규칙을 바꾸지 않고 담당자 항목만 변경한다.

같은 도메인을 여러 팀원이 나누어 담당하면 `docs/api/{domain}/**` 전체를 허용하지
않고 담당하는 개별 API 명세 파일을 각각 등록한다. 도메인 전체를 한 scope가 단독
소유할 때만 디렉터리 glob을 사용할 수 있다. 코드 경로도 같은 원칙을 적용하여
`src/main/java/.../{domain}/**` 대신 담당 하위 패키지나 개별 파일을 등록한다.

현재 패키지 구조에서 담당 기능을 안정적으로 구분할 하위 경로가 없다면 임의의
파일명 패턴으로 권한을 넓히지 않는다. 작업에 필요한 파일 후보를 먼저 정한 뒤
scope에 명시하거나, 팀 합의로 패키지 소유 경계를 먼저 결정한다.

## 등록된 Scope

| Scope | Notion 명세 담당자 | 책임 | 문서 |
| --- | --- | --- | --- |
| `auction` | 신희원, 김택림 | 경매방 생성·조회, 상태 전이, 종료·결과 및 경매 운영 | [`auction.scope.md`](./scopes/auction.scope.md) |
| `auth` | 신희원 | OAuth 로그인과 토큰 생명주기 | [`auth.scope.md`](./scopes/auth.scope.md) |
| `bid` | 김택림 | 입찰 명령, 최고 입찰가 및 실시간 입찰 이벤트 | [`bid.scope.md`](./scopes/bid.scope.md) |
| `chat` | 김경민 | 경매 채팅, 채팅 요약 및 실시간 채팅 이벤트 | [`chat.scope.md`](./scopes/chat.scope.md) |
| `common` | 김경민 | 공통 WebSocket 연결·인증·오류와 횡단 관심사 | [`common.scope.md`](./scopes/common.scope.md) |
| `openapi-infrastructure` | 인프라 담당자 | OpenAPI 문서 생성, Swagger UI와 공통 API 메타데이터 | [`openapi-infrastructure.scope.md`](./scopes/openapi-infrastructure.scope.md) |
| `redis-infrastructure` | 인프라 담당자 | Redis 실행 환경, 연결, 공통 직렬화 및 개발 문서 | [`redis-infrastructure.scope.md`](./scopes/redis-infrastructure.scope.md) |
| `observability` | 김경민 | 실시간 요청 추적, 구조화 로그와 Actuator·Micrometer 운영 지표 | [`observability.scope.md`](./scopes/observability.scope.md) |
| `oauth-auto-signup` | 신희원 | OAuth 신규 회원·소셜 계정·기본 지갑 자동 생성 | [`oauth-auto-signup.scope.md`](./scopes/oauth-auto-signup.scope.md) |
| `deal` | 신희원 | 거래 확정, 구매·판매 내역과 배송 정보 | [`deal.scope.md`](./scopes/deal.scope.md) |
| `media` | 이연주, 김경민 | 이미지 업로드와 OpenVidu 방송 세션·토큰 | [`media.scope.md`](./scopes/media.scope.md) |
| `member` | 신희원 | 회원 프로필, 약관 동의와 회원별 조회 | [`member.scope.md`](./scopes/member.scope.md) |
| `prediction` | 김택림 | AI 가격 예측 조회 | [`prediction.scope.md`](./scopes/prediction.scope.md) |
| `product` | 김택림, 이연주 | 상품 이미지 분석, 카테고리·상품 등록과 관리 | [`product.scope.md`](./scopes/product.scope.md) |
| `report` | 신희원 | 경매 신고 접수 | [`report.scope.md`](./scopes/report.scope.md) |
| `wallet` | 신희원, 이연주 | 포인트 잔액·내역·충전·환불 | [`wallet.scope.md`](./scopes/wallet.scope.md) |
