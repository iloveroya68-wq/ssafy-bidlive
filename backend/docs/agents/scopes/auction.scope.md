# Auction Scope

이 scope는 `auction` 도메인 전체를 하나의 담당 범위가 소유할 때 사용하는 기본 scope다.
같은 도메인을 여러 팀원이 나누어 담당하면 이 파일을 기능별 scope로 분리한다.

## Scope Information

| 항목 | 값 |
| --- | --- |
| Scope ID | `auction` |
| Notion 명세 담당자 | 신희원, 김택림 |
| 실제 작업 담당자 | 작업 지시에서 지정 |
| 담당 기능 | 경매방 생성·조회, 상태 전이, 종료·결과 및 경매 운영 |
| API 색인 | [docs/api/auction/README.md](../../api/auction/README.md) |

## Owned Paths

- `src/main/java/com/ssafy/bidlive/auction/**`
- `src/test/java/com/ssafy/bidlive/auction/**`
- `docs/api/auction/**`

## Owned APIs

| 기능 ID | Method | Path / Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `AI-03` | `GET` | `/api/v1/auction-rooms/{auctionId}/catch-up-summary` | 확정 | [중간 입장 요약 조회](../../api/auction/ai-03-get-auction-rooms-by-auctionid-catch-up-summary-2ae776eb.md) |
| `BID-03` | `POST` | `/api/v1/auctions/{auctionId}/extensions` | 확정 | [판매자 방송 연장](../../api/auction/bid-03-post-auctions-by-auctionid-extensions-408776eb.md) |
| `BID-05` | `WS` | `/topic/auctions/{auctionId}` | 확정 | [서버 타이머 동기화](../../api/auction/bid-05-ws-topic-auctions-by-auctionid-4ef776eb.md) |
| `BID-07` | `INTERNAL` | 내부 처리 | 확정 | [비크리 엔진](../../api/auction/bid-07-internal-internal-c29776eb.md) |
| `DEAL-06, DEAL-08` | `GET` | `/api/v1/auction-rooms/{auctionId}/result` | 확정 | [경매 결과 조회](../../api/auction/deal-06-deal-08-get-auction-rooms-by-auctionid-result-800776eb.md) |
| `DEAL-09` | `POST` | `/api/v1/admin/auctions/{auctionId}/force-end` | 확정 | [운영 강제 종료](../../api/auction/deal-09-post-admin-auctions-by-auctionid-force-end-0c1776eb.md) |
| `ROOM-01` | `POST` | `/api/v1/auction-rooms` | 확정 | [경매방 생성](../../api/auction/room-01-post-auction-rooms-177776eb.md) |
| `ROOM-02` | `GET` | `/api/v1/auction-rooms` | 확정 | [경매방 목록 조회](../../api/auction/room-02-get-auction-rooms-98a776eb.md) |
| `ROOM-02` | `GET` | `/api/v1/auctions/categories` | 확정 | [경매방 카테고리 목록 조회](../../api/auction/room-02-get-auctions-categories-a23776eb.md) |
| `ROOM-03` | `GET` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 상세 조회](../../api/auction/room-03-get-auction-rooms-by-auctionid-323776eb.md) |
| `ROOM-03` | `GET` | ~~`/api/v1/auctions?categoryId=1&status=LIVE&size=20&cursor={cursor}`~~ | 🚫 폐기 (2026-07-30) | [카테고리별 경매방 목록 조회](../../api/auction/room-03-get-auctions-categoryid-1-status-live-size-20-cursor-by-cursor-293776eb.md) — ROOM-02 `GET /auction-rooms`가 완전히 포함한다. 구현하지 않는다 |
| `ROOM-04` | `DELETE` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 삭제](../../api/auction/room-04-delete-auction-rooms-by-auctionid-53b776eb.md) |
| `ROOM-04` | `PATCH` | `/api/v1/auction-rooms/{auctionId}` | 확정 | [경매방 수정](../../api/auction/room-04-patch-auction-rooms-by-auctionid-a9b776eb.md) |
| `ROOM-05, LIVE-02` | `POST` | `/api/v1/auction-rooms/{auctionId}/start` | 확정 | [경매 방송 시작](../../api/auction/room-05-live-02-post-auction-rooms-by-auctionid-start-4bf776eb.md) |
| `ROOM-07` | `POST` | `/api/v1/auction-rooms/{auctionId}/join` | 확정 | [대기방 입장 및 상태 조회](../../api/auction/room-07-post-auction-rooms-by-auctionid-join-9e0776eb.md) |
| `ROOM-08` | `GET` | `/api/v1/auction-rooms/upcoming` | 확정 | [곧 시작 경매 조회](../../api/auction/room-08-get-auction-rooms-upcoming-d7d776eb.md) |
| `ROOM-09` | `POST` | `/api/v1/auctions/{auctionId}/end` | 확정 | [판매자 경매 종료](../../api/auction/room-09-post-auctions-by-auctionid-end.md) |

## Owned WebSocket Events

| 연관 기능 ID | Event Type | Destination | 상태 | 명세 |
| --- | --- | --- | --- | --- |
| `BID-07, DEAL-09` | `AUCTION_CANCELED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-canceled.md) |
| `BID-07` | `AUCTION_ENDED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-ended.md) |
| `BID-03` | `AUCTION_EXTENDED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-extended.md) |
| `BID-07` | `AUCTION_NO_SALE` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-no-sale.md) |
| `—` | `AUCTION_PAUSED` | `/topic/auctions/{auctionId}` | 🚫 폐기 (2026-08-01) | [폐기 사유](../../api/auction/events/auction-paused.md) |
| `—` | `AUCTION_RESUMED` | `/topic/auctions/{auctionId}` | 🚫 폐기 (2026-08-01) | [폐기 사유](../../api/auction/events/auction-resumed.md) |
| `BID-07` | `AUCTION_SETTLED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-settled.md) |
| `ROOM-05` | `AUCTION_STARTED` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/auction-started.md) |
| `BID-05` | `TIME_SYNC` | `/topic/auctions/{auctionId}` | 확정 | [이벤트 명세](../../api/auction/events/time-sync.md) |

소유 목록에 있어도 상태가 `확정`이 아닌 계약은 확정된 것으로 가정해 구현하지 않는다.

## Allowed Changes

- `Auction` API Controller, WebSocket 진입점과 DTO
- `Auction` Application Service와 유스케이스
- `Auction` Domain Model, 정책과 이벤트
- `Auction` Infrastructure Adapter와 Repository 구현
- `Auction` 테스트
- `docs/api/auction/**` 명세

## Restricted Changes

- `src/main/java/com/ssafy/bidlive/**` 중 `auction` 밖의 도메인
- `src/test/java/com/ssafy/bidlive/**` 중 `auction` 밖의 테스트
- `docs/api/**` 중 `auction` 밖의 명세
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
