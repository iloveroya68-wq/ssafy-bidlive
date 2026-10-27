# Wallet Scope

이 scope는 `wallet` 도메인 전체를 하나의 담당 범위가 소유할 때 사용하는 기본 scope다.
같은 도메인을 여러 팀원이 나누어 담당하면 이 파일을 기능별 scope로 분리한다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `wallet` |
| Notion 명세 담당자 | 신희원, 이연주 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 거래·베팅 포인트 지갑·원장·정산 |
| API 색인 | [docs/api/wallet/README.md](../../api/wallet/README.md) |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/wallet/**`
- `src/test/java/com/ssafy/bidlive/wallet/**`
- `docs/api/wallet/**`

## Owned APIs

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `PNT-01` | `GET` | `/api/v1/members/me/wallets` | 확정 | [거래 포인트 지갑 조회](../../api/wallet/pnt-01-get-members-me-wallets-2e8776eb.md) |
| `PNT-01` | `POST` | `/api/v1/members/me/wallets/trade/charge` | 확정 | [거래 포인트 충전](../../api/wallet/pnt-01-post-members-me-wallets-trade-charge-4b6776eb.md) |
| `PNT-01` | `POST` | `/api/v1/members/me/wallets/trade/refunds` | 확정 | [거래 포인트 환불](../../api/wallet/pnt-01-post-members-me-wallets-trade-refunds-b98776eb.md) |
| `PNT-02` | `GET` | `/api/v1/members/me/point-ledgers` | 확정 | [포인트 원장 내역 조회](../../api/wallet/pnt-02-get-members-me-point-ledgers-499776eb.md) |
| `PNT-03` | `INTERNAL` | 내부 처리 | 확정 | [포인트 홀드·해제·정산 처리](../../api/wallet/pnt-03-internal-internal-556776eb.md) |
| `PNT-04` | `POST` | `/api/v1/payments/charge` | 확정 | [포인트 PG 테스트 결제 충전](../../api/wallet/pnt-04-post-payments-charge-f44776eb.md) |
| `PNT-05` | `INTERNAL` | 내부 처리 | 확정 | [베팅 포인트 지급 처리](../../api/wallet/pnt-05-internal-internal-fa7776eb.md) |

소유 목록에 있어도 상태가 `확정`이 아닌 계약은 확정된 것으로 가정해 구현하지 않는다.

## Allowed Changes

- `Wallet` API Controller, WebSocket 진입점과 DTO
- `Wallet` Application Service와 유스케이스
- `Wallet` Domain Model, 정책과 이벤트
- `Wallet` Infrastructure Adapter와 Repository 구현
- `Wallet` 테스트
- `docs/api/wallet/**` 명세

## Restricted Changes

- `src/main/java/com/ssafy/bidlive/**` 중 `wallet` 밖의 도메인
- `src/test/java/com/ssafy/bidlive/**` 중 `wallet` 밖의 테스트
- `docs/api/**` 중 `wallet` 밖의 명세
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
