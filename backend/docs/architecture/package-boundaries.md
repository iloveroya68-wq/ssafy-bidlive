# Package Boundaries

> 이 문서는 프로젝트의 패키지 책임, 계층 간 의존성 방향, 파일 배치 기준을 정의합니다.

## 1. 아키텍처 개요

이 프로젝트는 다음 두 가지 원칙을 따릅니다.

1. 기능을 기준으로 도메인을 분리한다.
2. 각 도메인 내부를 `API → Application → Domain ← Infrastructure` 계층으로 분리한다.

핵심 비즈니스 규칙은 `domain`에 두고, PostgreSQL/JPA, Redis, OpenVidu, 외부 API 등 기술 구현은 `infrastructure`에 둡니다.

```text
외부 요청
   │
   ▼
 api ──▶ application ──▶ domain
              │             ▲
              ▼             │
       port 인터페이스       │
              ▲             │
              │             │
       infrastructure ──────┘
       ├── persistence
       ├── redis
       └── openvidu
```

### 기본 의존성 원칙

- `api`는 `application`을 호출합니다.
- `application`은 `domain`을 사용하여 유스케이스를 실행합니다.
- `infrastructure`는 `application` 또는 `domain`이 정의한 계약을 구현합니다.
- `domain`은 `api`, `infrastructure`, HTTP, Redis, OpenVidu 등의 외부 기술에 의존하지 않습니다.
- Redis 또는 JPA 구현체를 `application`에서 직접 사용하지 않습니다.
- 외부 기술이 필요한 경우 인터페이스를 정의하고 `infrastructure`에서 구현합니다.

---

## 2. 도메인 패키지

| 도메인 | 담당 기능 |
|---|---|
| `auth` | 인증 및 인가 기능 |
| `member` | 회원 기능 |
| `product` | 상품 기능 |
| `auction` | 경매 진행, 상태 전이, 경매 결과 및 정산 |
| `bid` | 입찰 기능 |
| `prediction` | 최종가 예측 기능 |
| `chat` | 채팅 기능 |
| `media` | 미디어 및 방송 기능 |
| `wallet` | 거래·베팅 포인트 지갑 기능 |
| `deal` | 낙찰 이후 거래 기능 |
| `report` | 신고 기능 |

도메인에 종속된 코드는 반드시 해당 도메인 아래에 둡니다. 여러 도메인이 공유한다는 이유만으로 비즈니스 규칙을 `common`으로 이동하지 않습니다.

---

## 3. 도메인 내부 계층

아래의 `{domain}`에는 `auth`, `member`, `product`, `auction` 등의 도메인명이 들어갑니다.

### `{domain}.api`

REST API 및 WebSocket/STOMP 요청을 받는 외부 진입점입니다.

#### 포함 대상

- Controller
- WebSocket/STOMP Message Mapping
- 요청 데이터 검증
- 인증 사용자 정보 추출 및 전달
- Application Service 호출
- HTTP 상태 및 응답 반환

#### 포함하지 않는 대상

- 핵심 비즈니스 규칙
- JPA Repository 직접 호출
- Redis 직접 호출
- OpenVidu SDK 직접 호출
- 복잡한 트랜잭션 흐름

---

### `{domain}.api.dto`

HTTP, WebSocket 및 외부 API의 요청·응답 데이터 계약을 정의합니다.

#### 포함 대상

- Request DTO
- Response DTO
- WebSocket Event Payload
- 외부 입력 형식 검증 규칙
- API 계층 전용 변환 메서드

#### 목적

- 내부 도메인 객체가 외부에 직접 노출되는 것을 방지합니다.
- JPA Entity가 API 응답 모델로 직접 사용되는 것을 방지합니다.
- 외부 계약 변경이 핵심 도메인까지 전파되는 범위를 줄입니다.

---

### `{domain}.application`

유스케이스를 실행하고 전체 흐름을 조율합니다.

#### 포함 대상

- Command Service
- Query Service
- Use Case
- 트랜잭션 경계
- Repository 인터페이스 호출
- 도메인 객체의 행위 실행
- 외부 기술 호출 순서 조율
- 이벤트 발행
- 여러 도메인 또는 인프라 컴포넌트 간 오케스트레이션

#### 작성 원칙

- 비즈니스 규칙 자체는 가능한 한 `domain`에 둡니다.
- JPA, Redis, OpenVidu의 구체 클래스를 직접 참조하지 않습니다.
- 필요한 기능은 인터페이스를 통해 사용합니다.
- Controller에 있어서는 안 되는 흐름 제어를 담당합니다.

---

### `{domain}.domain`

핵심 비즈니스 모델과 규칙을 정의합니다.

#### 포함 대상

- Entity
- Aggregate
- Value Object
- Enum
- 상태 전이 규칙
- 정책 및 검증 규칙
- 계산기
- 도메인 서비스
- 도메인 이벤트
- Repository 및 Gateway 추상화

#### 작성 원칙

- Spring MVC, Spring Data JPA, Spring Data Redis, OpenVidu SDK에 의존하지 않습니다.
- HTTP 요청·응답 형식을 알지 못해야 합니다.
- DB 테이블 구조보다 비즈니스 개념을 우선합니다.
- 유효하지 않은 상태가 생성되거나 유지되지 않도록 도메인 내부에서 보호합니다.

---

### `{domain}.infrastructure`

DB, Redis, OpenVidu, 외부 API 등 실제 기술 구현을 모으는 상위 패키지입니다.

#### 역할

- 외부 기술의 세부사항을 상위 계층으로부터 격리합니다.
- `application` 또는 `domain`에서 정의한 인터페이스를 구현합니다.
- 기술별 하위 패키지를 묶는 경계로 사용합니다.

---

### `{domain}.infrastructure.persistence`

PostgreSQL/JPA 기반 영속성 구현을 담당합니다.

#### 포함 대상

- JPA Entity
- Spring Data JPA Repository
- QueryDSL 구현
- Projection
- Persistence Adapter
- Domain–JPA Mapper
- DB 조회 및 저장 구현

#### 포함하지 않는 대상

- Controller
- API DTO
- 핵심 비즈니스 정책
- Redis 상태 처리
- OpenVidu 세션 처리

---

### `{domain}.infrastructure.redis`

Redis 기반 실시간 상태와 원자적 처리를 담당합니다.

#### 포함 대상

- Redis Repository 및 Adapter
- Redis Key 정의와 생성 규칙
- Lua Script
- 원자적 연산
- 동시성 제어
- 멱등성 처리
- Pub/Sub
- Redis Stream
- 실시간 상태 저장 및 조회
- Redis 직렬화 관련 구현

#### 작성 원칙

- Redis Key 문자열을 여러 파일에서 직접 하드코딩하지 않습니다.
- Key 생성 책임을 한곳에 모읍니다.
- 원자성이 필요한 연산을 여러 Redis 명령으로 분리하지 않습니다.
- Lua Script의 입력, 출력, 실패 코드를 명확하게 정의합니다.

---

### `{domain}.infrastructure.openvidu`

OpenVidu 연동을 담당합니다.

#### 포함 대상

- OpenVidu 세션 생성 및 종료
- 참가 토큰 발급
- 참가자 조회
- OpenVidu SDK 호출
- OpenVidu 응답을 내부 모델로 변환하는 Adapter
- OpenVidu 예외 변환

#### 작성 원칙

- OpenVidu SDK 타입을 `domain`까지 전달하지 않습니다.
- SDK 호출 결과는 내부에서 사용하는 모델로 변환합니다.
- API Controller가 OpenVidu SDK를 직접 호출하지 않습니다.

---

## 4. `common` 패키지

`common`은 여러 도메인이 공유하는 횡단 관심사와 기반 코드를 위한 패키지입니다.

특정 도메인의 비즈니스 규칙을 `common`에 두지 않습니다. 두 곳 이상에서 사용된다는 이유만으로 무조건 공통화하지 않으며, 실제로 도메인과 무관한 개념인지 먼저 판단합니다.

| 패키지 | 역할 |
|---|---|
| `common` | 공통 기반 코드의 최상위 경계 |
| `common.config` | 애플리케이션 실행 환경, 공통 Bean, Spring 및 외부 기술 설정 |
| `common.security` | HTTP 인증 정보 추출·검증, 인증 사용자 식별, 공통 인증·인가 기반 기능 |
| `common.response` | 성공, 오류, 페이지네이션 및 메타데이터 등 공통 API 응답 구조 |
| `common.exception` | 공통 예외 표현, 오류 정책 및 API 오류 응답 변환 |
| `common.event` | 도메인·애플리케이션 간 이벤트 계약, 이벤트 발행 추상화 및 공통 메타데이터 |
| `common.support` | 특정 도메인에 속하지 않는 보조 객체, 공통 값 타입 및 시스템 기능 추상화 |

### `common` 배치 판단 기준

다음 질문에 모두 해당할 때만 `common` 배치를 고려합니다.

1. 특정 도메인의 비즈니스 의미를 포함하지 않는가?
2. 여러 도메인에서 동일한 의미와 방식으로 사용되는가?
3. 변경 이유가 특정 도메인의 정책 변경이 아닌가?
4. 해당 코드가 없어도 도메인의 핵심 규칙을 설명할 수 있는가?

하나라도 충족하지 않으면 해당 도메인 내부에 두는 것을 우선합니다.

---

## 5. 인터페이스 배치 기준

- 도메인 객체의 저장·조회 계약은 `domain`에 둡니다.
- 특정 유스케이스에서만 필요한 외부 기능 계약은 `application.port.out`에 둡니다.
- Controller가 호출하는 유스케이스 계약이 필요하면 `application.port.in`에 둡니다.
- Infrastructure는 위 인터페이스를 구현합니다.

---

## 6. 파일 배치 판단 순서

새 파일을 만들기 전에 다음 순서로 위치를 결정합니다.

1. 어느 도메인의 책임인지 판단합니다.
2. 외부 요청 또는 응답 계약이면 `api` 또는 `api.dto`에 둡니다.
3. 유스케이스 흐름 조율이면 `application`에 둡니다.
4. 핵심 비즈니스 규칙이면 `domain`에 둡니다.
5. PostgreSQL/JPA 구현이면 `infrastructure.persistence`에 둡니다.
6. Redis 구현이면 `infrastructure.redis`에 둡니다.
7. OpenVidu 구현이면 `infrastructure.openvidu`에 둡니다.
8. 특정 도메인과 무관한 횡단 관심사일 때만 `common`을 검토합니다.

위 기준으로 결정할 수 없는 경우 임의로 새 패키지를 만들지 말고 필요한 책임과 의존성을 먼저 설명합니다.

---

## 7. 도메인 간 의존성

- 한 도메인의 API 또는 Infrastructure가 다른 도메인의 Infrastructure를 직접 참조하지 않습니다.
- 다른 도메인의 데이터가 필요하면 해당 도메인의 Application Service 또는 공개된 계약을 사용합니다.
- 여러 도메인에 걸친 흐름은 Application 계층에서 조율합니다.
- 도메인 간 결합을 줄일 수 있는 경우 이벤트를 사용합니다.
- 다른 도메인의 JPA Entity와 Redis Adapter를 직접 사용하지 않습니다.

---

## 8. 변경 범위 관리 원칙

작업을 수행하면서 허용된 범위 밖의 파일 수정이 필요한 경우 다음 절차를 따릅니다.

1. 수정이 필요한 이유를 먼저 보고합니다.
2. 허용되지 않은 파일을 임의로 수정하지 않습니다.
3. 필요한 최소 수정 범위를 제안합니다.
4. 승인된 범위만 변경합니다.

리팩터링이 유익하더라도 현재 작업의 요구사항과 직접 관련이 없다면 함께 수행하지 않습니다.

---

## 9. 구현 완료 기준

작업 완료 시 다음 조건을 모두 만족해야 합니다.

- [ ] 요구된 기능이 구현되었습니다.
- [ ] 허용된 파일 범위만 수정했습니다.
- [ ] 패키지 의존성 규칙을 위반하지 않았습니다.
- [ ] 기존 테스트가 모두 통과합니다.
- [ ] 새 기능 또는 변경된 동작에 대한 테스트가 추가되었습니다.
- [ ] 빌드가 성공합니다.
- [ ] 관련 문서가 갱신되었습니다.
- [ ] 사용하지 않는 코드와 임시 코드가 없습니다.
- [ ] 외부 기술의 구체 구현이 `domain`에 노출되지 않습니다.
- [ ] 예외와 실패 경로가 명시적으로 처리되었습니다.

---

## 10. 아키텍처 위반 사례

다음 중 하나라도 해당하면 작업은 완료된 것으로 간주하지 않습니다.

- `api`가 Redis 또는 Persistence 구현을 직접 호출합니다.
- `domain`이 Spring Data Redis, Spring Data JPA 또는 OpenVidu를 import합니다.
- `application`이 JPA Repository, RedisTemplate 또는 OpenVidu SDK 구현체를 직접 사용합니다.
- 허용 범위 밖 파일을 임의로 수정합니다.
- 테스트를 삭제하거나 비활성화하여 통과시킵니다.
- 빌드 또는 테스트가 실패합니다.
- Redis Key를 여러 파일에서 문자열로 하드코딩합니다.
- 예외를 무시하거나 빈 `catch` 블록을 사용합니다.
- `TODO`만 남기고 작업을 완료 처리합니다.
- JPA Entity를 API 응답으로 직접 반환합니다.
- 핵심 비즈니스 규칙이 Controller 또는 Infrastructure에만 존재합니다.

---

## 11. 변경 사항 기록 형식

작업을 완료한 뒤 다음 내용을 요약합니다.

```text
변경 목적:
- 무엇을 구현하거나 수정했는지

수정 파일:
- 변경한 파일 목록과 각 파일의 역할

아키텍처 판단:
- 파일을 해당 패키지에 배치한 이유
- 계층 간 의존성이 어떤 방향으로 연결되는지

검증 결과:
- 실행한 테스트
- 빌드 결과
- 확인하지 못한 항목

남은 사항:
- 후속 작업 또는 알려진 제한
```
